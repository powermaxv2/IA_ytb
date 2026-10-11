package com.powermaxv2.ytdownloader.core.sticker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Hands a pack to WhatsApp ("Ajouter à WhatsApp"), with the intent of WhatsApp's sticker sample app. */
object WhatsAppStickers {
    const val CONSUMER = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"
    private const val ACTION = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"

    /** Extra WhatsApp puts in a cancelled result when it refuses the pack. */
    const val VALIDATION_ERROR = "validation_error"

    /** WhatsApp apps installed on the phone (the manifest's <queries> lets Gulp see them). */
    fun installed(context: Context): List<String> = listOf(CONSUMER, BUSINESS).filter { isInstalled(context, it) }

    private fun isInstalled(context: Context, name: String): Boolean = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(name, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(name, 0)
        }
        info.enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** The "Ajouter le pack" request, or null when WhatsApp is not installed. */
    fun addIntent(context: Context, pack: StickerPack): Intent? {
        val apps = installed(context)
        if (apps.isEmpty()) return null
        val intent = Intent(ACTION)
            .putExtra("sticker_pack_id", pack.id)
            .putExtra("sticker_pack_authority", StickerProvider.authority(context.packageName))
            .putExtra("sticker_pack_name", pack.name)
        // Both WhatsApp and WhatsApp Business: Android asks which one.
        return if (apps.size == 1) intent.setPackage(apps.first()) else Intent.createChooser(intent, "Ajouter le pack à")
    }
}
