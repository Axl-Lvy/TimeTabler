package fr.axllvy.timetabler.data.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Room database of the native clients. */
@Database(entities = [EventEntity::class, MetaEntity::class], version = 1, exportSchema = false)
@ConstructedBy(TimeTablerDatabaseConstructor::class)
abstract class TimeTablerDatabase : RoomDatabase() {
  /** Access to the event tables. */
  abstract fun eventDao(): EventDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object TimeTablerDatabaseConstructor : RoomDatabaseConstructor<TimeTablerDatabase> {
  override fun initialize(): TimeTablerDatabase
}

/** Builder for the on-disk database of this platform. */
internal expect fun databaseBuilder(): RoomDatabase.Builder<TimeTablerDatabase>

/** Finishes [builder] with the bundled SQLite driver; schema changes recreate the database. */
fun buildDatabase(builder: RoomDatabase.Builder<TimeTablerDatabase>): TimeTablerDatabase =
  builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()
