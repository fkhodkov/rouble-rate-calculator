package info.fkhodkov.rates.core;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Rate(LocalDate date, BigDecimal rublesPerUnit) {
}
