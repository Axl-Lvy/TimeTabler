package fr.axllvy.timetabler.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.axllvy.timetabler.data.EventStoreLocal
import fr.axllvy.timetabler.data.EventSync
import fr.axllvy.timetabler.data.SyncState
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.domain.tonight
import fr.axllvy.timetabler.ui.nightLabel
import kotlin.time.Clock
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Every upcoming event, grouped by night, with search and quick filters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(onOpen: (EventId) -> Unit) {
  val store = koinInject<EventStoreLocal>()
  val sync = koinInject<EventSync>()
  val scope = rememberCoroutineScope()
  val events by store.events().collectAsState(initial = null)
  val syncState by sync.state.collectAsState()
  var hasEverSynced by remember { mutableStateOf(true) }
  var query by rememberSaveable { mutableStateOf("") }
  var filter by rememberSaveable { mutableStateOf(QuickFilter.ALL) }
  LaunchedEffect(syncState) { hasEverSynced = store.hasEverSynced() }

  val now = Clock.System.now()
  val groups = remember(events, query, filter, now.epochSeconds / 60) {
    filterEvents(events.orEmpty(), query, filter, now)
  }

  Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
    Text(
      "TimeTabler",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      placeholder = { Text("Search artist, venue, event…") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
      Chip("Tonight", filter == QuickFilter.TONIGHT) {
        filter = if (filter == QuickFilter.TONIGHT) QuickFilter.ALL else QuickFilter.TONIGHT
      }
      Chip("This weekend", filter == QuickFilter.WEEKEND) {
        filter = if (filter == QuickFilter.WEEKEND) QuickFilter.ALL else QuickFilter.WEEKEND
      }
    }
    PullToRefreshBox(
      isRefreshing = syncState == SyncState.SYNCING && events != null,
      onRefresh = { scope.launch { sync.syncOnce() } },
      modifier = Modifier.fillMaxSize(),
    ) {
      when {
        events?.isEmpty() == true && !hasEverSynced && syncState == SyncState.OFFLINE ->
          Message("Connect once to load events")
        groups.isEmpty() && events != null && syncState != SyncState.SYNCING ->
          Message(if (query.isBlank()) "No events" else "No events match \"$query\"")
        else ->
          LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            val tonight = tonight(now)
            groups.forEach { group ->
              item(key = group.night.toString()) {
                Text(
                  nightLabel(group.night, tonight).uppercase(),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
              }
              items(group.events, key = { it.id.value }) { event ->
                EventCard(event, onClick = { onOpen(event.id) })
              }
            }
          }
      }
    }
  }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun Message(text: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
