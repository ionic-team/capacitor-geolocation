package com.outsystems.plugins.osgeolocation.controller

import android.app.Activity
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.outsystems.plugins.osgeolocation.model.OSGLOCException
import com.outsystems.plugins.osgeolocation.model.OSGLOCLocationOptions
import com.outsystems.plugins.osgeolocation.model.OSGLOCLocationResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

/**
 * Entry point in OSGeolocationLib-Android
 *
 */
class OSGLOCController(
    fusedLocationClient: FusedLocationProviderClient,
    activityLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val helper: OSGLOCServiceHelper = OSGLOCServiceHelper(
        fusedLocationClient,
        activityLauncher
    )
) {
    private lateinit var resolveLocationSettingsResultFlow: MutableSharedFlow<Result<Unit>>
    private val locationCallbacks: MutableMap<String, LocationCallback> = mutableMapOf()
    private val watchIdsBlacklist: MutableList<String> = mutableListOf()

    /**
     * Obtains the device's location using FusedLocationProviderClient.
     * Tries to obtain the last retrieved location, and then gets a fresh one if necessary.
     * @param activity the Android activity from which the location request is being triggered
     * @param options OSLocationOptions object with the options to obtain the location with (e.g. timeout)
     * @return Result<OSLocationResult> object with either the location or an exception to be handled by the caller
     */
    suspend fun getCurrentPosition(
        activity: Activity,
        options: OSGLOCLocationOptions
    ): Result<OSGLOCLocationResult> {
        try {
            val checkResult: Result<Unit> =
                checkLocationPreconditions(activity, options, isSingleLocationRequest = true)
            return if (checkResult.isFailure) {
                Result.failure(
                    checkResult.exceptionOrNull() ?: NullPointerException()
                )
            } else {
                val location = helper.getCurrentLocation(options)
                return Result.success(location.toOSLocationResult())
            }
        } catch (exception: Exception) {
            Log.d(LOG_TAG, "Error fetching location: ${exception.message}")
            return Result.failure(exception)
        }
    }

    /**
     * Function to be called by the client after returning from the activity
     * that is launched when resolving the ResolvableApiException in checkLocationSettings,
     * that prompts the user to enable the location if it is disabled.
     * @param resultCode to determine if the user enabled the location when prompted
     */
    suspend fun onResolvableExceptionResult(resultCode: Int) {
        resolveLocationSettingsResultFlow.emit(
            if (resultCode == Activity.RESULT_OK)
                Result.success(Unit)
            else
                Result.failure(
                    OSGLOCException.OSGLOCRequestDeniedException(
                        message = "Request to enable location denied."
                    )
                )
        )
    }

    /**
     * Checks if location services are enabled
     * @param context Context to use when determining if location is enabled
     */
    fun areLocationServicesEnabled(context: Context): Boolean {
        return LocationManagerCompat.isLocationEnabled(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
    }

    /**
     * Creates a callback for location updates.
     * @param activity the Android activity from which the location request is being triggered
     * @param options location request options to use
     * @param watchId a unique id identifying the watch
     * @return Flow in which location updates will be emitted
     */
    fun addWatch(
        activity: Activity,
        options: OSGLOCLocationOptions,
        watchId: String
    ): Flow<Result<List<OSGLOCLocationResult>>> = callbackFlow {

        try {
            val checkResult: Result<Unit> =
                checkLocationPreconditions(activity, options, isSingleLocationRequest = true)
            if (checkResult.isFailure) {
                trySend(
                    Result.failure(checkResult.exceptionOrNull() ?: NullPointerException())
                )
            } else {
                locationCallbacks[watchId] = object : LocationCallback() {
                    override fun onLocationResult(location: LocationResult) {
                        if (checkWatchInBlackList(watchId)) {
                            return
                        }
                        val locations = location.locations.map { currentLocation ->
                            currentLocation.toOSLocationResult()
                        }
                        trySend(Result.success(locations))
                    }
                }.also {
                    helper.requestLocationUpdates(options, it)
                }
            }
        } catch (exception: Exception) {
            Log.d(LOG_TAG, "Error requesting location updates: ${exception.message}")
            trySend(Result.failure(exception))
        }

        awaitClose {
            clearWatch(watchId)
        }
    }

    /**
     * Clears a watch by removing its location update request
     * @param id the watch id
     * @return true if watch was cleared, false if watch was not found
     */
    fun clearWatch(id: String): Boolean = clearWatch(id, addToBlackList = true)

    /**
     * Checks if all preconditions for retrieving location are met
     * @param activity the Android activity from which the location request is being triggered
     * @param options location request options to use
     * @param isSingleLocationRequest true if request is for a single location, false if for location updates
     */
    private suspend fun checkLocationPreconditions(
        activity: Activity,
        options: OSGLOCLocationOptions,
        isSingleLocationRequest: Boolean
    ): Result<Unit> {
        // check timeout
        if (options.timeout <= 0) {
            return Result.failure(
                OSGLOCException.OSGLOCInvalidTimeoutException(
                    message = "Timeout needs to be a positive value."
                )
            )
        }

        val playServicesResult = helper.checkGooglePlayServicesAvailable(activity)
        if (playServicesResult.isFailure) {
            return Result.failure(playServicesResult.exceptionOrNull() ?: NullPointerException())
        }

        resolveLocationSettingsResultFlow = MutableSharedFlow()
        val locationSettingsChecked = helper.checkLocationSettings(
            activity,
            options,
            interval = if (isSingleLocationRequest) 0 else options.timeout
        )

        return if (locationSettingsChecked) {
            Result.success(Unit)
        } else {
            resolveLocationSettingsResultFlow.first()
        }
    }

    /**
     * Clears a watch by removing its location update request
     * @param id the watch id
     * @param addToBlackList whether or not the watch id should go in blacklist if not found
     * @return true if watch was cleared, false if watch was not found
     */
    private fun clearWatch(id: String, addToBlackList: Boolean): Boolean {
        val locationCallback = locationCallbacks.remove(key = id)
        return if (locationCallback != null) {
            helper.removeLocationUpdates(locationCallback)
            true
        } else {
            if (addToBlackList) {
                // It is possible that clearWatch is being called before requestLocationUpdates is triggered (e.g. very low timeout on JavaScript side.)
                //  add to a blacklist in order to remove the location callback in the future
                watchIdsBlacklist.add(id)
            }
            false
        }
    }

    /**
     * Checks if the current watch is in the blacklist
     *
     * If the watch is in the blacklist, location updates for that watch should be removed.
     * @param watchId the unique id of the watch
     * @return true if watch is in blacklist, false otherwise
     */
    private fun checkWatchInBlackList(watchId: String): Boolean {
        if (watchIdsBlacklist.contains(watchId)) {
            val cleared = clearWatch(watchId, addToBlackList = false)
            if (cleared) {
                watchIdsBlacklist.remove(watchId)
            }
            return true
        }
        return false
    }

    /**
     * Extension function to convert Location object into OSLocationResult object
     * @return OSLocationResult object
     */
    private fun Location.toOSLocationResult(): OSGLOCLocationResult = OSGLOCLocationResult(
        latitude = this.latitude,
        longitude = this.longitude,
        altitude = this.altitude,
        accuracy = this.accuracy,
        altitudeAccuracy = if (OSGLOCBuildConfig.getAndroidSdkVersionCode() >= Build.VERSION_CODES.O) this.verticalAccuracyMeters else null,
        heading = this.bearing,
        speed = this.speed,
        timestamp = this.time
    )

    companion object {
        private const val LOG_TAG = "OSGeolocationController"
    }

}