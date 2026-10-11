package com.powermaxv2.ytdownloader.core.sticker

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * How WhatsApp reads Gulp's packs (the contract of WhatsApp's "stickers" sample app). Read-only, and only for
 * WhatsApp: the manifest guards it with WhatsApp's own `com.whatsapp.sticker.READ` permission.
 *
 * - `content://<authority>/metadata` and `/metadata/<id>`: the packs
 * - `/stickers/<id>`: the stickers of a pack (file name + emoji)
 * - `/stickers_asset/<id>/<file>`: a sticker or the tray icon
 */
class StickerProvider : ContentProvider() {
    private lateinit var matcher: UriMatcher

    override fun onCreate(): Boolean {
        val authority = authority(context!!.packageName)
        matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, METADATA, ALL_PACKS)
            addURI(authority, "$METADATA/*", ONE_PACK)
            addURI(authority, "$STICKERS/*", PACK_STICKERS)
            addURI(authority, "$STICKERS_ASSET/*/*", ASSET)
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = context ?: return null
        return when (matcher.match(uri)) {
            ALL_PACKS -> packs(StickerStore.list(context))
            ONE_PACK -> packs(listOfNotNull(StickerStore.find(context, uri.lastPathSegment)))
            PACK_STICKERS -> stickers(StickerStore.find(context, uri.lastPathSegment))
            else -> null
        }
    }

    private fun packs(packs: List<StickerPack>): Cursor {
        val cursor = MatrixCursor(PACK_COLUMNS)
        packs.forEach { pack ->
            cursor.addRow(
                arrayOf<Any>(
                    pack.id,
                    pack.name,
                    StickerRules.PUBLISHER,
                    StickerRules.TRAY_FILE,
                    "", // Play Store link
                    "", // iOS link
                    "", // e-mail
                    "", // website
                    "", // privacy policy
                    "", // licence
                    "1", // image data version: a pack never changes
                    0, // WhatsApp may cache the stickers
                    if (pack.animated) 1 else 0,
                ),
            )
        }
        return cursor
    }

    private fun stickers(pack: StickerPack?): Cursor {
        val cursor = MatrixCursor(arrayOf(STICKER_FILE_NAME, STICKER_EMOJI, STICKER_ACCESSIBILITY))
        pack?.stickers?.forEach { cursor.addRow(arrayOf(it, pack.emoji, "Sticker Gulp")) }
        return cursor
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        if (matcher.match(uri) != ASSET || mode != "r") throw FileNotFoundException("Fichier inconnu")
        val segments = uri.pathSegments
        val file = StickerStore.asset(context!!, segments.getOrNull(1), segments.getOrNull(2))
            ?: throw FileNotFoundException("Fichier inconnu")
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(descriptor, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String? {
        val authority = authority(context!!.packageName)
        return when (matcher.match(uri)) {
            ALL_PACKS, ONE_PACK -> "vnd.android.cursor.dir/vnd.$authority.$METADATA"
            PACK_STICKERS -> "vnd.android.cursor.dir/vnd.$authority.$STICKERS"
            ASSET -> if (uri.lastPathSegment == StickerRules.TRAY_FILE) "image/png" else "image/webp"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        fun authority(packageName: String) = "$packageName.stickers"

        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"
        private const val ALL_PACKS = 1
        private const val ONE_PACK = 2
        private const val PACK_STICKERS = 3
        private const val ASSET = 4

        private const val STICKER_FILE_NAME = "sticker_file_name"
        private const val STICKER_EMOJI = "sticker_emoji"
        private const val STICKER_ACCESSIBILITY = "sticker_accessibility_text"

        val PACK_COLUMNS = arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_download_link",
            "sticker_pack_publisher_email",
            "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement_website",
            "image_data_version",
            "whatsapp_will_not_cache_stickers",
            "animated_sticker_pack",
        )
    }
}
