package com.gpsupdater.app.geo

import com.gpsupdater.app.model.Coordinates
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geodetic datums used by maps in mainland China.
 *
 * - [WGS84]: GPS / Android LocationManager / GPX / KML / OpenStreetMap / 天地图 (CGCS2000 ≈ WGS-84)
 * - [GCJ02]: "火星坐标"，高德、腾讯、谷歌中国、苹果中国地图
 * - [BD09]:  百度地图
 *
 * The app keeps every stored and injected coordinate in WGS-84. Conversion only happens at the
 * edges: when drawing on a GCJ-02 tile layer, and when the user types coordinates copied from a
 * Chinese map app.
 */
enum class CoordinateSystem {
    WGS84,
    GCJ02,
    BD09,
}

object CoordinateTransform {
    private const val PI = Math.PI
    private const val X_PI = PI * 3000.0 / 180.0

    // Krasovsky 1940 ellipsoid, as used by the GCJ-02 algorithm.
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    private const val INVERSE_TOLERANCE_DEGREES = 1e-10
    private const val INVERSE_MAX_ITERATIONS = 30

    fun convert(coordinates: Coordinates, from: CoordinateSystem, to: CoordinateSystem): Coordinates {
        if (from == to) return coordinates
        val wgs = when (from) {
            CoordinateSystem.WGS84 -> coordinates
            CoordinateSystem.GCJ02 -> gcj02ToWgs84(coordinates)
            CoordinateSystem.BD09 -> gcj02ToWgs84(bd09ToGcj02(coordinates))
        }
        return when (to) {
            CoordinateSystem.WGS84 -> wgs
            CoordinateSystem.GCJ02 -> wgs84ToGcj02(wgs)
            CoordinateSystem.BD09 -> gcj02ToBd09(wgs84ToGcj02(wgs))
        }
    }

    /**
     * Rough mainland-China bounding box. Outside it GCJ-02 equals WGS-84, matching the behaviour
     * of AutoNavi tiles abroad. Hong Kong, Macau and Taiwan use WGS-84 on AutoNavi tiles, but they
     * fall inside this box; the box is the widely used convention and errs on the side of shifting.
     */
    fun isOutOfChina(latitude: Double, longitude: Double): Boolean =
        longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271

    fun wgs84ToGcj02(coordinates: Coordinates): Coordinates {
        val (lat, lng) = coordinates
        if (isOutOfChina(lat, lng)) return coordinates
        val (dLat, dLng) = delta(lat, lng)
        return clamp(lat + dLat, lng + dLng)
    }

    /**
     * Exact inverse of [wgs84ToGcj02] by fixed-point iteration (converges to < 1 cm in a few steps),
     * instead of the common one-step approximation which can be off by 1–2 m.
     */
    fun gcj02ToWgs84(coordinates: Coordinates): Coordinates {
        val (gcjLat, gcjLng) = coordinates
        if (isOutOfChina(gcjLat, gcjLng)) return coordinates
        var lat = gcjLat
        var lng = gcjLng
        repeat(INVERSE_MAX_ITERATIONS) {
            val (dLat, dLng) = if (isOutOfChina(lat, lng)) 0.0 to 0.0 else delta(lat, lng)
            val nextLat = gcjLat - dLat
            val nextLng = gcjLng - dLng
            val converged = abs(nextLat - lat) < INVERSE_TOLERANCE_DEGREES &&
                abs(nextLng - lng) < INVERSE_TOLERANCE_DEGREES
            lat = nextLat
            lng = nextLng
            if (converged) return clamp(lat, lng)
        }
        return clamp(lat, lng)
    }

    fun gcj02ToBd09(coordinates: Coordinates): Coordinates {
        val (lat, lng) = coordinates
        val z = sqrt(lng * lng + lat * lat) + 0.00002 * sin(lat * X_PI)
        val theta = atan2(lat, lng) + 0.000003 * cos(lng * X_PI)
        return clamp(z * sin(theta) + 0.006, z * cos(theta) + 0.0065)
    }

    fun bd09ToGcj02(coordinates: Coordinates): Coordinates {
        val x = coordinates.longitude - 0.0065
        val y = coordinates.latitude - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        return clamp(z * sin(theta), z * cos(theta))
    }

    private fun delta(lat: Double, lng: Double): Pair<Double, Double> {
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return dLat to dLng
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    private fun clamp(lat: Double, lng: Double): Coordinates =
        Coordinates(lat.coerceIn(-90.0, 90.0), lng.coerceIn(-180.0, 180.0))
}
