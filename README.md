# Rouble rate calculator

A dependency-free Java command-line program that downloads official exchange-rate
history from the Bank of Russia and calculates arithmetic averages for the last
1, 3, 6, and 12 months.

The result is expressed as Russian roubles per one unit of the selected currency.
Only rates actually published by the CBR are averaged; weekends and other dates
without a newly published rate are not filled in.

## Requirements

- Java 17 or newer
- Maven 3.8 or newer
- Internet access to `www.cbr.ru`

## Build and run

```bash
mvn package
java -jar target/rouble-rate-calculator-1.0.0.jar
```

USD is used by default. A currency and an inclusive end date can be supplied:

```bash
java -jar target/rouble-rate-calculator-1.0.0.jar EUR 2026-07-31
```

Run with `--help` to see the command syntax.

Data source: the Bank of Russia `XML_daily.asp` and `XML_dynamic.asp` endpoints.
