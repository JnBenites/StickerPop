package com.jnbenites.stickerpop.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import java.io.File
import kotlin.math.hypot

class StickerOverlay(
    private val context: Context,
    private val path: String,
    private val initialX: Int,
    private val initialY: Int,
    private val initialSize: Int,
    private val onMoved: (Int, Int) -> Unit,
    private val onResized: (Int) -> Unit
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private lateinit var imageView: ImageView
    private lateinit var params: WindowManager.LayoutParams

    private var currentSize = initialSize

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        imageView = ImageView(context)

        val loader = ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

        val request = ImageRequest.Builder(context)
            .data(File(path))
            .target(imageView)
            .build()

        loader.enqueue(request)

        params = WindowManager.LayoutParams(
            currentSize,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        imageView.adjustViewBounds = true
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        var startX = 0f
        var startY = 0f
        var touchX = 0f
        var touchY = 0f
        var initialDistance = 0f
        var initialSizeAtZoom = 0

        imageView.setOnTouchListener { _, event ->
            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    startX = params.x.toFloat()
                    startY = params.y.toFloat()
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) {
                        params.x = (startX + (event.rawX - touchX)).toInt()
                        params.y = (startY + (event.rawY - touchY)).toInt()
                        windowManager.updateViewLayout(imageView, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    onMoved(params.x, params.y)
                    true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        initialDistance = distanceBetweenFingers(event)
                        initialSizeAtZoom = currentSize
                    }
                    true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    onResized(currentSize)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 2) {
                        val currentDistance = distanceBetweenFingers(event)
                        if (initialDistance > 0) {
                            val factor = currentDistance / initialDistance
                            val newSize = (initialSizeAtZoom * factor).toInt()

                            currentSize = newSize.coerceIn(80, 1200)

                            params.width = currentSize
                            windowManager.updateViewLayout(imageView, params)
                        }
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(imageView, params)
    }

    private fun distanceBetweenFingers(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return hypot(x, y)
    }

    fun hide() {
        try {
            if (::imageView.isInitialized) {
                windowManager.removeView(imageView)
            }
        } catch (_: Exception) { }
    }
}