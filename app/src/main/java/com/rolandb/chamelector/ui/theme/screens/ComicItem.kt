package com.rolandb.chamelector.ui.theme.screens

import android.graphics.Bitmap
import android.net.Uri

data class ComicItem(
    val name: String,
    val uri: Uri,
    val cover: Bitmap? = null
)
