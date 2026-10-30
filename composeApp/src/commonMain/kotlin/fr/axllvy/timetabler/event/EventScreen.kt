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
import fr.axllvy.timetabler.ui.dateNames
import fr.axllvy.timetabler.ui.hours
import fr.axllvy.timetabler.ui.shortDate
import fr.axllvy.timetabler.wire.EventDto
import fr.axllvy.timetabler.wire.ListingDto
import fr.axllvy.timetabler.wire.ListingStatus
import fr.axllvy.timetabler.wire.Source
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import timetabler.composeapp.generated.resources.Res
import timetabler.composeapp.generated.resources.event_back
import timetabler.composeapp.generated.resources.event_lineup
import timetabler.composeapp.generated.resources.event_no_set_times
import timetabler.composeapp.generated.resources.event_not_found
import timetabler.composeapp.generated.resources.event_not_offline
import timetabler.composeapp.generated.resources.event_source_unknown
import timetabler.composeapp.generated.resources.event_tickets_on
import timetabler.composeapp.generated.resources.event_tickets_on_cancelled
import timetabler.composeapp.generated.resources.event_tickets_on_sold_out
import timetabler.composeapp.generated.resources.events_connect_once

/** The stored copy of one event, null when the store does not have it. */
private class Stored(val event: EventDto?)

/**
 * One event: poster, date and venue, ticket links, and its lineup; "Event not found" once a sync
 * finished without it, an offline hint while the server is unreachable.
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
      Icon(
        Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(Res.string.event_back),
      )
    }
    val event = stored?.event
    when {
      event != null -> EventDetails(event)
      stored == null || hasEverSynced == null -> Unit
      syncState == SyncState.IDLE && hasEverSynced == true ->
        Message(stringResource(Res.string.event_not_found))
      syncState == SyncState.OFFLINE && hasEverSynced == true ->
        Message(stringResource(Res.string.event_not_offline))
      syncState == SyncState.OFFLINE -> Message(stringResource(Res.string.events_connect_once))
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
    "${shortDate(event.nightDate, dateNames())} · ${hours(event.startsAt, event.endsAt)} · ${event.venue.name}",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(vertical = 8.dp),
  ) {
    event.listings.forEach { listing ->
      Button(onClick = { uriHandler.openUri(listing.url) }) { Text(ticketsLabel(listing)) }
    }
  }
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      stringResource(Res.string.event_no_set_times),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(10.dp),
    )
  }
  if (event.lineup.isNotEmpty()) {
    Text(
      stringResource(Res.string.event_lineup),
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(top = 12.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      event.lineup.forEach { artist -> AssistChip(onClick = {}, label = { Text(artist) }) }
    }
  }
}

@Composable
private fun ticketsLabel(listing: ListingDto): String {
  val source =
    when (listing.source) {
      Source.DICE -> "DICE"
      Source.SHOTGUN -> "Shotgun"
      Source.UNKNOWN -> stringResource(Res.string.event_source_unknown)
    }
  val label =
    when (listing.status) {
      ListingStatus.SOLD_OUT -> Res.string.event_tickets_on_sold_out
      ListingStatus.CANCELLED -> Res.string.event_tickets_on_cancelled
      ListingStatus.ON_SALE,
      ListingStatus.UNKNOWN -> Res.string.event_tickets_on
    }
  return stringResource(label, source)
}
