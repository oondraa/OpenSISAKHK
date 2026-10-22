package io.github.oondraa.opensisakhk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.ApiError
import io.github.oondraa.opensisakhk.data.Unauthorized
import io.github.oondraa.opensisakhk.ui.theme.Ember
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LoginScreen(onSignedIn: (Offset) -> Unit) {
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || username.isBlank() || password.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            try {
                Api.login(username.trim(), password)
                // Same frame as signedIn flipping: the welcome covers the switch to the app.
                onSignedIn(buttonCenter)
            } catch (e: Exception) {
                busy = false
                error = when {
                    e is Unauthorized -> "Špatné jméno nebo heslo."
                    e is ApiError && e.code == "rate_limited" -> "Moc pokusů o přihlášení. Zkus to za minutu."
                    else -> "SIS teď neodpovídá. Zkus to za chvíli."
                }
                // A failed try shakes the form, even when the message stays the same.
                for (x in listOf(14f, -12f, 8f, -5f, 0f)) shake.animateTo(x, spring(stiffness = 4000f))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Open") }
                    withStyle(SpanStyle(color = Ember, fontWeight = FontWeight.Black)) { append("SISAKHK") }
                },
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Rozvrh, známky, absence a obědy. Přihlas se stejně jako do školního SIS.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            Column(Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Uživatelské jméno") },
                    leadingIcon = { Icon(Icons.User, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    supportingText = {
                        Text(buildAnnotatedString {
                            append("Je to část školního e-mailu před zavináčem. Z ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("novak.adam") }
                            append("@ssakhk.cz je to ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("novak.adam") }
                            append(".")
                        })
                    },
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Heslo") },
                    leadingIcon = { Icon(Icons.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.EyeOff else Icons.Eye, if (show) "Skrýt heslo" else "Zobrazit heslo")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                )
                AnimatedVisibility(error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = ::submit,
                    enabled = busy || (username.isNotBlank() && password.isNotEmpty()),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp).onGloballyPositioned { buttonCenter = it.boundsInRoot().center },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                    else Text("Přihlásit se", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Heslo zůstává zašifrované v tomhle zařízení. Na server se uloží jen se zapnutými notifikacemi. Neoficiální aplikace, není spojená se školou.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
