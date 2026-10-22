package io.github.oondraa.opensisakhk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.oondraa.opensisakhk.absence.AbsenceScreen
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.finance.FinanceScreen
import io.github.oondraa.opensisakhk.grades.GradesScreen
import io.github.oondraa.opensisakhk.lunch.LunchScreen
import io.github.oondraa.opensisakhk.projects.ProjectsScreen
import io.github.oondraa.opensisakhk.school.GymScreen
import io.github.oondraa.opensisakhk.school.RoomsScreen
import io.github.oondraa.opensisakhk.school.TeachersScreen
import io.github.oondraa.opensisakhk.settings.ClassPrompt
import io.github.oondraa.opensisakhk.settings.SettingsScreen
import io.github.oondraa.opensisakhk.timetable.TimetableScreen
import io.github.oondraa.opensisakhk.ui.LoginScreen
import io.github.oondraa.opensisakhk.ui.Welcome
import io.github.oondraa.opensisakhk.ui.theme.Look
import io.github.oondraa.opensisakhk.ui.theme.OpenSISAKHKTheme

class MainActivity : ComponentActivity() {
    /** A part a notification or a widget asked to open. */
    private val linked = mutableStateOf<Part?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) linked.value = partOf(intent)
        enableEdgeToEdge()
        setContent {
            val dark = when (Look.mode) {
                Look.Mode.System -> isSystemInDarkTheme()
                Look.Mode.Light -> false
                Look.Mode.Dark -> true
            }
            OpenSISAKHKTheme(darkTheme = dark, dynamicColor = Look.dynamic) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val signedIn by Api.signedIn.collectAsStateWithLifecycle()
                    // Signing in plays the welcome over the switch from the login to the app.
                    var welcome by remember { mutableStateOf<Offset?>(null) }
                    var covered by remember { mutableStateOf(false) }
                    val inside = signedIn && (welcome == null || covered)
                    AnimatedContent(
                        inside,
                        transitionSpec = { if (welcome != null) EnterTransition.None togetherWith ExitTransition.None else fadeIn() togetherWith fadeOut() },
                        label = "auth",
                    ) { app ->
                        if (app) AppShell(linked) else LoginScreen(onSignedIn = { welcome = it; covered = false })
                    }
                    welcome?.let { Welcome(it, onCovered = { covered = true }, onDone = { welcome = null }) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        partOf(intent)?.let { linked.value = it }
    }

    private fun partOf(intent: Intent?) = intent?.getStringExtra(EXTRA_PART)?.let { n -> Part.entries.find { it.name == n } }

    companion object {
        const val EXTRA_PART = "part"
    }
}

/**
 * The home overview, and the part opened over it. One level deep like the web app: every part opens from the
 * home and goes back to it (the system back gesture too). `closed` tells the home to read its glances again.
 */
@Composable
private fun AppShell(linked: MutableState<Part?>) {
    var open by rememberSaveable { mutableStateOf<Part?>(null) }
    LaunchedEffect(linked.value) {
        linked.value?.let {
            open = it
            linked.value = null
        }
    }
    var closed by rememberSaveable { mutableIntStateOf(0) }
    val back: () -> Unit = {
        open = null
        closed++
    }
    BackHandler(open != null, back)
    // Unlike the web app, the home doesn't wait for each part to be opened once: it fills its glances on launch.
    LaunchedEffect(Unit) {
        prefetch { closed++ }
    }

    AnimatedContent(
        open,
        transitionSpec = {
            if (targetState != null)
                (slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(220))) togetherWith (scaleOut(tween(320), targetScale = 0.96f) + fadeOut(tween(220)))
            else
                (scaleIn(tween(320), initialScale = 0.96f) + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(200)))
        },
        label = "screens",
    ) { part ->
        when (part) {
            null -> {
                HomeScreen(onOpen = { open = it }, refresh = closed)
                ClassPrompt()
            }
            Part.Timetable -> TimetableScreen(viewModel(key = "timetable-${Api.generation}"), onBack = back)
            Part.Lunch -> LunchScreen(back)
            Part.Gym -> GymScreen(back)
            Part.Rooms -> RoomsScreen(back)
            Part.Teachers -> TeachersScreen(back)
            Part.Grades -> GradesScreen(back)
            Part.Absence -> AbsenceScreen(back)
            Part.Projects -> ProjectsScreen(back)
            Part.Finance -> FinanceScreen(back)
            Part.Settings -> SettingsScreen(back, onOrder = { closed++ })
        }
    }
}
