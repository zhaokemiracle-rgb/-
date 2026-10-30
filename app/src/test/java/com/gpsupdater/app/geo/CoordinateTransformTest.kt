package com.gpsupdater.app.geo

import com.gpsupdater.app.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CoordinateTransformTest {
    // Reference values from the widely used wandergis/coordtransform implementation.
    private val sample = Coordinates(39.915, 116.404)

    @Test
    fun wgs84ToGcj02MatchesReference() {
        val gcj = CoordinateTransform.wgs84ToGcj02(sample)
        assertEquals(39.91640428150164, gcj.latitude, 1e-9)
        assertEquals(116.41024449916938, gcj.longitude, 1e-9)
    }

    @Test
    fun gcj02ToBd09MatchesReference() {
        val bd = CoordinateTransform.gcj02ToBd09(sample)
        assertEquals(39.92133699351021, bd.latitude, 1e-9)
        assertEquals(116.41036949371029, bd.longitude, 1e-9)
    }

    @Test
    fun bd09ToGcj02MatchesReference() {
        val gcj = CoordinateTransform.bd09ToGcj02(sample)
        assertEquals(39.90865673957631, gcj.latitude, 1e-9)
        assertEquals(116.39762729119315, gcj.longitude, 1e-9)
    }

    @Test
    fun gcj02ToWgs84IsCloseToOneStepApproximation() {
        val wgs = CoordinateTransform.gcj02ToWgs84(sample)
        // The reference library uses a one-step approximation; the exact inverse is within ~1 m.
        assertEquals(39.91359571849836, wgs.latitude, 2e-5)
        assertEquals(116.39775550083061, wgs.longitude, 2e-5)
    }

    @Test
    fun gcj02InverseRoundTripsBelowOneCentimetre() {
        val cities = listOf(
            Coordinates(39.9087, 116.3975), // 北京
            Coordinates(31.2304, 121.4737), // 上海
            Coordinates(22.5431, 114.0579), // 深圳
            Coordinates(30.5728, 104.0668), // 成都
            Coordinates(43.8256, 87.6168), // 乌鲁木齐
            Coordinates(18.2528, 109.5119), // 三亚
            Coordinates(45.8038, 126.5350), // 哈尔滨
        )
        for (wgs in cities) {
            val back = CoordinateTransform.gcj02ToWgs84(CoordinateTransform.wgs84ToGcj02(wgs))
            assertTrue("lat drift at $wgs", abs(back.latitude - wgs.latitude) < 1e-7)
            assertTrue("lng drift at $wgs", abs(back.longitude - wgs.longitude) < 1e-7)
        }
    }

    @Test
    fun offsetInChinaIsHundredsOfMetres() {
        val gcj = CoordinateTransform.wgs84ToGcj02(sample)
        val metres = com.gpsupdater.app.routes.GeoMath.distanceMeters(sample, gcj)
        assertTrue("offset was $metres m", metres in 100.0..1000.0)
    }

    @Test
    fun outsideChinaIsUnchanged() {
        val tokyo = Coordinates(35.6812, 139.7671)
        val paris = Coordinates(48.8566, 2.3522)
        assertEquals(tokyo, CoordinateTransform.wgs84ToGcj02(tokyo))
        assertEquals(tokyo, CoordinateTransform.gcj02ToWgs84(tokyo))
        assertEquals(paris, CoordinateTransform.convert(paris, CoordinateSystem.GCJ02, CoordinateSystem.WGS84))
    }

    @Test
    fun convertBd09RoundTrips() {
        val wgs = Coordinates(31.2304, 121.4737)
        val bd = CoordinateTransform.convert(wgs, CoordinateSystem.WGS84, CoordinateSystem.BD09)
        val back = CoordinateTransform.convert(bd, CoordinateSystem.BD09, CoordinateSystem.WGS84)
        assertEquals(wgs.latitude, back.latitude, 1e-5)
        assertEquals(wgs.longitude, back.longitude, 1e-5)
    }
}
