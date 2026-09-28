package com.tobicodes.mute.ui.settings
import android.content.Context

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tobicodes.mute.MuteApplication
import com.tobicodes.mute.core.tts.TtsEngineHelper
import com.tobicodes.mute.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as MuteApplication

    private val fontLabels = arrayOf("Small", "Medium", "Large", "XL")
    private val lsLabels = arrayOf("Compact", "Normal", "Relaxed", "Airy")
    private val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private val pitches = floatArrayOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)

    private fun prefs() = requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindAll()
    }

    private fun bindAll() {
        val p = prefs()

        // ----- Reading -----
        stepper(binding.btnFontMinus, binding.btnFontPlus, "txt_size_idx", 1, fontLabels.size) { i ->
            binding.tvFontValue.text = fontLabels[i]
        }
        stepper(binding.btnLsMinus, binding.btnLsPlus, "ls_idx", 1, lsLabels.size) { i ->
            binding.tvLsValue.text = lsLabels[i]
            prefs().edit().putInt("line_spacing", intArrayOf(0, 4, 8, 14)[i]).apply()
        }
        binding.swAwake.isChecked = p.getBoolean("keep_awake", false)
        binding.swAwake.setOnCheckedChangeListener { _, on -> p.edit().putBoolean("keep_awake", on).apply() }
        binding.swRemember.isChecked = p.getBoolean("remember_position", true)
        binding.swRemember.setOnCheckedChangeListener { _, on -> p.edit().putBoolean("remember_position", on).apply() }
        binding.swAutosave.isChecked = p.getBoolean("autosave", true)
        binding.swAutosave.setOnCheckedChangeListener { _, on -> p.edit().putBoolean("autosave", on).apply() }

        // ----- Voice -----
        refreshVoiceLabel()
        binding.rowVoice.setOnClickListener { showEnginePicker() }
        stepper(binding.btnSpeedMinus, binding.btnSpeedPlus, "speed_idx", 1, speeds.size) { i ->
            binding.tvSpeedValue.text = "${speeds[i]}x".replace(".0x", ".0x")
        }
        val pitchOk = p.getBoolean("pitch_supported", true)
        binding.rowPitch.visibility = if (pitchOk) View.VISIBLE else View.GONE
        stepper(binding.btnPitchMinus, binding.btnPitchPlus, "pitch_idx", 1, pitches.size) { i ->
            binding.tvPitchValue.text = pitches[i].toString()
        }
        binding.swAutoNext.isChecked = p.getBoolean("auto_next", true)
        binding.swAutoNext.setOnCheckedChangeListener { _, on -> p.edit().putBoolean("auto_next", on).apply() }
        binding.swVoice.isChecked = p.getBoolean("voice_enabled", true)
        binding.swVoice.setOnCheckedChangeListener { _, on -> p.edit().putBoolean("voice_enabled", on).apply() }

        // ----- Library -----
        binding.swConfirm.isChecked = p.getBoolean("confirm_delete", true)
        binding.swConfirm.setOnCheckedChangeListener { _, on -> p.edit().putBoolean("confirm_delete", on).apply() }
        binding.rowClear.setOnClickListener { confirmClearProgress() }
        loadLibraryInfo()

        // ----- App behavior -----
        binding.swOpenLast.isChecked = p.getBoolean("open_last", false)
        binding.swOpenLast.setOnCheckedChangeListener { _, on -> p.edit().putBoolean("open_last", on).apply() }

        // ----- About -----
        val pm = requireContext().packageManager
        binding.tvAppName.text = requireContext().applicationInfo.loadLabel(pm)
        val vname = runCatching { pm.getPackageInfo(requireContext().packageName, 0).versionName }.getOrNull() ?: "1.0"
        binding.tvVersion.text = "Version $vname"

        // ----- Reset -----
        binding.rowReset.setOnClickListener { confirmReset() }
    }

    private fun stepper(minus: View, plus: View, key: String, def: Int, max: Int, render: (Int) -> Unit) {
        val cur = { prefs().getInt(key, def) }
        render(cur())
        minus.setOnClickListener {
            val v = (cur() - 1).coerceAtLeast(0)
            prefs().edit().putInt(key, v).apply(); render(v)
        }
        plus.setOnClickListener {
            val v = (cur() + 1).coerceAtMost(max - 1)
            prefs().edit().putInt(key, v).apply(); render(v)
        }
    }

    private fun refreshVoiceLabel() {
        val saved = TtsEngineHelper.savedEngine(requireContext())
        val label = TtsEngineHelper.installedEngines(requireContext())
            .firstOrNull { it.packageName == saved }?.label ?: "System default"
        binding.tvVoiceValue.text = label
    }

    private fun showEnginePicker() {
        val engines = TtsEngineHelper.installedEngines(requireContext())
        if (engines.isEmpty()) {
            Toast.makeText(requireContext(), "No TTS engine installed", Toast.LENGTH_LONG).show()
            return
        }
        val saved = TtsEngineHelper.savedEngine(requireContext())
        val names = engines.map { e -> e.label + if (e.packageName == saved) " ✓" else "" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Choose Voice Engine")
            .setItems(names) { _, which ->
                TtsEngineHelper.saveEngine(requireContext(), engines[which].packageName)
                refreshVoiceLabel()
            }
            .setNeutralButton("System default") { _, _ ->
                TtsEngineHelper.saveEngine(requireContext(), null)
                refreshVoiceLabel()
            }
            .show()
    }

    private fun confirmClearProgress() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear reading progress?")
            .setMessage("All saved page and voice positions will be reset. Your PDFs stay in the library.")
            .setPositiveButton("Clear") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    app.documentRepository.clearAllProgress()
                    Toast.makeText(requireContext(), "Reading progress cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadLibraryInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            val docs: List<com.tobicodes.mute.domain.model.Document>? = null
            if (docs != null) {
                binding.tvLibInfo.text = "${docs.size} document(s) stored locally"
            }
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset settings?")
            .setMessage("Preferences return to defaults. PDFs and reading progress are NOT deleted.")
            .setPositiveButton("Reset") { _, _ ->
                prefs().edit().clear().apply()
                bindAll()
                Toast.makeText(requireContext(), "Settings reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
