package com.outsystems.plugins.osgeolocation

import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaInterface
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CordovaWebView
import org.apache.cordova.PermissionHelper
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import com.outsystems.plugins.osgeolocation.controller.OSGLOCController
import com.outsystems.plugins.osgeolocation.model.OSGLOCException
import com.outsystems.plugins.osgeolocation.model.OSGLOCLocationOptions
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Cordova bridge, inherits from CordovaPlugin
 */
class OSGeolocation : CordovaPlugin() {

    private lateinit var controller: OSGLOCController
    private val gson by lazy { Gson() }

    // for permissions
    private lateinit var permissionsFlow: MutableSharedFlow<OSGeolocationPermissionEvents>
    private lateinit var coroutineScope: CoroutineScope

    companion object {
        private const val LOCATION_PERMISSIONS_REQUEST_CODE = 22332
        private const val ID = "id"
        private const val TIMEOUT = "timeout"
        private const val MAXIMUM_AGE = "maximumAge"
        private const val ENABLE_HIGH_ACCURACY = "enableHighAccuracy"
    }

    override fun initialize(cordova: CordovaInterface, webView: CordovaWebView) {
        super.initialize(cordova, webView)

        coroutineScope = CoroutineScope(Dispatchers.Main)
        val activityLauncher = cordova.activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            coroutineScope.launch {
                controller.onResolvableExceptionResult(result.resultCode)
            }
        }

        this.controller = OSGLOCController(
            LocationServices.getFusedLocationProviderClient(cordova.context),
            activityLauncher
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        when (action) {
            "getCurrentPosition" -> {
                getCurrentPosition(args, callbackContext)
            }
            "watchPosition" -> {
                watchPosition(args, callbackContext)
            }
            "clearWatch" -> {
                clearWatch(args, callbackContext)
            }
        }
        return true
    }

    /**
     * Calls the getCurrentPosition method of OSGeolocationController to get the device's geolocation
     * @param args JSONArray that contains the parameters to parse (e.g. timeout)
     * @param callbackContext CallbackContext the method should return to
     */
    private fun getCurrentPosition(args: JSONArray, callbackContext: CallbackContext) {
        val options: JSONObject
        try {
            options = args.getJSONObject(0)
        } catch (e: Exception) {
            callbackContext.sendError(OSGeolocationErrors.INVALID_INPUT_GET_POSITION)
            return
        }

        coroutineScope.launch {
            handleLocationPermission(callbackContext) {
                val locationOptions = OSGLOCLocationOptions(
                    options.getLong(TIMEOUT),
                    options.getLong(MAXIMUM_AGE),
                    options.getBoolean(ENABLE_HIGH_ACCURACY))

                val locationResult = controller.getCurrentPosition(cordova.activity, locationOptions)

                if (locationResult.isSuccess) {
                    callbackContext.sendSuccess(JSONObject(gson.toJson(locationResult.getOrNull())))
                } else {
                    handleErrors(locationResult.exceptionOrNull(), callbackContext)
                }
            }
        }
    }

    /**
     * Calls the addWatch method of OSGeolocationController to start watching the device's geolocation
     * @param args JSONArray that contains the parameters to parse (e.g. timeout)
     * @param callbackContext CallbackContext the method should return to
     */
    private fun watchPosition(args: JSONArray, callbackContext: CallbackContext) {
        val options: JSONObject
        try {
            options = args.getJSONObject(0)
        } catch (e: Exception) {
            callbackContext.sendError(OSGeolocationErrors.INVALID_INPUT_WATCH_POSITION)
            return
        }
        val watchId = options.getString(ID)

        coroutineScope.launch {
            handleLocationPermission(callbackContext) {
                val locationOptions = OSGLOCLocationOptions(
                    timeout = options.getLong(TIMEOUT),
                    maximumAge = options.getLong(MAXIMUM_AGE),
                    enableHighAccuracy = options.getBoolean(ENABLE_HIGH_ACCURACY),
                )

                controller.addWatch(cordova.activity, locationOptions, watchId).collect { result ->
                    result.onSuccess { locationList ->
                        locationList.forEach { locationResult ->
                            callbackContext.sendSuccess(
                                result = JSONObject(gson.toJson(locationResult)),
                                keepCallback = true
                            )
                        }
                    }
                    result.onFailure { exception ->
                        handleErrors(exception, callbackContext)
                    }
                }
            }
        }

    }

    /**
     * Helper function to handle errors from getCurrentPosition and watchPosition
     * @param exception Throwable exception to handle
     * @param callbackContext CallbackContext to use when sending the error callback
     */
    private fun handleErrors(
        exception: Throwable?,
        callbackContext: CallbackContext
    ) {
        when (exception) {
            is OSGLOCException.OSGLOCRequestDeniedException -> {
                callbackContext.sendError(OSGeolocationErrors.LOCATION_ENABLE_REQUEST_DENIED)
            }

            is OSGLOCException.OSGLOCSettingsException -> {
                callbackContext.sendError(OSGeolocationErrors.LOCATION_SETTINGS_ERROR)
            }

            is OSGLOCException.OSGLOCInvalidTimeoutException -> {
                callbackContext.sendError(OSGeolocationErrors.INVALID_TIMEOUT)
            }

            is OSGLOCException.OSGLOCGoogleServicesException -> {
                if (exception.resolvable) {
                    callbackContext.sendError(OSGeolocationErrors.GOOGLE_SERVICES_RESOLVABLE)
                } else {
                    callbackContext.sendError(OSGeolocationErrors.GOOGLE_SERVICES_ERROR)
                }
            }

            is OSGLOCException.OSGLOCLocationRetrievalTimeoutException -> {
                callbackContext.sendError(OSGeolocationErrors.GET_LOCATION_TIMEOUT)
            }

            else -> {
                callbackContext.sendError(OSGeolocationErrors.GET_LOCATION_GENERAL)
            }
        }
    }

    /**
     * Calls the addWatch method of OSGeolocationController to stop watching the device's geolocation
     * @param args JSONArray that contains the parameters to parse (e.g. timeout)
     * @param callbackContext CallbackContext the method should return to
     */
    private fun clearWatch(args: JSONArray, callbackContext: CallbackContext) {
        val options: JSONObject
        try {
            options = args.getJSONObject(0)
        } catch (e: Exception) {
            callbackContext.sendError(OSGeolocationErrors.INVALID_INPUT_CLEAR_WATCH)
            return
        }
        val id = options.optString(ID)
        if (id.isNullOrBlank()) {
            callbackContext.sendError(OSGeolocationErrors.WATCH_ID_NOT_PROVIDED)
            return
        }
        val watchCleared = controller.clearWatch(id)
        if (watchCleared) {
            callbackContext.sendSuccess()
        } else {
            callbackContext.sendError(OSGeolocationErrors.WATCH_ID_NOT_FOUND)
        }
    }

    /**
     * Extension function to return a successful plugin result
     * @param result JSONObject with the JSON content to return, or null if there's no json data
     * @param keepCallback whether the callback should be kept or not. By default, false
     */
    private fun CallbackContext.sendSuccess(result: JSONObject? = null, keepCallback: Boolean = false) {
        val pluginResult = if (result != null) {
            PluginResult(PluginResult.Status.OK, result)
        } else {
            PluginResult(PluginResult.Status.OK)
        }
        pluginResult.keepCallback = keepCallback
        this.sendPluginResult(pluginResult)
    }

    /**
     * Extension function to return a unsuccessful plugin result
     * @param error error class representing the error to return, containing a code and message
     */
    private fun CallbackContext.sendError(error: OSGeolocationErrors.ErrorInfo) {
        val pluginResult = PluginResult(
            PluginResult.Status.ERROR,
            JSONObject().apply {
                put("code", error.code)
                put("message", error.message)
            }
        )
        this.sendPluginResult(pluginResult)
    }

    /**
     * Helper function to handle the location permission request using a Flow
     * @param callbackContext CallbackContext to use in case an error should be returned
     * @param onLocationGranted callback to use in case permissions are granted
     */
    private suspend fun handleLocationPermission(callbackContext: CallbackContext, onLocationGranted: suspend () -> Unit) {
        permissionsFlow = MutableSharedFlow(replay = 1)

        // first, we request permissions if necessary
        if (hasLocationPermissions()) {
            permissionsFlow.emit(OSGeolocationPermissionEvents.Granted)
        } else { // request necessary permissions
            requestLocationPermissions()
        }

        // collect the flow to handle permission request result
        permissionsFlow.collect { permissionEvent ->
            if (permissionEvent == OSGeolocationPermissionEvents.Granted) {
                onLocationGranted()
            } else {
                callbackContext.sendError(OSGeolocationErrors.LOCATION_PERMISSIONS_DENIED)
            }
        }
    }

    /**
     * Helper function to determine Location permission state
     * @return Boolean indicating if permissions are granted or not
     */
    private fun hasLocationPermissions(): Boolean {
        for (permission in listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            if (!PermissionHelper.hasPermission(this, permission)) {
                return false
            }
        }
        return true
    }

    /**
     * Helper function to request location permissions
     */
    private fun requestLocationPermissions() {
        PermissionHelper.requestPermissions(
            this,
            LOCATION_PERMISSIONS_REQUEST_CODE,
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            ).toTypedArray()
        )
    }

    override fun onRequestPermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSIONS_REQUEST_CODE) {
            coroutineScope.launch {
                permissionsFlow.emit(
                    if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                        OSGeolocationPermissionEvents.Granted
                    } else {
                        OSGeolocationPermissionEvents.NotGranted
                    }
                )
            }
        }
    }

}