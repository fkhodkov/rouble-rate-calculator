package info.fkhodkov.rates.android.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RateDao {
    @Query(
        """
        SELECT * FROM rates
        WHERE currency = :currency AND rateDate BETWEEN :from AND :to
        ORDER BY rateDate
        """,
    )
    fun loadRates(currency: String, from: String, to: String): List<RateEntity>

    @Query("SELECT * FROM coverage WHERE currency = :currency ORDER BY startDate")
    fun loadCoverage(currency: String): List<CoverageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRates(rates: List<RateEntity>)

    @Query("DELETE FROM coverage WHERE currency = :currency")
    fun deleteCoverage(currency: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCoverage(ranges: List<CoverageEntity>)
}
