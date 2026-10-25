package com.powermaxv2.ytdownloader.core.billing

import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
import kotlinx.coroutines.delay

/** What the "Gulp Plus" unlock sells: one purchase, no subscription. */
object PlusProduct {
    /** Product id in the Play Console (one-time, non-consumable) once real billing is plugged in. */
    const val ID = "gulp_plus"
    const val PRICE_CENTS = 200
    const val PRICE_LABEL = "2 €"

    /** Donation amounts offered, in cents (consumable products "gulp_don_1", "gulp_don_2"… on Google Play). */
    val DONATIONS = listOf(100, 200, 500, 1000)

    fun label(cents: Int): String = if (cents % 100 == 0) "${cents / 100} €" else "${cents / 100},${(cents % 100).toString().padStart(2, '0')} €"
}

/** Which tools need Gulp Plus. Everything else is free, forever. */
object PlusCatalog {
    /** Heavy or "pro" tools: video/photo processing, documents, private vault. */
    val premiumIds: Set<String> = setOf(
        ToolRegistry.COMPRESSOR,
        ToolRegistry.TRIMMER,
        ToolRegistry.SLIDESHOW,
        ToolRegistry.GIF_MAKER,
        ToolRegistry.VIDEO_SPEED,
        ToolRegistry.SCANNER,
        ToolRegistry.SIGN_PDF,
        ToolRegistry.WATERMARK,
        ToolRegistry.PDF_JOIN,
        ToolRegistry.WIPER,
        ToolRegistry.VAULT,
        ToolRegistry.VOICE_TEXT,
        ToolRegistry.STICKERS,
        ToolRegistry.COLLAGE,
    )

    fun isPremium(toolId: String?): Boolean = toolId != null && toolId in premiumIds

    /** Needs Gulp Plus and the user does not have it. */
    fun locked(toolId: String?, plus: Boolean): Boolean = !plus && isPremium(toolId)
}

sealed interface BillingResult {
    data object Success : BillingResult
    data object Cancelled : BillingResult
    data class Failed(val message: String) : BillingResult
}

/**
 * Payment backend. Today only [SimulatedBilling] exists (nobody is charged); a `PlayBilling` class wrapping
 * Google Play Billing (queryProductDetails + launchBillingFlow + acknowledge) implements the same interface
 * and replaces it in [com.powermaxv2.ytdownloader.App], nothing else changes.
 */
interface BillingGateway {
    /** True while no real money moves (the screens say so). */
    val simulated: Boolean

    suspend fun buyPlus(): BillingResult

    /** Gives Plus back to someone who already bought it (new phone, reinstall); true if owned. */
    suspend fun restore(): Boolean

    suspend fun donate(cents: Int): BillingResult
}

/** Mock payment: a short wait, then success. */
class SimulatedBilling(private val owned: () -> Boolean, private val latencyMs: Long = 1700) : BillingGateway {
    override val simulated = true

    override suspend fun buyPlus(): BillingResult {
        delay(latencyMs)
        return BillingResult.Success
    }

    override suspend fun restore(): Boolean {
        delay(700)
        return owned()
    }

    override suspend fun donate(cents: Int): BillingResult {
        delay(latencyMs)
        return BillingResult.Success
    }
}
