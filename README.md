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

- CLI: JDK 25 or newer and Maven 3.8 or newer
- Android: JDK 17, Android SDK 34, and Android Build Tools 35.0.0
- Internet access to `www.cbr.ru`

The Maven CLI and Android builds currently require different active JDKs. The
CLI is compiled with Java 25. The Android build uses Gradle 8.9 and AGP 8.7,
whose recommended JDK is 17. Gradle 8.9 supports running on Java 17 through 22,
but this project is tested with Java 17; it cannot run on Java 25.

Set `JAVA_HOME` per command, or switch JDKs with your preferred version manager:

```bash
JAVA_HOME=/path/to/jdk-25 mvn test
JAVA_HOME=/path/to/jdk-17 ./gradlew test assembleDebug
```

In Android Studio, set the Gradle JDK to 17 under the Gradle settings. This is
independent of the JDK selected in a terminal for Maven or GraalVM.

## Build and run

```bash
mvn package
java -jar rate-cli/target/rouble-rate-calculator-1.0.0.jar
```

All parameters are named and optional. The defaults are USD, yesterday's date in
Moscow, and a three-month period:

```bash
java -jar rate-cli/target/rouble-rate-calculator-1.0.0.jar --currency EUR --end-date 2026-07-31
java -jar rate-cli/target/rouble-rate-calculator-1.0.0.jar --periods 3m,7d,1w,1y
java -jar rate-cli/target/rouble-rate-calculator-1.0.0.jar -c EUR -e 2026-07-31 -p 3m,7d,1w,1y
java -jar rate-cli/target/rouble-rate-calculator-1.0.0.jar -c EUR --today
java -jar rate-cli/target/rouble-rate-calculator-1.0.0.jar -c EUR --start-date 2026-01-01
java -jar rate-cli/target/rouble-rate-calculator-1.0.0.jar -s 2026-01-01 -e 2026-06-30
java -jar rate-cli/target/rouble-rate-calculator-1.0.0.jar -s 2026-01-01 -p 3m
```

Both `--name value` and `--name=value` forms are accepted. Period units are `d`
for days, `w` for weeks, `m` for months and `y` for years. Short forms are `-c`
for currency, `-e` for end date, and `-p` for periods. Argument parsing is
provided by Picocli.

Use `-t` or `--today` to print only the currently effective official rate. The
output includes the CBR effective date, which may differ around weekends and
holidays. Today's mode cannot be combined with date or period options.

Use `-s` or `--start-date` for an explicit interval:

- Start only: from the start date through yesterday in Moscow.
- Start and end: the inclusive interval between those dates.
- Start and one period: from the start date through start plus that period.
- Start, end, and period together: rejected as ambiguous.

Run with `--help` to see the command syntax.

CLI output and help are available in English and Russian. By default the app
uses the system language when it is Russian and falls back to English for other
locales. Select a language explicitly with `-l en`, `--language ru`, or the
corresponding `--language=...` form. Option names, currency codes, ISO input
dates, and period syntax remain language-independent.

Run the unit tests with:

```bash
mvn test
```

## Native executable with GraalVM CE

Install GraalVM Community Edition 25 with the `native-image` tool, make it the
active JDK, and run:

```bash
mvn -Pnative clean package
./rate-cli/target/rouble-rate-calculator
```

The native executable accepts the same named options:

```bash
./rate-cli/target/rouble-rate-calculator --currency EUR --periods 3m,7d,1w,1y
```

Data source: the Bank of Russia `XML_daily.asp` and `XML_dynamic.asp` endpoints.

## Android app

The Android application lives in `android-app` and consumes the same `rate-core`
and `rate-cbr` sources through the Gradle multi-project build. Its Compose screen
accepts a three-letter currency and one or more comma-separated periods, then
loads their averages from CBR using OkHttp. It also supports an explicit
start/end interval, a start date plus one period, a start date through yesterday,
and the currently effective official rate. Dates use `YYYY-MM-DD`. The defaults
are USD and three months through yesterday. Normalized rates and downloaded
coverage are persisted in a Room database; historical cache coverage has the
same semantics as the CLI's SQLite cache. Date fields support both ISO text
entry and a calendar picker. The last successful inputs and result persist
between launches, allowing the app to reopen directly on the previous result
and refresh it with one tap. Android saved state also preserves in-progress UI
state across activity or process recreation.
The Android UI follows the device language and includes complete English and
Russian resources, localized result dates and numbers, and pluralized rate
counts.

Open the repository root in Android Studio, or build from a terminal with:

```bash
./gradlew test assembleDebug
```

With an emulator or device connected, run the Room and Compose instrumentation
tests with:

```bash
./gradlew connectedDebugAndroidTest
```

The debug APK is written to
`android-app/build/outputs/apk/debug/android-app-debug.apk`. The current scaffold
uses Android SDK 34, Build Tools 35.0.0, Java 17, and an Android API 26 minimum.

## Modules

- `rate-core` is a standalone Java 17, JDK-only library containing the data model,
  calculations, use cases, and the `ExchangeRateSource` and `ExchangeRateStore`
  contracts. It is intended to be shared with Android.
- `rate-cbr` is a Java 17 library containing the shared CBR XML parser and its raw
  transport interface.
- `rate-cli` contains Picocli presentation, Java HTTP, the SQLite store, and
  GraalVM native-image configuration.
- `android-app` contains Kotlin/Compose UI, OkHttp transport, and Room storage.
