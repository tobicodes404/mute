package com.tobicodes.mute.ui.reader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.view.WindowManager
import android.graphics.Color
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.tobicodes.mute.MuteApplication
import com.tobicodes.mute.R
import com.tobicodes.mute.core.ocr.OcrEngine
import com.tobicodes.mute.core.tts.NeuralTtsController
import com.tobicodes.mute.core.tts.Sentence
import com.tobicodes.mute.core.tts.TtsEngineHelper
import com.tobicodes.mute.core.tts.TtsManager
import com.tobicodes.mute.core.tts.VoiceService
import com.tobicodes.mute.core.util.PdfTextExtractor
import com.tobicodes.mute.core.util.SentenceSplitter
import com.tobicodes.mute.databinding.FragmentReaderBinding
import com.tobicodes.mute.domain.model.Document
import com.tobicodes.mute.domain.model.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReaderFragment : Fragment() {
    private var _binding: FragmentReaderBinding? = null
    private val binding get() = _binding!!

    private val args: ReaderFragmentArgs by navArgs()
    private val app get() = requireActivity().application as MuteApplication

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var currentPage = 0
    private var totalPages = 0
    private var currentBitmap: Bitmap? = null
    private var cachedDocument: Document? = null

    private var textLines: List<String> = emptyList()
    private val linesPerPage = 40
    private var renderScale = 2.0f
    private var baseTextSize = 16f
    private var controlsVisible = true

    // System TTS
    private lateinit var ttsManager: TtsManager
    private var voiceSentences: List<Sentence> = emptyList()
    private val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 1
    private val pitches = floatArrayOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)
    private var pitchIndex = 1

    // Neural TTS
    private var neural: NeuralTtsController? = null
    private var useNeural = false

    // Service binding
    private var voiceService: VoiceService? = null
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            voiceService = (service as VoiceService.VoiceBinder).getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
            serviceBound = false
        }
    }

    private val notificationReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "mute.voice.toggle" -> if (useNeural) neural?.playPause() else ttsManager.playPause()
                "mute.voice.prev" -> if (useNeural) neural?.previousSentence() else ttsManager.previousSentence()
                "mute.voice.next" -> if (useNeural) neural?.nextSentence() else ttsManager.nextSentence()
            }
        }
    }

    private var detectedLocale: Locale? = null
    private var langWarningShown = false
    private var pdfBoxPages: List<String> = emptyList()
    private var ocrRunning = false
    private var ocrDone = 0
    private var ocrTotal = 0
    private var resumeSentenceIndex = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val density = resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_MEDIUM
        renderScale = density.coerceAtLeast(2.0f)

        ttsManager = TtsManager(requireContext())
        initTts()
        neural = NeuralTtsController(requireContext())

        setupControls()
        binding.ivPage.setOnClickListener { toggleControls() }
        binding.tvTextContent.setOnClickListener { toggleControls() }
        setupVoiceBar()
        applyReadingPrefs()
        if (!requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE).getBoolean("voice_enabled", true)) {
            binding.voiceBar.visibility = View.GONE
        }
        observeVoice()
        bindVoiceService()
        loadDocument()

        val filter = android.content.IntentFilter().apply {
            addAction("mute.voice.toggle")
            addAction("mute.voice.prev")
            addAction("mute.voice.next")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(notificationReceiver, filter)
        }
    }

    // ---------- TTS Init ----------

    private fun initTts() {
        val engine = TtsEngineHelper.savedEngine(requireContext())
        ttsManager.init(engine) { ok ->
            if (!ok) {
                Toast.makeText(
                    requireContext(),
                    if (engine != null) "Saved engine failed — using system default"
                    else "No TTS engine available",
                    Toast.LENGTH_LONG
                ).show()
                if (engine != null) {
                    TtsEngineHelper.saveEngine(requireContext(), null)
                    ttsManager.init(null) { }
                }
            } else {
                detectedLocale?.let { ttsManager.configureFor(it) }
                val pp = requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE)
                ttsManager.setSpeed(floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)[pp.getInt("speed_idx", 1)])
                ttsManager.setPitch(floatArrayOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)[pp.getInt("pitch_idx", 1)])
                ttsManager.load(voiceSentences)
            }
        }
    }

    // ---------- Service ----------

    private fun bindVoiceService() {
        val intent = Intent(requireContext(), VoiceService::class.java)
        requireContext().startService(intent)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updateNotification() {
        val svc = voiceService ?: return
        val doc = cachedDocument ?: return
        val state = if (useNeural) (neural?.state?.value ?: TtsManager.State.IDLE) else ttsManager.state.value
        val idx = if (useNeural) (neural?.currentIndex?.value ?: -1) else ttsManager.currentIndex.value
        val preview = if (idx in voiceSentences.indices) voiceSentences[idx].text.take(80) else ""
        val mode = if (useNeural) "🧠 Neural" else "🔊 System"
        val progress = if (voiceSentences.isNotEmpty()) "$mode • ${idx + 1}/${voiceSentences.size}" else mode
        svc.updateNotification(state, doc.title, preview, progress)
    }

    // ---------- Controls ----------

    private fun setupControls() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_zoom_in -> { renderScale = (renderScale + 0.5f).coerceAtMost(5.0f); applyZoom(); true }
                R.id.action_zoom_out -> { renderScale = (renderScale - 0.5f).coerceAtLeast(1.0f); applyZoom(); true }
                R.id.action_search -> { Toast.makeText(requireContext(), "Search coming soon", Toast.LENGTH_SHORT).show(); true }
                R.id.action_voice_engine -> { showEnginePicker(); true }
                R.id.action_document_info -> { showDocumentInfo(); true }
                R.id.action_reading_settings -> { showReadingSettings(); true }
                else -> false
            }
        }

        binding.btnPrevious.setOnClickListener { if (currentPage > 0) { currentPage--; showCurrentPage() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; showCurrentPage() } }
        binding.btnFirstPage.setOnClickListener { currentPage = 0; showCurrentPage() }
        binding.btnLastPage.setOnClickListener { currentPage = (totalPages - 1).coerceAtLeast(0); showCurrentPage() }

        binding.btnBookmark.setOnClickListener {
            val doc = cachedDocument ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                val added = app.bookmarkRepository.toggleBookmark(doc.id, currentPage, buildPreview())
                Toast.makeText(
                    requireContext(),
                    if (added) "Bookmark added — page ${currentPage + 1}" else "Bookmark removed",
                    Toast.LENGTH_SHORT
                ).show()
                updateBookmarkIcon()
            }
        }

        binding.btnGoToPage.setOnClickListener {
            val target = binding.etPageJump.text.toString().toIntOrNull()
            if (target != null && target in 1..totalPages) {
                currentPage = target - 1
                showCurrentPage()
                binding.etPageJump.text?.clear()
            } else {
                Toast.makeText(requireContext(), "Enter a page between 1 and $totalPages", Toast.LENGTH_SHORT).show()
            }
        }


    }

    private fun setupVoiceBar() {
        binding.btnVoicePlay.setOnClickListener {
            if (useNeural) {
                neuralPlayPause()
                return@setOnClickListener
            }
            if (voiceSentences.isEmpty()) {
                val msg = if (ocrRunning) "OCR in progress: $ocrDone/$ocrTotal pages"
                else "No readable text found."
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            detectedLocale?.let { ttsManager.configureFor(it) }
            ttsManager.playPause()
        }
        binding.btnVoicePrev.setOnClickListener {
            if (useNeural) neural?.previousSentence() else ttsManager.previousSentence()
        }
        binding.btnAutoNext.setOnClickListener {
            val p = requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE)
            val current = p.getBoolean("auto_next", true)
            p.edit().putBoolean("auto_next", !current).apply()
            binding.btnAutoNext.setColorFilter(
                if (!current) android.graphics.Color.parseColor("#6E6E76") 
                else android.graphics.Color.parseColor("#ECECEF"),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            Toast.makeText(requireContext(), if (!current) "Auto-next OFF" else "Auto-next ON", Toast.LENGTH_SHORT).show()
        }
        // Initialize auto-next icon state
        val pInit = requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE)
        binding.btnAutoNext.setColorFilter(
            if (!pInit.getBoolean("auto_next", true)) android.graphics.Color.parseColor("#6E6E76") 
            else android.graphics.Color.parseColor("#ECECEF"),
            android.graphics.PorterDuff.Mode.SRC_IN
        )

        binding.btnVoiceNext.setOnClickListener {
            if (useNeural) neural?.nextSentence() else ttsManager.nextSentence()
        }
        binding.tvVoiceSpeed.setOnClickListener {
            speedIndex = (speedIndex + 1) % speeds.size
            val s = speeds[speedIndex]
            ttsManager.setSpeed(s)
            binding.tvVoiceSpeed.text = "${"%.2f".format(s).trimEnd('0').trimEnd('.')}x"
            requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE)
                .edit().putInt("speed_idx", speedIndex).apply()
        }
        binding.tvVoicePitch.setOnClickListener {
            pitchIndex = (pitchIndex + 1) % pitches.size
            val p = pitches[pitchIndex]
            ttsManager.setPitch(p)
            binding.tvVoicePitch.text = "♪ ${"%.1f".format(p)}"
        }
    }

    // ---------- Neural ----------

    private fun toggleNeural() {
        if (!useNeural) {
            val ctrl = neural ?: return
            if (!ctrl.init()) {
                Toast.makeText(
                    requireContext(),
                    "Neural model not found. Push it first: adb push vits-bn-female /sdcard/Download/",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            useNeural = true
            ttsManager.pause()
            ctrl.load(voiceSentences)
            binding.tvVoiceSentence.text = "🧠 Neural Bangla voice ready • tap ▶"
            Toast.makeText(requireContext(), "Neural Bangla voice ON", Toast.LENGTH_SHORT).show()
        } else {
            useNeural = false
            neural?.stop()
            Toast.makeText(requireContext(), "System TTS mode", Toast.LENGTH_SHORT).show()
        }
        updateVoiceBarStatus()
    }

    private fun neuralPlayPause() {
        val ctrl = neural ?: return
        if (voiceSentences.isEmpty()) {
            Toast.makeText(requireContext(), "No sentences ready yet", Toast.LENGTH_LONG).show()
            return
        }
        ctrl.playPause()
    }

    // ---------- Engine picker ----------

    private fun showEnginePicker() {
        val engines = TtsEngineHelper.installedEngines(requireContext())
        if (engines.isEmpty()) {
            Toast.makeText(requireContext(), "No TTS engine installed", Toast.LENGTH_LONG).show()
            return
        }
        val saved = TtsEngineHelper.savedEngine(requireContext())
        val names = engines.map { e ->
            val mark = if (e.packageName == saved) " ✓" else ""
            "${e.label}$mark"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Choose Voice Engine")
            .setItems(names) { _, which ->
                TtsEngineHelper.saveEngine(requireContext(), engines[which].packageName)
                ttsManager.shutdown()
                ttsManager = TtsManager(requireContext())
                initTts()
                Toast.makeText(requireContext(), "Voice engine: ${engines[which].label}", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("System default") { _, _ ->
                TtsEngineHelper.saveEngine(requireContext(), null)
                ttsManager.shutdown()
                ttsManager = TtsManager(requireContext())
                initTts()
            }
            .show()
    }

    // ---------- Voice observation ----------

    private fun observeVoice() {
        viewLifecycleOwner.lifecycleScope.launch {
            ttsManager.state.collect { st ->
                if (!useNeural) {
                    binding.btnVoicePlay.setImageResource(
                        if (st == TtsManager.State.PLAYING) R.drawable.ic_pause
                        else R.drawable.ic_play_arrow
                    )
                    updateNotification()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ttsManager.currentIndex.collect { idx ->
                if (!useNeural && idx in voiceSentences.indices) {
                    val sentence = voiceSentences[idx]
                    binding.tvVoiceSentence.text = sentence.text
                    updateNotification()
                    viewLifecycleOwner.lifecycleScope.launch {
                        try { app.documentRepository.updateVoicePosition(args.documentId, idx) } catch (_: Exception) {}
                    }
                    if (sentence.pageIndex != currentPage && sentence.pageIndex < totalPages) {
                        if (requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE).getBoolean("auto_next", true)) {
                            currentPage = sentence.pageIndex
                            showCurrentPage()
                        } else {
                            if (useNeural) neural?.pause() else ttsManager.pause()
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            neural?.state?.collect { st ->
                if (useNeural) {
                    binding.btnVoicePlay.setImageResource(
                        if (st == TtsManager.State.PLAYING) R.drawable.ic_pause
                        else R.drawable.ic_play_arrow
                    )
                    updateNotification()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            neural?.currentIndex?.collect { idx ->
                if (useNeural && idx in voiceSentences.indices) {
                    val sentence = voiceSentences[idx]
                    binding.tvVoiceSentence.text = sentence.text
                    updateNotification()
                    viewLifecycleOwner.lifecycleScope.launch {
                        try { app.documentRepository.updateVoicePosition(args.documentId, idx) } catch (_: Exception) {}
                    }
                    if (sentence.pageIndex != currentPage && sentence.pageIndex < totalPages) {
                        if (requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE).getBoolean("auto_next", true)) {
                            currentPage = sentence.pageIndex
                            showCurrentPage()
                        } else {
                            if (useNeural) neural?.pause() else ttsManager.pause()
                        }
                    }
                }
            }
        }
    }

    // ---------- Language ----------

    private fun configureLanguage(sample: String) {
        val bengaliChars = sample.count { it in 'ঀ'..'৿' }
        val totalChars = sample.count { it.isLetter() }
        if (totalChars == 0) return
        val locale = if (bengaliChars * 2 > totalChars) Locale("bn") else Locale.ENGLISH
        if (locale == detectedLocale) return
        detectedLocale = locale
        ttsManager.configureFor(locale)
        if (locale.language == "bn" && !langWarningShown) {
            langWarningShown = true
            AlertDialog.Builder(requireContext())
                .setTitle("Bangla voice not installed")
                .setMessage("To listen in Bangla, install a Bangla TTS voice: Google TTS settings → Install voice data → Bangla.")
                .setPositiveButton("Open TTS settings") { _, _ ->
                    runCatching {
                        startActivity(android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                    }
                }
                .setNegativeButton("Later", null)
                .show()
        }
        updateVoiceBarStatus()
    }

    // ---------- Helpers ----------

    private fun applyZoom() {
        when (cachedDocument?.type) {
            DocumentType.PDF -> renderPdfPage()
            DocumentType.TXT -> binding.tvTextContent.textSize = baseTextSize * (renderScale / 2f)
            else -> {}
        }
        Toast.makeText(requireContext(), "Zoom: ${"%.1f".format(renderScale)}x", Toast.LENGTH_SHORT).show()
    }

    private fun buildPreview(): String = when (cachedDocument?.type) {
        DocumentType.TXT -> textLines.drop(currentPage * linesPerPage).take(2).joinToString(" ").take(120)
        else -> "Page ${currentPage + 1}"
    }

    private fun updateBookmarkIcon() {
        val doc = cachedDocument ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val exists = app.bookmarkRepository.getBookmark(doc.id, currentPage) != null
            // Update bookmark icon in Toolbar menu
            val menuItem = binding.toolbar.menu.findItem(R.id.action_bookmark)
            if (menuItem != null) {
                menuItem.setIcon(if (exists) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
            }
        }
    }

    private fun toggleControls() {
        controlsVisible = !controlsVisible
        val v = if (controlsVisible) View.VISIBLE else View.GONE
        binding.toolbar.visibility = v
        // // // binding.tvTitle.visibility = v // Handled by Toolbar // Handled by Toolbar
        binding.tvPageInfo.visibility = v
        binding.navigationControls.visibility = v
        binding.progressBar.visibility = v
        binding.voiceBar.visibility = v

        val window = requireActivity().window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (controlsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun updateVoiceBarStatus() {
        binding.voiceBar.visibility = View.VISIBLE
        val mode = if (useNeural) " 🧠" else ""
        val lang = when (detectedLocale?.language) {
            "bn" -> " • BN voice"
            "en" -> " • EN voice"
            else -> ""
        }
        binding.tvVoiceSentence.text = when {
            ocrRunning -> "OCR: $ocrDone/$ocrTotal pages • ${voiceSentences.size} sentences$lang$mode"
            voiceSentences.isNotEmpty() -> "${voiceSentences.size} sentences ready$lang$mode • tap ▶"
            else -> "No selectable text found in this PDF."
        }
    }

    // ---------- Document loading ----------

    private fun loadDocument() {
        viewLifecycleOwner.lifecycleScope.launch {
            val document = app.documentRepository.getDocumentById(args.documentId)
            if (document == null) {
                Toast.makeText(requireContext(), "Document not found in database", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
                return@launch
            }
            cachedDocument = document
            binding.tvTitle.text = document.title

            val prefs0 = requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE)
            val remember = prefs0.getBoolean("remember_position", true)
            val startPage = if (args.startPage >= 0) args.startPage else if (remember) document.currentPage else 0
            val startSentence = if (remember) document.lastSentenceIndex else -1

            try {
                when (document.type) {
                    DocumentType.PDF -> loadPdf(Uri.parse(document.uriString), startPage, startSentence)
                    DocumentType.TXT -> loadTxt(Uri.parse(document.uriString), startPage, startSentence)
                    DocumentType.EPUB -> {
                        Toast.makeText(requireContext(), "EPUB support coming soon", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                }
            } catch (e: SecurityException) {
                // URI permission lost after app restart
                Toast.makeText(
                    requireContext(),
                    "Permission lost. Please re-upload the file.",
                    Toast.LENGTH_LONG
                ).show()
                app.documentRepository.deleteDocument(document)
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error opening document: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                findNavController().navigateUp()
            }
        }
    }

    private suspend fun loadPdf(uri: Uri, startPage: Int, startSentence: Int) = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("mute_pdf_", ".pdf", requireContext().cacheDir)
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            parcelFileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
            totalPages = pdfRenderer!!.pageCount
            currentPage = startPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

            pdfBoxPages = PdfTextExtractor.extractPages(requireContext(), uri)

            withContext(Dispatchers.Main) {
                binding.svPdf.visibility = View.VISIBLE
                binding.svText.visibility = View.GONE
                binding.progressBar.max = totalPages
                showCurrentPage()
                startTextPreparation(uri, startSentence)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun loadTxt(uri: Uri, startPage: Int, startSentence: Int) = withContext(Dispatchers.IO) {
        try {
            val text = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            textLines = text.lines()
            totalPages = ((textLines.size + linesPerPage - 1) / linesPerPage).coerceAtLeast(1)
            currentPage = startPage.coerceIn(0, totalPages - 1)

            val pages = (0 until totalPages).map { p ->
                val s = p * linesPerPage
                val e = minOf(s + linesPerPage, textLines.size)
                if (s < textLines.size) textLines.subList(s, e).joinToString(" ") else ""
            }

            withContext(Dispatchers.Main) {
                binding.svPdf.visibility = View.GONE
                binding.svText.visibility = View.VISIBLE
                binding.progressBar.max = totalPages
                showCurrentPage()
                buildVoiceFromPages(pages, startSentence)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------- OCR ----------

    private fun startTextPreparation(uri: Uri, resumeSentence: Int) {
        val doc = cachedDocument ?: return
        val cacheDir = File(requireContext().cacheDir, "ocr/${doc.id}")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        // If PdfBox extracted text, use that (fast path)
        if (pdfBoxPages.isNotEmpty() && pdfBoxPages.any { it.length > 50 }) {
            val cleaned = pdfBoxPages
            buildVoiceFromPages(cleaned, resumeSentence)
            return
        }

        // Otherwise fallback to OCR (scanned PDF)
        ocrRunning = true
        ocrTotal = totalPages
        ocrDone = 0
        val startFrom = currentPage
        resumeSentenceIndex = resumeSentence
        updateVoiceBarStatus()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val engine = OcrEngine(requireContext())
            if (!engine.isReady) {
                withContext(Dispatchers.Main) {
                    ocrRunning = false
                    updateVoiceBarStatus()
                    Toast.makeText(requireContext(), "OCR engine not ready", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val tempFile = File.createTempFile("mute_ocr_", ".pdf", requireContext().cacheDir)
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            var pfd: ParcelFileDescriptor? = null
            var ocrRenderer: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                ocrRenderer = PdfRenderer(pfd)

                val order = mutableListOf<Int>()
                var fwd = startFrom
                while (fwd < ocrRenderer.pageCount) { order.add(fwd); fwd++ }
                var bwd = startFrom - 1
                while (bwd >= 0) { order.add(bwd); bwd-- }

                for (i in order) {
                    val cacheFile = File(cacheDir, "page_$i.txt")
                    val text = when {
                        cacheFile.exists() -> cacheFile.readText()
                        else -> ocrRenderer.openPage(i).use { page ->
                            val scale = (2200f / page.height).coerceIn(2.0f, 4.0f)
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt(),
                                (page.height * scale).toInt(),
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val t = engine.recognize(bmp)
                            bmp.recycle()
                            t
                        }
                    }
                    if (!cacheFile.exists()) cacheFile.writeText(text)
                    ocrDone = order.indexOf(i) + 1
                    withContext(Dispatchers.Main) {
                        rebuildSentencesFromCache(cacheDir)
                        updateVoiceBarStatus()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "OCR error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                engine.shutdown()
                runCatching { ocrRenderer?.close() }
                runCatching { pfd?.close() }
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    ocrRunning = false
                    updateVoiceBarStatus()
                }
            }
        }
    }

    private fun rebuildSentencesFromCache(cacheDir: File) {
        val sentences = (0 until totalPages).flatMap { i ->
            val f = File(cacheDir, "page_$i.txt")
            if (f.exists()) SentenceSplitter.split(f.readText()).map { Sentence(i, it) } else emptyList()
        }
        voiceSentences = sentences
        ttsManager.load(sentences)
        neural?.load(sentences)
        updateVoiceBarStatus()

        if (resumeSentenceIndex >= 0 && resumeSentenceIndex < sentences.size) {
            ttsManager.playFrom(resumeSentenceIndex)
            ttsManager.pause()
            resumeSentenceIndex = -1
        }

        if (sentences.isNotEmpty() && detectedLocale == null) {
            configureLanguage(sentences.take(40).joinToString(" ") { it.text })
        }
    }

    private fun buildVoiceFromPages(pages: List<String>, startSentenceIndex: Int) {
        voiceSentences = pages.flatMapIndexed { pageIndex, text ->
            SentenceSplitter.split(text).map { Sentence(pageIndex, it) }
        }
        ttsManager.load(voiceSentences)
        neural?.load(voiceSentences)
        updateVoiceBarStatus()

        if (startSentenceIndex >= 0 && startSentenceIndex < voiceSentences.size) {
            ttsManager.playFrom(startSentenceIndex)
            ttsManager.pause()
        }

        if (voiceSentences.isNotEmpty()) {
            configureLanguage(voiceSentences.take(40).joinToString(" ") { it.text })
        }
    }

    // ---------- Page rendering ----------

    private fun showCurrentPage() {
        binding.tvPageInfo.text = "Page ${currentPage + 1} of $totalPages"
        binding.progressBar.progress = currentPage + 1
        updateBookmarkIcon()

        if (requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE).getBoolean("autosave", true)) {
            viewLifecycleOwner.lifecycleScope.launch {
                app.documentRepository.updateProgress(args.documentId, currentPage, totalPages)
            }
        }

        when (cachedDocument?.type) {
            DocumentType.PDF -> renderPdfPage()
            DocumentType.TXT -> renderTxtPage()
            else -> {}
        }
    }

    private fun renderPdfPage() {
        val renderer = pdfRenderer ?: return
        if (currentPage >= renderer.pageCount) return

        renderer.openPage(currentPage).use { page ->
            currentBitmap?.recycle()
            val bitmap = Bitmap.createBitmap(
                (page.width * renderScale).toInt(),
                (page.height * renderScale).toInt(),
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            currentBitmap = bitmap
            binding.ivPage.setImageBitmap(bitmap)
        }
    }

    private fun renderTxtPage() {
        val start = currentPage * linesPerPage
        val end = minOf(start + linesPerPage, textLines.size)
        binding.tvTextContent.text = if (start < textLines.size) {
            textLines.subList(start, end).joinToString("\n")
        } else "(Empty page)"
    }

    // ---------- Dialogs ----------

    private fun showDocumentInfo() {
        val doc = cachedDocument ?: return
        val msg = buildString {
            appendLine("Title: ${doc.title}")
            appendLine("Type: ${doc.type.name}")
            appendLine("Pages: $totalPages")
            appendLine("Size: ${formatSize(doc.sizeBytes)}")
            appendLine("Progress: ${"%.0f".format(doc.progressPercentage)}%")
            appendLine("Added: ${formatDate(doc.createdAt)}")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Document Info")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------- Gestures (swipe + tap zones + pinch zoom) ----------

    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleDetector: ScaleGestureDetector

    private fun setupGestures() {
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val w = binding.root.width
                when {
                    e.x < w * 0.25f -> prevPage()          // বাম পাশ tap = আগের page
                    e.x > w * 0.75f -> nextPage()          // ডান পাশ tap = পরের page
                    else -> toggleControls()               // মাঝে tap = controls show/hide
                }
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                val dx = e2.x - (e1?.x ?: 0f)
                val dy = e2.y - (e1?.y ?: 0f)
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 120) {
                    if (dx < 0) nextPage() else prevPage()  // swipe = page change
                    return true
                }
                return false
            }
        })
        scaleDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderScale = (renderScale * detector.scaleFactor).coerceIn(1.0f, 5.0f)
                if (cachedDocument?.type == DocumentType.PDF) renderPdfPage()
                return true
            }
        })
        val touch = View.OnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            false // ScrollView এর vertical scroll অক্ষত থাকে
        }
    }

    private fun nextPage() { if (currentPage < totalPages - 1) { currentPage++; showCurrentPage() } }
    private fun prevPage() { if (currentPage > 0) { currentPage--; showCurrentPage() } }

    // ---------- Reading settings (premium) ----------

    private fun prefs() = requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE)

    private fun applyReadingPrefs() {
        val p = prefs()
        baseTextSize = when (p.getInt("txt_size_idx", 1)) { 0 -> 14f; 1 -> 16f; 2 -> 18f; else -> 21f }
        binding.tvTextContent.setLineSpacing(p.getInt("line_spacing", 4).toFloat(), 1f)
        applyTheme(p.getInt("theme_idx", 0))
        if (p.getBoolean("keep_awake", false)) {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        binding.tvTextContent.textSize = baseTextSize * (renderScale / 2f)
    }

    private fun applyTheme(idx: Int) {
        val (bg, fg) = when (idx) {
            1 -> Color.parseColor("#F4ECD8") to Color.parseColor("#5B4636")  // Sepia
            2 -> Color.parseColor("#FFFFFF") to Color.parseColor("#222222")  // White
            else -> Color.parseColor("#0D0D16") to Color.parseColor("#E8E8F0") // Dark
        }
        binding.svText.setBackgroundColor(bg)
        binding.tvTextContent.setTextColor(fg)
        binding.svPdf.setBackgroundColor(bg)
    }

    private fun showReadingSettings() {
        val options = arrayOf("Text Size", "Line Spacing", "Theme", "Keep Screen Awake")
        AlertDialog.Builder(requireContext())
            .setTitle("Reading Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> singlePick("Text Size", arrayOf("Small", "Medium", "Large", "XL"), prefs().getInt("txt_size_idx", 1), "txt_size_idx") { applyReadingPrefs() }
                    1 -> singlePick("Line Spacing", arrayOf("Compact", "Normal", "Relaxed", "Airy"), prefs().getInt("ls_idx", 1), "ls_idx") {
                        prefs().edit().putInt("line_spacing", intArrayOf(0, 4, 8, 14)[prefs().getInt("ls_idx", 1)]).apply()
                        applyReadingPrefs()
                    }
                    2 -> singlePick("Theme", arrayOf("Dark", "Sepia", "White"), prefs().getInt("theme_idx", 0), "theme_idx") { applyReadingPrefs() }
                    3 -> {
                        val on = !prefs().getBoolean("keep_awake", false)
                        prefs().edit().putBoolean("keep_awake", on).apply()
                        if (on) requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        Toast.makeText(requireContext(), if (on) "Screen will stay awake" else "Screen sleep normal", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun singlePick(title: String, items: Array<String>, current: Int, key: String, apply: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(items, current) { d, w ->
                prefs().edit().putInt(key, w).apply()
                apply()
                d.dismiss()
            }
            .show()
    }

    private fun formatSize(bytes: Long) = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    private fun formatDate(ts: Long) = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ts))

    override fun onDestroyView() {
        super.onDestroyView()
        try { requireContext().unregisterReceiver(notificationReceiver) } catch (_: Exception) {}
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        ttsManager.shutdown()
        neural?.release()
        pdfRenderer?.close()
        parcelFileDescriptor?.close()
        currentBitmap?.recycle()
        _binding = null
    }
}
