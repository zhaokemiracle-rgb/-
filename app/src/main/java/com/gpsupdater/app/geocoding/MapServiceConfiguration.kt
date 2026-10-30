package com.gpsupdater.app.geocoding

import android.content.Context
import com.gpsupdater.app.BuildConfig
import com.gpsupdater.app.geo.CoordinateSystem
import com.gpsupdater.app.geo.CoordinateTransform
import com.gpsupdater.app.model.Coordinates
import org.osmdroid.api.IGeoPoint
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

object MapServiceConfiguration {
    /**
     * Applies the user's selected base map (and its transparent label layer, if any) to [map].
     * Returns the adapter that converts between stored WGS-84 coordinates and map coordinates;
     * use it for every GeoPoint that goes into or comes out of [map].
     */
    fun apply(context: Context, map: MapView): MapCoordinateAdapter {
        val source = effectiveSource(context)
        val key = source.keyKind?.let { MapSettings.key(context, it) }.orEmpty()

        map.overlays.removeAll { it is LabelTilesOverlay }
        map.setTileSource(tileSource(source, source.baseUrl, key, layerName = "base"))
        source.labelUrl?.let { labelUrl ->
            val provider = MapTileProviderBasic(
                context.applicationContext,
                tileSource(source, labelUrl, key, layerName = "label"),
            )
            map.overlays.add(0, LabelTilesOverlay(provider, context))
        }
        map.maxZoomLevel = source.maxZoom.toDouble()
        if (map.zoomLevelDouble > map.maxZoomLevel) map.controller.setZoom(map.maxZoomLevel)
        map.invalidate()
        return MapCoordinateAdapter(source.datum)
    }

    /** The selected source, falling back to AutoNavi when a keyed source has no key configured. */
    fun effectiveSource(context: Context): MapSource {
        val selected = MapSettings.mapSource(context)
        val kind = selected.keyKind ?: return selected
        return if (MapSettings.key(context, kind).isBlank()) MapSource.DEFAULT else selected
    }

    fun attribution(context: Context): String {
        val source = effectiveSource(context)
        return if (source == MapSource.OPENSTREETMAP) BuildConfig.MAP_ATTRIBUTION else source.attribution
    }

    private fun tileSource(
        source: MapSource,
        template: String,
        key: String,
        layerName: String,
    ): OnlineTileSourceBase {
        val urlTemplate = if (source == MapSource.OPENSTREETMAP) BuildConfig.TILE_URL_TEMPLATE else template
        val extension = if (urlTemplate.substringBefore('?').endsWith(".jpg")) ".jpg" else ".png"
        // The name doubles as osmdroid's on-disk cache folder, so it must be unique per layer.
        return object : OnlineTileSourceBase(
            "${source.id}_$layerName",
            3,
            source.maxZoom,
            256,
            extension,
            arrayOf(urlTemplate),
            attribution(source),
        ) {
            override fun getTileURLString(mapTileIndex: Long): String {
                val x = MapTileIndex.getX(mapTileIndex)
                val y = MapTileIndex.getY(mapTileIndex)
                val subdomain = if (source.subdomains.isEmpty()) {
                    ""
                } else {
                    source.subdomains[Math.floorMod(x + y, source.subdomains.size)]
                }
                return urlTemplate
                    .replace("{z}", MapTileIndex.getZoom(mapTileIndex).toString())
                    .replace("{x}", x.toString())
                    .replace("{y}", y.toString())
                    .replace("{s}", subdomain)
                    .replace("{key}", key)
            }
        }
    }

    private fun attribution(source: MapSource): String =
        if (source == MapSource.OPENSTREETMAP) BuildConfig.MAP_ATTRIBUTION else source.attribution

    /** Marker type so a re-apply can remove the previous label layer without touching other overlays. */
    private class LabelTilesOverlay(provider: MapTileProviderBasic, context: Context) :
        TilesOverlay(provider, context) {
        init {
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            loadingLineColor = android.graphics.Color.TRANSPARENT
        }
    }
}

/**
 * Converts between the app's canonical WGS-84 [Coordinates] and the datum the current tiles are
 * drawn in. Outside mainland China both directions are the identity.
 */
class MapCoordinateAdapter(val mapDatum: CoordinateSystem) {
    fun toMap(coordinates: Coordinates): GeoPoint {
        val shifted = CoordinateTransform.convert(coordinates, CoordinateSystem.WGS84, mapDatum)
        return GeoPoint(shifted.latitude, shifted.longitude)
    }

    fun fromMap(point: IGeoPoint): Coordinates =
        CoordinateTransform.convert(
            Coordinates(
                point.latitude.coerceIn(-90.0, 90.0),
                point.longitude.coerceIn(-180.0, 180.0),
            ),
            mapDatum,
            CoordinateSystem.WGS84,
        )
}
