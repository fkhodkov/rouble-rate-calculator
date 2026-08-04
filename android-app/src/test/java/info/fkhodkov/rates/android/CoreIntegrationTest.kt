package info.fkhodkov.rates.android

import info.fkhodkov.rates.core.Period
import info.fkhodkov.rates.core.PeriodUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreIntegrationTest {
    @Test
    fun androidModuleUsesSharedPeriodLogic() {
        val end = LocalDate.of(2026, 8, 3)

        assertEquals(LocalDate.of(2026, 5, 3), Period(3, PeriodUnit.MONTH).startDate(end))
    }
}
