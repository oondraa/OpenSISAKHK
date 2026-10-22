package io.github.oondraa.opensisakhk.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Project
import io.github.oondraa.opensisakhk.data.ProjectChoice
import io.github.oondraa.opensisakhk.data.ProjectsResponse
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
import io.github.oondraa.opensisakhk.ui.Section
import io.github.oondraa.opensisakhk.ui.SectionTitle
import io.github.oondraa.opensisakhk.ui.SkeletonList
import io.github.oondraa.opensisakhk.ui.Tag
import io.github.oondraa.opensisakhk.ui.rememberCached
import io.github.oondraa.opensisakhk.ui.theme.Tabular
import io.github.oondraa.opensisakhk.ui.theme.Tones

/** "21/3" → zapsaní over places; without a limit SIS shows just the count. */
private data class Fill(val n: Int, val of: Int?, val over: Boolean)

private fun fill(s: String): Fill {
    val parts = s.split("/")
    val n = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
    val of = parts.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it > 0 }
    return Fill(n, of, of != null && n > of)
}

private fun label(s: String) = s.replace("-", " – ")

@Composable
fun ProjectsScreen(onBack: () -> Unit) {
    var election by rememberSaveable { mutableStateOf<String?>(null) }
    val res = rememberCached("projects:${election ?: "latest"}", ProjectsResponse.serializer()) { Api.projects(election) }
    val data = res.data
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val projects = data?.projects.orEmpty()
    val mine = data?.mine.orEmpty()
    val approved = mine.find { it.approved }

    Screen(
        header = { ScreenHeader("Projekty", data?.election?.let { "Volba ${label(it.label)}" } ?: "Projektové dny", onBack = onBack, loading = res.loading) },
        onRefresh = res.refresh,
    ) {
        if (res.failed && data != null) item { Banner("Projekty se nepodařilo obnovit. Zobrazuji poslední uložené.") }
        when {
            approved != null -> item { MyProject(approved, projects.find { it.number == approved.number }) }
            mine.isNotEmpty() -> item {
                Section("Moje přihláška") {
                    mine.forEachIndexed { i, c ->
                        if (i > 0) Divider(64.dp)
                        ListRow(c.name, "${c.order}. volba · ${c.role}", lead = { Number(c.number, false) })
                    }
                }
            }
            !res.loading && data != null -> item { Footer("V téhle volbě nemáš vybraný žádný projekt.") }
        }

        data?.profile?.let { p ->
            item {
                Section("Ve škole") {
                    var first = true
                    @Composable
                    fun sep() {
                        if (!first) Divider()
                        first = false
                    }
                    p.`class`?.let { sep(); ListRow("Třída", trail = { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }) }
                    if (p.groups.isNotEmpty()) {
                        sep()
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Skupiny", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) { for (g in p.groups) Tag(g) }
                        }
                    }
                    for (b in p.blocks) {
                        sep()
                        ListRow("Blok ${b.block}", trail = { Text(b.subject, color = MaterialTheme.colorScheme.onSurfaceVariant) })
                    }
                    p.email?.let { sep(); ListRow("E-mail", trail = { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) }) }
                }
            }
        }

        if (data != null && data.elections.size > 1) item {
            var menu by rememberSaveable { mutableStateOf(false) }
            Section("Volba") {
                Box {
                    ListRow("Projektové dny", onClick = { menu = true }, trail = { Text(data.election?.let { label(it.label) } ?: "", color = MaterialTheme.colorScheme.primary) })
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        for (e in data.elections) DropdownMenuItem(text = { Text(label(e.label)) }, onClick = { election = e.id; menu = false })
                    }
                }
            }
        }

        when {
            projects.isNotEmpty() -> {
                item { SectionTitle("Všechny projekty · ${projects.size}") }
                item {
                    Card {
                        projects.forEachIndexed { i, p ->
                            if (i > 0) Divider(64.dp)
                            ProjectRow(p, mine.any { it.number == p.number }, open == p.number) { open = if (open == p.number) null else p.number }
                        }
                    }
                }
            }
            res.loading -> item { SkeletonList(6) }
            else -> item { Empty(Icons.Project, "Žádné projekty", "SIS zatím žádnou volbu projektů nemá.", Modifier.fillMaxWidth()) }
        }
        item { Footer("Přihlašuje se dál v SIS. Čísla u rolí: zapsaní / volná místa pro 1., 2. a 3. volbu.") }
    }
}

@Composable
private fun Number(n: String, mine: Boolean, big: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(10.dp), color = if (mine) c.primary else c.surfaceVariant, contentColor = if (mine) c.onPrimary else c.onSurfaceVariant) {
        Text(
            n,
            style = (if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium).merge(Tabular),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = if (big) 56.dp else 36.dp).padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MyProject(choice: ProjectChoice, project: Project?) {
    Card(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Number(choice.number, true, big = true)
                Spacer(Modifier.width(12.dp))
                Text("Schváleno · ${choice.role}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(choice.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (project != null) {
                listOfNotNull(project.company.ifEmpty { null }, project.leader.ifEmpty { null }?.let { "vede $it" }).joinToString(" · ").ifEmpty { null }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
                if (project.goal.isNotEmpty()) Text(project.goal, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (project.comment.isNotEmpty()) Text(project.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun ProjectRow(p: Project, mine: Boolean, open: Boolean, onToggle: () -> Unit) {
    val total = fill(p.total)
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Number(p.number, mine)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                listOf(p.company, p.leader).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (p.total.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text("${total.n}${total.of?.let { "/$it" } ?: ""}", style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = if (total.over) Tones.red else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(6.dp))
            Expander(open)
        }
        Collapsible(open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p.goal.isNotEmpty()) Text(p.goal, style = MaterialTheme.typography.bodyMedium)
                if (p.comment.isNotEmpty()) Text(p.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.roles.isNotEmpty()) {
                    RoleLine("Role", listOf("1.", "2.", "3."), MaterialTheme.colorScheme.onSurfaceVariant)
                    for (r in p.roles) RoleLine(r.name, r.choices, MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun RoleLine(name: String, cells: List<String>, color: Color) {
    Row {
        Text(name, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
        for (c in cells) Text(
            c,
            style = MaterialTheme.typography.bodySmall.merge(Tabular),
            color = if (fill(c).over) Tones.red else color,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}
