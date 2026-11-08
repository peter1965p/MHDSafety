package com.paeffgen.mhdsafety.domain

import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Rechteck in Pixeln, rechts/unten exklusiv. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Graustufenbild, 0 = schwarz, 255 = weiß. */
class Gray(val width: Int, val height: Int, val pixels: IntArray) {
    operator fun get(x: Int, y: Int) = pixels[y * width + x]
}

/** Wie der Bildausschnitt für die Texterkennung aufbereitet wird. */
enum class DateVariant {
    /** Unverändert – klappt bei sauberem Druck am besten. */
    RAW,

    /** Lokaler Kontrast hochgezogen: blasser Druck wird schwarz auf weiß. */
    CONTRAST,

    /** Wie CONTRAST, zusätzlich die Punkte von Punktmatrix-Schrift zu Strichen verbunden. */
    DOTS,

    /** Helle Schrift auf dunklem Grund (gelasert, weiße Tinte) umgedreht. */
    INVERTED,
}

/**
 * Bildaufbereitung fürs Datum-Scannen, ohne Android-Klassen – damit testbar.
 *
 * Die Kamera liefert das Bild liegend (Sensor), die Vorschau zeigt es aufrecht und
 * mittig beschnitten (FILL_CENTER). Wir schneiden genau den Rahmen aus, drehen ihn
 * aufrecht und machen den Kontrast so, dass die Texterkennung damit klarkommt.
 */
object DateImage {
    /**
     * Der Vorschau-Rahmen in Koordinaten des aufrechten Kamerabilds.
     * @param extendUp so viel (in Vorschau-Pixeln) über dem Rahmen mitnehmen, für „zu verbrauchen bis“
     */
    fun uprightBox(
        imageWidth: Int, imageHeight: Int, rotation: Int,
        viewWidth: Int, viewHeight: Int, frame: Box, extendUp: Int,
    ): Box? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val uw = if (rotation % 180 == 0) imageWidth else imageHeight
        val uh = if (rotation % 180 == 0) imageHeight else imageWidth
        val scale = max(viewWidth.toFloat() / uw, viewHeight.toFloat() / uh)
        fun x(v: Int) = (uw / 2f + (v - viewWidth / 2f) / scale).roundToInt().coerceIn(0, uw)
        fun y(v: Int) = (uh / 2f + (v - viewHeight / 2f) / scale).roundToInt().coerceIn(0, uh)
        val box = Box(x(frame.left), y(frame.top - extendUp), x(frame.right), y(frame.bottom))
        return box.takeIf { it.width > 8 && it.height > 8 }
    }

    /** Wie viele Zeilen oben im Ausschnitt noch über dem eigentlichen Rahmen liegen. */
    fun rowsAboveFrame(box: Box, frame: Box, extendUp: Int): Int =
        if (extendUp <= 0) 0 else (box.height * extendUp.toFloat() / (frame.height + extendUp)).roundToInt()

    /**
     * Schneidet [box] (aufrechte Koordinaten) aus der Helligkeitsebene des Sensors aus
     * und dreht dabei gleich richtig herum.
     * @param rotation um so viel Grad im Uhrzeigersinn muss das Sensorbild gedreht werden
     */
    fun uprightCrop(
        luma: ByteBuffer, rowStride: Int, pixelStride: Int,
        imageWidth: Int, imageHeight: Int, rotation: Int, box: Box,
    ): Gray {
        val w = box.width
        val h = box.height
        val out = IntArray(w * h)
        for (j in 0 until h) {
            val uy = box.top + j
            for (i in 0 until w) {
                val ux = box.left + i
                val sx: Int
                val sy: Int
                when (rotation) {
                    90 -> { sx = uy; sy = imageHeight - 1 - ux }
                    180 -> { sx = imageWidth - 1 - ux; sy = imageHeight - 1 - uy }
                    270 -> { sx = imageWidth - 1 - uy; sy = ux }
                    else -> { sx = ux; sy = uy }
                }
                out[j * w + i] = luma.get(sy * rowStride + sx * pixelStride).toInt() and 0xFF
            }
        }
        return Gray(w, h, out)
    }

    fun prepare(gray: Gray, variant: DateVariant): Gray = when (variant) {
        DateVariant.RAW -> gray
        DateVariant.CONTRAST -> normalize(gray, darkText = true)
        DateVariant.DOTS -> joinDots(normalize(gray, darkText = true), dotRadius(gray))
        DateVariant.INVERTED -> joinDots(normalize(gray, darkText = false), dotRadius(gray))
    }

    /** Punktabstand wächst mit der Schriftgröße, und die mit der Ausschnitthöhe. */
    private fun dotRadius(gray: Gray) = (gray.height / 120).coerceIn(1, 3)

    /**
     * Lokaler Kontrast: jedes Pixel wird mit seiner Umgebung verglichen statt mit dem ganzen
     * Bild. So wird blasser Druck tiefschwarz, und Schatten oder Glanz auf der Packung stören
     * nicht. Ergebnis ist immer dunkle Schrift auf weißem Grund.
     * @param darkText false für helle Schrift auf dunklem Grund
     */
    fun normalize(gray: Gray, darkText: Boolean): Gray {
        val w = gray.width
        val h = gray.height
        val smooth = boxBlur(gray, 1)
        // Fenster deutlich größer als ein Schriftstrich, damit es den Hintergrund misst.
        val background = boxBlur(smooth, max(4, min(w, h) / 6))
        val ink = IntArray(w * h) { i ->
            val d = smooth.pixels[i] - background.pixels[i]
            max(0, if (darkText) -d else d)
        }
        // Robuste Obergrenze statt Maximum, sonst bestimmen einzelne Ausreißer alles.
        val histogram = IntArray(256)
        ink.forEach { histogram[min(it, 255)]++ }
        var seen = 0
        var strong = 255
        for (v in 255 downTo 0) {
            seen += histogram[v]
            if (seen >= ink.size * 0.02) {
                strong = v
                break
            }
        }
        // Schon bei drei Vierteln davon voll schwarz: kräftige Striche liest die Erkennung besser.
        // Untergrenze, damit reines Rauschen nicht zu Schrift aufgeblasen wird.
        strong = max(strong * 3 / 4, 8)
        val out = IntArray(w * h) { i -> 255 - min(255, ink[i] * 255 / strong) }
        return Gray(w, h, out)
    }

    /** Minimumfilter: dunkle Punkte wachsen, bis die Lücken dazwischen zu sind. */
    fun joinDots(gray: Gray, radius: Int): Gray {
        val w = gray.width
        val h = gray.height
        val horizontal = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var m = 255
                for (k in max(0, x - radius)..min(w - 1, x + radius)) m = min(m, gray.pixels[y * w + k])
                horizontal[y * w + x] = m
            }
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var m = 255
                for (k in max(0, y - radius)..min(h - 1, y + radius)) m = min(m, horizontal[k * w + x])
                out[y * w + x] = m
            }
        }
        return Gray(w, h, out)
    }

    /** Mittelwert über ein (2r+1)²-Fenster, per Summentabelle in konstanter Zeit pro Pixel. */
    private fun boxBlur(gray: Gray, radius: Int): Gray {
        val w = gray.width
        val h = gray.height
        val sum = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += gray.pixels[y * w + x]
                sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row
            }
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - radius)
            val y1 = min(h, y + radius + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - radius)
                val x1 = min(w, x + radius + 1)
                val s = sum[y1 * (w + 1) + x1] - sum[y0 * (w + 1) + x1] - sum[y1 * (w + 1) + x0] + sum[y0 * (w + 1) + x0]
                out[y * w + x] = (s / ((y1 - y0) * (x1 - x0))).toInt()
            }
        }
        return Gray(w, h, out)
    }
}

/**
 * Sammelt die erkannten Daten der letzten Bilder. Ein Datum gilt erst, wenn es mehrmals
 * gelesen wurde und kein anderes öfter – einzelne Lesefehler rutschen so nicht durch,
 * und ein Bild ohne Treffer (etwa beim Wechsel der Aufbereitung) setzt nichts zurück.
 */
class DateVotes(private val window: Int = 8, private val needed: Int = 2) {
    private val recent = ArrayDeque<ParsedDate?>()

    /** @return das sichere Datum, sobald es eins gibt */
    fun add(parsed: ParsedDate?): ParsedDate? {
        recent.addLast(parsed)
        while (recent.size > window) recent.removeFirst()
        val counts = recent.filterNotNull().groupingBy { it.date }.eachCount()
        val (date, count) = counts.maxByOrNull { it.value } ?: return null
        if (count < needed || counts.count { it.value == count } > 1) return null
        // "zu verbrauchen" zählt, wenn es bei irgendeinem Treffer dabei stand.
        return ParsedDate(date, useBy = recent.any { it?.date == date && it.useBy })
    }

    fun clear() = recent.clear()
}
