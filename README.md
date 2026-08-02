# Rouble rate calculator

A self-contained Java command-line program that downloads official exchange-rate
history from the Bank of Russia and calculates arithmetic averages for requested
day, week, month or year periods.

The result is expressed as Russian roubles per one unit of the selected currency.
Only rates actually published by the CBR are averaged; weekends and other dates
without a newly published rate are not filled in.

Downloaded rates are cached in SQLite at
`~/.cache/rouble-rate-calculator/rates.db`. Set the `ROUBLE_RATE_DB` environment
variable to use another database file. The cache records both rates and fetched
date coverage, so weekends and holidays do not look like missing data. When a
request has several gaps, the calculator makes one CBR request covering the
first through last gap. Historical coverage is reused permanently; today and
future dates remain refreshable.

## Requirements

- Java 25 or newer
- Maven 3.8 or newer
- Internet access to `www.cbr.ru`

## Build and run

```bash
mvn package
java -jar target/rouble-rate-calculator-1.0.0.jar
```

All parameters are named and optional. The defaults are USD, yesterday's date in
Moscow, and a three-month period:

```bash
java -jar target/rouble-rate-calculator-1.0.0.jar --currency EUR --end-date 2026-07-31
java -jar target/rouble-rate-calculator-1.0.0.jar --periods 3m,7d,1w,1y
java -jar target/rouble-rate-calculator-1.0.0.jar -c EUR -e 2026-07-31 -p 3m,7d,1w,1y
java -jar target/rouble-rate-calculator-1.0.0.jar -c EUR --today
```

Both `--name value` and `--name=value` forms are accepted. Period units are `d`
for days, `w` for weeks, `m` for months and `y` for years. Short forms are `-c`
for currency, `-e` for end date, and `-p` for periods. Argument parsing is
provided by Picocli.

Use `-t` or `--today` to print only the currently effective official rate. The
output includes the CBR effective date, which may differ around weekends and
holidays. Today's mode cannot be combined with `--end-date` or `--periods`.

Run with `--help` to see the command syntax.

Run the unit tests with:

```bash
mvn test
```

## Native executable with GraalVM CE

Install GraalVM Community Edition 25 with the `native-image` tool, make it the
active JDK, and run:

```bash
mvn -Pnative clean package
./target/rouble-rate-calculator
```

The native executable accepts the same named options:

```bash
./target/rouble-rate-calculator --currency EUR --periods 3m,7d,1w,1y
```

Data source: the Bank of Russia `XML_daily.asp` and `XML_dynamic.asp` endpoints.

## Structure

- `ExchangeRateApp` is the command-line entry point and formats results.
- `ExchangeRateCalculator` contains fetching, caching, and calculation logic.
- `CbrClient` is the injectable raw-XML client interface.
- `CbrClientImpl` is its production HTTP/TLS implementation.
- `RateCache` manages SQLite persistence and cached date coverage.
