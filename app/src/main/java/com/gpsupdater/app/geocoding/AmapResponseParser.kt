package com.gpsupdater.app.geocoding

import com.gpsupdater.app.geo.CoordinateSystem
import com.gpsupdater.app.geo.CoordinateTransform
import com.gpsupdater.app.model.Coordinates
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses AutoNavi (高德) Web Service responses. AutoNavi returns GCJ-02 "lng,lat" strings; every
 * result is converted to WGS-84 here so the rest of the app never sees GCJ-02.
 */
internal object AmapResponseParser {
    /** `/v3/config/district` */
    fun parseDistricts(body: String): List<GeocodingResult> {
        val json = checkStatus(body)
        val districts = json.optJSONArray("districts") ?: return emptyList()
        return collect(districts) { item ->
            val name = text(item, "name")
            val coordinates = parseLocation(text(item, "center")) ?: return@collect null
            GeocodingResult(
                name = name,
                displayName = name,
                coordinates = coordinates,
                zoomLevel = zoomForLevel(text(item, "level")),
            )
        }
    }

    /** `/v3/place/text` */
    fun parsePois(body: String): List<GeocodingResult> {
        val json = checkStatus(body)
        val pois = json.optJSONArray("pois") ?: return emptyList()
        return collect(pois) { item ->
            val name = text(item, "name")
            val coordinates = parseLocation(text(item, "location")) ?: return@collect null
            val region = listOf(text(item, "pname"), text(item, "cityname"), text(item, "adname"))
                .filter(String::isNotBlank)
                .distinct()
                .joinToString("")
            val address = text(item, "address")
            GeocodingResult(
                name = name,
                displayName = listOf(name, region + address).filter(String::isNotBlank).joinToString(" · "),
                coordinates = coordinates,
                zoomLevel = POI_ZOOM,
            )
        }
    }

    /** `/v3/assistant/inputtips` */
    fun parseTips(body: String): List<GeocodingResult> {
        val json = checkStatus(body)
        val tips = json.optJSONArray("tips") ?: return emptyList()
        return collect(tips) { item ->
            val name = text(item, "name")
            val coordinates = parseLocation(text(item, "location")) ?: return@collect null
            val district = text(item, "district")
            GeocodingResult(
                name = name,
                displayName = listOf(name, district).filter(String::isNotBlank).joinToString(" · "),
                coordinates = coordinates,
                zoomLevel = POI_ZOOM,
            )
        }
    }

    /** Parses an AutoNavi GCJ-02 `"lng,lat"` string into WGS-84 coordinates. */
    fun parseLocation(value: String): Coordinates? {
        val parts = value.split(',')
        if (parts.size != 2) return null
        val longitude = parts[0].trim().toDoubleOrNull() ?: return null
        val latitude = parts[1].trim().toDoubleOrNull() ?: return null
        val gcj = runCatching { Coordinates(latitude, longitude) }.getOrNull() ?: return null
        return CoordinateTransform.convert(gcj, CoordinateSystem.GCJ02, CoordinateSystem.WGS84)
    }

    private fun checkStatus(body: String): JSONObject {
        val json = JSONObject(body)
        if (json.optString("status") != "1") {
            val info = json.optString("info").ifBlank { "UNKNOWN_ERROR" }
            val code = json.optString("infocode")
            throw IOException("AutoNavi error $code: $info")
        }
        return json
    }

    private inline fun collect(
        array: JSONArray,
        map: (JSONObject) -> GeocodingResult?,
    ): List<GeocodingResult> {
        val results = mutableListOf<GeocodingResult>()
        val seen = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val result = map(item) ?: continue
            if (result.name.isBlank()) continue
            val key = "${result.name}|${result.coordinates.formattedLatitude()},${result.coordinates.formattedLongitude()}"
            if (seen.add(key)) results += result
        }
        return results
    }

    /** AutoNavi returns `[]` instead of `""` for empty string fields. */
    private fun text(item: JSONObject, key: String): String {
        val value = item.opt(key)
        return if (value is String) value.trim() else ""
    }

    private fun zoomForLevel(level: String): Double = when (level) {
        "country" -> 5.0
        "province" -> 7.0
        "city" -> 11.0
        "district" -> 13.0
        "street" -> 15.0
        else -> 12.0
    }

    private const val POI_ZOOM = 17.0
}
