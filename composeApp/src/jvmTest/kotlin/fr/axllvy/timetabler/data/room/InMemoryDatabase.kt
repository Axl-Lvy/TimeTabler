package fr.axllvy.timetabler.data.room

import androidx.room.Room

/** A fresh in-memory database for tests. */
fun inMemoryDatabase(): TimeTablerDatabase =
  buildDatabase(Room.inMemoryDatabaseBuilder<TimeTablerDatabase>())
