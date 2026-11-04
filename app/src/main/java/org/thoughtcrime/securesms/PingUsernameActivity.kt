package org.thoughtcrime.securesms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.models.ServiceId.ACI
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.security.SecureRandom
import java.util.UUID

/**
 * Ping frontend-testing entry point: enter a username instead of a phone
 * number. No server, no SMS code. Writes a local phone-number-less account
 * state (random ACI, generated identity key, username) and opens the main
 * UI. Messaging cannot work without a backend/account — UI testing only.
 *
 * This activity is the launcher entry (all RoutingActivity aliases target
 * it), so the Signal registration flow is never the first screen. When a
 * complete local account already exists, it forwards straight to MainActivity.
 */
class PingUsernameActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (hasCompletePingAccount()) {
      startActivity(MainActivity.clearTop(this))
      finish()
      return
    }
    enableEdgeToEdge()
    setContent {
      PingUsernameScreen(onNext = ::enterWithUsername)
    }
  }

  private fun hasCompletePingAccount(): Boolean {
    val servicePassword = SignalStore.account.servicePassword
    return SignalStore.account.isRegistered &&
           SignalStore.account.aci != null &&
           !servicePassword.isNullOrEmpty()
  }

  private fun enterWithUsername(rawUsername: String) {
    val username = rawUsername.trim().removePrefix("@").trim()
    if (username.isEmpty()) return

    lifecycleScope.launch(Dispatchers.IO) {
      SignalStore.account.generateAciIdentityKeyIfNecessary()
      SignalStore.account.setAci(ACI.from(UUID.randomUUID()))
      SignalStore.account.username = username
      SignalStore.account.registrationId = 1 + SecureRandom().nextInt(16380)
      SignalStore.account.setRegistered(true)
      SignalStore.registration.hasPromptedPushRegistration = true
      SignalStore.registration.markRegistrationComplete()

      withContext(Dispatchers.Main) {
        startActivity(MainActivity.clearTop(this@PingUsernameActivity))
        finish()
      }
    }
  }
}

@Composable
private fun PingUsernameScreen(onNext: (String) -> Unit) {
  var username by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  MaterialTheme(colorScheme = darkColorScheme()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF121214))
        .padding(24.dp)
    ) {
      Column {
        Spacer(modifier = Modifier.height(64.dp))
        Text(text = "Username", color = Color.White, fontSize = 32.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Enter a username to enter Ping. No phone number, no server — frontend testing only.",
          color = Color(0xFFB0B0B0),
          fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text("Username") },
          placeholder = { Text("@basant") },
          singleLine = true,
          enabled = !busy,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
          ),
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
          onClick = {
            busy = true
            onNext(username)
          },
          enabled = username.isNotBlank() && !busy,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C6BED)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Next")
        }
      }
    }
  }
}
