package com.outsystems.plugins.osgeolocation

/**
 * Object with plugin errors
 */
object OSGeolocationErrors {

    private fun formatErrorCode(number: Int): String {
        return "OS-PLUG-GLOC-" + number.toString().padStart(4, '0')
    }

    data class ErrorInfo(
        val code: String,
        val message: String
    )

    val GOOGLE_SERVICES_RESOLVABLE = ErrorInfo(
        code = formatErrorCode(1),
        message = "Google Play Services error user resolvable."
    )

    val GOOGLE_SERVICES_ERROR = ErrorInfo(
        code = formatErrorCode(2),
        message = "Google Play Services error."
    )

    val INVALID_INPUT_GET_POSITION = ErrorInfo(
        code = formatErrorCode(3),
        message = "The input parameters for GetLocation aren't valid."
    )

    val INVALID_INPUT_WATCH_POSITION = ErrorInfo(
        code = formatErrorCode(4),
        message = "The input parameters for WatchPosition aren't valid."
    )

    val INVALID_INPUT_CLEAR_WATCH = ErrorInfo(
        code = formatErrorCode(5),
        message = "The input parameters for ClearWatch aren't valid."
    )

    val GET_LOCATION_TIMEOUT = ErrorInfo(
        code = formatErrorCode(6),
        message = "Could not obtain location in time. Try with a higher timeout."
    )

    val GET_LOCATION_GENERAL = ErrorInfo(
        code = formatErrorCode(7),
        message = "There was en error trying to obtain the location."
    )

    val LOCATION_PERMISSIONS_DENIED = ErrorInfo(
        code = formatErrorCode(8),
        message = "Location permission request was denied."
    )

    val LOCATION_ENABLE_REQUEST_DENIED = ErrorInfo(
        code = formatErrorCode(9),
        message = "Request to enable location denied."
    )

    val LOCATION_SETTINGS_ERROR = ErrorInfo(
        code = formatErrorCode(10),
        message = "Location settings error."
    )

    val INVALID_TIMEOUT = ErrorInfo(
        code = formatErrorCode(11),
        message = "Timeout needs to be a positive value."
    )

    val WATCH_ID_NOT_FOUND = ErrorInfo(
        code = formatErrorCode(12),
        message = "WatchId not found"
    )

    val WATCH_ID_NOT_PROVIDED = ErrorInfo(
        code = formatErrorCode(13),
        message = "WatchId needs to be provided."
    )

}