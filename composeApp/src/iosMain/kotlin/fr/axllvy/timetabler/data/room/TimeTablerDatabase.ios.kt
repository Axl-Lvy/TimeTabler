package fr.axllvy.timetabler.data.room

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual fun databaseBuilder(): RoomDatabase.Builder<TimeTablerDatabase> =
  Room.databaseBuilder<TimeTablerDatabase>(documentDirectory() + "/timetabler.db")

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String =
  requireNotNull(
    NSFileManager.defaultManager
      .URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
      )
      ?.path
  )
