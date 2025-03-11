/*
 * Updated: 2025-01-21 20:39:04
 * Author: William8677
 */

package com.williamfq.xhat.domain.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.williamfq.xhat.domain.model.GalleryFolder
import com.williamfq.xhat.domain.model.GalleryImage
import com.williamfq.xhat.domain.model.GallerySortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

    private val imageProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )

    suspend fun getImages(
        sortOrder: GallerySortOrder = GallerySortOrder.DATE_DESC,
        folderId: String? = null
    ): List<GalleryImage> = withContext(Dispatchers.IO) {
        val images = mutableListOf<GalleryImage>()

        val selection = folderId?.let {
            "${MediaStore.Images.Media.BUCKET_ID} = ?"
        }
        val selectionArgs = folderId?.let {
            arrayOf(it)
        }

        val sortOrderString = when (sortOrder) {
            GallerySortOrder.DATE_DESC -> "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            GallerySortOrder.DATE_ASC -> "${MediaStore.Images.Media.DATE_MODIFIED} ASC"
            GallerySortOrder.NAME_ASC -> "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            GallerySortOrder.NAME_DESC -> "${MediaStore.Images.Media.DISPLAY_NAME} DESC"
            GallerySortOrder.SIZE_DESC -> "${MediaStore.Images.Media.SIZE} DESC"
            GallerySortOrder.SIZE_ASC -> "${MediaStore.Images.Media.SIZE} ASC"
        }

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            selection,
            selectionArgs,
            sortOrderString
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                val dateAdded = Date(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)) * 1000)
                val dateModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)) * 1000)
                val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
                val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH))
                val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                images.add(
                    GalleryImage(
                        id = id.toString(),
                        uri = contentUri,
                        name = name,
                        path = path,
                        size = size,
                        dateCreated = dateAdded,
                        dateModified = dateModified,
                        mimeType = mimeType,
                        width = width,
                        height = height
                    )
                )
            }
        }

        images
    }

    suspend fun getFolders(): List<GalleryFolder> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, GalleryFolder>()

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID))
                val bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                val imageId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))

                val folder = folders.getOrPut(bucketId) {
                    GalleryFolder(
                        id = bucketId,
                        name = bucketName ?: "Unknown",
                        path = File(path).parent ?: "",
                        coverImage = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            imageId
                        ),
                        imageCount = 0
                    )
                }

                folders[bucketId] = folder.copy(imageCount = folder.imageCount + 1)
            }
        }

        folders.values.toList()
    }

    suspend fun deleteImages(images: List<GalleryImage>) = withContext(Dispatchers.IO) {
        images.forEach { image ->
            try {
                contentResolver.delete(
                    image.uri,
                    null,
                    null
                )
            } catch (e: Exception) {
                throw GalleryException.DeleteError(e.message ?: "Error al eliminar imagen")
            }
        }
    }

    suspend fun shareImages(images: List<GalleryImage>) = withContext(Dispatchers.IO) {
        if (images.isEmpty()) return@withContext

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "image/*"

            val uris = images.map { image ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    File(image.path)
                )
            }

            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Compartir imágenes")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }
}

sealed class GalleryException : Exception() {
    data class LoadError(override val message: String) : GalleryException()
    data class DeleteError(override val message: String) : GalleryException()
    data class ShareError(override val message: String) : GalleryException()
}