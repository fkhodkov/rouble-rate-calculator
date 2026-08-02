package info.fkhodkov.rates.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Rate(LocalDate date, BigDecimal rublesPerUnit) {
}
