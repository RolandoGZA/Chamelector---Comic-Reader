package com.rolandb.chamelector.ui.theme.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.ByteArrayOutputStream

object CbrCoverFetcher {

    suspend fun extractCover(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var archivePtr: Long = 0

        try {
            // 1. Obtener el File Descriptor directamente desde la URI de SAF
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            val fd = pfd.fd

            // 2. Inicializar libarchive
            archivePtr = Archive.readNew()
            Archive.readSupportFilterAll(archivePtr)
            Archive.readSupportFormatAll(archivePtr)

            // 3. Abrir usando el File Descriptor nativo
            Archive.readOpenFd(archivePtr, fd, 10240)

            var bestImageName = "zzzzzzzz"
            var bestImageBytes: ByteArray? = null

            val entryPtr = ArchiveEntry.new2(archivePtr)

            // 4. Recorrer entradas buscando la portada
            try {
                while (true) {
                    val result = Archive.readNextHeader2(archivePtr, entryPtr)
                    if (result == Archive.ERRNO_EOF.toLong()) break

                    val entryName = ArchiveEntry.pathnameUtf8(entryPtr) ?: continue

                    if (isImageFile(entryName)) {
                        val cleanName = getCleanFileName(entryName)

                        if (cleanName < bestImageName) {
                            bestImageName = cleanName
                            bestImageBytes = readEntryData(archivePtr)
                        }
                    }
                }
            } catch (_: ArchiveException) {
                // Captura el fin de archivo (EOF) o fallos de lectura en iteración
            } finally {
                ArchiveEntry.free(entryPtr)
            }

            bestImageBytes?.let { bytes ->
                return@withContext decodeSampledBitmapFromByteArray(bytes, targetWidth = 300, targetHeight = 450)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (archivePtr != 0L) {
                try {
                    Archive.readFree(archivePtr)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            try {
                pfd?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext null
    }

    private fun readEntryData(archivePtr: Long): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val chunkSize = 8192
        val byteBuffer = ByteBuffer.allocateDirect(chunkSize)

        try {
            while (true) {
                byteBuffer.clear()
                Archive.readData(archivePtr, byteBuffer)
                val bytesRead = byteBuffer.position()
                if (bytesRead == 0) break

                byteBuffer.flip()
                val tempArray = ByteArray(bytesRead)
                byteBuffer.get(tempArray)
                outputStream.write(tempArray, 0, tempArray.size)
            }
        } catch (_: ArchiveException) {
            // Manejo de fin de lectura de la entrada
        }
        return outputStream.toByteArray()
    }

    private fun isImageFile(fileName: String): Boolean {
        val clean = fileName.lowercase()
        return clean.endsWith(".jpg") || clean.endsWith(".jpeg") ||
                clean.endsWith(".png") || clean.endsWith(".webp")
    }

    private fun getCleanFileName(path: String): String {
        return path.replace('\\', '/').substringAfterLast('/')
    }

    private fun decodeSampledBitmapFromByteArray(
        bytes: ByteArray,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        options.inJustDecodeBounds = false

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}