package io.github.oondraa.opensisakhk.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Day
import io.github.oondraa.opensisakhk.data.Lesson
import io.github.oondraa.opensisakhk.data.LunchDay
import io.github.oondraa.opensisakhk.data.WEEKDAYS_SHORT
import io.github.oondraa.opensisakhk.data.inMinutes
import io.github.oondraa.opensisakhk.data.longDate
import io.github.oondraa.opensisakhk.data.minutes
import io.github.oondraa.opensisakhk.data.monday
import io.github.oondraa.opensisakhk.data.nowMinutes
import io.github.oondraa.opensisakhk.data.span
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.data.weekdayIndex
import io.github.oondraa.opensisakhk.ui.DayStrip
import io.github.oondraa.opensisakhk.ui.Empty
import io.github.oondraa.opensisakhk.ui.ScreenHeader
import io.github.oondraa.opensisakhk.data.defaultSchoolDay
import io.github.oondraa.opensisakhk.ui.Icons
import io.github.oondraa.opensisakhk.ui.theme.LocalExtra
import io.github.oondraa.opensisakhk.ui.theme.Tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class State { Past, Now, Future }

/** Minutes since midnight in Prague, ticking over on the minute. */
@Composable
fun rememberNow() = produceState(nowMinutes()) {
    while (true) {
        delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
        value = nowMinutes()
    }
}

@Composable
fun TimetableScreen(vm: TimetableViewModel = viewModel(), onBack: () -> Unit) {
    val selected = vm.selected
    val monday = selected.monday()
    val today = today()
    val now by rememberNow()
    LaunchedEffect(monday) { vm.ensure(monday) }
    val week = vm.week(monday)
    val days = week?.days.orEmpty()

    val todayLessons = if (selected == today) held(days.find { it.date == today.toString() }?.lessons.orEmpty()) else emptyList()
    val current = todayLessons.find { minutes(it.start) <= now && now < minutes(it.end) }
    val next = todayLessons.find { minutes(it.start) > now }

    Column(Modifier.fillMaxSize()) {
        Header(selected, monday, today, vm.isLoading(monday), onSelect = vm::select, onBack = onBack)
        // Stays put while it collapses, showing the last lesson it had, so the list doesn't jump mid-swipe.
        val shown = remember { mutableStateOf<Pair<Lesson?, Lesson?>?>(null) }
        if (current != null || next != null) shown.value = current to next
        AnimatedVisibility(current != null || next != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            shown.value?.let { (c, n) -> NowCard(c, n, now) }
        }
        if (vm.hasFailed(monday) && week != null) {
            Text(
                "Rozvrh se nepodařilo obnovit. Zobrazuji poslední uložený.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        val pager = rememberPagerState(initialPage = selected.weekdayIndex.coerceIn(0, 4)) { 5 }
        val mondayNow by rememberUpdatedState(monday)
        LaunchedEffect(selected) {
            val i = selected.weekdayIndex.coerceIn(0, 4)
            if (pager.currentPage != i) pager.animateScrollToPage(i)
        }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect {
                val d = mondayNow.plusDays(it.toLong())
                if (d != vm.selected) vm.select(d)
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, key = { "$monday-$it" }) { i ->
            val date = monday.plusDays(i.toLong())
            val day = days.find { it.date == date.toString() }
            PullToRefreshBox(isRefreshing = vm.isLoading(monday) && week != null, onRefresh = { vm.refresh(monday) }, modifier = Modifier.fillMaxSize()) {
                when {
                    day != null -> DayView(day, live = date == today, now = now, meal = vm.lunchFor(day.date))
                    vm.isLoading(monday) -> Skeleton()
                    else -> Empty(Icons.CalendarOff, "Žádná data", if (vm.hasFailed(monday)) "Rozvrh se nepodařilo načíst. Potáhni dolů a zkus to znovu." else null)
                }
            }
        }
    }
}

@Composable
private fun Header(selected: LocalDate, monday: LocalDate, today: LocalDate, loading: Boolean, onSelect: (LocalDate) -> Unit, onBack: () -> Unit) {
    ScreenHeader(
        "Rozvrh", longDate(selected), onBack = onBack, loading = loading,
        accessory = {
            if (monday != today.monday() || selected.weekdayIndex > 4) TextButton(onClick = { onSelect(defaultSchoolDay()) }) { Text("Dnes") }
        },
    ) {
        Spacer(Modifier.height(12.dp))
        DayStrip(selected, onSelect, Modifier.padding(end = 8.dp))
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun NowCard(current: Lesson?, next: Lesson?, now: Int) {
    val l = current ?: next ?: return
    val progress = if (current != null) (now - minutes(current.start)).toFloat() / (minutes(current.end) - minutes(current.start)) else 0f
    val animated by animateFloatAsState(progress, spring(stiffness = 60f), label = "progress")
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                (if (current != null) "Teď · končí ${inMinutes(minutes(current.end) - now)}" else "Další · ${inMinutes(minutes(l.start) - now)}").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(l.subject, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(l.room, style = MaterialTheme.typography.headlineMedium.merge(Tabular))
            }
            Text(
                buildString {
                    append("${l.teacher} · ${l.start}–${l.end}")
                    if (current != null && next != null) append(" · pak ${next.subject} v ${next.room}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (current != null) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

private sealed interface Group {
    data class Free(val item: Item.Free, val index: Int) : Group
    data class Block(val rows: List<Pair<Item, Int>>) : Group
}

/** Lessons that follow each other share one block; free time splits the day into blocks. */
private fun groups(items: List<Item>): List<Group> {
    val out = mutableListOf<Group>()
    items.forEachIndexed { i, it ->
        val last = out.lastOrNull()
        when {
            it is Item.Free -> out += Group.Free(it, i)
            last is Group.Block -> out[out.lastIndex] = Group.Block(last.rows + (it to i))
            else -> out += Group.Block(listOf(it to i))
        }
    }
    return out
}

@Composable
private fun DayView(day: Day, live: Boolean, now: Int, meal: LunchDay?) {
    if (day.lessons.isEmpty()) return Empty(Icons.Sun, "Volno", "Tenhle den nemáš žádnou hodinu.")
    val items = remember(day) { dayItems(day) }
    val groups = remember(items) { groups(items) }
    fun state(it: Item) = when {
        !live -> State.Future
        now >= minutes(it.end) -> State.Past
        now >= minutes(it.start) -> State.Now
        else -> State.Future
    }
    // Today opens on what's on now (or next), earlier ones stay above.
    val focus = if (live) items.indexOfFirst { now < minutes(it.end) } else -1
    val focusGroup = groups.indexOfFirst { g -> if (g is Group.Free) g.index == focus else (g as Group.Block).rows.any { it.second == focus } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = if (focus > 0) focusGroup.coerceAtLeast(0) else 0)

    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(groups, key = { _, g -> if (g is Group.Free) "free-${g.item.start}" else "block-${(g as Group.Block).rows.first().first.start}" }) { _, g ->
            when (g) {
                is Group.Free -> FreeRow(g.item, state(g.item), now)
                is Group.Block -> Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        g.rows.forEachIndexed { i, (it, _) ->
                            val s = state(it)
                            // Hairlines between rows, stepping aside around the one that's on now.
                            if (i > 0 && s != State.Now && state(g.rows[i - 1].first) != State.Now) {
                                HorizontalDivider(Modifier.padding(start = 54.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            when (it) {
                                is Item.LessonItem -> LessonRow(it.lesson, it.was, s)
                                is Item.Lunch -> LunchRow(it, s, meal)
                                is Item.Free -> Unit
                            }
                        }
                    }
                }
            }
        }
        item { Box(Modifier.navigationBarsPadding()) }
    }
}

/** The three columns every row shares: period (or icon), subject over its details, room. */
@Composable
private fun RowFrame(
    state: State,
    accent: Color,
    onClick: (() -> Unit)?,
    lead: @Composable () -> Unit,
    main: @Composable () -> Unit,
    trail: @Composable () -> Unit,
) {
    val wash = accent.copy(alpha = 0.13f)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                if (state == State.Now) {
                    drawRect(wash)
                    drawRoundRect(accent, Offset(0f, 10.dp.toPx()), Size(3.dp.toPx(), size.height - 20.dp.toPx()), CornerRadius(3.dp.toPx()))
                }
            }
            .heightIn(min = 62.dp)
            .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) { lead() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { main() }
        Spacer(Modifier.width(12.dp))
        trail()
    }
}

@Composable
private fun LessonRow(l: Lesson, was: List<Lesson>, state: State) {
    var open by rememberSaveable(l.start, l.subject, l.group, l.cancelled) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val tag = tagOf(l, was)
    val c = MaterialTheme.colorScheme
    val faint = c.onSurface.copy(alpha = 0.38f)
    val changed = !l.cancelled && (l.changed || l.added || was.isNotEmpty())
    val strong = when {
        l.cancelled -> faint
        state == State.Past -> c.onSurfaceVariant
        else -> c.onSurface
    }
    val weak = if (l.cancelled || state == State.Past) faint else c.onSurfaceVariant
    val period = periodOf(l.start, l.end)

    Column {
        RowFrame(
            state = state,
            accent = c.primary,
            onClick = when {
                tag.isNotEmpty() -> ({ open = !open })
                l.meet != null -> ({ uri.openUri(l.meet) })
                else -> null
            },
            lead = {
                Text(
                    period,
                    style = (if (period.length > 2) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge).merge(Tabular),
                    color = if (state == State.Now) c.primary else if (l.cancelled || state == State.Past) faint else c.onSurfaceVariant,
                )
            },
            main = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        l.subject,
                        style = MaterialTheme.typography.titleMedium,
                        color = strong,
                        textDecoration = if (l.cancelled) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tag.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Tag(tag, accent = !l.cancelled, open = open)
                    }
                }
                Text(
                    buildString {
                        append("${l.start}–${l.end}")
                        if (l.teacher.isNotEmpty()) append(" · ${l.teacher}")
                        if (l.group.isNotEmpty()) append(" · sk. ${l.group}")
                    },
                    style = MaterialTheme.typography.bodySmall.merge(Tabular),
                    color = weak,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trail = {
                Text(l.room, style = MaterialTheme.typography.titleMedium.merge(Tabular), color = if (changed && state != State.Past) c.primary else strong)
            },
        )
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 54.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (line in changeLines(l, was)) {
                    Row {
                        Text(line.label, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.width(76.dp))
                        Text(
                            buildAnnotatedString {
                                if (line.from != null) {
                                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = c.onSurfaceVariant)) { append(line.from.ifEmpty { "–" }) }
                                    withStyle(SpanStyle(color = faint)) { append("  →  ") }
                                }
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(line.to.ifEmpty { "–" }) }
                            },
                            style = MaterialTheme.typography.bodyMedium.merge(Tabular),
                        )
                    }
                }
                if (l.meet != null) TextButton(onClick = { uri.openUri(l.meet) }, contentPadding = PaddingValues(0.dp)) { Text("Otevřít Meet") }
            }
        }
    }
}

@Composable
private fun Tag(text: String, accent: Boolean, open: Boolean) {
    val c = MaterialTheme.colorScheme
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Surface(shape = RoundedCornerShape(6.dp), color = if (accent) c.primaryContainer else c.surfaceVariant, contentColor = if (accent) c.onPrimaryContainer else c.onSurfaceVariant) {
        Row(Modifier.padding(start = 6.dp, end = 4.dp, top = 1.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp))
            Icon(Icons.ChevronDown, null, Modifier.size(12.dp).rotate(turn))
        }
    }
}

@Composable
private fun LunchRow(it: Item.Lunch, state: State, meal: LunchDay?) {
    val green = LocalExtra.current.green
    val c = MaterialTheme.colorScheme
    val past = state == State.Past
    RowFrame(
        state = state,
        accent = green,
        onClick = null,
        lead = { Icon(Icons.Utensils, null, Modifier.size(20.dp), tint = if (past) c.onSurface.copy(alpha = 0.38f) else green) },
        main = {
            Text(meal?.ordered?.label ?: "Oběd", style = MaterialTheme.typography.titleMedium, color = if (past) c.onSurfaceVariant else c.onSurface)
            Text(
                "${it.start}–${it.end} · ${if (meal != null) meal.ordered?.name ?: "Nemáš objednáno" else "Jídelna"}",
                style = MaterialTheme.typography.bodySmall.merge(Tabular),
                color = if (past) c.onSurface.copy(alpha = 0.38f) else c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trail = {
            meal?.ordered?.number?.let { n -> Text("$n", style = MaterialTheme.typography.titleMedium.merge(Tabular), color = if (past) c.onSurfaceVariant else green) }
        },
    )
}

/** Free time between blocks: one quiet line, aligned with the subjects. */
@Composable
private fun FreeRow(it: Item.Free, state: State, now: Int) {
    val c = MaterialTheme.colorScheme
    val color = when (state) {
        State.Now -> c.primary
        State.Past -> c.onSurface.copy(alpha = 0.38f)
        State.Future -> c.onSurfaceVariant
    }
    Row(Modifier.padding(start = 54.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state == State.Now) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(c.primary))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(if (state == State.Now) "Volno ještě ${span(maxOf(1, minutes(it.end) - now))}" else "Volno ${span(minutes(it.end) - minutes(it.start))}")
                }
                withStyle(SpanStyle(color = c.onSurface.copy(alpha = 0.38f))) { append("   ${it.start}–${it.end}") }
            },
            style = MaterialTheme.typography.bodySmall.merge(Tabular),
            color = color,
        )
    }
}

/** Placeholder shaped like the day's rows while the first copy loads. */
@Composable
private fun Skeleton() {
    val pulse by produceState(0.6f) {
        while (true) {
            value = if (value > 0.8f) 0.5f else 1f
            delay(700)
        }
    }
    val alpha by animateFloatAsState(pulse, label = "pulse")
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
        Column {
            repeat(6) {
                Row(Modifier.height(62.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f * alpha)))
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth(0.4f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f * alpha)))
                        Box(Modifier.fillMaxWidth(0.6f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f * alpha)))
                    }
                }
            }
        }
    }
}
