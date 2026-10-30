package com.gpsupdater.app.geocoding

import android.content.Context
import com.gpsupdater.app.BuildConfig
import com.gpsupdater.app.geo.CoordinateSystem

/**
 * Map and coordinate preferences for mainland-China use.
 *
 * Keys typed in the app take precedence over the build-time defaults
 * (`GPS_TIANDITU_KEY` / `GPS_AMAP_WEB_KEY` in gradle.properties or the environment).
 */
object MapSettings {
    private const val FILE_NAME = "map_settings"
    private const val KEY_MAP_SOURCE = "map_source"
    private const val KEY_INPUT_SYSTEM = "input_coordinate_system"
    private const val KEY_TIANDITU = "tianditu_key"
    private const val KEY_AMAP_WEB = "amap_web_key"

    fun mapSource(context: Context): MapSource =
        MapSource.fromId(preferences(context).getString(KEY_MAP_SOURCE, null))

    fun setMapSource(context: Context, source: MapSource) {
        preferences(context).edit().putString(KEY_MAP_SOURCE, source.id).apply()
    }

    /** Coordinate system of the latitude/longitude text fields on the main screen. */
    fun inputCoordinateSystem(context: Context): CoordinateSystem =
        preferences(context).getString(KEY_INPUT_SYSTEM, null)
            ?.let { name -> CoordinateSystem.entries.firstOrNull { it.name == name } }
            ?: CoordinateSystem.WGS84

    fun setInputCoordinateSystem(context: Context, system: CoordinateSystem) {
        preferences(context).edit().putString(KEY_INPUT_SYSTEM, system.name).apply()
    }

    fun key(context: Context, kind: MapKeyKind): String {
        val stored = preferences(context).getString(prefKey(kind), null)?.trim().orEmpty()
        if (stored.isNotEmpty()) return stored
        return when (kind) {
            MapKeyKind.TIANDITU -> BuildConfig.TIANDITU_KEY
            MapKeyKind.AMAP_WEB_SERVICE -> BuildConfig.AMAP_WEB_KEY
        }.trim()
    }

    /** The key the user typed in the app (empty when only the build-time default applies). */
    fun storedKey(context: Context, kind: MapKeyKind): String =
        preferences(context).getString(prefKey(kind), null)?.trim().orEmpty()

    fun setKey(context: Context, kind: MapKeyKind, value: String) {
        preferences(context).edit().putString(prefKey(kind), value.trim()).apply()
    }

    private fun prefKey(kind: MapKeyKind): String = when (kind) {
        MapKeyKind.TIANDITU -> KEY_TIANDITU
        MapKeyKind.AMAP_WEB_SERVICE -> KEY_AMAP_WEB
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
