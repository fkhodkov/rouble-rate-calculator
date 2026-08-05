package info.fkhodkov.rates.android.storage

import androidx.room.Entity

@Entity(tableName = "coverage", primaryKeys = ["currency", "startDate", "endDate"])
data class CoverageEntity(
    val currency: String,
    val startDate: String,
    val endDate: String,
)
