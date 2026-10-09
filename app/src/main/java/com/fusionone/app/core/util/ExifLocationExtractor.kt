package com.fusionone.app.core.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class PhotoLocation(
    val hasGps: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val dateTimeOriginal: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val placeName: String? = null
)

@Singleton
class ExifLocationExtractor @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    suspend fun extract(imageUri: Uri): PhotoLocation = withContext(Dispatchers.IO) {
        val exif = openExif(imageUri) ?: return@withContext PhotoLocation(hasGps = false)

        val latLong = FloatArray(2)
        val hasLatLong = exif.getLatLong(latLong)

        val altitude = exif.getAltitude(Double.NaN).takeIf { !it.isNaN() }

        val result = PhotoLocation(
            hasGps = hasLatLong,
            latitude = if (hasLatLong) latLong[0].toDouble() else null,
            longitude = if (hasLatLong) latLong[1].toDouble() else null,
            altitudeMeters = altitude,
            dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
            cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
            cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)
        )

        if (hasLatLong) {
            val placeName = reverseGeocode(result.latitude!!, result.longitude!!)
            result.copy(placeName = placeName)
        } else {
            result
        }
    }

    private fun openExif(uri: Uri): ExifInterface? = runCatching {
        val stream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { ExifInterface(it) }
    }.getOrNull()

    private fun reverseGeocode(lat: Double, lon: Double): String? = runCatching {
        @Suppress("DEPRECATION")
        val geocoder = android.location.Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        addresses?.firstOrNull()?.let { addr ->
            listOfNotNull(addr.locality, addr.adminArea, addr.countryName)
                .joinToString(", ")
                .ifBlank { null }
        }
    }.getOrNull()
}
