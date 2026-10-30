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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import timetabler.composeapp.generated.resources.Res
import timetabler.composeapp.generated.resources.admin_cancel
import timetabler.composeapp.generated.resources.admin_checking
import timetabler.composeapp.generated.resources.admin_close
import timetabler.composeapp.generated.resources.admin_confirmed
import timetabler.composeapp.generated.resources.admin_not_admin
import timetabler.composeapp.generated.resources.admin_refused
import timetabler.composeapp.generated.resources.admin_sign_in
import timetabler.composeapp.generated.resources.admin_sign_in_prompt
import timetabler.composeapp.generated.resources.admin_sign_out
import timetabler.composeapp.generated.resources.admin_signed_in_as
import timetabler.composeapp.generated.resources.admin_title
import timetabler.composeapp.generated.resources.admin_unavailable
import timetabler.composeapp.generated.resources.admin_unreachable

/** Admin sign-in and sign-out, with what the server makes of the signed-in account. */
@Composable
fun AdminDialog(onDismiss: () -> Unit) {
  val koin = getKoin()
  val auth = remember { koin.getOrNull<AdminAuth>() }
  val api = remember { koin.getOrNull<AdminApi>() }
  if (auth == null || api == null) {
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(Res.string.admin_title)) },
      text = { Text(stringResource(Res.string.admin_unavailable)) },
      confirmButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.admin_close)) }
      },
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
    title = { Text(stringResource(Res.string.admin_title)) },
    text = {
      if (signedIn == null) {
        Text(stringResource(Res.string.admin_sign_in_prompt))
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(stringResource(Res.string.admin_signed_in_as, signedIn.name ?: signedIn.sub))
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
        TextButton(onClick = { scope.launch { auth.signIn() } }) {
          Text(stringResource(Res.string.admin_sign_in))
        }
      } else {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.admin_close)) }
      }
    },
    dismissButton = {
      if (signedIn == null) {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.admin_cancel)) }
      } else {
        TextButton(onClick = auth::signOut) { Text(stringResource(Res.string.admin_sign_out)) }
      }
    },
  )
}

@Composable
private fun AdminCheck?.label(): String =
  stringResource(
    when (this) {
      null -> Res.string.admin_checking
      is AdminCheck.Admin -> Res.string.admin_confirmed
      AdminCheck.NotAdmin -> Res.string.admin_not_admin
      AdminCheck.SignedOut -> Res.string.admin_refused
      AdminCheck.Unreachable -> Res.string.admin_unreachable
    }
  )
