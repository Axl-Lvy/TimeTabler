package fr.axllvy.timetabler.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One stored event; the whole wire event is kept as JSON so the feed shape can grow freely. */
@Entity(tableName = "event")
data class EventEntity(@PrimaryKey val id: String, val revision: Long, val json: String)

/** One key-value setting of the local store, such as the last applied revision. */
@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)
