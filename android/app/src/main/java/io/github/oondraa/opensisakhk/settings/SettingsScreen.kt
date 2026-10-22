package io.github.oondraa.opensisakhk.settings

import android.graphics.BitmapFactory
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.BuildConfig
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.ApiError
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.Me
import io.github.oondraa.opensisakhk.data.Unauthorized
import io.github.oondraa.opensisakhk.homeOrder
import io.github.oondraa.opensisakhk.saveHomeOrder
import io.github.oondraa.opensisakhk.school.CLASSES
import io.github.oondraa.opensisakhk.ui.Card
import io.github.oondraa.opensisakhk.ui.Divider
import io.github.oondraa.opensisakhk.ui.Footer
import io.github.oondraa.opensisakhk.ui.Icons
import io.github.oondraa.opensisakhk.ui.ListRow
import io.github.oondraa.opensisakhk.ui.Screen
import io.github.oondraa.opensisakhk.ui.ScreenHeader
import io.github.oondraa.opensisakhk.ui.Section
import io.github.oondraa.opensisakhk.ui.Segmented
import io.github.oondraa.opensisakhk.ui.TintIcon
import io.github.oondraa.opensisakhk.ui.rememberCached
import io.github.oondraa.opensisakhk.ui.theme.Look
import io.github.oondraa.opensisakhk.ui.theme.Tones
import io.github.oondraa.opensisakhk.watch.Message
import io.github.oondraa.opensisakhk.watch.Notifications
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOrder: () -> Unit) {
    val me = rememberCached("me", Me.serializer()) { Api.me() }
    var cls by remember { mutableStateOf<String?>(null) }
    val myClass = cls ?: me.data?.`class`
    var report by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current

    Screen(header = { ScreenHeader("Nastavení", onBack = onBack) }) {
        item { Profile(me.data, myClass) }
        // Right under the profile, so a broken screen is two taps from being reported.
        item {
            Section(footer = "Přijde to rovnou autorovi appky.") {
                ListRow("Nahlásit chybu nebo nápad", onClick = { report = true }, lead = { TintIcon(Icons.Bug, Tones.red) })
            }
        }
        item {
            var menu by remember { mutableStateOf(false) }
            Section {
                Box {
                    ListRow("Třída", onClick = { menu = true }, trail = { Text(myClass ?: "Vyber…", color = MaterialTheme.colorScheme.primary) })
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        for (c in CLASSES) DropdownMenuItem(text = { Text(c) }, onClick = {
                            menu = false
                            cls = c
                            scope.launch {
                                try {
                                    Api.setClass(c)
                                    me.data?.let { Cache.set("me", Me.serializer(), it.copy(`class` = c)) }
                                } catch (e: Exception) {
                                    cls = null
                                }
                            }
                        })
                    }
                }
            }
        }
        item {
            Section(
                "Vzhled",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Barvy z tapety: appka si vezme barvy z tvé tapety (Material You). Vypnuté: oranžová jako na webu." else null,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Režim", style = MaterialTheme.typography.bodyLarge)
                    Segmented(listOf(Look.Mode.System to "Auto", Look.Mode.Light to "Světlý", Look.Mode.Dark to "Tmavý"), Look.mode, Look::chooseMode)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Divider()
                    ListRow("Barvy z tapety", chevron = false, onClick = { Look.chooseDynamic(!Look.dynamic) }, trail = { Switch(Look.dynamic, onCheckedChange = Look::chooseDynamic) })
                }
            }
        }
        item { OrderSection(onOrder) }
        item { NotificationSection() }
        item { StravaSection(me.data?.strava ?: false) }
        item {
            Section("Soukromí") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Privacy(
                        "Co ukládáme:",
                        "z profilu v SIS tvoje jméno, profilovou fotku, školní e-mail, skupiny, volitelné bloky a projekty, k tomu třídu, kterou potvrdíš. Slouží k tomu, abychom věděli, kdo aplikaci používá, a pro další funkce (třeba změny rozvrhu pro tvoji třídu a skupiny).",
                    )
                    Privacy(
                        "Heslo:",
                        "zůstává zašifrované v tomhle zařízení, aby tě aplikace nemusela odhlašovat, když SIS ukončí relaci. Do databáze se dostane jen tehdy, když si zapneš notifikace, a to zašifrované.",
                    )
                    Privacy(
                        "Co neukládáme:",
                        "známky, rozvrh, absenci ani finance (finance ani v tomhle zařízení). U notifikací o známkách si server pamatuje jen krátké otisky (hashe), podle kterých pozná, co je nové.",
                    )
                    TextButton(onClick = { uri.openUri("https://github.com/oondraa/OpenSISAKHK") }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Zdrojový kód je veřejný.")
                    }
                }
            }
        }
        item {
            Section(footer = "Appka je zdarma a bez reklam. Když ti šetří nervy, můžeš autorovi koupit kafe.") {
                ListRow("Podpořit na Ko-fi", onClick = { uri.openUri("https://ko-fi.com/oondraa") }, lead = { TintIcon(Icons.Coffee, Tones.orange) })
            }
        }
        item {
            Card {
                ListRow("Odhlásit se", chevron = false, titleColor = MaterialTheme.colorScheme.error, onClick = { scope.launch { Api.logout() } })
            }
        }
        item { Footer("OpenSISAKHK pro Android ${BuildConfig.VERSION_NAME}\nstudentská aplikace, není spojená se školou", center = true) }
    }

    if (report) ReportSheet { report = false }
}

@Composable
private fun Privacy(title: String, text: String) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            append(title)
            pop()
            append(" $text")
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun Profile(me: Me?, cls: String?) {
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(me?.hasPhoto) {
        if (me?.hasPhoto == true) photo = runCatching { Api.photo()?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
    }
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                val p = photo
                if (p != null) Image(p, null, Modifier.size(60.dp), contentScale = ContentScale.Crop)
                else Text(
                    (me?.name ?: "?").split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(me?.name ?: "Přihlášený student", style = MaterialTheme.typography.titleLarge)
                Text("${if (cls != null) "$cls · " else ""}Přihlášeno přes SIS SSAKHK", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * One switch for timetable changes, new grades and new absence. The phone checks by itself about every 15 minutes
 * with the session it has; nothing about it is stored on the server.
 */
@Composable
private fun NotificationSection() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(Notifications.enabled && Notifications.permitted(context)) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        on = granted
        Notifications.setEnabled(granted)
    }
    fun set(want: Boolean) {
        if (want && !Notifications.permitted(context)) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        else {
            on = want
            Notifications.setEnabled(want)
        }
    }
    Section(
        "Notifikace",
        if (denied) "Notifikace jsou pro appku zakázané. Povol je v nastavení Androidu u OpenSISAKHK."
        else "Přijde ti zpráva, když se ti změní rozvrh, přibude známka nebo absence. Telefon to sám kontroluje zhruba každých 15 minut, když spí, tak o něco méně často. Heslo kvůli tomu nikam neposíláme.",
    ) {
        ListRow("Rozvrh, známky a absence", chevron = false, onClick = { set(!on) }, trail = { Switch(on, onCheckedChange = ::set) })
        if (on) {
            Divider()
            ListRow("Poslat zkušební notifikaci", chevron = false, titleColor = MaterialTheme.colorScheme.primary, onClick = {
                Notifications.post(context, io.github.oondraa.opensisakhk.Part.Timetable, Message("Takhle vypadá notifikace", "Dnes: 10:45 MAT · 211 · NO"))
            })
        }
    }
}

/** Up/down arrows rather than dragging: precise with a thumb, and works with TalkBack. */
@Composable
private fun OrderSection(onOrder: () -> Unit) {
    var order by remember { mutableStateOf(homeOrder()) }
    fun move(i: Int, by: Int) {
        val next = order.toMutableList()
        next[i] = order[i + by].also { next[i + by] = order[i] }
        order = next
        saveHomeOrder(next)
        onOrder()
    }
    Section("Pořadí na ploše", "Rozvrh je vždycky nahoře, první čtyři jsou velké dlaždice, ostatní v seznamu pod nimi.") {
        order.forEachIndexed { i, part ->
            if (i > 0) Divider(62.dp)
            ListRow(part.label, chevron = false, lead = { TintIcon(part.icon, part.tint) }, trail = {
                Row {
                    IconButton(onClick = { move(i, -1) }, enabled = i > 0) { Icon(Icons.ChevronDown, "${part.label} výš", Modifier.size(18.dp).rotate(180f)) }
                    IconButton(onClick = { move(i, 1) }, enabled = i < order.lastIndex) { Icon(Icons.ChevronDown, "${part.label} níž", Modifier.size(18.dp)) }
                }
            })
        }
    }
}

/** Canteen account (Strava.cz): its login lives only in the encrypted session cookie, like the SIS one. */
@Composable
private fun StravaSection(connected: Boolean) {
    var on by remember { mutableStateOf<Boolean?>(null) }
    var jmeno by remember { mutableStateOf("") }
    var heslo by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val isOn = on ?: connected
    val scope = rememberCoroutineScope()

    fun forgetMeals() {
        Cache.remove("lunch")
        Cache.remove("canteen")
    }

    Section(
        "Obědy (Strava)",
        msg ?: if (isOn) "Obědy objednáváš a měníš v sekci Obědy, v rozvrhu u oběda uvidíš, co máš objednané."
        else "Propoj účet jídelny a budeš objednávat obědy přímo tady. Heslo zůstává jen zašifrované v tomhle zařízení.",
    ) {
        if (isOn) {
            ListRow("Účet jídelny", chevron = false, trail = { Text("Propojeno", color = Tones.green) })
            Divider()
            ListRow("Odpojit Stravu", chevron = false, titleColor = MaterialTheme.colorScheme.error, onClick = {
                if (busy) return@ListRow
                busy = true
                scope.launch {
                    runCatching { Api.stravaDisconnect() }.onSuccess { forgetMeals(); on = false }
                    busy = false
                }
            })
        } else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(jmeno, { jmeno = it }, label = { Text("Uživatel ve Stravě") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                heslo, { heslo = it }, label = { Text("Heslo do Stravy") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    busy = true
                    msg = null
                    scope.launch {
                        try {
                            Api.stravaConnect(jmeno.trim(), heslo)
                            forgetMeals()
                            on = true
                            heslo = ""
                        } catch (e: Unauthorized) {
                            // signed out
                        } catch (e: Exception) {
                            msg = if ((e as? ApiError)?.code == "strava_bad_credentials") "Špatné jméno nebo heslo do Stravy." else "Stravu se nepodařilo propojit."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && jmeno.isNotBlank() && heslo.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Propojuji…" else "Propojit") }
        }
    }
}

private const val MAX = 2000

/** Bug report or idea, straight to the developer's Telegram (POST /api/report). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kind by remember { mutableStateOf("bug") }
    var text by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("idle") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = { if (state != "busy") onClose() }, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state == "sent") {
                Icon(Icons.Check, null, Modifier.size(48.dp).align(Alignment.CenterHorizontally), tint = Tones.green)
                Text("Díky!", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Přišlo to rovnou autorovi appky.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
                Button(onClick = onClose, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Hotovo") }
                return@Column
            }
            Text("Nahlásit", style = MaterialTheme.typography.headlineMedium)
            Segmented(listOf("bug" to "Chybu", "idea" to "Nápad"), kind, { kind = it })
            OutlinedTextField(
                text, { if (it.length <= MAX) text = it },
                placeholder = { Text(if (kind == "bug") "Co se stalo a kde? Třeba: v Rozvrhu se u čtvrtka ukazuje špatná místnost." else "Co by ti v appce pomohlo?") },
                minLines = 5, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Spolu s textem se pošle tvoje jméno a třída, verze appky a typ zařízení, ať se dá chyba najít a ozvat se ti." +
                    if (text.length > MAX - 200) " ${MAX - text.length} znaků zbývá." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = {
                    state = "busy"
                    error = null
                    scope.launch {
                        try {
                            Api.report(
                                kind, text.trim(),
                                mapOf(
                                    "version" to "android ${BuildConfig.VERSION_NAME}",
                                    "layout" to "plocha",
                                    "display" to "nativní appka",
                                    "device" to "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
                                ),
                            )
                            state = "sent"
                        } catch (e: Exception) {
                            state = "idle"
                            error = when ((e as? ApiError)?.code) {
                                "rate_limited" -> "Moc hlášení najednou. Zkus to za minutu."
                                "not_configured" -> "Hlášení teď nejde poslat."
                                else -> "Nepodařilo se odeslat. Zkus to za chvíli."
                            }
                        }
                    }
                },
                enabled = state != "busy" && text.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (state == "busy") "Odesílám…" else "Odeslat") }
            TextButton(onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onClose() } }, enabled = state != "busy", modifier = Modifier.fillMaxWidth()) { Text("Zrušit") }
        }
    }
}

/** Asks for the class once, when the server doesn't have it yet. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ClassPrompt() {
    val me = rememberCached("me", Me.serializer()) { Api.me() }
    var closed by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf("open") }
    val scope = rememberCoroutineScope()
    // A cached answer without the field reads as null too; wait for the fresh one.
    if (me.loading || me.data == null || me.data.`class` != null || closed) return

    ModalBottomSheet(onDismissRequest = { closed = true }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ve které jsi třídě?", style = MaterialTheme.typography.headlineMedium)
            Text("SIS třídu neukazuje. Vyber ji a appka ti bude ukazovat změny pro tvoji třídu.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in CLASSES) FilterChip(chosen == c, onClick = { chosen = c }, label = { Text(c, style = MaterialTheme.typography.titleMedium) })
            }
            if (state == "error") Text("Třídu se nepodařilo uložit, zkus to znovu.", color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    val c = chosen ?: return@Button
                    state = "busy"
                    scope.launch {
                        try {
                            Api.setClass(c)
                            Cache.set("me", Me.serializer(), me.data.copy(`class` = c))
                            closed = true
                        } catch (e: Exception) {
                            state = "error"
                        }
                    }
                },
                enabled = chosen != null && state != "busy",
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (state == "busy") "Ukládám…" else "Potvrdit") }
            TextButton(onClick = { closed = true }, modifier = Modifier.fillMaxWidth()) { Text("Teď ne") }
        }
    }
}
