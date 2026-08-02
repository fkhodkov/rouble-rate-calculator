package info.fkhodkov.rates;

import java.math.BigDecimal;
import java.time.LocalDate;

record CurrentRate(String currency, LocalDate effectiveDate, BigDecimal rublesPerUnit) {
}
