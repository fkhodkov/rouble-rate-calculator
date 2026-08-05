package info.fkhodkov.rates.android.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import info.fkhodkov.rates.core.DateRange
import info.fkhodkov.rates.core.Rate
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRateStoreTest {
    private lateinit var database: RateDatabase
    private lateinit var store: RoomRateStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomRateStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storesRatesAndMergesHistoricalCoverage() {
        store.storeDownload(
            "USD",
            DateRange(date(1), date(3)),
            date(10),
            listOf(Rate(date(1), BigDecimal("75.1"))),
        )
        store.storeDownload(
            "USD",
            DateRange(date(4), date(6)),
            date(10),
            listOf(Rate(date(6), BigDecimal("76.2"))),
        )

        assertEquals(
            listOf(
                Rate(date(1), BigDecimal("75.1")),
                Rate(date(6), BigDecimal("76.2")),
            ),
            store.loadRates("USD", date(1), date(6)),
        )
        assertEquals(emptyList<DateRange>(), store.missingRanges("USD", date(1), date(6), date(11)))
    }

    @Test
    fun keepsTodayRefreshableWhilePersistingHistoricalCoverage() {
        store.storeDownload(
            "EUR",
            DateRange(date(8), date(11)),
            date(10),
            listOf(Rate(date(10), BigDecimal("90.0"))),
        )

        assertEquals(
            listOf(DateRange(date(11), date(11))),
            store.missingRanges("EUR", date(8), date(11), date(11)),
        )
    }

    private fun date(day: Int) = LocalDate.of(2026, 8, day)
}
