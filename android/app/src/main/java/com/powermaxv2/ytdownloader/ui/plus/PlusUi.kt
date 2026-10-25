package com.powermaxv2.ytdownloader.ui.plus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.billing.PlusCatalog
import com.powermaxv2.ytdownloader.core.billing.PlusProduct
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.PayPhase
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PrimaryButton
import com.powermaxv2.ytdownloader.ui.SuccessBurst
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
import com.powermaxv2.ytdownloader.ui.panel
import com.powermaxv2.ytdownloader.ui.pulsing
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Gold = Color(0xFFFFC53D)
private val GoldDeep = Color(0xFFFF8A1F)

/** The Plus and donation pop-ups, drawn over the whole app (the skin's theme applies). */
@Composable
fun PlusOverlays(vm: MainViewModel) {
    BackHandler(enabled = vm.paywall != null) { vm.dismissPaywall() }
    BackHandler(enabled = vm.donateOpen) { vm.closeDonate() }
    PopupSheet(open = vm.paywall != null, onDismiss = vm::dismissPaywall) {
        PaywallContent(vm)
    }
    PopupSheet(open = vm.donateOpen, onDismiss = vm::closeDonate) {
        DonateContent(vm)
    }
}

@Composable
private fun PopupSheet(open: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            open,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.78f, stiffness = 380f)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(200)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Palette.Surface)
                    .border(1.dp, Palette.Stroke, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Palette.Stroke))
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

// MARK: - Gulp Plus

@Composable
private fun PaywallContent(vm: MainViewModel) {
    val request = vm.paywall
    // Keep the last request while the sheet slides away.
    var last by remember { mutableStateOf(request) }
    if (request != null) last = request
    PaywallBody(
        toolName = ToolRegistry.find(last?.toolId)?.name,
        phase = vm.paywallPhase,
        simulated = vm.billing.simulated,
        onBuy = vm::buyPlus, onRestore = vm::restorePlus, onLater = vm::dismissPaywall,
    )
}

/** Stateless Plus pop-up content (also drawn by the screenshot tests). */
@Composable
fun PaywallBody(toolName: String?, phase: PayPhase, simulated: Boolean, onBuy: () -> Unit, onRestore: () -> Unit, onLater: () -> Unit) {
    val done = phase == PayPhase.DONE

    Box(contentAlignment = Alignment.Center) {
        PlusHalo(Modifier.size(168.dp))
        SuccessBurst(play = done, color = Gold, modifier = Modifier.size(190.dp))
        Box(
            Modifier
                .size(74.dp)
                .pulsing(0.96f, 1.06f)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Gold, GoldDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (done) Icons.Rounded.Check else Icons.Rounded.AutoAwesome, null, tint = Color(0xFF3A1B00), modifier = Modifier.size(38.dp))
        }
    }
    Text(
        if (done) "Bienvenue dans Gulp Plus" else "Gulp Plus",
        color = Palette.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
        fontFamily = LocalTokens.current.display, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        when {
            done -> if (toolName != null) "$toolName s'ouvre…" else "Tous les outils Plus sont à toi."
            toolName != null -> "$toolName fait partie de Gulp Plus."
            else -> "Débloque les ${PlusCatalog.premiumIds.size} outils avancés."
        },
        color = Palette.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    if (!done) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val perks = listOf(
                "${PlusCatalog.premiumIds.size} outils avancés" to "Compresseur, découpeur, scanner, coffre-fort, signature PDF…",
                "Un seul paiement" to "${PlusProduct.PRICE_LABEL} une fois pour toutes, pas d'abonnement.",
                "Les prochains outils Plus inclus" to "Chaque nouvel outil avancé s'ajoute sans repayer.",
                "Toujours hors ligne" to "Pas de compte, pas de pub, tes fichiers restent sur ton téléphone.",
            )
            perks.forEachIndexed { i, (title, text) ->
                Row(Modifier.fillMaxWidth().staggeredEntrance(i, baseDelay = 250), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Gold, GoldDeep))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Check, null, tint = Color(0xFF3A1B00), modifier = Modifier.size(13.dp)) }
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Text(text, color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = "Débloquer pour ${PlusProduct.PRICE_LABEL}",
            modifier = Modifier.fillMaxWidth(),
            loading = phase == PayPhase.WORKING,
            onClick = onBuy,
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextLink("Restaurer mon achat", enabled = phase == PayPhase.OFFER, onClick = onRestore)
            TextLink("Plus tard", enabled = phase == PayPhase.OFFER, onClick = onLater)
        }
        if (simulated) SimulationNote()
    } else {
        Spacer(Modifier.height(12.dp))
    }
    Spacer(Modifier.height(6.dp))
}

/** Slowly turning golden rays behind the sheet's icon. */
@Composable
private fun PlusHalo(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "halo")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "haloT")
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        rotate(t * 360f, c) {
            for (i in 0 until 12) {
                val a = i * (2 * PI / 12)
                val inner = size.minDimension * 0.30f
                val outer = size.minDimension * (if (i % 2 == 0) 0.50f else 0.42f)
                drawLine(
                    Gold.copy(alpha = if (i % 2 == 0) 0.55f else 0.30f),
                    c + Offset((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat()),
                    c + Offset((cos(a) * outer).toFloat(), (sin(a) * outer).toFloat()),
                    strokeWidth = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun TextLink(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = Palette.TextTertiary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    )
}

@Composable
private fun SimulationNote() {
    Text(
        "Mode démonstration : aucun paiement réel n'est effectué.",
        color = Palette.TextTertiary, fontSize = 11.5.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

// MARK: - Donation

@Composable
private fun DonateContent(vm: MainViewModel) {
    DonateBody(vm.donatePhase, vm.billing.simulated, onDonate = vm::donate, onLater = vm::closeDonate)
}

/** Stateless donation pop-up content (also drawn by the screenshot tests). */
@Composable
fun DonateBody(phase: PayPhase, simulated: Boolean, onDonate: (Int) -> Unit, onLater: () -> Unit, initialAmount: Int = PlusProduct.DONATIONS[1]) {
    var amount by remember { mutableStateOf(initialAmount) }
    val done = phase == PayPhase.DONE

    Box(contentAlignment = Alignment.Center) {
        SuccessBurst(play = done, color = Palette.Danger, modifier = Modifier.size(190.dp))
        Box(
            Modifier
                .size(120.dp)
                .pulsing(0.9f, if (done) 1.18f else 1.08f, if (done) 500 else 900),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Favorite, null, tint = Palette.Danger, modifier = Modifier.size(78.dp))
        }
    }
    Text(
        if (done) "Merci !" else "Soutenir Gulp",
        color = Palette.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
        fontFamily = LocalTokens.current.display,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        if (done) "Ton don de ${PlusProduct.label(amount)} fait vivre Gulp. Vraiment." else
            "Gulp est fait par une seule personne, sans pub et sans suivi. Un petit don aide à garder ça comme ça.",
        color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
    )
    if (!done) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlusProduct.DONATIONS.forEach { cents ->
                val selected = cents == amount
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Brush.linearGradient(listOf(LocalAccent.current.start, LocalAccent.current.end)) else Brush.linearGradient(listOf(Palette.Surface2, Palette.Surface2)))
                        .border(1.dp, if (selected) Color.Transparent else Palette.Stroke, RoundedCornerShape(16.dp))
                        .bounceClick(enabled = phase == PayPhase.OFFER) { amount = cents },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(PlusProduct.label(cents), color = if (selected) Palette.OnAccent else Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Offrir ${PlusProduct.label(amount)}",
            icon = Icons.Rounded.Favorite,
            modifier = Modifier.fillMaxWidth(),
            loading = phase == PayPhase.WORKING,
            onClick = { onDonate(amount) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextLink("Pas maintenant", enabled = phase == PayPhase.OFFER, onClick = onLater)
        }
        if (simulated) SimulationNote()
    }
    Spacer(Modifier.height(10.dp))
}

// MARK: - Home pieces

/** Small golden "PLUS" pill on a locked tool of the home screen. */
@Composable
fun PlusLockBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Gold, GoldDeep)))
            .shimmer()
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = Color(0xFF3A1B00), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text("PLUS", color = Color(0xFF3A1B00), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}

/** Banner at the top of the home screen: Gulp Plus pitch (or thanks) and the donation heart. */
@Composable
fun PlusBanner(plus: Boolean, count: Int, onPlus: () -> Unit, onDonate: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(LocalTokens.current.cardRadius)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Gold.copy(alpha = 0.22f), GoldDeep.copy(alpha = 0.10f))))
                .border(1.dp, Gold.copy(alpha = 0.55f), shape)
                .shimmer()
                .bounceClick(enabled = !plus, onClick = onPlus)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Gold, GoldDeep))),
                contentAlignment = Alignment.Center,
            ) { Icon(if (plus) Icons.Rounded.Check else Icons.Rounded.AutoAwesome, null, tint = Color(0xFF3A1B00), modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (plus) "Gulp Plus actif" else "Gulp Plus", color = Palette.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(
                    if (plus) "Les $count outils avancés sont à toi." else "$count outils avancés · ${PlusProduct.PRICE_LABEL} une fois",
                    color = Palette.TextSecondary, fontSize = 12.5.sp,
                )
            }
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(shape)
                .background(Palette.Surface)
                .border(1.dp, Palette.Stroke, shape)
                .bounceClick(onClick = onDonate),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Favorite, "Faire un don", tint = Palette.Danger, modifier = Modifier.size(24.dp).pulsing(0.9f, 1.12f, 1100))
        }
    }
}

/** "Gulp Plus" block of the settings screen. */
@Composable
fun PlusSettingsSection(vm: MainViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (vm.plus) "Gulp Plus actif" else "Gulp Plus · ${PlusProduct.PRICE_LABEL}", color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            if (vm.plus) "Merci ! Les ${PlusCatalog.premiumIds.size} outils avancés sont débloqués sur ce compte."
            else "Débloque les ${PlusCatalog.premiumIds.size} outils avancés, une seule fois, sans abonnement.",
            color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        if (!vm.plus) PrimaryButton("Débloquer Gulp Plus", Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome) { vm.showPaywall() }
        PrimaryButton(
            if (vm.donated > 0) "Faire un autre don" else "Faire un don",
            Modifier.fillMaxWidth(), icon = Icons.Rounded.Favorite,
        ) { vm.openDonate() }
        if (vm.donated > 0) Text("Déjà donné : ${PlusProduct.label(vm.donated)}. Merci ♥", color = Palette.TextTertiary, fontSize = 12.sp)
    }
}

/** The pop-up's frame without the slide animation, for screenshots. */
@Composable
fun PlusSheetPreview(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
