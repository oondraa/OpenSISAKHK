package io.github.oondraa.opensisakhk.finance

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Finance
import io.github.oondraa.opensisakhk.data.FinanceAccount
import io.github.oondraa.opensisakhk.data.Unauthorized
import io.github.oondraa.opensisakhk.lunch.kc
import io.github.oondraa.opensisakhk.ui.Banner
import io.github.oondraa.opensisakhk.ui.Card
import io.github.oondraa.opensisakhk.ui.Divider
import io.github.oondraa.opensisakhk.ui.Empty
import io.github.oondraa.opensisakhk.ui.Footer
import io.github.oondraa.opensisakhk.ui.Icons
import io.github.oondraa.opensisakhk.ui.ListRow
import io.github.oondraa.opensisakhk.ui.Screen
import io.github.oondraa.opensisakhk.ui.ScreenHeader
import io.github.oondraa.opensisakhk.ui.Section
import io.github.oondraa.opensisakhk.ui.Segmented
import io.github.oondraa.opensisakhk.ui.SkeletonList
import io.github.oondraa.opensisakhk.ui.theme.Tabular
import io.github.oondraa.opensisakhk.ui.theme.Tones
import kotlinx.coroutines.delay
import java.time.LocalDate

private fun date(iso: String) = LocalDate.parse(iso).let { "${it.dayOfMonth}. ${it.monthValue}. ${it.year}" }

/**
 * Tuition and the student fund, straight from SIS. Deliberately not cached anywhere: it loads when the screen opens
 * and is gone when it closes.
 */
@Composable
fun FinanceScreen(onBack: () -> Unit) {
    var data by remember { mutableStateOf<Finance?>(null) }
    var failed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var account by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick) {
        failed = false
        try {
            data = Api.finance()
        } catch (e: Unauthorized) {
            // signed out
        } catch (e: Exception) {
            failed = true
        }
    }
    val current = data?.accounts?.find { it.id == account } ?: data?.accounts?.firstOrNull()

    Screen(header = { ScreenHeader("Finance", "Školné a fond", onBack = onBack, loading = data == null && !failed) }, onRefresh = { tick++ }) {
        val d = data
        when {
            failed && d == null -> item { Empty(Icons.Wallet, "Finance se nepodařilo načíst", "Zkus to za chvíli znovu.", Modifier.fillMaxWidth()) }
            d == null -> item { SkeletonList(4) }
            else -> {
                d.notice?.let { item { Banner(it) } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { for (a in d.accounts) BalanceCard(a, Modifier.weight(1f)) }
                }
                item {
                    Footer(
                        "Přeplatek a nedoplatek počítá SIS a nemusí být přesné: škola finance po výpadku ještě doplňuje" +
                            (d.paymentsAsOf?.let { " a platby z banky má načtené jen k ${date(it)}" } ?: "") +
                            ". Nedoplatek může být i předpis, který ještě není splatný. Když si nejsi jistý, ověř to ve škole.",
                    )
                }
                if (d.accounts.size > 1) item { Segmented(d.accounts.map { it.id to it.title }, current?.id ?: "", { account = it }) }
                for (s in current?.sections.orEmpty()) item {
                    Section(s.title, s.total?.let { "Celkem ${kc(it)}" }) {
                        if (s.rows.isEmpty()) ListRow("Nic")
                        s.rows.reversed().forEachIndexed { i, r ->
                            if (i > 0) Divider()
                            ListRow(r.text, date(r.date), trail = { Text(kc(r.amount), style = MaterialTheme.typography.bodyLarge.merge(Tabular)) })
                        }
                    }
                }
                if (d.bankAccount != null || d.symbols.isNotEmpty()) item {
                    Section("Platba", "Ťuknutím zkopíruješ. Platí se převodem z banky, appka nic neplatí.") {
                        d.bankAccount?.let { CopyRow("Číslo účtu", it) }
                        d.symbols.forEach {
                            Divider()
                            CopyRow(it.label, it.value)
                        }
                    }
                }
                if (d.documents.isNotEmpty()) item {
                    val uri = LocalUriHandler.current
                    Section("Předpisy") {
                        d.documents.forEachIndexed { i, doc ->
                            if (i > 0) Divider()
                            ListRow("${doc.label} (PDF)", onClick = { uri.openUri(doc.url) }, titleColor = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item { Footer("Finance se nikam neukládají, ani v tomhle zařízení. Načtou se při každém otevření ze SIS.") }
            }
        }
    }
}

@Composable
private fun BalanceCard(a: FinanceAccount, modifier: Modifier) {
    val b = a.balance
    val tone = when (b?.kind) {
        "overpaid" -> Tones.green
        "underpaid" -> Tones.red
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(a.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (b == null) "–" else if (b.kind == "even") "Vyrovnáno" else kc(b.amount), style = MaterialTheme.typography.headlineMedium.merge(Tabular), color = tone)
            Text(
                when (b?.kind) {
                    "overpaid" -> "Přeplatek podle SIS"
                    "underpaid" -> "Nedoplatek podle SIS"
                    null -> "Bez údajů"
                    else -> "Vyrovnáno podle SIS"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CopyRow(label: String, value: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    ListRow(label, chevron = false, onClick = {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
        copied = true
    }, trail = {
        Text(if (copied) "Zkopírováno" else value, style = MaterialTheme.typography.bodyLarge.merge(Tabular), color = if (copied) Tones.green else MaterialTheme.colorScheme.onSurfaceVariant)
    })
}
