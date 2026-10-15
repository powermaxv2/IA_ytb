package com.powermaxv2.ytdownloader.ui.tools.bill

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.bill.Bill
import com.powermaxv2.ytdownloader.core.bill.BillCurrency
import com.powermaxv2.ytdownloader.core.bill.BillProblem
import com.powermaxv2.ytdownloader.core.bill.BillSplitter
import com.powermaxv2.ytdownloader.core.bill.Diner
import com.powermaxv2.ytdownloader.core.bill.Split
import com.powermaxv2.ytdownloader.core.bill.Tip
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PrimaryButton
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.compress.SwitchRow
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction

const val BILL_TITLE = "Addition"
const val BILL_COMMAND = "addition"
const val BILL_SUBTITLE = "Divise la note du resto entre amis, pourboire compris, au centime près."

/** One person at the table as typed: [own] is only counted while [ownOpen]. */
@Immutable
data class DinerInput(val id: Int, val name: String = "", val halves: Int = 2, val own: String = "", val ownOpen: Boolean = false)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
@Immutable
data class BillUiState(
    val amount: String = "",
    val currency: BillCurrency = BillCurrency.EURO,
    /** Chosen tip chip: a percentage (0 = none), or null for "Montant" with [tipAmount]. */
    val tipPercent: Int? = 0,
    val tipAmount: String = "",
    val diners: List<DinerInput> = listOf(DinerInput(0, "Moi"), DinerInput(1)),
    val roundUp: Boolean = false,
    /** [DinerInput.id] of who paid the whole bill. */
    val payer: Int? = null,
) {
    val bill: Bill
        get() = Bill(
            totalCents = BillSplitter.parseAmount(amount) ?: 0,
            diners = diners.map { Diner(it.name, it.halves, if (it.ownOpen) BillSplitter.parseAmount(it.own) ?: 0 else 0) },
            tip = when (tipPercent) {
                null -> Tip.Amount(BillSplitter.parseAmount(tipAmount) ?: 0)
                0 -> Tip.None
                else -> Tip.Percent(tipPercent)
            },
            roundUp = roundUp,
            payer = diners.indexOfFirst { it.id == payer }.takeIf { it >= 0 },
            currency = currency,
        )
    val split: Split get() = BillSplitter.split(bill)
}

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class BillActions(
    val onAmount: (String) -> Unit = {},
    val onCurrency: (BillCurrency) -> Unit = {},
    val onTipPercent: (Int?) -> Unit = {},
    val onTipAmount: (String) -> Unit = {},
    val onName: (Int, String) -> Unit = { _, _ -> },
    val onHalves: (Int, Int) -> Unit = { _, _ -> },
    val onOwnOpen: (Int) -> Unit = {},
    val onOwn: (Int, String) -> Unit = { _, _ -> },
    val onRemove: (Int) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onPayer: (Int) -> Unit = {},
    val onRoundUp: (Boolean) -> Unit = {},
    val onShare: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onReset: () -> Unit = {},
)

/** Keeps the table while Gulp runs (back home and in again, rotation); the names also stay in the prefs. */
private object BillSession {
    var last: BillUiState? = null
}

/**
 * Partage d'addition (ToolRegistry.BILL_SPLIT): the amount of the bill, a tip, the people with their parts and their
 * own orders, and what each one pays, to the cent; the recap goes to the group's chat in one touch. Nothing leaves the
 * phone but that text, when the user sends it.
 */
@Composable
fun BillTool(vm: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var state by remember {
        mutableStateOf(BillSession.last ?: BillUiState(diners = savedDiners(prefs.getString(KEY_NAMES, null))))
    }

    fun update(change: (BillUiState) -> BillUiState) {
        val next = change(state)
        state = next
        BillSession.last = next
    }

    fun saveNames(diners: List<DinerInput>) {
        prefs.edit().putString(KEY_NAMES, diners.joinToString("\n") { it.name.replace("\n", " ") }).apply()
    }

    fun editDiner(id: Int, change: (DinerInput) -> DinerInput) = update { s -> s.copy(diners = s.diners.map { if (it.id == id) change(it) else it }) }

    val actions = BillActions(
        onAmount = { text -> update { it.copy(amount = text) } },
        onCurrency = { c -> update { it.copy(currency = c) } },
        onTipPercent = { p -> update { it.copy(tipPercent = p) } },
        onTipAmount = { text -> update { it.copy(tipAmount = text.take(12)) } },
        onName = { id, name ->
            editDiner(id) { it.copy(name = name) }
            saveNames(state.diners)
        },
        onHalves = { id, h -> editDiner(id) { it.copy(halves = h.coerceIn(0, BillSplitter.MAX_HALVES)) } },
        onOwnOpen = { id -> editDiner(id) { it.copy(ownOpen = !it.ownOpen) } },
        onOwn = { id, text -> editDiner(id) { it.copy(own = text) } },
        onRemove = { id ->
            update { s -> s.copy(diners = s.diners.filterNot { it.id == id }, payer = s.payer.takeIf { it != id }) }
            saveNames(state.diners)
        },
        onAdd = {
            update { s ->
                if (s.diners.size >= BillSplitter.MAX_DINERS) s
                else s.copy(diners = s.diners + DinerInput((s.diners.maxOfOrNull { it.id } ?: -1) + 1))
            }
        },
        onPayer = { id -> update { it.copy(payer = if (it.payer == id) null else id) } },
        onRoundUp = { on -> update { it.copy(roundUp = on) } },
        onShare = {
            val text = BillSplitter.recap(state.bill, state.split)
            if (text.isNotEmpty()) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                runCatching { context.startActivity(Intent.createChooser(send, "Envoyer le récap")) }
                    .onFailure { vm.toast("Partage impossible", success = false) }
            }
        },
        onCopy = {
            val text = BillSplitter.recap(state.bill, state.split)
            if (text.isNotEmpty()) {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Addition", text))
                if (Build.VERSION.SDK_INT < 33) vm.toast("Récap copié")
            }
        },
        onReset = {
            // Same people (names kept), everything else back to zero.
            update { s -> BillUiState(currency = s.currency, diners = s.diners.map { DinerInput(it.id, it.name) }) }
        },
    )

    ToolScaffold(vm, title = BILL_TITLE, command = BILL_COMMAND, subtitle = BILL_SUBTITLE, scrollState = rememberScrollState()) {
        BillContent(state, actions)
    }
}

/** Body of the tool for a [state]. */
@Composable
fun ColumnScope.BillContent(state: BillUiState, actions: BillActions) {
    val split = state.split
    val bill = state.bill
    val c = state.currency

    // The bill.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BigAmountField(state.amount, c, actions.onAmount)
        OptionChips(BillCurrency.entries, c, label = { it.symbol }, onSelect = actions.onCurrency)
    }

    // The tip.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 40)
            .toolCard()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlockLabel("Pourboire", Modifier.weight(1f))
            AnimatedContent(
                targetState = BillSplitter.tipCents(bill.tip, bill.totalCents),
                transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.7f) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "tipAmount",
            ) { tip ->
                Text(
                    if (tip > 0) "+ ${BillSplitter.format(tip, c)}" else skinText("Sans pourboire"),
                    color = if (tip > 0) LocalAccent.current.start else Palette.TextTertiary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        OptionChips(
            BillSplitter.tipPercents + listOf<Int?>(null),
            state.tipPercent,
            label = { p -> when (p) { null -> "Montant"; 0 -> "Aucun"; else -> "$p %" } },
            onSelect = actions.onTipPercent,
        )
        AnimatedVisibility(state.tipPercent == null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            QrField(
                label = "Pourboire (${c.symbol})",
                value = state.tipAmount,
                onChange = actions.onTipAmount,
                keyboard = KeyboardType.Decimal,
                placeholder = "5,00",
                maxLength = 12,
            )
        }
    }

    // The people at the table.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 40)
            .toolCard()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val n = state.diners.size
        BlockLabel(if (n == 1) "À table · 1 personne" else "À table · $n personnes")
        state.diners.forEachIndexed { i, d ->
            key(d.id) {
                DinerRow(d, i, payer = state.payer == d.id, currency = c, canRemove = n > 1, actions = actions)
            }
        }
        if (n < BillSplitter.MAX_DINERS) {
            MiniAction("Ajouter quelqu'un", Icons.Rounded.PersonAdd, onClick = actions.onAdd)
        }
    }

    // What everyone pays.
    AnimatedContent(
        targetState = split.problem,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "billResult",
    ) { problem ->
        if (problem == null) {
            Receipt(state, split, actions.onRoundUp)
        } else {
            Hint(
                problem.message,
                warning = problem != BillProblem.NO_TOTAL,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
    }

    Column(Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 40), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(
            text = "Envoyer le récap",
            icon = Icons.AutoMirrored.Rounded.Send,
            subtitle = if (split.ok) "Total ${BillSplitter.format(split.grandCents, c)}" else null,
            enabled = split.ok,
            modifier = Modifier.fillMaxWidth(),
            onClick = actions.onShare,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            if (split.ok) MiniAction("Copier", Icons.Rounded.ContentCopy, onClick = actions.onCopy)
            MiniAction("Nouvelle addition", Icons.Rounded.RestartAlt, onClick = actions.onReset)
        }
    }

    Hint(
        "Tout se calcule sur le téléphone. Les parts tombent juste au centime : le centime qui reste va à la plus grosse " +
            "fraction. Seuls les prénoms restent ici pour la prochaine fois.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

/** The receipt: total, ring of shares, one rolling line per person, the round-up switch, and who pays back whom. */
@Composable
private fun Receipt(state: BillUiState, split: Split, onRoundUp: (Boolean) -> Unit) {
    val c = state.currency
    val bill = state.bill
    val grand = split.grandCents.coerceAtLeast(1)
    Column(
        Modifier
            .fillMaxWidth()
            .receiptCard()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BlockLabel("À payer en tout")
                RollingAmount(split.grandCents, c, 32.sp, Palette.TextPrimary, weight = FontWeight.ExtraBold, display = true)
                Text(
                    skinText(
                        if (split.tipCents > 0) "dont ${BillSplitter.format(split.tipCents, c)} de pourboire"
                        else "sans pourboire",
                    ),
                    color = Palette.TextTertiary,
                    fontSize = 12.5.sp,
                )
            }
            ShareRing(split.shares.map { it.cents.toFloat() / grand }, Modifier.size(96.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${split.shares.size}", color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalTokens.current.display)
                    Text(skinText("pers."), color = Palette.TextTertiary, fontSize = 10.sp)
                }
            }
        }
        val biggest = split.shares.maxOf { it.cents }.coerceAtLeast(1)
        split.shares.forEachIndexed { i, share ->
            val d = bill.diners[i]
            val note = listOfNotNull(
                BillSplitter.partsText(d.halves).takeIf { d.halves != 2 }?.let { if (d.halves <= 2) "$it part" else "$it parts" },
                d.ownCents.takeIf { it > 0 }?.let { "dont ${BillSplitter.format(it, c)} perso" },
                share.tipCents.takeIf { it > 0 }?.let { "pourboire ${BillSplitter.format(it, c)}" },
            ).joinToString(" · ").ifEmpty { null }
            ShareLine(share, BillSplitter.displayName(share.name, i), note, i, share.cents.toFloat() / biggest, c)
        }
        SwitchRow(
            title = "Arrondir chaque part",
            subtitle = "Chacun paie à l'unité au-dessus (${BillSplitter.format(100, c)}) ; le surplus va au pourboire.",
            checked = state.roundUp,
            onChange = onRoundUp,
        )
        val backs = BillSplitter.paybacks(bill, split)
        AnimatedVisibility(backs.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.height(2.dp))
                BlockLabel("Qui rembourse qui")
                val payerIndex = bill.payer ?: 0
                backs.forEachIndexed { k, back ->
                    val from = split.shares.indices.filter { it != payerIndex && split.shares[it].cents > 0 }.getOrElse(k) { 0 }
                    PaybackLine(back, from, payerIndex, k, c)
                }
            }
        }
    }
}

// MARK: - Saved names

private const val PREFS = "bill"
private const val KEY_NAMES = "names"

/** The people of last time (names only), or "Moi" and an empty seat. */
internal fun savedDiners(saved: String?): List<DinerInput> {
    val names = saved?.split('\n')?.take(BillSplitter.MAX_DINERS).orEmpty()
    if (names.size < 2 && names.all { it.isBlank() }) return BillUiState().diners
    return names.mapIndexed { i, name -> DinerInput(i, name.take(24)) }
}
