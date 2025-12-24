package ir.patraplus.webui

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class FloatingOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
    }

    private fun showBubble() {
        if (bubbleView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        view.setOnTouchListener(BubbleTouchListener(params))

        windowManager.addView(view, params)
        bubbleView = view
    }

    private inner class BubbleTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var lastX = 0
        private var lastY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragged = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragged = false
                    lastX = params.x
                    lastY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - touchX).toInt()
                    val deltaY = (event.rawY - touchY).toInt()
                    if (deltaX != 0 || deltaY != 0) {
                        dragged = true
                        params.x = lastX + deltaX
                        params.y = lastY + deltaY
                        windowManager.updateViewLayout(v, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        sendBroadcast(Intent(MainActivity.ACTION_OVERLAY_TAP))
                    }
                    return true
                }
            }
            return false
        }
    }
}
