package com.powermaxv2.ytdownloader.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

// Reading and writing codes with ZXing (Apache-2.0), on the phone, offline. Pure JVM: no Android class.

/** The modules of a QR code, without the quiet zone: [size] × [size], row by row. */
class QrMatrix(val size: Int, private val cells: BooleanArray, val strong: Boolean) {
    operator fun get(x: Int, y: Int): Boolean = cells[y * size + x]

    /** True for the three big squares in the corners (drawn as rounded eyes, never hidden by the logo). */
    fun isFinder(x: Int, y: Int): Boolean {
        fun inBox(bx: Int, by: Int) = x in bx until bx + 7 && y in by until by + 7
        return inBox(0, 0) || inBox(size - 7, 0) || inBox(0, size - 7)
    }

    /** Modules hidden under the Gulp logo: a centred square of about a sixth of the side, only on a [strong] code. */
    fun logoSpan(): IntRange? {
        if (!strong) return null
        val side = (size * 0.22f).toInt().let { if (it % 2 == size % 2) it else it + 1 }.coerceAtLeast(5)
        val start = (size - side) / 2
        return start until start + side
    }
}

/** A code read by the scanner: its text and its ZXing format name ("QR_CODE", "EAN_13"…). */
data class ScanResult(val text: String, val format: String)

object QrCodec {
    /** The most a created code holds: past this, phones struggle to read a code that dense. */
    const val MAX_CHARS = 900

    /**
     * The QR code of [text]: error correction H (30 % can be hidden, so the logo fits) when the text is short enough
     * for a code that stays easy to scan, M otherwise (no logo then). Null when [text] is empty or too long.
     */
    fun encode(text: String): QrMatrix? {
        if (text.isEmpty() || text.length > MAX_CHARS) return null
        val strong = text.toByteArray(Charsets.UTF_8).size <= 400
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to if (strong) ErrorCorrectionLevel.H else ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 0,
            )
            val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
            val n = bits.width
            QrMatrix(n, BooleanArray(n * n) { bits[it % n, it / n] }, strong)
        } catch (e: WriterException) {
            null
        }
    }

    private val formats = listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC,
        BarcodeFormat.PDF_417,
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
        BarcodeFormat.ITF,
    )

    private fun reader(tryHarder: Boolean) = MultiFormatReader().apply {
        val hints = mutableMapOf<DecodeHintType, Any>(DecodeHintType.POSSIBLE_FORMATS to formats)
        if (tryHarder) hints[DecodeHintType.TRY_HARDER] = true
        setHints(hints)
    }

    /** A camera frame (its Y plane, [rowStride] bytes per row): quick read, tried on every frame. Null when nothing is seen. */
    fun decodeFrame(y: ByteArray, rowStride: Int, width: Int, height: Int): ScanResult? =
        decode(PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false), tryHarder = false)

    /** A picture (ARGB pixels), read carefully: as is, then with light and dark swapped (a white code on black). */
    fun decodePicture(pixels: IntArray, width: Int, height: Int): ScanResult? {
        val source = RGBLuminanceSource(width, height, pixels)
        return decode(source, tryHarder = true) ?: decode(source.invert(), tryHarder = true)
    }

    private fun decode(source: LuminanceSource, tryHarder: Boolean): ScanResult? = try {
        val result = reader(tryHarder).decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        result.text?.takeIf { it.isNotEmpty() }?.let { ScanResult(it, result.barcodeFormat.name) }
    } catch (e: NotFoundException) {
        null
    } catch (e: ReaderException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
