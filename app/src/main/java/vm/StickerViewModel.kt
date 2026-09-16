package com.jnbenites.stickerpop.vm

import android.app.Application
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jnbenites.stickerpop.data.AppDatabase
import com.jnbenites.stickerpop.data.StickerEntity
import com.jnbenites.stickerpop.overlay.OverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class StickerViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.stickerDao()

    val stickers = dao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(getApplication())
    }

    fun addSticker(uri: Uri) {
        viewModelScope.launch {
            val localFile = copyToInternalStorage(uri)
            val newSticker = StickerEntity(path = localFile.absolutePath)
            dao.insert(newSticker)
        }
    }

    private suspend fun copyToInternalStorage(uri: Uri): File = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val folder = File(context.filesDir, "stickers").apply { mkdirs() }
        val file = File(folder, "sticker_${System.currentTimeMillis()}.png")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file
    }

    fun toggleActive(sticker: StickerEntity) {
        viewModelScope.launch {
            val newState = !sticker.active
            dao.update(sticker.copy(active = newState))

            val context = getApplication<Application>()

            if (newState) {
                OverlayService.start(context)
            } else {
                val activeStickers = dao.getActive()
                if (activeStickers.isEmpty()) {
                    OverlayService.stop(context)
                } else {
                    OverlayService.stop(context)
                    OverlayService.start(context)
                }
            }
        }
    }

    fun deleteSticker(sticker: StickerEntity) {
        viewModelScope.launch {
            val wasActive = sticker.active

            runCatching { File(sticker.path).delete() }
            dao.delete(sticker)

            if (wasActive) {
                val context = getApplication<Application>()
                val activeStickers = dao.getActive()
                OverlayService.stop(context)
                if (activeStickers.isNotEmpty()) {
                    OverlayService.start(context)
                }
            }
        }
    }

    fun changeSize(sticker: StickerEntity, newSize: Int) {
        viewModelScope.launch {
            val finalSize = newSize.coerceIn(80, 1200)
            dao.update(sticker.copy(size = finalSize))

            val context = getApplication<Application>()
            if (sticker.active) {
                OverlayService.stop(context)
                OverlayService.start(context)
            }
        }
    }
}