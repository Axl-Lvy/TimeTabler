package fr.axllvy.timetabler.data.room

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

internal actual fun databaseBuilder(): RoomDatabase.Builder<TimeTablerDatabase> =
  Room.databaseBuilder<TimeTablerDatabase>(
    File(System.getProperty("user.home"), ".timetabler.db").absolutePath
  )
