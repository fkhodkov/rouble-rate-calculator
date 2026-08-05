package info.fkhodkov.rates.android.network

import info.fkhodkov.rates.cbr.CbrClient
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpCbrClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) : CbrClient {
    override fun downloadDailyRates(): InputStream = execute(
        "https://www.cbr.ru/scripts/XML_daily.asp".toHttpUrl(),
    )

    override fun downloadHistoricalRates(
        currencyId: String,
        from: LocalDate,
        to: LocalDate,
    ): InputStream {
        val url = "https://www.cbr.ru/scripts/XML_dynamic.asp".toHttpUrl().newBuilder()
            .addQueryParameter("date_req1", QUERY_DATE.format(from))
            .addQueryParameter("date_req2", QUERY_DATE.format(to))
            .addQueryParameter("VAL_NM_RQ", currencyId)
            .build()
        return execute(url)
    }

    private fun execute(url: okhttp3.HttpUrl): InputStream {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/xml,text/xml")
            .header("User-Agent", "rouble-rate-calculator-android/1.0")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("CBR HTTP request failed with status ${response.code}")
        }
        return response.body.byteStream()
    }

    private companion object {
        val QUERY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    }
}
