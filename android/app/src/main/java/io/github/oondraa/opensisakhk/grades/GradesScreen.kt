package io.github.oondraa.opensisakhk.grades

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.Part
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Grade
import io.github.oondraa.opensisakhk.data.GradesResponse
import io.github.oondraa.opensisakhk.data.Prefs
import io.github.oondraa.opensisakhk.data.Subject
import io.github.oondraa.opensisakhk.ui.Banner
import io.github.oondraa.opensisakhk.ui.Card
import io.github.oondraa.opensisakhk.ui.Collapsible
import io.github.oondraa.opensisakhk.ui.Divider
import io.github.oondraa.opensisakhk.ui.Empty
import io.github.oondraa.opensisakhk.ui.Expander
import io.github.oondraa.opensisakhk.ui.Footer
import io.github.oondraa.opensisakhk.ui.Icons
import io.github.oondraa.opensisakhk.ui.ListRow
import io.github.oondraa.opensisakhk.ui.Screen
import io.github.oondraa.opensisakhk.ui.ScreenHeader
import io.github.oondraa.opensisakhk.ui.Segmented
import io.github.oondraa.opensisakhk.ui.SkeletonList
import io.github.oondraa.opensisakhk.ui.TintIcon
import io.github.oondraa.opensisakhk.ui.cz
import io.github.oondraa.opensisakhk.ui.plural
import io.github.oondraa.opensisakhk.ui.rememberCached
import io.github.oondraa.opensisakhk.ui.theme.Tabular
import io.github.oondraa.opensisakhk.ui.theme.gradeTone
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@Composable
fun GradesScreen(onBack: () -> Unit) {
    val grades = rememberCached("grades", GradesResponse.serializer()) { Api.grades() }
    val subjects = grades.data?.subjects.orEmpty()
    // Target grades open inside this screen, like the absence calculator.
    var goals by rememberSaveable { mutableStateOf(false) }
    BackHandler(goals) { goals = false }

    AnimatedContent(
        goals,
        transitionSpec = {
            if (targetState) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
            else fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
        },
        label = "goals",
    ) { inGoals ->
        if (inGoals) GoalsScreen(subjects) { goals = false }
        else GradesList(subjects, grades.loading, grades.failed && grades.data != null, grades.data == null, grades.refresh, onBack) { goals = true }
    }
}

@Composable
private fun GradesList(subjects: List<Subject>, loading: Boolean, stale: Boolean, none: Boolean, refresh: () -> Unit, onBack: () -> Unit, openGoals: () -> Unit) {
    val averages = subjects.mapNotNull { it.average }
    val overall = if (averages.isNotEmpty()) averages.average() else null
    val count = subjects.sumOf { it.grades.size }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Screen(header = { ScreenHeader("Známky", "Toto pololetí", onBack = onBack, loading = loading) }, onRefresh = refresh) {
        if (stale) item { Banner("Známky se nepodařilo obnovit. Zobrazuji poslední uložené.") }
        if (overall != null) item {
            Summary("Průměr ze všech předmětů", overall.cz(), gradeTone(overall), "${subjects.size} předmětů", "$count ${plural(count, "známka", "známky", "známek")}")
        }
        if (subjects.isNotEmpty()) item {
            Card { ListRow("Cílové známky", "Co potřebuješ z dalších písemek", onClick = openGoals, lead = { TintIcon(Icons.Target, Part.Grades.tint) }) }
        }
        when {
            subjects.isNotEmpty() -> item {
                Card {
                    subjects.forEachIndexed { i, s ->
                        if (i > 0) Divider(64.dp)
                        SubjectRow(s, open == s.name) { open = if (open == s.name) null else s.name }
                    }
                }
            }
            loading && none -> item { SkeletonList(8) }
            else -> item { Empty(Icons.Book, "Zatím žádné známky", modifier = Modifier.fillMaxWidth()) }
        }
        item { Footer("Průměr počítá SIS a má jen informativní charakter.") }
    }
}

/** The big number on top of Známky, Absence and the calculator, with two facts beside it. */
@Composable
fun Summary(label: String, value: String, tone: Color, side1: String, side2: String, side2Color: Color = MaterialTheme.colorScheme.onSurfaceVariant, unit: String? = null) {
    Card {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    buildAnnotatedString {
                        append(value)
                        if (unit != null) withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = FontWeight.Medium)) { append(" $unit") }
                    },
                    style = MaterialTheme.typography.displaySmall.merge(Tabular),
                    color = tone,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(side1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(side2, style = MaterialTheme.typography.bodyMedium, color = side2Color)
            }
        }
    }
}

/** A grade or average in its tone: a soft square with the number. */
@Composable
fun GradeChip(text: String, n: Double?, big: Boolean = false) {
    val tone = gradeTone(n)
    Surface(shape = RoundedCornerShape(if (big) 12.dp else 8.dp), color = tone.copy(alpha = 0.16f), contentColor = tone) {
        Box(Modifier.widthIn(min = if (big) 40.dp else 26.dp).padding(horizontal = if (big) 8.dp else 5.dp, vertical = if (big) 8.dp else 2.dp), contentAlignment = Alignment.Center) {
            Text(text, style = (if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge).merge(Tabular), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SubjectRow(s: Subject, open: Boolean, onToggle: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(48.dp)) { GradeChip(s.average?.cz() ?: "–", s.average, big = true) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { for (g in s.grades.takeLast(6)) GradeChip(g.value, g.numeric) }
            }
            Expander(open)
        }
        Collapsible(open) {
            for (g in s.grades.reversed()) GradeLine(g)
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun GradeLine(g: Grade) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(60.dp)) { GradeChip(g.value, g.numeric, big = true) }
        Column(Modifier.weight(1f)) {
            Text(g.name.ifEmpty { g.typeName }, style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append(g.date)
                    append(" · ")
                    append(g.typeName.ifEmpty { g.type })
                    if (g.weight != null) append(" · váha ${g.weight.cz(0)}")
                },
                style = MaterialTheme.typography.bodySmall.merge(Tabular),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (g.comment.isNotEmpty()) Text("„${g.comment}“", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val TARGETS = (1..5).map { it to "$it" }
private val TARGETS_SER = MapSerializer(String.serializer(), Int.serializer())

private fun short(n: Need) = when (n) {
    Need.Safe -> "v suchu"
    is Need.Grade -> "aspoň ${gradeLabel(n.worst)}"
    is Need.Ones -> "${n.count}× 1"
}

private fun sentence(n: Need, target: Int, w: Double): String {
    val ws = w.cz(0)
    val test = "z příští známky s váhou $ws"
    return when (n) {
        Need.Safe -> "Na $target to vyjde, i kdybys $test dostal pětku."
        is Need.Grade -> "Na $target potřebuješ $test aspoň ${gradeLabel(n.worst)}."
        is Need.Ones -> "Jedna jednička na $target nestačí. Potřebuješ ${n.count} ${plural(n.count, "jedničku", "jedničky", "jedniček")} s váhou $ws za sebou."
    }
}

/**
 * Pick the final grade wanted from each subject and see what the next test has to be. The targets stay on this
 * device (a preference, nothing SIS needs to know).
 */
@Composable
private fun GoalsScreen(subjects: List<Subject>, onBack: () -> Unit) {
    val targets = remember { mutableStateMapOf<String, Int>().apply { putAll(Prefs.get("gradeTargets", TARGETS_SER).orEmpty()) } }
    val chosen = remember { mutableStateMapOf<String, Double>() }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Screen(header = { ScreenHeader("Cílové známky", "Toto pololetí", onBack = onBack, backLabel = "Známky") }) {
        item {
            Card {
                subjects.forEachIndexed { i, s ->
                    if (i > 0) Divider(64.dp)
                    val avg = average(s.grades)
                    val now = avg?.let { finalGrade(it) }
                    val target = targets[s.name] ?: now ?: 1
                    val ws = weights(s.grades)
                    val w = chosen[s.name] ?: ws.firstOrNull() ?: 5.0
                    val n = need(s.grades, target, w)
                    val isOpen = open == s.name
                    val needColor = when (n) {
                        Need.Safe -> io.github.oondraa.opensisakhk.ui.theme.Tones.green
                        is Need.Grade -> MaterialTheme.colorScheme.onSurface
                        is Need.Ones -> io.github.oondraa.opensisakhk.ui.theme.Tones.red
                    }
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { open = if (isOpen) null else s.name }.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(48.dp)) { GradeChip(avg?.cz() ?: "–", avg, big = true) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.bodyLarge)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Chci", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    GradeChip("$target", target.toDouble())
                                    Text(short(n), style = MaterialTheme.typography.bodySmall, color = needColor)
                                }
                            }
                            Expander(isOpen)
                        }
                        Collapsible(isOpen) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Výsledná známka", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Segmented(TARGETS, target, onChange = {
                                    targets[s.name] = it
                                    Prefs.set("gradeTargets", TARGETS_SER, targets.toMap())
                                })
                                if (ws.size > 1) {
                                    Text("Váha příští známky", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Segmented(ws.map { it to it.cz(0) }, w, onChange = { chosen[s.name] = it })
                                }
                                Text(sentence(n, target, w), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = needColor)
                                if (avg != null) Text("Teď ${avg.cz()}, to je $now.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            Footer("Počítám vážený průměr ze známek v SIS. Cíl platí, jen když je průměr pod hranicí, např. na 2 pod 2,50, protože přesné zaokrouhlení na půlce záleží na vyučujícím. Cíle si pamatuje jen tohle zařízení.")
        }
    }
}
