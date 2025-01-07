package com.outsystems.plugins.osgeolocation.model

/**
 * Data class representing the object returned in getCurrentPosition and watchPosition
 */
data class OSGLOCLocationResult(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val altitudeAccuracy: Float? = null,
    val heading: Float,
    val speed: Float,
    val timestamp: Long
)
