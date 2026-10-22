package io.github.oondraa.opensisakhk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.Unauthorized
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer

/** Czech plural: 1 hodina, 2–4 hodiny, 5+ hodin. */
fun plural(n: Int, one: String, few: String, many: String) = if (n == 1) one else if (n in 2..4) few else many

/** "2,47": decimals the Czech way. */
fun Double.cz(decimals: Int = 2) = "%.${decimals}f".format(this).replace('.', ',')

/** What a screen loaded: the cached copy at once, then a fresh one. `failed` with data = showing the old copy. */
class Loaded<T>(val data: T?, val loading: Boolean, val failed: Boolean, val refresh: () -> Unit)

/**
 * The web app's useCachedFetch: shows the last stored response straight away, fetches a fresh one, and keeps the
 * old one on screen (with `failed`) when SIS doesn't answer. A 401 has already sent the app back to the login.
 */
@Composable
fun <T> rememberCached(key: String?, ser: KSerializer<T>, fetch: suspend () -> T): Loaded<T> {
    var data by remember(key) { mutableStateOf(key?.let { Cache.get(it, ser) }) }
    var loading by remember(key) { mutableStateOf(key != null) }
    var failed by remember(key) { mutableStateOf(false) }
    var tick by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, tick) {
        if (key == null) return@LaunchedEffect
        loading = true
        try {
            val fresh = fetch()
            data = fresh
            failed = false
            Cache.set(key, ser, fresh)
        } catch (e: Unauthorized) {
            // signed out
        } catch (e: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    return Loaded(data, loading, failed) { tick++ }
}

/** Title, the date or context above it, an optional back link, and a thin bar while loading. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Přehled",
    loading: Boolean = false,
    accessory: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(Modifier.statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = if (onBack != null) 4.dp else 12.dp)) {
            if (onBack != null) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(start = 0.dp, end = 12.dp)) {
                    Icon(Icons.ChevronLeft, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(backLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (subtitle != null) Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                accessory()
            }
            below()
        }
        Box(Modifier.fillMaxWidth().height(3.dp).padding(top = 1.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), trackColor = Color.Transparent)
        }
    }
}

/** A screen: header on top, then a scrolling, pull-to-refresh list of sections. */
@Composable
fun Screen(
    header: @Composable () -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        header()
        val list = @Composable {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                item { Box(Modifier.navigationBarsPadding()) }
            }
        }
        if (onRefresh != null) PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) { list() }
        else Box(Modifier.weight(1f)) { list() }
    }
}

/** "Couldn't refresh, showing the last copy" and the like. */
@Composable
fun Banner(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

/** Inset grouped list: one rounded surface, hairlines between the rows. */
@Composable
fun Card(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = color, modifier = modifier.fillMaxWidth()) { Column(content = content) }
}

@Composable
fun Divider(inset: Dp = 16.dp) = HorizontalDivider(Modifier.padding(start = inset), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

/** Section with an optional small title above and a footnote under it. */
@Composable
fun Section(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) SectionTitle(title)
        Card(content = content)
        if (footer != null) Footer(footer)
    }
}

@Composable
fun SectionTitle(text: String) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))

@Composable
fun Footer(text: String, center: Boolean = false) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = if (center) TextAlign.Center else TextAlign.Start,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
)

/** Square-ish icon in the part's tint, like the web app's .more-icon. */
@Composable
fun TintIcon(icon: ImageVector, tint: Color, size: Dp = 32.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(tint), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.6f), tint = Color.White)
    }
}

/** A list row: optional lead, title over a subtitle, an optional trailing thing, a chevron when it opens something. */
@Composable
fun ListRow(
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    lead: (@Composable () -> Unit)? = null,
    trail: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lead != null) {
            lead()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trail != null) {
            Spacer(Modifier.width(10.dp))
            trail()
        }
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

/** A chevron that turns when its section opens. */
@Composable
fun Expander(open: Boolean) {
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "expander")
    Icon(Icons.ChevronRight, null, Modifier.size(18.dp).rotate(turn), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
}

@Composable
fun Collapsible(open: Boolean, content: @Composable ColumnScope.() -> Unit) =
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { Column(content = content) }

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(
                selected = v == value,
                onClick = { onChange(v) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

/** Small rounded label: "změna", "sk. 2", "neomluveno". */
@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.surfaceVariant, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = RoundedCornerShape(6.dp), color = color, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

@Composable
fun Empty(icon: ImageVector, title: String, text: String? = null, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (text != null) {
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Placeholder rows while the first copy loads. */
@Composable
fun SkeletonList(count: Int = 6, rowHeight: Dp = 62.dp) {
    val pulse by produceState(0.6f) {
        while (true) {
            value = if (value > 0.8f) 0.5f else 1f
            delay(700)
        }
    }
    val a by animateFloatAsState(pulse, label = "pulse")
    val ink = MaterialTheme.colorScheme.onSurface
    Card {
        repeat(count) {
            Row(Modifier.height(rowHeight).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(ink.copy(alpha = 0.08f * a)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth(0.45f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(ink.copy(alpha = 0.1f * a)))
                    Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(ink.copy(alpha = 0.07f * a)))
                }
            }
        }
    }
}
