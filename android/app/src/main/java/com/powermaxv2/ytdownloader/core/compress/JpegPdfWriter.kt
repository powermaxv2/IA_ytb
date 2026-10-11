package com.powermaxv2.ytdownloader.core.compress

import java.io.Closeable
import java.io.OutputStream
import java.util.Locale

/**
 * Writes a PDF whose pages are JPEG pictures (DCTDecode), one page at a time, so a long document never sits in
 * memory. Android's PdfDocument would store the pixels uncompressed (Flate), which makes a lighter PDF heavier:
 * this tiny writer is what lets the Compresseur rebuild a PDF smaller. Pure JVM, unit tested.
 */
class JpegPdfWriter(output: OutputStream) : Closeable {
    private val out = CountingStream(output)
    private val offsets = ArrayList<Long>()
    private val pageIds = ArrayList<Int>()
    private var finished = false

    init {
        // Header, then a comment of high bytes so tools treat the file as binary.
        out.write("%PDF-1.4\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        // Objects 1 (catalog) and 2 (page tree) are written last, their numbers are reserved now.
        offsets.add(-1)
        offsets.add(-1)
    }

    val pageCount: Int get() = pageIds.size

    /**
     * Adds a page of [widthPt] × [heightPt] points (1/72 inch) filled by the JPEG [jpeg] of [pixelWidth] ×
     * [pixelHeight] pixels (RGB baseline, as Bitmap.compress writes it). The picture fills the page, or the
     * [drawWidth] × [drawHeight] box at [x], [y] (points from the bottom-left corner; the rest stays white).
     */
    fun addPage(
        jpeg: ByteArray,
        pixelWidth: Int,
        pixelHeight: Int,
        widthPt: Float,
        heightPt: Float,
        x: Float = 0f,
        y: Float = 0f,
        drawWidth: Float = widthPt,
        drawHeight: Float = heightPt,
    ) {
        check(!finished)
        require(pixelWidth > 0 && pixelHeight > 0 && widthPt > 0 && heightPt > 0 && drawWidth > 0 && drawHeight > 0)
        val image = begin()
        out.write(
            ("<< /Type /XObject /Subtype /Image /Width $pixelWidth /Height $pixelHeight /ColorSpace /DeviceRGB " +
                "/BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n").latin1(),
        )
        out.write(jpeg)
        out.write("\nendstream\nendobj\n".latin1())

        val w = num(widthPt)
        val h = num(heightPt)
        val content = "q ${num(drawWidth)} 0 0 ${num(drawHeight)} ${num(x)} ${num(y)} cm /Im0 Do Q".latin1()
        val contents = begin()
        out.write("<< /Length ${content.size} >>\nstream\n".latin1())
        out.write(content)
        out.write("\nendstream\nendobj\n".latin1())

        val page = begin()
        out.write(
            ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] " +
                "/Resources << /XObject << /Im0 $image 0 R >> >> /Contents $contents 0 R >>\nendobj\n").latin1(),
        )
        pageIds.add(page)
    }

    /** Writes the page tree, the catalog and the cross-reference table. The stream itself stays open. */
    fun finish() {
        if (finished) return
        check(pageIds.isNotEmpty()) { "Un PDF a au moins une page" }
        finished = true
        offsets[1] = out.count
        out.write("2 0 obj\n<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pageIds.size} >>\nendobj\n".latin1())
        offsets[0] = out.count
        out.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".latin1())
        val xref = out.count
        val table = StringBuilder()
        table.append("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { table.append(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        table.append("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.write(table.toString().latin1())
        out.flush()
    }

    override fun close() {
        out.close()
    }

    /** Starts the next object and returns its number. */
    private fun begin(): Int {
        offsets.add(out.count)
        val id = offsets.size
        out.write("$id 0 obj\n".latin1())
        return id
    }

    private fun num(value: Float): String = String.format(Locale.ROOT, "%.2f", value)

    private fun String.latin1(): ByteArray = toByteArray(Charsets.ISO_8859_1)

    private class CountingStream(private val target: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            target.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            count += len
        }

        override fun flush() = target.flush()

        override fun close() = target.close()
    }
}
