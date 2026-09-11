package com.rolandb.chamelector.ui.theme.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rolandb.chamelector.ui.theme.utils.CbrCoverFetcher
import com.rolandb.chamelector.ui.theme.utils.FolderContentObserver
import com.rolandb.chamelector.ui.theme.utils.FolderSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ComicViewModel(application: Application) : AndroidViewModel(application) {

    private val _comics = MutableStateFlow<List<ComicItem>>(emptyList())
    val comics: StateFlow<List<ComicItem>> = _comics

    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri

    private var folderObserver: FolderContentObserver? = null
    private var loadJob: Job? = null

    fun onFolderSelected(uri: Uri) {
        val context = getApplication<Application>().applicationContext

        // Solicitar permisos persistentes para mantener acceso tras reinicios
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _selectedFolderUri.value = uri

        // 1. Iniciar observación en tiempo real
        setupFolderObserver(uri)

        // 2. Programar monitoreo periódico de segundo plano
        scheduleBackgroundSync(uri)

        // 3. Cargar la lista inicial
        loadComicsFromFolder(uri)
    }

    private fun setupFolderObserver(uri: Uri) {
        folderObserver?.unregister()
        folderObserver = FolderContentObserver(
            context = getApplication(),
            folderUri = uri,
        ) {
            // Se detectó una adición/modificación en la carpeta
            _selectedFolderUri.value?.let { loadComicsFromFolder(it) }
        }.apply {
            register()
        }
    }

    private fun scheduleBackgroundSync(uri: Uri) {
        val context = getApplication<Application>()
        val data = Data.Builder()
            .putString(FolderSyncWorker.KEY_FOLDER_URI, uri.toString())
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<FolderSyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ComicFolderSyncWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    fun loadComicsFromFolder(folderUri: Uri) {
        // Cancelar escaneos anteriores si aún no terminan
        loadJob?.cancel()

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val directory = DocumentFile.fromTreeUri(context, folderUri) ?: return@launch

            val comicList = mutableListOf<ComicItem>()

            directory.listFiles().forEach { file ->
                if (file.name?.endsWith(".cbr", ignoreCase = true) == true) {
                    val cover = CbrCoverFetcher.extractCover(context, file.uri)
                    comicList.add(
                        ComicItem(
                            name = file.name ?: "Sin título",
                            uri = file.uri,
                            cover = cover
                        )
                    )
                }
            }

            _comics.value = comicList
        }
    }

    override fun onCleared() {
        super.onCleared()
        folderObserver?.unregister()
    }
}