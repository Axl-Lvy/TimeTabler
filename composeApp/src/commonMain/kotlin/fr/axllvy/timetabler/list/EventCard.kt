package fr.axllvy.timetabler.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.axllvy.timetabler.data.serverBaseUrl
import fr.axllvy.timetabler.ui.headliners
import fr.axllvy.timetabler.ui.hours
import fr.axllvy.timetabler.wire.EventDto

/**
 * One event of the list: poster, title, venue and hours, first artists, and its timetable status.
 */
@Composable
fun EventCard(event: EventDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
  ) {
    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Poster(event, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            event.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Badge("lineup")
        }
        Text(
          "${event.venue.name} · ${hours(event.startsAt, event.endsAt)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (event.lineup.isNotEmpty()) {
          Text(
            headliners(event.lineup),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** The event's poster from the server, or a gradient while there is none. */
@Composable
fun Poster(event: EventDto, modifier: Modifier = Modifier) {
  val gradient =
    Brush.linearGradient(
      listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )
  Box(modifier.background(gradient)) {
    event.posterPath?.let {
      AsyncImage(
        model = serverBaseUrl() + it,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
      )
    }
  }
}

@Composable
private fun Badge(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
      Modifier.padding(start = 6.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 6.dp, vertical = 1.dp),
  )
}
