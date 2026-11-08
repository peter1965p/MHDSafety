package com.paeffgen.mhdsafety

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.paeffgen.mhdsafety.domain.DateImage
import com.paeffgen.mhdsafety.domain.DateParser
import com.paeffgen.mhdsafety.domain.DateVariant
import com.paeffgen.mhdsafety.domain.Gray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.random.Random

/**
 * Echte Texterkennung auf künstlichen Packungsaufdrucken: Punktmatrix-Schrift,
 * unterschiedlich blass, mit Rauschen. Vergleicht rohes Bild mit den Aufbereitungen.
 */
@RunWith(AndroidJUnit4::class)
class DateScanQualityTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val expected = LocalDate.of(2027, 3, 12)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Zeichnet [text] als Punktmatrix: erst winzig (≈7 px hoch) rendern, dann jedes
     * dunkle Pixel als runden Tintenpunkt mit Abstand dazwischen.
     */
    private fun dotMatrix(text: String, ink: Int, background: Int, dotSpacing: Int, noise: Int, seed: Int): Gray {
        val paint = Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = 9f
            color = Color.BLACK
            isAntiAlias = false
        }
        val tw = paint.measureText(text).toInt() + 4
        val small = Bitmap.createBitmap(tw, 12, Bitmap.Config.ARGB_8888)
        Canvas(small).apply { drawColor(Color.WHITE); drawText(text, 2f, 10f, paint) }

        val margin = dotSpacing * 6
        val w = tw * dotSpacing + 2 * margin
        val h = 12 * dotSpacing + 2 * margin
        val px = IntArray(w * h) { background }
        val r = dotSpacing * 0.33
        for (sy in 0 until 12) for (sx in 0 until tw) {
            if (Color.red(small.getPixel(sx, sy)) > 128) continue
            val cx = margin + sx * dotSpacing + dotSpacing / 2
            val cy = margin + sy * dotSpacing + dotSpacing / 2
            for (y in (cy - r.toInt())..(cy + r.toInt())) for (x in (cx - r.toInt())..(cx + r.toInt())) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) px[y * w + x] = ink
            }
        }
        val rnd = Random(seed)
        // Leichter Helligkeitsverlauf wie bei schräger Beleuchtung, plus Sensorrauschen.
        for (y in 0 until h) for (x in 0 until w) {
            val shade = (x * 25 / w) - 12
            px[y * w + x] = (px[y * w + x] + shade + rnd.nextInt(-noise, noise + 1)).coerceIn(0, 255)
        }
        return Gray(w, h, px)
    }

    private fun read(gray: Gray): String {
        val argb = IntArray(gray.pixels.size) { i -> gray.pixels[i].let { (0xFF shl 24) or (it shl 16) or (it shl 8) or it } }
        val bmp = Bitmap.createBitmap(argb, gray.width, gray.height, Bitmap.Config.ARGB_8888)
        return Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0))).text.replace('\n', ' ')
    }

    @Test
    fun compareVariants() {
        val cases = listOf(
            // ink, background, Punktabstand, Rauschen
            Triple("kräftig", 40 to 200, 5),
            Triple("mittel", 130 to 200, 5),
            Triple("blass", 170 to 200, 5),
            Triple("sehr blass", 182 to 200, 5),
            Triple("blass grob", 170 to 200, 7),
            Triple("blass fein", 170 to 200, 4),
        )
        val wins = mutableMapOf<DateVariant, Int>()
        var anyWins = 0
        for ((label, colors, spacing) in cases) {
            for (seed in 1..3) {
                val gray = dotMatrix("MHD 12.03.27", colors.first, colors.second, spacing, noise = 6, seed = seed)
                val hits = DateVariant.entries.filter { variant ->
                    val text = read(DateImage.prepare(gray, variant))
                    val ok = DateParser.parse(text, today)?.date == expected
                    Log.i("DateScanQuality", "$label/$seed $variant ${if (ok) "OK " else "-- "} „$text“")
                    ok
                }
                hits.forEach { wins[it] = (wins[it] ?: 0) + 1 }
                if (hits.isNotEmpty()) anyWins++
            }
        }
        Log.i("DateScanQuality", "Treffer je Variante: $wins, mindestens eine: $anyWins von ${cases.size * 3}")
        assertTrue("Aufbereitung soll mehr lesen als roh: $wins", (wins[DateVariant.RAW] ?: 0) < anyWins || anyWins == cases.size * 3)
    }

    /** Normaler, durchgehender Druck – dunkel auf hell oder hell auf dunkel. */
    private fun solid(text: String, ink: Int, background: Int): Gray {
        val paint = Paint().apply { textSize = 48f; color = Color.BLACK; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
        val w = paint.measureText(text).toInt() + 60
        val bmp = Bitmap.createBitmap(w, 100, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(Color.WHITE); drawText(text, 30f, 66f, paint) }
        val rnd = Random(7)
        return Gray(w, 100, IntArray(w * 100) { i ->
            val t = Color.red(bmp.getPixel(i % w, i / w)) / 255f
            (ink + (background - ink) * t).toInt().plus(rnd.nextInt(-5, 6)).coerceIn(0, 255)
        })
    }

    @Test
    fun solidPrintStillWorks() {
        for ((label, gray) in listOf("dunkel auf hell" to solid("MHD 12.03.27", 30, 210), "hell auf dunkel" to solid("MHD 12.03.27", 220, 40))) {
            val hits = DateVariant.entries.filter { DateParser.parse(read(DateImage.prepare(gray, it)), today)?.date == expected }
            Log.i("DateScanQuality", "$label: $hits")
            assertTrue("$label: $hits", hits.isNotEmpty())
        }
    }
}
