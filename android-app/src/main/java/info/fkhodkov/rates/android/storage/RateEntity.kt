package info.fkhodkov.rates.android.storage

import androidx.room.Entity

@Entity(tableName = "rates", primaryKeys = ["currency", "rateDate"])
data class RateEntity(
    val currency: String,
    val rateDate: String,
    val rublesPerUnit: String,
)
