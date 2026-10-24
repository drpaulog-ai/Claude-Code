package com.drpaulog.photopuzzle

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

data class Photo(val id: Long, val uri: Uri)

/** Lê as fotos do dispositivo (cada foto é um nível) e guarda o progresso. */
class PhotoRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun hasPhotoAccess(): Boolean = requiredPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Fotos ordenadas da mais antiga para a mais recente, para que o número de cada nível não mude. */
    fun loadPhotos(): List<Photo> {
        if (!hasPhotoAccess()) return emptyList()
        val photos = ArrayList<Photo>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(
            collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                photos += Photo(id, ContentUris.withAppendedId(collection, id))
            }
        }
        return photos
    }

    fun completedLevels(gridSize: Int): Set<Long> =
        prefs.getStringSet(completedKey(gridSize), emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun markCompleted(gridSize: Int, photoId: Long, moves: Int) {
        val done = prefs.getStringSet(completedKey(gridSize), emptySet())!! + photoId.toString()
        val bestKey = "best_${gridSize}_$photoId"
        val best = prefs.getInt(bestKey, Int.MAX_VALUE)
        prefs.edit()
            .putStringSet(completedKey(gridSize), done)
            .putInt(bestKey, minOf(best, moves))
            .apply()
    }

    fun bestMoves(gridSize: Int, photoId: Long): Int? =
        prefs.getInt("best_${gridSize}_$photoId", -1).takeIf { it > 0 }

    var gridSize: Int
        get() = prefs.getInt("grid_size", GRID_SIZES.first()).takeIf { it in GRID_SIZES } ?: GRID_SIZES.first()
        set(value) = prefs.edit().putInt("grid_size", value).apply()

    /** Ids das fotos escolhidas para níveis, ou null se o utilizador ainda não escolheu nenhuma vez. */
    fun levelPhotoIds(): Set<Long>? =
        prefs.getStringSet("level_ids", null)?.mapNotNull { it.toLongOrNull() }?.toSet()

    fun saveLevelPhotoIds(ids: Set<Long>) {
        prefs.edit().putStringSet("level_ids", ids.map { it.toString() }.toSet()).apply()
    }

    private fun completedKey(gridSize: Int) = "completed_$gridSize"

    companion object {
        fun requiredPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
