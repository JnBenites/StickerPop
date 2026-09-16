package com.jnbenites.stickerpop.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jnbenites.stickerpop.R
import com.jnbenites.stickerpop.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val overlays = mutableMapOf<Int, StickerOverlay>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 34)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else
                0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            val dao = AppDatabase.get(applicationContext).stickerDao()
            val activeStickers = dao.getActive()
            activeStickers.forEach { sticker ->
                if (!overlays.containsKey(sticker.id)) {
                    showSticker(
                        id = sticker.id,
                        path = sticker.path,
                        x = sticker.posX,
                        y = sticker.posY,
                        size = sticker.size
                    )
                }
            }
        }
        return START_STICKY
    }

    private fun showSticker(id: Int, path: String, x: Int, y: Int, size: Int) {
        val overlay = StickerOverlay(
            context = this,
            path = path,
            initialX = x,
            initialY = y,
            initialSize = size,
            onMoved = { newX, newY ->
                scope.launch {
                    val dao = AppDatabase.get(applicationContext).stickerDao()
                    val current = dao.getById(id) ?: return@launch
                    dao.update(current.copy(posX = newX, posY = newY))
                }
            },
            onResized = { newSize ->
                scope.launch {
                    val dao = AppDatabase.get(applicationContext).stickerDao()
                    val current = dao.getById(id) ?: return@launch
                    dao.update(current.copy(size = newSize))
                }
            }
        )
        overlay.show()
        overlays[id] = overlay
    }

    fun hideSticker(id: Int) {
        overlays.remove(id)?.hide()
    }

    override fun onDestroy() {
        overlays.values.forEach { it.hide() }
        overlays.clear()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "StickerPop",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StickerPop active")
            .setContentText("Your stickers are floating on the screen")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "stickerpop_channel"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}