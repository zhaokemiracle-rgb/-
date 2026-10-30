package com.gpsupdater.app.geocoding

import com.gpsupdater.app.geo.CoordinateTransform
import com.gpsupdater.app.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AmapResponseParserTest {
    @Test
    fun parsesDistrictsAndConvertsToWgs84() {
        val body = """
            {"status":"1","info":"OK","infocode":"10000","count":"1",
             "districts":[{"citycode":"0571","adcode":"330100","name":"杭州市",
             "center":"120.210792,30.246026","level":"city","districts":[]}]}
        """.trimIndent()
        val results = AmapResponseParser.parseDistricts(body)
        assertEquals(1, results.size)
        assertEquals("杭州市", results[0].name)
        assertEquals(11.0, results[0].zoomLevel, 0.0)
        val expected = CoordinateTransform.gcj02ToWgs84(Coordinates(30.246026, 120.210792))
        assertEquals(expected, results[0].coordinates)
        // WGS-84 should be a few hundred metres south-west of the GCJ-02 input in Hangzhou.
        assertTrue(results[0].coordinates.longitude < 120.210792)
    }

    @Test
    fun parsesPoisAndToleratesEmptyArrayFields() {
        val body = """
            {"status":"1","count":"2","info":"OK","infocode":"10000","pois":[
              {"name":"西湖","location":"120.141375,30.236863","pname":"浙江省","cityname":"杭州市",
               "adname":"西湖区","address":[]},
              {"name":"broken","location":[],"pname":"浙江省"}
            ]}
        """.trimIndent()
        val results = AmapResponseParser.parsePois(body)
        assertEquals(1, results.size)
        assertEquals("西湖", results[0].name)
        assertEquals("西湖 · 浙江省杭州市西湖区", results[0].displayName)
    }

    @Test
    fun parsesTipsSkippingEntriesWithoutLocation() {
        val body = """
            {"status":"1","count":"2","info":"OK","infocode":"10000","tips":[
              {"name":"杭州东站","district":"浙江省杭州市上城区","location":"120.212842,30.290580"},
              {"name":"杭州","district":[],"location":[]}
            ]}
        """.trimIndent()
        val results = AmapResponseParser.parseTips(body)
        assertEquals(listOf("杭州东站"), results.map { it.name })
    }

    @Test(expected = IOException::class)
    fun throwsOnApiError() {
        AmapResponseParser.parsePois("""{"status":"0","info":"INVALID_USER_KEY","infocode":"10001"}""")
    }

    @Test
    fun rejectsMalformedLocations() {
        assertNull(AmapResponseParser.parseLocation(""))
        assertNull(AmapResponseParser.parseLocation("120.1"))
        assertNull(AmapResponseParser.parseLocation("abc,30"))
        assertNull(AmapResponseParser.parseLocation("200,30"))
    }
}
