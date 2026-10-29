package fr.axllvy.timetabler.domain

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/** Identifier of one real-world event, shared by all of its source listings. */
@JvmInline @Serializable value class EventId(val value: String)

/** Server-assigned position in the event feed; a higher revision is a later change. */
@JvmInline
@Serializable
value class Revision(val value: Long) : Comparable<Revision> {

  override fun compareTo(other: Revision): Int = value.compareTo(other.value)

  companion object {
    /** The revision before any change, where a client starts its first sync. */
    val ZERO = Revision(0)
  }
}
