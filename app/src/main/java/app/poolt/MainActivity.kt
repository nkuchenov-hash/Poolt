package app.poolt

import android.os.Bundle
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color as AColor
import android.animation.ValueAnimator
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.doOnLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.poolt.core.Device
import app.poolt.core.RemoteCommand

private val Bg = Color(0xFF0A0A0C)
private val Panel = Color(0xFF17171B)
private val Panel2 = Color(0xFF202026)
private val Muted = Color(0xFF9A9AA1)
private val Accent = Color(0xFFE6263E)

class MainActivity : ComponentActivity() {
    private var splashFinished = false
    private var splashTimerStarted = false
    private var wordFillView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(AndroidColor.BLACK) }

        // Keep the approved sunset artwork and its existing remote illustration.
        // Draw only the localized brand word on a separate bitmap; the app's
        // internal package, user data and Wi-Fi pairing identity stay unchanged.
        val originalArtwork = BitmapFactory.decodeResource(resources, R.drawable.poolt_splash)
        val artwork = createBrandedSplash(originalArtwork, getString(R.string.app_name))
        val splash = ImageView(this).apply {
            setImageBitmap(artwork)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(
            splash,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        // Recolour only the bright pixels of the original Poolt lettering.
        // No second font, no added words and no JPEG approximation.
        val wordFill = ImageView(this).apply {
            setImageBitmap(makeWordFill(artwork))
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipBounds = Rect(0, 0, 0, 0)
        }
        root.addView(
            wordFill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        wordFillView = wordFill
        setContentView(root)
    }

    /**
     * Rebrand the wordmark without touching the TV, remote, red logo or layout.
     * The old white word is on an almost-black uniform strip below the logo.
     * Interpolate its background horizontally before painting the new name.
     */
    private fun createBrandedSplash(original: Bitmap, title: String): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height
        val left = (width * 0.285f).toInt()
        val right = (width * 0.72f).toInt()
        val top = (height * 0.814f).toInt()
        val bottom = (height * 0.883f).toInt()
        for (y in top until bottom) {
            val leftPixel = original.getPixel((left - 12).coerceAtLeast(0), y)
            val rightPixel = original.getPixel((right + 12).coerceAtMost(width - 1), y)
            for (x in left until right) {
                val t = (x - left).toFloat() / (right - left).coerceAtLeast(1)
                fun mix(a: Int, b: Int) = (a * (1f - t) + b * t).toInt().coerceIn(0, 255)
                result.setPixel(x, y, AndroidColor.rgb(
                    mix(AndroidColor.red(leftPixel), AndroidColor.red(rightPixel)),
                    mix(AndroidColor.green(leftPixel), AndroidColor.green(rightPixel)),
                    mix(AndroidColor.blue(leftPixel), AndroidColor.blue(rightPixel))
                ))
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = AndroidColor.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = width * 0.089f
        }
        val maxWordWidth = width * 0.82f
        val actualWidth = paint.measureText(title)
        if (actualWidth > maxWordWidth) paint.textSize *= maxWordWidth / actualWidth
        Canvas(result).drawText(title, width / 2f, height * 0.867f, paint)
        return result
    }

    private fun makeWordFill(artwork: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(artwork.width, artwork.height, Bitmap.Config.ARGB_8888)
        val x0 = (artwork.width * 0.085f).toInt()
        val y0 = (artwork.height * 0.809f).toInt()
        val right = (artwork.width * 0.915f).toInt().coerceAtMost(artwork.width)
        val bottom = (artwork.height * 0.887f).toInt().coerceAtMost(artwork.height)
        val w = right - x0
        val h = bottom - y0
        if (w <= 0 || h <= 0) return result
        val original = IntArray(w * h)
        val coloured = IntArray(w * h)
        artwork.getPixels(original, 0, w, x0, y0, w, h)
        for (i in original.indices) {
            val c = original[i]
            val brightness = minOf(AColor.red(c), AColor.green(c), AColor.blue(c))
            val alpha = ((brightness - 45).coerceIn(0, 180) * 255 / 180)
            coloured[i] = AColor.argb(alpha, 230, 38, 62)
        }
        result.setPixels(coloured, 0, w, x0, y0, w, h)
        return result
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || splashTimerStarted || splashFinished) return

        splashTimerStarted = true
        // Clip the whole correctly-aligned overlay, not guessed window coordinates.
        wordFillView?.doOnLayout { fill ->
            if (isFinishing || isDestroyed || splashFinished) return@doOnLayout
            val width = fill.width
            val height = fill.height
            if (width <= 0 || height <= 0) return@doOnLayout
            fill.clipBounds = Rect(0, 0, 0, height)
            ValueAnimator.ofInt(0, width).apply {
                duration = 1450L
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { animation ->
                    fill.clipBounds = Rect(0, 0, animation.animatedValue as Int, height)
                }
                start()
            }
        }
        window.decorView.postDelayed({
            if (isFinishing || isDestroyed || splashFinished) return@postDelayed
            splashFinished = true
            enableEdgeToEdge()
            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        background = Bg,
                        surface = Bg,
                        primary = Accent,
                        onPrimary = Color.White,
                        secondary = Accent,
                        onSecondary = Color.White,
                        primaryContainer = Panel2,
                        onPrimaryContainer = Color.White,
                        onBackground = Color.White,
                        onSurface = Color.White
                    )
                ) { PooltApp() }
            }
        }, 1600L)
    }
}

// All protocol handling, pairing and splash remain in their existing components.
// The visual remote is isolated in RemoteScreen.kt for safe design iterations.
@Composable
fun PooltApp(vm: MainViewModel = viewModel()) {
    RemoteScreen(vm)
}
