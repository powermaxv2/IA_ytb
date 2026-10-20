package com.powermaxv2.ytdownloader.core.pdfimages

import android.graphics.Bitmap
import android.graphics.Rect
import com.powermaxv2.ytdownloader.core.convert.Images
import com.powermaxv2.ytdownloader.core.convert.Target
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.IdentityHashMap
import kotlin.coroutines.coroutineContext

/**
 * The pictures placed inside the pages of a PDF (photos of a catalogue, scans, illustrations), read with PdfBox.
 * A JPG picture is copied byte for byte (no quality lost); the others are decoded and written in the chosen format.
 * A picture used on several pages comes out once; hairlines, dots and tiny logos are left out.
 */
object EmbeddedImages {
    /** Forms inside forms: deeper than this is a loop or a pathological file. */
    private const val MAX_DEPTH = 6

    private const val SCRATCH_MEMORY = 48L * 1024 * 1024

    /**
     * Writes the pictures of [pages] (numbered from 1) of [pdf] into [outDir], named after [base]. [onPage] gets the
     * number of pages looked at and their total. Returns the written files, in page order.
     */
    suspend fun extract(
        pdf: File,
        pages: List<Int>,
        format: ImagesFormat,
        outDir: File,
        base: String,
        onPage: (Int, Int) -> Unit,
    ): List<File> {
        val out = ArrayList<File>()
        PDDocument.load(pdf, MemoryUsageSetting.setupMixed(SCRATCH_MEMORY)).use { doc ->
            val count = doc.numberOfPages
            val seen = IdentityHashMap<COSBase, Unit>()
            pages.forEachIndexed { done, number ->
                coroutineContext.ensureActive()
                if (number in 1..count) {
                    val found = ArrayList<PDImageXObject>()
                    runCatching { collect(doc.getPage(number - 1).resources, found, seen, 0) }
                    var rank = 0
                    for (image in found) {
                        coroutineContext.ensureActive()
                        val file = runCatching { write(image, format, outDir, base, number, count, rank + 1) }.getOrNull()
                        if (file != null) {
                            rank++
                            out += file
                        }
                    }
                }
                onPage(done + 1, pages.size)
            }
        }
        return out
    }

    /** The pictures of [resources] and of the forms it holds, each once ([seen] spans the whole document). */
    private fun collect(resources: PDResources?, into: MutableList<PDImageXObject>, seen: IdentityHashMap<COSBase, Unit>, depth: Int) {
        if (resources == null || depth > MAX_DEPTH) return
        for (name in resources.xObjectNames) {
            val xobject = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
            val key = xobject.cosObject
            if (seen.containsKey(key)) continue
            seen[key] = Unit
            when (xobject) {
                is PDImageXObject -> if (!xobject.isStencil && PdfImagesPlan.worthKeeping(xobject.width, xobject.height)) into += xobject
                is PDFormXObject -> collect(xobject.resources, into, seen, depth + 1)
            }
        }
    }

    private fun write(image: PDImageXObject, format: ImagesFormat, outDir: File, base: String, page: Int, count: Int, rank: Int): File? {
        // A JPG in grey or RGB is a ready JPG file: copied as is. CMYK JPGs look wrong outside a PDF, so they are redrawn.
        val components = runCatching { image.colorSpace.numberOfComponents }.getOrDefault(0)
        if (image.suffix == "jpg" && (components == 1 || components == 3) && !hasMask(image)) {
            val file = File(outDir, PdfImagesPlan.embeddedName(base, page, count, rank, "jpg"))
            image.stream.createInputStream(listOf(COSName.DCT_DECODE.name)).use { input ->
                file.outputStream().buffered().use { input.copyTo(it) }
            }
            if (file.length() > 0) return file
            file.delete()
        }
        val factor = PdfImagesPlan.subsampling(image.width, image.height)
        val bitmap: Bitmap = (if (factor > 1) image.getImage(Rect(0, 0, image.width, image.height), factor) else image.image) ?: return null
        try {
            val file = File(outDir, PdfImagesPlan.embeddedName(base, page, count, rank, format.ext))
            Images.encode(bitmap, if (format == ImagesFormat.PNG) Target.PNG else Target.JPG, PdfImagesPlan.JPEG_QUALITY, file)
            return file
        } finally {
            bitmap.recycle()
        }
    }

    /** A picture cut out by a mask (SMask / Mask) needs PdfBox to apply it: its raw JPG would show the hidden parts. */
    private fun hasMask(image: PDImageXObject): Boolean =
        image.cosObject.containsKey(COSName.SMASK) || image.cosObject.containsKey(COSName.MASK)
}
