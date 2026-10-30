package fr.axllvy.timetabler.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import fr.axllvy.timetabler.auth.AdminApi
import fr.axllvy.timetabler.auth.AdminAuth
import fr.axllvy.timetabler.auth.AdminCheck
import kotlinx.coroutines.launch
import org.koin.compose.getKoin

/** Admin sign-in and sign-out, with what the server makes of the signed-in account. */
@Composable
fun AdminDialog(onDismiss: () -> Unit) {
  val koin = getKoin()
  val auth = remember { koin.getOrNull<AdminAuth>() }
  val api = remember { koin.getOrNull<AdminApi>() }
  if (auth == null || api == null) {
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Admin") },
      text = { Text("Admin sign-in is not available in this app.") },
      confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    return
  }
  val scope = rememberCoroutineScope()
  val account by auth.account.collectAsState()
  var check by remember { mutableStateOf<AdminCheck?>(null) }
  LaunchedEffect(account) { check = if (account == null) null else api.check() }

  val signedIn = account
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Admin") },
    text = {
      if (signedIn == null) {
        Text("Sign in to manage timetables.")
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Signed in as ${signedIn.name ?: signedIn.sub}")
          Text(
            check.label(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      if (signedIn == null) {
        TextButton(onClick = { scope.launch { auth.signIn() } }) { Text("Sign in") }
      } else {
        TextButton(onClick = onDismiss) { Text("Close") }
      }
    },
    dismissButton = {
      if (signedIn == null) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
      } else {
        TextButton(onClick = auth::signOut) { Text("Sign out") }
      }
    },
  )
}

private fun AdminCheck?.label(): String =
  when (this) {
    null -> "Checking admin access…"
    is AdminCheck.Admin -> "Admin access confirmed"
    AdminCheck.NotAdmin -> "This account is not an admin"
    AdminCheck.SignedOut -> "The server refused this session; sign out and sign in again"
    AdminCheck.Unreachable -> "Could not reach the server"
  }
