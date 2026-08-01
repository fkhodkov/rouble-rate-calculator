package info.fkhodkov.rates;

import java.math.BigDecimal;
import java.time.LocalDate;

record Rate(LocalDate date, BigDecimal rublesPerUnit) {
}
