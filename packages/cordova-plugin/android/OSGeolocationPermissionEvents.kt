package com.outsystems.plugins.osgeolocation

/**
 * Sealed class containing the Location permission events
 */
sealed class OSGeolocationPermissionEvents {
    data object Granted: OSGeolocationPermissionEvents()
    data object NotGranted: OSGeolocationPermissionEvents()
}