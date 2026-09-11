package com.rolandb.chamelector.ui.theme.utils

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FolderSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val folderUriString = inputData.getString(KEY_FOLDER_URI) ?: return@withContext Result.failure()
        val folderUri = folderUriString.toUri()

        val directory = DocumentFile.fromTreeUri(applicationContext, folderUri)
            ?: return@withContext Result.failure()

        // Filtrar y validar los cómics existentes
        val cbrFiles = directory.listFiles().filter { file ->
            file.name?.endsWith(".cbr", ignoreCase = true) == true
        }

        // Aquí se puede actualizar la base de datos local (Room) o notificar al sistema
        if (cbrFiles.isNotEmpty()) {
            Result.success()
        } else {
            Result.success()
        }
    }

    companion object {
        const val KEY_FOLDER_URI = "key_folder_uri"
    }
}