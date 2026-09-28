package com.tobicodes.mute.ui.bookmarks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tobicodes.mute.databinding.ItemBookmarkBinding

data class BookmarkItem(
    val bookmark: com.tobicodes.mute.domain.model.Bookmark,
    val documentTitle: String
)

class BookmarkAdapter(
    private val onItemClick: (BookmarkItem) -> Unit,
    private val onDeleteClick: (BookmarkItem) -> Unit
) : ListAdapter<BookmarkItem, BookmarkAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemBookmarkBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BookmarkItem) {
            binding.tvTitle.text = item.documentTitle
            binding.tvMeta.text = "Page ${item.bookmark.page + 1}"
            binding.tvPreview.text = item.bookmark.previewText ?: ""
            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    private class Diff : DiffUtil.ItemCallback<BookmarkItem>() {
        override fun areItemsTheSame(a: BookmarkItem, b: BookmarkItem) = a.bookmark.id == b.bookmark.id
        override fun areContentsTheSame(a: BookmarkItem, b: BookmarkItem) = a == b
    }
}
