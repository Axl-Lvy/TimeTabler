package fr.axllvy.timetabler.data.room

import androidx.room.Room
import androidx.room.RoomDatabase
import fr.axllvy.timetabler.AndroidContext

internal actual fun databaseBuilder(): RoomDatabase.Builder<TimeTablerDatabase> {
  val context = AndroidContext.context
  return Room.databaseBuilder<TimeTablerDatabase>(
    context,
    context.getDatabasePath("timetabler.db").absolutePath,
  )
}
