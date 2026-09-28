package com.tobicodes.mute.ui.home
import android.content.Context

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.tobicodes.mute.R
import com.tobicodes.mute.databinding.FragmentHomeBinding
import com.tobicodes.mute.ui.library.DocumentAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {
    companion object { private var autoOpened = false }
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var recentAdapter: DocumentAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGreeting()
        setupRecentList()
        observeData()
        binding.btnAddFirst.setOnClickListener { findNavController().navigate(R.id.nav_library) }
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvUserName.text = "Hello Shehab"
        binding.tvGreeting.text = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    private fun setupRecentList() {
        recentAdapter = DocumentAdapter(
            onItemClick = { doc ->
                findNavController().navigate(
                    HomeFragmentDirections.actionNavHomeToReaderFragment(doc.id)
                )
            },
            onDeleteClick = { doc -> viewModel.deleteDocument(doc) }
        )
        binding.rvRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecent.adapter = recentAdapter
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recentDocument.collect { doc ->
                if (doc != null) {
                    binding.cardContinue.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    
                    binding.tvContinueType.text = doc.type.name
                    binding.tvContinueTitle.text = doc.title
                    binding.tvContinueTime.text = doc.lastOpenedAt?.let { formatRelativeTime(it) } ?: ""
                    binding.tvContinueMeta.text = if (doc.totalPages > 0) {
                        "Page ${doc.currentPage + 1} of ${doc.totalPages} • ${"%.0f".format(doc.progressPercentage)}%"
                    } else {
                        "${"%.0f".format(doc.progressPercentage)}% complete"
                    }
                    binding.progressContinue.progress = doc.progressPercentage.toInt()
                    binding.btnContinue.setOnClickListener {
                        findNavController().navigate(
                            HomeFragmentDirections.actionNavHomeToReaderFragment(doc.id)
                        )
                    }
                } else {
                    binding.cardContinue.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                }
            }

                if (requireContext().getSharedPreferences("mute_prefs", Context.MODE_PRIVATE).getBoolean("open_last", false) && !autoOpened) {
                    autoOpened = true
                    binding.btnContinue.performClick()
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collect { stats ->
                binding.layoutStats.visibility = if (stats.totalDocuments > 0) View.VISIBLE else View.GONE
                binding.tvStatTotal.text = stats.totalDocuments.toString()
                binding.tvStatProgress.text = stats.totalInProgress.toString()
                binding.tvStatDone.text = stats.totalCompleted.toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recentDocuments.collect { docs ->
                val show = docs.size > 1
                binding.tvRecentHeader.visibility = if (show) View.VISIBLE else View.GONE
                binding.rvRecent.visibility = if (show) View.VISIBLE else View.GONE
                recentAdapter.submitList(docs)
            }
        }
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
