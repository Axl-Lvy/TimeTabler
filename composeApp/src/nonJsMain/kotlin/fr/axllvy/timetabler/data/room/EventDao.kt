package fr.axllvy.timetabler.data.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Queries of the local event tables. */
@Dao
interface EventDao {

  @Query("SELECT * FROM event") fun observeAll(): Flow<List<EventEntity>>

  @Query("SELECT * FROM event WHERE id = :id") fun observe(id: String): Flow<EventEntity?>

  @Upsert suspend fun upsertAll(events: List<EventEntity>)

  @Query("DELETE FROM event WHERE id IN (:ids)") suspend fun deleteAll(ids: List<String>)

  @Query("SELECT value FROM meta WHERE `key` = :key") suspend fun meta(key: String): String?

  @Upsert suspend fun setMeta(meta: MetaEntity)

  /** Applies one feed page atomically. */
  @Transaction
  suspend fun applyPage(events: List<EventEntity>, removed: List<String>, revision: Long) {
    upsertAll(events)
    if (removed.isNotEmpty()) deleteAll(removed)
    setMeta(MetaEntity(LAST_REVISION, revision.toString()))
  }

  companion object {
    /** Meta key holding the last applied revision. */
    const val LAST_REVISION = "lastRevision"
  }
}
