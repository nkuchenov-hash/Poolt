package app.poolt

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.content.ContextCompat

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        val view = SplashView(this)
        setContentView(view)

        view.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }, 1650L)
    }
}

private class SplashView(context: android.content.Context) : View(context) {
    private val bitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.poolt_splash)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(75, 23, 25)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val progressPaint = Paint(basePaint).apply {
        color = Color.rgb(158, 32, 39)
    }
    private val startedAt = SystemClock.uptimeMillis()

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return

        // Center-crop the approved splash image without allocating another full-size bitmap.
        val scale = maxOf(vw / bitmap.width, vh / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val left = (vw - dw) / 2f
        val top = (vh - dh) / 2f
        val dst = RectF(left, top, left + dw, top + dh)
        canvas.drawBitmap(bitmap, null, dst, imagePaint)

        val text = "ПУЛЬТ"
        val textSize = (vw * 0.065f).coerceIn(22f, 38f)
        basePaint.textSize = textSize
        progressPaint.textSize = textSize

        val y = vh - maxOf(42f, vh * 0.045f)
        canvas.drawText(text, vw / 2f, y, basePaint)

        val elapsed = (SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0L)
        val progress = (elapsed / 1400f).coerceIn(0f, 1f)
        val textWidth = progressPaint.measureText(text)
        val textLeft = vw / 2f - textWidth / 2f

        canvas.save()
        canvas.clipRect(textLeft, y - textSize * 1.2f, textLeft + textWidth * progress, y + textSize * 0.35f)
        canvas.drawText(text, vw / 2f, y, progressPaint)
        canvas.restore()

        if (progress < 1f) postInvalidateOnAnimation()
    }
}
