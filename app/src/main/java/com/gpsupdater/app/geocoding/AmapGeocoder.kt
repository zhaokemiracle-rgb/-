package com.gpsupdater.app.geocoding

import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Place search for mainland China through the AutoNavi (高德) Web Service API.
 * Requires a "Web服务" key from https://console.amap.com/ (free tier is enough for personal use).
 * All results are returned in WGS-84.
 */
class AmapGeocoder(
    private val keyProvider: () -> String,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : GeocodingService, AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = ConcurrentHashMap<String, List<GeocodingResult>>()

    override fun search(
        country: CountryOption,
        city: String,
        district: String,
        languageTag: String,
        callback: (Result<List<GeocodingResult>>) -> Unit,
    ) {
        val cityQuery = city.trim()
        val keyword = district.trim()
        run("search|$cityQuery|$keyword", callback) {
            if (keyword.isBlank()) {
                district(cityQuery)
            } else {
                place(keyword, cityQuery).ifEmpty { district(keyword) }
            }
        }
    }

    override fun suggestCities(
        country: CountryOption,
        cityQuery: String,
        languageTag: String,
        callback: (Result<List<GeocodingResult>>) -> Unit,
    ) {
        val query = cityQuery.trim()
        run("city|$query", callback) { district(query).take(SUGGESTION_LIMIT) }
    }

    override fun suggestDistricts(
        country: CountryOption,
        city: String,
        districtQuery: String,
        languageTag: String,
        callback: (Result<List<GeocodingResult>>) -> Unit,
    ) {
        val cityQuery = city.trim()
        val query = districtQuery.trim()
        run("tips|$cityQuery|$query", callback) { tips(query, cityQuery).take(SUGGESTION_LIMIT) }
    }

    override fun close() {
        executor.shutdownNow()
    }

    private fun district(keywords: String): List<GeocodingResult> {
        if (keywords.isBlank()) return emptyList()
        return AmapResponseParser.parseDistricts(
            get(
                "v3/config/district",
                "keywords" to keywords,
                "subdistrict" to "0",
                "extensions" to "base",
            ),
        )
    }

    private fun place(keywords: String, city: String): List<GeocodingResult> =
        AmapResponseParser.parsePois(
            get(
                "v3/place/text",
                "keywords" to keywords,
                "city" to city,
                "citylimit" to city.isNotBlank().toString(),
                "offset" to RESULT_LIMIT.toString(),
                "page" to "1",
                "extensions" to "base",
            ),
        )

    private fun tips(keywords: String, city: String): List<GeocodingResult> {
        if (keywords.isBlank()) return emptyList()
        return AmapResponseParser.parseTips(
            get(
                "v3/assistant/inputtips",
                "keywords" to keywords,
                "city" to city,
                "citylimit" to city.isNotBlank().toString(),
                "datatype" to "all",
            ),
        )
    }

    private fun run(
        cacheKey: String,
        callback: (Result<List<GeocodingResult>>) -> Unit,
        block: () -> List<GeocodingResult>,
    ) {
        cache[cacheKey]?.let {
            callback(Result.success(it))
            return
        }
        executor.execute {
            val result = runCatching(block)
            result.getOrNull()?.let { cache[cacheKey] = it }
            mainHandler.post { callback(result) }
        }
    }

    private fun get(path: String, vararg params: Pair<String, String>): String {
        val key = keyProvider().trim()
        if (key.isEmpty()) throw IOException("AutoNavi Web Service key is not configured")
        val builder = Uri.parse(BASE_URL).buildUpon().appendEncodedPath(path)
        params.filter { it.second.isNotEmpty() }.forEach { (name, value) ->
            builder.appendQueryParameter(name, value)
        }
        builder.appendQueryParameter("output", "JSON")
        builder.appendQueryParameter("key", key)
        val connection = URL(builder.build().toString()).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                throw IOException("AutoNavi returned HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE_URL = "https://restapi.amap.com/"
        private const val RESULT_LIMIT = 20
        private const val SUGGESTION_LIMIT = 8
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}

/**
 * Uses AutoNavi for China when a Web Service key is configured, and Nominatim (OpenStreetMap)
 * everywhere else or when no key is set.
 */
class ChinaAwareGeocoder(
    private val china: AmapGeocoder,
    private val global: NominatimGeocoder,
    private val hasChinaKey: () -> Boolean,
) : GeocodingService, AutoCloseable {
    private fun pick(country: CountryOption): GeocodingService =
        if (country.code.equals("CN", ignoreCase = true) && hasChinaKey()) china else global

    override fun search(
        country: CountryOption,
        city: String,
        district: String,
        languageTag: String,
        callback: (Result<List<GeocodingResult>>) -> Unit,
    ) = pick(country).search(country, city, district, languageTag, callback)

    override fun suggestCities(
        country: CountryOption,
        cityQuery: String,
        languageTag: String,
        callback: (Result<List<GeocodingResult>>) -> Unit,
    ) = pick(country).suggestCities(country, cityQuery, languageTag, callback)

    override fun suggestDistricts(
        country: CountryOption,
        city: String,
        districtQuery: String,
        languageTag: String,
        callback: (Result<List<GeocodingResult>>) -> Unit,
    ) = pick(country).suggestDistricts(country, city, districtQuery, languageTag, callback)

    override fun close() {
        china.close()
        global.close()
    }
}
