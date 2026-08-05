package info.fkhodkov.rates.android

import android.app.Application
import androidx.room.Room
import info.fkhodkov.rates.android.network.OkHttpCbrClient
import info.fkhodkov.rates.android.storage.RateDatabase
import info.fkhodkov.rates.android.storage.RoomRateStore
import info.fkhodkov.rates.cbr.CbrRateSource
import info.fkhodkov.rates.core.ExchangeRateCalculator
import java.time.Clock
import java.time.ZoneId

class RateApplication : Application() {
    lateinit var calculator: ExchangeRateCalculator
        private set
    lateinit var stateStore: RateStateStore
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            applicationContext,
            RateDatabase::class.java,
            "rates.db",
        ).build()
        calculator = ExchangeRateCalculator(
            Clock.system(ZoneId.of("Europe/Moscow")),
            { RoomRateStore(database) },
            CbrRateSource(OkHttpCbrClient()),
        )
        stateStore = PreferencesRateStateStore(applicationContext)
    }
}
