package fr.axllvy.timetabler.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.axllvy.timetabler.data.EventStoreLocal
import fr.axllvy.timetabler.data.EventSync
import fr.axllvy.timetabler.data.SyncState
import fr.axllvy.timetabler.domain.EventId
import fr.axllvy.timetabler.list.Poster
import fr.axllvy.timetabler.ui.hours
import fr.axllvy.timetabler.ui.shortDate
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/** The stored copy of one event, null when the store does not have it. */
private class Stored(val event: EventDto?)

/**
 * One event: poster, date and venue, ticket links, and its lineup; "Event not found" once the store
 * is known not to have it.
 */
@Composable
fun EventScreen(id: EventId, onBack: () -> Unit) {
  val store = koinInject<EventStoreLocal>()
  val sync = koinInject<EventSync>()
  val stored by remember(id) { store.event(id).map(::Stored) }.collectAsState(initial = null)
  val syncState by sync.state.collectAsState()
  var hasEverSynced by remember { mutableStateOf<Boolean?>(null) }
  LaunchedEffect(syncState) { hasEverSynced = store.hasEverSynced() }

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
    IconButton(onClick = onBack) {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
    val event = stored?.event
    when {
      event != null -> EventDetails(event)
      stored == null || syncState == SyncState.SYNCING -> Unit
      hasEverSynced == true -> Message("Event not found")
      hasEverSynced == false && syncState == SyncState.OFFLINE ->
        Message("Connect once to load events")
    }
  }
}

@Composable
private fun Message(text: String) {
  Text(
    text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventDetails(event: EventDto) {
  val uriHandler = LocalUriHandler.current
  Poster(event, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)))
  Text(
    event.title,
    style = MaterialTheme.typography.headlineSmall,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(top = 10.dp),
  )
  Text(
    "${shortDate(event.nightDate)} · ${hours(event.startsAt, event.endsAt)} · ${event.venue.name}",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(vertical = 8.dp),
  ) {
    event.listings.forEach { listing ->
      Button(onClick = { uriHandler.openUri(listing.url) }) {
        Text("Tickets on ${listing.source.label()}${listing.status.suffix()}")
      }
    }
  }
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      "No set times yet. They usually land in the promoter's story the day before.",
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(10.dp),
    )
  }
  if (event.lineup.isNotEmpty()) {
    Text(
      "Lineup",
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(top = 12.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      event.lineup.forEach { artist -> AssistChip(onClick = {}, label = { Text(artist) }) }
    }
  }
}

private fun ListingStatus.suffix(): String =
  when (this) {
    ListingStatus.SOLD_OUT -> " (sold out)"
    ListingStatus.CANCELLED -> " (cancelled)"
    ListingStatus.ON_SALE,
    ListingStatus.UNKNOWN -> ""
  }

private fun Source.label(): String =
  when (this) {
    Source.DICE -> "DICE"
    Source.SHOTGUN -> "Shotgun"
  }
