package com.gpsupdater.app.geocoding

import androidx.annotation.StringRes
import com.gpsupdater.app.R
import com.gpsupdater.app.geo.CoordinateSystem

/**
 * Selectable base maps.
 *
 * [datum] is the coordinate system the tiles are drawn in. osmdroid always projects in WGS-84
 * Web Mercator, so for GCJ-02 tiles every point must be shifted before drawing (see
 * [MapCoordinateAdapter]); otherwise the pin lands 100–700 m away from the real place.
 *
 * URL templates use `{x}`, `{y}`, `{z}`, `{s}` (subdomain) and `{key}`.
 */
enum class MapSource(
    val id: String,
    @StringRes val labelRes: Int,
    val datum: CoordinateSystem,
    val baseUrl: String,
    val labelUrl: String?,
    val subdomains: List<String>,
    val maxZoom: Int,
    val attribution: String,
    val keyKind: MapKeyKind?,
) {
    AMAP_VECTOR(
        id = "amap_vector",
        labelRes = R.string.map_source_amap_vector,
        datum = CoordinateSystem.GCJ02,
        baseUrl = "https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
        labelUrl = null,
        subdomains = listOf("1", "2", "3", "4"),
        maxZoom = 18,
        attribution = "© 高德地图 AutoNavi",
        keyKind = null,
    ),
    AMAP_SATELLITE(
        id = "amap_satellite",
        labelRes = R.string.map_source_amap_satellite,
        datum = CoordinateSystem.GCJ02,
        baseUrl = "https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}",
        labelUrl = "https://webst0{s}.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}",
        subdomains = listOf("1", "2", "3", "4"),
        maxZoom = 18,
        attribution = "© 高德地图 AutoNavi",
        keyKind = null,
    ),
    TIANDITU_VECTOR(
        id = "tianditu_vector",
        labelRes = R.string.map_source_tianditu_vector,
        datum = CoordinateSystem.WGS84,
        baseUrl = tiandituUrl("vec"),
        labelUrl = tiandituUrl("cva"),
        subdomains = (0..7).map(Int::toString),
        maxZoom = 18,
        attribution = "© 天地图 国家地理信息公共服务平台",
        keyKind = MapKeyKind.TIANDITU,
    ),
    TIANDITU_SATELLITE(
        id = "tianditu_satellite",
        labelRes = R.string.map_source_tianditu_satellite,
        datum = CoordinateSystem.WGS84,
        baseUrl = tiandituUrl("img"),
        labelUrl = tiandituUrl("cia"),
        subdomains = (0..7).map(Int::toString),
        maxZoom = 18,
        attribution = "© 天地图 国家地理信息公共服务平台",
        keyKind = MapKeyKind.TIANDITU,
    ),
    OPENSTREETMAP(
        id = "osm",
        labelRes = R.string.map_source_osm,
        datum = CoordinateSystem.WGS84,
        baseUrl = "", // resolved from BuildConfig.TILE_URL_TEMPLATE so custom builds keep working
        labelUrl = null,
        subdomains = emptyList(),
        maxZoom = 20,
        attribution = "", // resolved from BuildConfig.MAP_ATTRIBUTION
        keyKind = null,
    ),
    ;

    companion object {
        val DEFAULT = AMAP_VECTOR

        fun fromId(id: String?): MapSource = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Which user-supplied key a map source or geocoder needs. */
enum class MapKeyKind {
    /** 天地图开发者 key (tk)，申请地址 https://console.tianditu.gov.cn/ */
    TIANDITU,

    /** 高德 Web 服务 key，申请地址 https://console.amap.com/ ，用于国内地点搜索 */
    AMAP_WEB_SERVICE,
}

private fun tiandituUrl(layer: String): String =
    "https://t{s}.tianditu.gov.cn/${layer}_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
        "&LAYER=$layer&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles" +
        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk={key}"
