package com.rolandb.chamelector.ui.theme.screens.comicviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.ByteArrayOutputStream

data class ComicViewerUiState(
    val isLoading: Boolean = true,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val pages: List<Bitmap> = emptyList(),
    val isControlsVisible: Boolean = true,
    val errorMessage: String? = null,
)

class ComicViewerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ComicViewerUiState())
    val uiState: StateFlow<ComicViewerUiState> = _uiState.asStateFlow()

    fun loadComicPages(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val loadedPages = extractAllPages(context, uri)

            if (loadedPages.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pages = loadedPages,
                        totalPages = loadedPages.size,
                        currentPage = 0
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error: No se pudieron extraer páginas. Verifica los permisos o el formato del archivo."
                    )
                }
            }
        }
    }

    fun onPageChanged(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }

    fun toggleControlsVisibility() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    private suspend fun extractAllPages(context: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
        val pagesMap = mutableMapOf<String, ByteArray>()
        var pfd: ParcelFileDescriptor? = null
        var archivePtr: Long = 0

        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext emptyList()
            archivePtr = Archive.readNew()
            Archive.readSupportFilterAll(archivePtr)
            Archive.readSupportFormatAll(archivePtr)

            Archive.readOpenFd(archivePtr, pfd.fd, 10240)

            val entryPtr = ArchiveEntry.new2(archivePtr)

            try {
                while (true) {
                    val result = Archive.readNextHeader2(archivePtr, entryPtr)
                    if (result == Archive.ERRNO_EOF.toLong()) break

                    val entryName = ArchiveEntry.pathnameUtf8(entryPtr) ?: continue

                    if (isImageFile(entryName)) {
                        val cleanName = getCleanFileName(entryName)
                        val entryBytes = readEntryData(archivePtr)
                        if (entryBytes.isNotEmpty()) {
                            pagesMap[cleanName] = entryBytes
                        }
                    } else {
                        Archive.readDataSkip(archivePtr)
                    }
                }
            } catch (e: ArchiveException) {
                e.printStackTrace()
            } finally {
                ArchiveEntry.free(entryPtr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error en extractAllPages: ${e.message}")
        } finally {
            if (archivePtr != 0L) {
                try { Archive.readFree(archivePtr) } catch (e: Exception) { e.printStackTrace() }
            }
            try { pfd?.close() } catch (e: Exception) { e.printStackTrace() }
        }

        // Ordenamos las páginas alfabéticamente/numéricamente por su nombre de archivo
        val sortedKeys = pagesMap.keys.sorted()
        val bitmaps = mutableListOf<Bitmap>()

        for (key in sortedKeys) {
            pagesMap[key]?.let { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    bitmaps.add(bitmap)
                }
            }
        }

        return@withContext bitmaps
    }

    private fun readEntryData(archivePtr: Long): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val chunkSize = 64 * 1024 // 64KB chunks for better performance
        val byteBuffer = ByteBuffer.allocateDirect(chunkSize)
        try {
            while (true) {
                byteBuffer.clear()
                Archive.readData(archivePtr, byteBuffer)
                val bytesRead = byteBuffer.position()
                if (bytesRead <= 0) break

                byteBuffer.flip()
                val tempArray = ByteArray(bytesRead)
                byteBuffer.get(tempArray)
                outputStream.write(tempArray, 0, tempArray.size)
            }
        } catch (e: ArchiveException) {
            if (e.code != Archive.ERRNO_EOF) {
                e.printStackTrace()
            }
        }
        return outputStream.toByteArray()
    }

    private fun isImageFile(fileName: String): Boolean {
        val clean = fileName.lowercase()
        return (clean.endsWith(".jpg") || clean.endsWith(".jpeg") ||
                clean.endsWith(".png") || clean.endsWith(".webp")) &&
                !clean.contains("__macosx")
    }

    private fun getCleanFileName(path: String): String {
        return path.replace('\\', '/').substringAfterLast('/')
    }
}