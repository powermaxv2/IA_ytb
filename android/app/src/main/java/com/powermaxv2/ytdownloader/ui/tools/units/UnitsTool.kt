package com.powermaxv2.ytdownloader.ui.tools.units

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.units.Ingredient
import com.powermaxv2.ytdownloader.core.units.MeasureUnit
import com.powermaxv2.ytdownloader.core.units.Quantity
import com.powermaxv2.ytdownloader.core.units.Units
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import kotlinx.coroutines.delay

const val UNITS_TITLE = "Convertisseur d'unités"
const val UNITS_COMMAND = "units"
const val UNITS_SUBTITLE = "Longueurs, poids, températures, vitesses, cuisine… tape un nombre, tout se convertit en direct."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class UnitsActions(
    val onInput: (String) -> Unit = {},
    val onQuantity: (Quantity) -> Unit = {},
    val onFrom: (MeasureUnit) -> Unit = {},
    val onTo: (MeasureUnit) -> Unit = {},
    val onSwap: () -> Unit = {},
    val onIngredient: (Ingredient) -> Unit = {},
    val onCopy: (String) -> Unit = {},
)

/** Everything the screen draws (stateless: also drawn by the screenshot tests). */
data class UnitsUiState(
    val quantity: Quantity = Quantity.LENGTH,
    /** What the user typed; blank = the quantity's sample value (1, or 20 °C). */
    val input: String = "",
    val from: MeasureUnit = Units.defaults(quantity).first,
    val to: MeasureUnit = Units.defaults(quantity).second,
    val ingredient: Ingredient = Ingredient.FLOUR,
    /** How many times the units were swapped: the swap button turns half a turn each time. */
    val swaps: Int = 0,
    /** The result was just copied: its button says "Copié" for a moment. */
    val copied: Boolean = false,
    /** Which unit list is open ("from" or "to"), null when both are closed. */
    val picking: Side? = null,
) {
    /** The number read from [input], the sample value when it is blank, null when it is not a number. */
    val value: Double? get() = if (input.isBlank()) Units.sampleValue(quantity) else Units.parseQuery(input, quantity)?.value
    val impossible: Boolean get() = value?.let { Units.impossible(it, from) } == true
    val result: Double? get() = value?.takeUnless { impossible }?.let { Units.convert(it, from, to, ingredient) }
}

/** Which of the two unit pickers. */
enum class Side { FROM, TO }

/**
 * The Convertisseur d'unités (ToolRegistry.UNIT_CONVERTER): a value in one unit written in another, and in every unit
 * of its kind. Everything is computed on the phone; nothing is saved or sent.
 */
@Composable
fun UnitsTool(vm: MainViewModel) {
    val context = LocalContext.current
    var quantity by rememberSaveable { mutableStateOf(Quantity.LENGTH) }
    var input by rememberSaveable { mutableStateOf("") }
    var fromId by rememberSaveable { mutableStateOf(Units.defaults(Quantity.LENGTH).first.id) }
    var toId by rememberSaveable { mutableStateOf(Units.defaults(Quantity.LENGTH).second.id) }
    var ingredient by rememberSaveable { mutableStateOf(Ingredient.FLOUR) }
    var swaps by rememberSaveable { mutableStateOf(0) }
    var picking by remember { mutableStateOf<Side?>(null) }
    var copied by remember { mutableStateOf(false) }
    var copies by remember { mutableStateOf(0) }
    LaunchedEffect(copies) {
        if (copies > 0) {
            delay(1_600)
            copied = false
        }
    }
    val from = Units.unit(fromId) ?: Units.defaults(quantity).first
    val to = Units.unit(toId) ?: Units.defaults(quantity).second

    fun show(q: Quantity) {
        quantity = q
        val (f, t) = Units.defaults(q)
        fromId = f.id
        toId = t.id
        picking = null
    }

    val actions = UnitsActions(
        onInput = typed@{ text ->
            input = text.take(Units.MAX_INPUT)
            // "350 °F en °C": the units typed pick the chips.
            val query = Units.parseQuery(input, quantity) ?: return@typed
            val typedFrom = query.from ?: return@typed
            if (typedFrom.quantity != quantity) show(typedFrom.quantity)
            fromId = typedFrom.id
            val target = query.to ?: Units.unit(toId)?.takeIf { it.quantity == typedFrom.quantity && it != typedFrom }
                ?: Units.defaults(typedFrom.quantity).let { (f, t) -> if (t != typedFrom) t else f }
            toId = target.id
        },
        onQuantity = { if (it != quantity) show(it) },
        onFrom = {
            fromId = it.id
            if (it == to) toId = from.id
            picking = null
        },
        onTo = {
            toId = it.id
            if (it == from) fromId = to.id
            picking = null
        },
        onSwap = {
            fromId = to.id
            toId = from.id
            swaps++
        },
        onIngredient = { ingredient = it },
        onCopy = { line ->
            copy(context, line)
            copied = true
            copies++
            // Android 13+ shows its own "Copié" bubble.
            if (Build.VERSION.SDK_INT < 33) vm.toast("Copié : $line")
        },
    )

    val scroll = rememberScrollState()
    ToolScaffold(vm, title = UNITS_TITLE, command = UNITS_COMMAND, subtitle = UNITS_SUBTITLE, scrollState = scroll) {
        UnitsContent(
            UnitsUiState(quantity, input, from, to, ingredient, swaps, copied, picking),
            actions,
            onPick = { side -> picking = if (picking == side) null else side },
        )
    }
}

/** Body of the tool for a [state]; [onPick] opens or closes a unit list. */
@Composable
fun ColumnScope.UnitsContent(state: UnitsUiState, actions: UnitsActions, onPick: (Side) -> Unit = {}) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    QuantityChips(state.quantity, actions.onQuantity, Modifier.staggeredEntrance(0, baseDelay = 40))

    // The value, the two units with the swap between them, and the result.
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 40)
            .toolCard()
            .animateContentSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QrField(
            label = "Valeur",
            value = state.input,
            onChange = actions.onInput,
            placeholder = "${Units.format(Units.sampleValue(state.quantity))}  ·  ex : 1 1/2 ou « 350 °F en °C »",
            maxLength = Units.MAX_INPUT,
        )
        UnitPicker("De", state.from, open = state.picking == Side.FROM, onOpen = { onPick(Side.FROM) }, onPick = actions.onFrom)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            SwapButton(state.swaps, actions.onSwap)
        }
        UnitPicker("Vers", state.to, open = state.picking == Side.TO, onOpen = { onPick(Side.TO) }, onPick = actions.onTo)
        AnimatedVisibility(
            Units.needsIngredient(state.from, state.to),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockLabel("Ingrédient")
                OptionChips(
                    options = Ingredient.entries,
                    selected = state.ingredient,
                    label = { if (terminal) it.label.uppercase() else it.label },
                    onSelect = actions.onIngredient,
                )
            }
        }
        ResultBlock(state, actions.onCopy)
    }

    val value = state.value
    when {
        value == null -> Hint(
            "Ce n'est pas un nombre : écris par exemple 12,5, 1 500 ou 1 1/2.",
            warning = true,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        state.impossible -> Hint(
            if (state.quantity == Quantity.TEMPERATURE) "Plus froid que le zéro absolu (−273,15 °C) : ça n'existe pas." else "Une mesure négative n'existe pas ici.",
            warning = true,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }

    QuantityVisual(state, Modifier.staggeredEntrance(2, baseDelay = 40))

    if (value != null && !state.impossible) {
        BlockLabel(
            "${Units.formatWith(value, state.from)} c'est aussi",
            Modifier.padding(start = 4.dp, top = 4.dp).staggeredEntrance(3, baseDelay = 40),
        )
        AllUnits(state, value, actions.onTo, Modifier.staggeredEntrance(4, key = state.quantity, baseDelay = 40))
    }

    Hint(
        quantityHint(state.quantity),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

/** A word about the quantity open, then the same reminder for all: offline, and units can be typed. */
private fun quantityHint(quantity: Quantity): String {
    val about = when (quantity) {
        Quantity.TEMPERATURE -> "Thermostat : le four français, Th 6 = 180 °C. "
        Quantity.COOKING -> "Une tasse de farine ne pèse pas comme une tasse d'eau : Gulp tient compte de l'ingrédient (valeurs moyennes). "
        Quantity.DATA -> "Go = 1 000 Mo (ce qu'écrivent les fabricants), Gio = 1 024 Mio (ce que compte l'ordinateur). "
        Quantity.SPEED -> "Mach au niveau de la mer (340 m/s). "
        Quantity.TIME -> "Mois et année moyens (365,24 jours). "
        else -> ""
    }
    return about + "Tout se calcule sur le téléphone, hors ligne. Tu peux aussi taper « 5 km en miles »."
}

// MARK: - Intents

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(UNITS_TITLE, text))
}
