package com.outsystems.plugins.osgeolocation.controller

import android.annotation.SuppressLint
import android.app.Activity
import android.location.Location
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.outsystems.plugins.osgeolocation.model.OSGLOCException
import com.outsystems.plugins.osgeolocation.model.OSGLOCLocationOptions
import kotlinx.coroutines.tasks.await

/**
 * Helper class that wraps the functionality of FusedLocationProviderClient
 */
class OSGLOCServiceHelper(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val activityLauncher: ActivityResultLauncher<IntentSenderRequest>
) {
    /**
     * Checks if location is on, as well as other conditions for retrieving device location
     * @param activity the Android activity from which the location request is being triggered
     * @param options location request options to use
     * @param interval interval for requesting location updates; use 0 if meant to retrieve a single location
     * @return true if location was checked and is on, false if it requires user to resolve issue (e.g. turn on location)
     *          If false, the result is returned in `resolveLocationSettingsResultFlow`
     * @throws [OSGLOCException.OSGLOCSettingsException] if an error occurs that is not resolvable by user
     */
    internal suspend fun checkLocationSettings(
        activity: Activity,
        options: OSGLOCLocationOptions,
        interval: Long
    ): Boolean {

        val request = LocationRequest.Builder(
            if (options.enableHighAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            interval
        ).build()

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(request)
        val client = LocationServices.getSettingsClient(activity)

        try {
            client.checkLocationSettings(builder.build()).await()
            return true
        } catch (e: ResolvableApiException) {

            // Show the dialog to enable location by calling startResolutionForResult(),
            // and then handle the result in onActivityResult
            val resolutionBuilder: IntentSenderRequest.Builder =
                IntentSenderRequest.Builder(e.resolution)
            val resolution: IntentSenderRequest = resolutionBuilder.build()

            activityLauncher.launch(resolution)
        } catch (e: Exception) {
            throw OSGLOCException.OSGLOCSettingsException(
                message = "There is an error with the location settings.",
                cause = e
            )
        }
        return false
    }

    /**
     * Checks if the device has google play services, required to use [FusedLocationProviderClient]
     * @param activity the Android activity from which the location request is being triggered
     * @return true if google play services is available, false otherwise
     */
    internal fun checkGooglePlayServicesAvailable(activity: Activity): Result<Unit> {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val status = googleApiAvailability.isGooglePlayServicesAvailable(activity)

        return if (status != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(status)) {
                googleApiAvailability.getErrorDialog(activity, status, 1)?.show()
                sendResultWithGoogleServicesException(
                    resolvable = true,
                    message = "Google Play Services error user resolvable."
                )
            } else {
                sendResultWithGoogleServicesException(
                    resolvable = false,
                    message = "Google Play Services error."
                )
            }
        } else {
            Result.success(Unit)
        }
    }

    /**
     * Returns a Result object containing an OSGLOCException.OSGLOCGoogleServicesException exception with the given
     * resolvable and message values
     * @param resolvable whether or not the exception is resolvable
     * @param message message to include in the exception
     * @return Result object with the exception to return
     *
     */
    private fun sendResultWithGoogleServicesException(resolvable: Boolean, message: String): Result<Unit> {
        return Result.failure(
            OSGLOCException.OSGLOCGoogleServicesException(
                resolvable = resolvable,
                message = message
            )
        )
    }

    /**
     * Obtains a fresh device location.
     * @param options location request options to use
     * @return Location object representing the location
     */
    @SuppressLint("MissingPermission")
    internal suspend fun getCurrentLocation(options: OSGLOCLocationOptions): Location {

        val locationRequest = CurrentLocationRequest.Builder()
            .setPriority(if (options.enableHighAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(options.maximumAge)
            .setDurationMillis(options.timeout)
            .build()

        return fusedLocationClient.getCurrentLocation(locationRequest, null).await()
            ?: throw OSGLOCException.OSGLOCLocationRetrievalTimeoutException(
                message = "Location request timed out"
            )
    }

    /**
     * Requests updates of device location.
     *
     * Locations returned in callback associated with watchId
     * @param options location request options to use
     * @param locationCallback the [LocationCallback] to receive location updates in
     */
    @SuppressLint("MissingPermission")
    internal fun requestLocationUpdates(
        options: OSGLOCLocationOptions,
        locationCallback: LocationCallback
    ) {
        val locationRequest = LocationRequest.Builder(options.timeout).apply {
            setMaxUpdateAgeMillis(options.maximumAge)
            setPriority(if (options.enableHighAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            if (options.minUpdateInterval != null) {
                setMinUpdateIntervalMillis(options.minUpdateInterval)
            }
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    /**
     * Remove location updates for a specific callback.
     *
     * This method only triggers the removal, it does not await to see if the callback was actually removed.
     *
     * @param locationCallback the location callback to be removed
     */
    internal fun removeLocationUpdates(
        locationCallback: LocationCallback
    ) {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}