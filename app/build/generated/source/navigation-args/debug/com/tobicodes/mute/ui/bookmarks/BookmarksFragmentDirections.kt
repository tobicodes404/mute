package com.tobicodes.mute.ui.bookmarks

import android.os.Bundle
import androidx.navigation.NavDirections
import com.tobicodes.mute.R
import kotlin.Int
import kotlin.String

public class BookmarksFragmentDirections private constructor() {
  private data class ActionNavBookmarksToReaderFragment(
    public val documentId: String,
    public val startPage: Int = -1,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_nav_bookmarks_to_readerFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putString("documentId", this.documentId)
        result.putInt("startPage", this.startPage)
        return result
      }
  }

  public companion object {
    public fun actionNavBookmarksToReaderFragment(documentId: String, startPage: Int = -1):
        NavDirections = ActionNavBookmarksToReaderFragment(documentId, startPage)
  }
}
