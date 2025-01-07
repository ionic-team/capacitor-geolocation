package com.outsystems.plugins.osgeolocation.model

/**
 * Sealed class with exceptions that the library's functions can throw
 */
sealed class OSGLOCException(message: String, cause: Throwable?) : Exception(message, cause) {
    class OSGLOCSettingsException(
        message: String, cause: Throwable? = null
    ) : OSGLOCException(message, cause)
    class OSGLOCRequestDeniedException(
        message: String, cause: Throwable? = null
    ) : OSGLOCException(message, cause)
    class OSGLOCGoogleServicesException(
        val resolvable: Boolean,
        message: String, cause: Throwable? = null
    ) : OSGLOCException(message, cause)
    class OSGLOCInvalidTimeoutException(
        message: String, cause: Throwable? = null
    ) : OSGLOCException(message, cause)
    class OSGLOCLocationRetrievalTimeoutException(
        message: String, cause: Throwable? = null
    ) : OSGLOCException(message, cause)
}