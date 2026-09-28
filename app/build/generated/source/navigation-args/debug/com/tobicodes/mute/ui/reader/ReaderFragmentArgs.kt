package com.tobicodes.mute.ui.reader

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavArgs
import java.lang.IllegalArgumentException
import kotlin.Int
import kotlin.String
import kotlin.jvm.JvmStatic

public data class ReaderFragmentArgs(
  public val documentId: String,
  public val startPage: Int = -1,
) : NavArgs {
  public fun toBundle(): Bundle {
    val result = Bundle()
    result.putString("documentId", this.documentId)
    result.putInt("startPage", this.startPage)
    return result
  }

  public fun toSavedStateHandle(): SavedStateHandle {
    val result = SavedStateHandle()
    result.set("documentId", this.documentId)
    result.set("startPage", this.startPage)
    return result
  }

  public companion object {
    @JvmStatic
    public fun fromBundle(bundle: Bundle): ReaderFragmentArgs {
      bundle.setClassLoader(ReaderFragmentArgs::class.java.classLoader)
      val __documentId : String?
      if (bundle.containsKey("documentId")) {
        __documentId = bundle.getString("documentId")
        if (__documentId == null) {
          throw IllegalArgumentException("Argument \"documentId\" is marked as non-null but was passed a null value.")
        }
      } else {
        throw IllegalArgumentException("Required argument \"documentId\" is missing and does not have an android:defaultValue")
      }
      val __startPage : Int
      if (bundle.containsKey("startPage")) {
        __startPage = bundle.getInt("startPage")
      } else {
        __startPage = -1
      }
      return ReaderFragmentArgs(__documentId, __startPage)
    }

    @JvmStatic
    public fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): ReaderFragmentArgs {
      val __documentId : String?
      if (savedStateHandle.contains("documentId")) {
        __documentId = savedStateHandle["documentId"]
        if (__documentId == null) {
          throw IllegalArgumentException("Argument \"documentId\" is marked as non-null but was passed a null value")
        }
      } else {
        throw IllegalArgumentException("Required argument \"documentId\" is missing and does not have an android:defaultValue")
      }
      val __startPage : Int?
      if (savedStateHandle.contains("startPage")) {
        __startPage = savedStateHandle["startPage"]
        if (__startPage == null) {
          throw IllegalArgumentException("Argument \"startPage\" of type integer does not support null values")
        }
      } else {
        __startPage = -1
      }
      return ReaderFragmentArgs(__documentId, __startPage)
    }
  }
}
