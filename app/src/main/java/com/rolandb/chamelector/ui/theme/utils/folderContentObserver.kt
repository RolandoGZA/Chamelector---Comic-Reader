package com.rolandb.chamelector.ui.theme.utils

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper

class FolderContentObserver(
    private val context: Context,
    private val folderUri: Uri,
    private val onChangeDetected: () -> Unit,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    fun register() {
        try {
            context.contentResolver.registerContentObserver(
                folderUri,
                true, // Notificar sobre cambios en sub-elementos (documentos dentro de la carpeta)
                this
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unregister() {
        try {
            context.contentResolver.unregisterContentObserver(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        onChangeDetected()
    }
}