package io.github.oondraa.opensisakhk.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.R
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.Me
import io.github.oondraa.opensisakhk.ui.theme.Ember
import io.github.oondraa.opensisakhk.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * After signing in: an ember circle grows out of the login button over the whole screen, the app takes the login's
 * place underneath once it's covered (`onCovered`), the icon and a hello pop in, then it all lifts away into the app.
 */
@Composable
fun Welcome(origin: Offset, onCovered: () -> Unit, onDone: () -> Unit) {
    val grow = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    var name by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        launch {
            runCatching { Api.me() }.getOrNull()?.let {
                Cache.set("me", Me.serializer(), it)
                name = it.name?.split(" ")?.firstOrNull()
            }
        }
        grow.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        onCovered()
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
        delay(900)
        lift.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        onDone()
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        // Far enough to reach the farthest corner from wherever the button was.
        val reach = max(max(hypot(origin.x, origin.y), hypot(w - origin.x, origin.y)), max(hypot(origin.x, h - origin.y), hypot(w - origin.x, h - origin.y)))

        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - lift.value }) {
            drawCircle(Ember, radius = reach * grow.value, center = origin)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                alpha = pop.value.coerceIn(0f, 1f) * (1f - lift.value)
                val s = 0.7f + 0.3f * pop.value + 0.12f * lift.value
                scaleX = s
                scaleY = s
                translationY = -60f * lift.value
            },
        ) {
            // The launcher icon itself: ink square, "Open SIS" in paper and ember.
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(28.dp)).background(Ink), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(156.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(if (name != null) "Vítej," else "Vítej", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = 0.85f))
            name?.let { Text(it, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, color = Color.White) }
        }
    }
}
