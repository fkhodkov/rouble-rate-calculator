package info.fkhodkov.rates.android.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RateEntity::class, CoverageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RateDatabase : RoomDatabase() {
    abstract fun rateDao(): RateDao
}
