package info.fkhodkov.rates.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CurrentRate(String currency, LocalDate effectiveDate, BigDecimal rublesPerUnit) {
}
