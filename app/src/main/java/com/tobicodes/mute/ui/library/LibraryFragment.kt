package com.tobicodes.mute.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.tobicodes.mute.databinding.FragmentLibraryBinding
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var adapter: DocumentAdapter

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.addDocument(it)
            Toast.makeText(requireContext(), "Document added to library", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupFab()
        observeDocuments()
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(
            onItemClick = { document ->
                // Navigate to Reader (with error handling)
                try {
                    val action = LibraryFragmentDirections.actionNavLibraryToReaderFragment(document.id)
                    findNavController().navigate(action)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Cannot open document. Please re-upload.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDeleteClick = { document ->
                viewModel.deleteDocument(document)
                Toast.makeText(requireContext(), "Removed from library", Toast.LENGTH_SHORT).show()
            }
        )
        
        binding.recyclerViewDocuments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LibraryFragment.adapter
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            pickFileLauncher.launch(arrayOf(
                "application/pdf",
                "text/plain",
                "application/epub+zip"
            ))
        }
    }

    private fun observeDocuments() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.documents.collect { documents ->
                adapter.submitList(documents)
                
                if (documents.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerViewDocuments.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerViewDocuments.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
