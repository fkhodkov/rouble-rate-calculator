# Rouble rate calculator

A dependency-free Java command-line program that downloads official exchange-rate
history from the Bank of Russia and calculates arithmetic averages for requested
day, week, or month periods.

The result is expressed as Russian roubles per one unit of the selected currency.
Only rates actually published by the CBR are averaged; weekends and other dates
without a newly published rate are not filled in.

## Requirements

- Java 25 or newer
- Maven 3.8 or newer
- Internet access to `www.cbr.ru`

## Build and run

```bash
mvn package
java -jar target/rouble-rate-calculator-1.0.0.jar
```

All parameters are named and optional. The defaults are USD, today's date in
Moscow, and a three-month period:

```bash
java -jar target/rouble-rate-calculator-1.0.0.jar --currency EUR --end-date 2026-07-31
java -jar target/rouble-rate-calculator-1.0.0.jar --periods 3m,7d,1w
```

Both `--name value` and `--name=value` forms are accepted. Period units are
`d` for days, `w` for weeks, and `m` for months.

Run with `--help` to see the command syntax.

## Native executable with GraalVM CE

Install GraalVM Community Edition 25 with the `native-image` tool, make it the
active JDK, and run:

```bash
mvn -Pnative clean package
./target/rouble-rate-calculator
```

The native executable accepts the same named options:

```bash
./target/rouble-rate-calculator --currency EUR --periods 3m,7d,1w
```

Data source: the Bank of Russia `XML_daily.asp` and `XML_dynamic.asp` endpoints.
