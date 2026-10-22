package io.github.oondraa.opensisakhk.lunch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.ApiError
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.CanteenResponse
import io.github.oondraa.opensisakhk.data.Meal
import io.github.oondraa.opensisakhk.data.MenuDay
import io.github.oondraa.opensisakhk.data.Unauthorized
import io.github.oondraa.opensisakhk.data.WEEKDAYS
import io.github.oondraa.opensisakhk.data.longDate
import io.github.oondraa.opensisakhk.data.nowMinutes
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.data.weekdayIndex
import io.github.oondraa.opensisakhk.ui.Banner
import io.github.oondraa.opensisakhk.ui.DayStrip
import io.github.oondraa.opensisakhk.ui.Empty
import io.github.oondraa.opensisakhk.ui.Footer
import io.github.oondraa.opensisakhk.ui.Icons
import io.github.oondraa.opensisakhk.ui.Screen
import io.github.oondraa.opensisakhk.ui.ScreenHeader
import io.github.oondraa.opensisakhk.ui.SkeletonList
import io.github.oondraa.opensisakhk.ui.Tag
import io.github.oondraa.opensisakhk.ui.rememberCached
import io.github.oondraa.opensisakhk.ui.theme.Tabular
import io.github.oondraa.opensisakhk.ui.theme.Tones
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private val CZ = NumberFormat.getNumberInstance(Locale.forLanguageTag("cs")).apply { maximumFractionDigits = 2 }

fun kc(n: Double) = "${CZ.format(n)} Kč"

/** "2026-10-14T12:00:00" → "st 14. 10. ve 12:00" */
private fun deadline(t: String): String {
    val d = LocalDate.parse(t.take(10))
    return "${WEEKDAYS[d.weekdayIndex].take(2).lowercase()} ${d.dayOfMonth}. ${d.monthValue}. ve ${t.substring(11, 16)}"
}

private val REFUSED = mapOf(
    "locked" to "Na tenhle den už objednávky skončily.",
    "not_applied" to "Strava změnu nepřijala. Zkontroluj objednávku ve Stravě.",
    "not_saved" to "Strava změnu neuložila.",
    "rate_limited" to "Moc změn za sebou, zkus to za minutu.",
)

/** Today until lunch is over, otherwise the next day with a menu. */
private fun startDay(days: List<MenuDay>?): LocalDate {
    val t = today().toString()
    val late = nowMinutes() >= 14 * 60
    return days?.find { it.date > t || (it.date == t && !late) }?.date?.let(LocalDate::parse) ?: today()
}

private data class Pending(val day: MenuDay, val meal: Meal?, val from: Meal?)

@Composable
fun LunchScreen(onBack: () -> Unit) {
    val menu = rememberCached("canteen", CanteenResponse.serializer()) { Api.canteen() }
    var fresh by remember { mutableStateOf<CanteenResponse?>(null) }
    val data = fresh ?: menu.data
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val selected = picked ?: startDay(data?.days)
    var pending by remember { mutableStateOf<Pending?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val day = data?.days?.find { it.date == selected.toString() }
    val scope = rememberCoroutineScope()

    fun confirm() {
        val p = pending ?: return
        busy = true
        msg = null
        scope.launch {
            try {
                val next = Api.order(p.day.date, p.meal?.veta)
                fresh = next
                Cache.set("canteen", CanteenResponse.serializer(), next)
                msg = true to if (p.meal != null) "Objednáno: ${p.meal.label}." else "Oběd je odhlášený."
            } catch (e: Unauthorized) {
                // signed out
            } catch (e: Exception) {
                msg = false to (REFUSED[(e as? ApiError)?.code] ?: "Objednávku se nepodařilo změnit. Nic se nezměnilo, nebo to zkontroluj ve Stravě.")
            } finally {
                busy = false
                pending = null
            }
        }
    }

    Screen(
        header = {
            ScreenHeader("Obědy", longDate(selected), onBack = onBack, loading = menu.loading || busy) {
                Spacer(Modifier.size(12.dp))
                DayStrip(selected, { picked = it; msg = null }, Modifier.padding(end = 8.dp))
            }
        },
        onRefresh = { fresh = null; menu.refresh() },
    ) {
        if (data != null && !data.connected) {
            item {
                Empty(
                    Icons.Utensils, "Strava není propojená",
                    if (data.error != null) "Heslo do Stravy se změnilo. Propoj ji znovu v Nastavení." else "Propoj účet jídelny v Nastavení a objednávej obědy tady.",
                    Modifier.fillMaxWidth(),
                )
            }
            return@Screen
        }
        if (menu.failed && fresh == null && data != null) item { Banner("Obědy se nepodařilo obnovit. Zobrazuji poslední uložené.") }
        msg?.let { (ok, text) -> item { Banner(text, if (ok) Tones.green else MaterialTheme.colorScheme.error) } }
        val ordered = day?.meals?.find { it.ordered }
        when {
            data == null && menu.loading -> item { SkeletonList(3, 88.dp) }
            day == null -> item { Empty(Icons.CalendarOff, "Na tenhle den jídelníček není", "Strava ukazuje zhruba tři týdny dopředu.", Modifier.fillMaxWidth()) }
            day.closed != null -> item { Empty(Icons.CalendarOff, "Nevaří se", day.closed, Modifier.fillMaxWidth()) }
            else -> {
                day.soup?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) } }
                for (m in day.meals) item(m.veta) { MealCard(m, enabled = m.editable && !m.ordered && !busy) { pending = Pending(day, m, ordered) } }
                if (ordered != null && ordered.editable) item {
                    TextButton(onClick = { pending = Pending(day, null, ordered) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Odhlásit oběd", color = MaterialTheme.colorScheme.error)
                    }
                }
                day.deadline?.let { dl ->
                    item {
                        Footer(
                            if (day.meals.any { it.editable }) "Objednat, změnit nebo odhlásit jde do ${deadline(dl)}."
                            else "Objednávky na tenhle den skončily ${deadline(dl)}. V den výdeje jde oběd měnit jen přes burzu ve Stravě.",
                        )
                    }
                }
            }
        }
        data?.balance?.let { item { Footer("Konto ve Stravě: ${kc(it)}", center = true) } }
    }

    pending?.let { p -> ConfirmOrder(p, busy, onConfirm = ::confirm, onCancel = { if (!busy) pending = null }) }
}

@Composable
private fun MealCard(m: Meal, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = if (m.ordered) c.primaryContainer else c.surfaceContainer,
        contentColor = if (m.ordered) c.onPrimaryContainer else c.onSurface,
        border = if (m.ordered) BorderStroke(2.dp, c.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${m.number ?: "–"}", style = MaterialTheme.typography.headlineMedium.merge(Tabular), modifier = Modifier.widthIn(min = 32.dp))
                Text(m.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (m.ordered) {
                    Tag("Objednáno", c.primary, c.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(kc(m.price), style = MaterialTheme.typography.bodyMedium.merge(Tabular))
            }
            Text(m.name, style = MaterialTheme.typography.bodyLarge)
            if (m.allergens.isNotEmpty()) Text(m.allergens.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Real money: every change is spelled out and confirmed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmOrder(p: Pending, busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val d = LocalDate.parse(p.day.date)
    val whenText = if (d == today().plusDays(1)) "na zítra" else "na ${WEEKDAYS[d.weekdayIndex].lowercase()} ${d.dayOfMonth}. ${d.monthValue}."
    val title = if (p.meal != null) (if (p.from != null) "Změnit oběd $whenText?" else "Objednat oběd $whenText?") else "Odhlásit oběd $whenText?"
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            p.from?.let {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it.label) }
                        append(" ${it.name}")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (p.meal != null) TextDecoration.LineThrough else null,
                    color = if (p.meal != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            p.meal?.let {
                Text(
                    buildAnnotatedString {
                        if (p.from != null) append("→ ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it.label) }
                        append(" ${it.name} · ${kc(it.price)}")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onConfirm,
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                colors = if (p.meal == null) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (busy) "Ukládám ve Stravě…" else if (p.meal != null) (if (p.from != null) "Změnit" else "Objednat") else "Odhlásit")
            }
            TextButton(onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onCancel() } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Zrušit") }
        }
    }
}
