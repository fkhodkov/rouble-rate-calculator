package info.fkhodkov.rates.android.storage

import info.fkhodkov.rates.core.CoverageRanges
import info.fkhodkov.rates.core.DateRange
import info.fkhodkov.rates.core.ExchangeRateStore
import info.fkhodkov.rates.core.Rate
import java.math.BigDecimal
import java.time.LocalDate

class RoomRateStore(
    private val database: RateDatabase,
) : ExchangeRateStore {
    private val dao = database.rateDao()

    override fun missingRanges(
        currency: String,
        requestedFrom: LocalDate,
        requestedTo: LocalDate,
        today: LocalDate,
    ): List<DateRange> = CoverageRanges.missing(
        requestedFrom,
        requestedTo,
        today,
        coverage(currency),
    )

    override fun loadRates(
        currency: String,
        from: LocalDate,
        to: LocalDate,
    ): List<Rate> = dao.loadRates(currency, from.toString(), to.toString()).map {
        Rate(LocalDate.parse(it.rateDate), BigDecimal(it.rublesPerUnit))
    }

    override fun storeDownload(
        currency: String,
        downloaded: DateRange,
        historicalThrough: LocalDate,
        rates: List<Rate>,
    ) {
        database.runInTransaction {
            dao.insertRates(rates.map {
                RateEntity(currency, it.date().toString(), it.rublesPerUnit().toPlainString())
            })
            if (!downloaded.from().isAfter(historicalThrough)) {
                val permanent = DateRange(
                    downloaded.from(),
                    minOf(downloaded.to(), historicalThrough),
                )
                replaceCoverage(currency, permanent)
            }
        }
    }

    private fun coverage(currency: String): List<DateRange> = dao.loadCoverage(currency).map {
        DateRange(LocalDate.parse(it.startDate), LocalDate.parse(it.endDate))
    }

    private fun replaceCoverage(currency: String, addition: DateRange) {
        val merged = CoverageRanges.merge(coverage(currency) + addition)
        dao.deleteCoverage(currency)
        dao.insertCoverage(merged.map {
            CoverageEntity(currency, it.from().toString(), it.to().toString())
        })
    }

    override fun close() = Unit
}
