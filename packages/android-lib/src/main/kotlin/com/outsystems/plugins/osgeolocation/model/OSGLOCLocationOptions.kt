package com.outsystems.plugins.osgeolocation.model

/**
 * Data class representing the options passed to getCurrentPosition and watchPosition
 */
data class OSGLOCLocationOptions(
    val timeout: Long,
    val maximumAge: Long,
    val enableHighAccuracy: Boolean,
    val minUpdateInterval: Long? = null
)
