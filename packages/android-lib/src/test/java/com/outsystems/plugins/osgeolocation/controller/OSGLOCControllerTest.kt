package com.outsystems.plugins.osgeolocation.controller

import android.app.Activity
import android.app.PendingIntent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import app.cash.turbine.test
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationSettingsResult
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.outsystems.plugins.osgeolocation.model.OSGLOCException
import com.outsystems.plugins.osgeolocation.model.OSGLOCLocationOptions
import com.outsystems.plugins.osgeolocation.model.OSGLOCLocationResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.max


@OptIn(ExperimentalCoroutinesApi::class)
class OSGLOCControllerTest {

    private val fusedLocationProviderClient = mockk<FusedLocationProviderClient>()
    private val activityResultLauncher = mockk<ActivityResultLauncher<IntentSenderRequest>>()
    private val googleApiAvailability = mockk<GoogleApiAvailability>()
    private val locationSettingsClient = mockk<SettingsClient>()
    private val helper = spyk(
        OSGLOCServiceHelper(fusedLocationProviderClient, activityResultLauncher)
    )

    private val mockAndroidLocation = mockkLocation()
    private val locationSettingsTask = mockk<Task<LocationSettingsResponse>>(relaxed = true)
    private val currentLocationTask = mockk<Task<Location?>>(relaxed = true)
    private val voidTask = mockk<Task<Void>>(relaxed = true)

    private lateinit var sut: OSGLOCController
    private lateinit var locationCallback: LocationCallback

    @Before
    fun setUp() {
        mockkStatic(GoogleApiAvailability::class)
        every { GoogleApiAvailability.getInstance() } returns googleApiAvailability
        mockkStatic(LocationServices::class)
        every { LocationServices.getSettingsClient(any()) } returns locationSettingsClient
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        mockkObject(OSGLOCBuildConfig)
        every { OSGLOCBuildConfig.getAndroidSdkVersionCode() } returns Build.VERSION_CODES.VANILLA_ICE_CREAM
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk<Looper>()

        sut = OSGLOCController(
            fusedLocationClient = fusedLocationProviderClient,
            activityLauncher = activityResultLauncher,
            helper = helper
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
        unmockkStatic(Log::class)
        unmockkObject(OSGLOCBuildConfig)
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
        unmockkStatic(LocationServices::class)
        unmockkStatic(GoogleApiAvailability::class)
    }

    // region getCurrentLocation tests
    @Test
    fun `given all conditions check out, when getCurrentLocation is called, a location is returned`() =
        runTest {
            givenSuccessConditions()

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)

            assertTrue(result.isSuccess)
            assertEquals(locationResult, result.getOrNull())
        }

    @Test
    fun `given negative timeout in getCurrentLocation, OSGLOCInvalidTimeoutException is returned`() =
        runTest {
            // nothing to setup in this test

            val result =
                sut.getCurrentPosition(mockk<Activity>(), locationOptions.copy(timeout = -1))

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is OSGLOCException.OSGLOCInvalidTimeoutException)
        }

    @Test
    fun `given null location is returned, when getCurrentLocation is called, OSGLOCLocationRetrievalTimeoutException is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            coEvery { currentLocationTask.await() } returns null

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is OSGLOCException.OSGLOCLocationRetrievalTimeoutException)
        }

    @Test
    fun `given play services not available with resolvable error, when getCurrentLocation is called, OSGLOCGoogleServicesException is returned with resolvable=true`() =
        runTest {
            givenPlayServicesNotAvailableWithResolvableError()

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)

            assertTrue(result.isFailure)
            result.exceptionOrNull().let { exception ->
                assertTrue(exception is OSGLOCException.OSGLOCGoogleServicesException)
                assertTrue((exception as OSGLOCException.OSGLOCGoogleServicesException).resolvable)
            }
        }

    @Test
    fun `given play services not available with un-resolvable error, when getCurrentLocation is called, OSGLOCGoogleServicesException is returned with resolvable=false`() =
        runTest {
            givenPlayServicesNotAvailableWithUnResolvableError()

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)

            assertTrue(result.isFailure)
            result.exceptionOrNull().let { exception ->
                assertTrue(exception is OSGLOCException.OSGLOCGoogleServicesException)
                assertFalse((exception as OSGLOCException.OSGLOCGoogleServicesException).resolvable)
            }
        }

    @Test
    fun `given user resolves location settings, when getCurrentLocation is called, the location is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            givenResolvableApiException(Activity.RESULT_OK)

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)
            testScheduler.advanceTimeBy(DELAY)

            assertTrue(result.isSuccess)
            assertEquals(locationResult, result.getOrNull())
        }

    @Test
    fun `given user does not resolve location settings, when getCurrentLocation is called, OSGLOCRequestDeniedException returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            givenResolvableApiException(Activity.RESULT_CANCELED)

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)
            testScheduler.advanceTimeBy(DELAY)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is OSGLOCException.OSGLOCRequestDeniedException)
        }

    @Test
    fun `given location settings check fails, when getCurrentLocation is called, OSGLOCSettingsException is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            val error = RuntimeException()
            coEvery { locationSettingsTask.await() } throws error

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)

            assertTrue(result.isFailure)
            result.exceptionOrNull().let { exception ->
                assertTrue(exception is OSGLOCException.OSGLOCSettingsException)
                assertEquals(
                    error,
                    (exception as OSGLOCException.OSGLOCSettingsException).cause
                )
            }
        }
    // endregion getCurrentLocation tests

    // region addWatch tests
    @Test
    fun `given all conditions check out, when addWatch is called, locations are returned in flow`() =
        runTest {
            givenSuccessConditions()

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                advanceUntilIdle()  // to wait until locationCallback is instantiated
                emitLocations(listOf(mockAndroidLocation))
                var result = awaitItem()
                assertTrue(result.isSuccess)
                assertEquals(listOf(locationResult), result.getOrNull())


                emitLocations(
                    listOf(
                        mockkLocation { every { time } returns 1234L },
                        mockkLocation { every { time } returns 12345L },
                        mockkLocation { every { time } returns 123456L }
                    )
                )
                result = awaitItem()
                assertEquals(
                    listOf(
                        locationResult.copy(timestamp = 1234L),
                        locationResult.copy(timestamp = 12345L),
                        locationResult.copy(timestamp = 123456L)
                    ),
                    result.getOrNull()
                )
            }
        }

    @Test
    fun `given play services not available, when addWatch is called, OSGLOCGoogleServicesException is returned`() =
        runTest {
            givenPlayServicesNotAvailableWithResolvableError()

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                val result = awaitItem()

                assertTrue(result.isFailure)
                result.exceptionOrNull().let { exception ->
                    assertTrue(exception is OSGLOCException.OSGLOCGoogleServicesException)
                    assertTrue((exception as OSGLOCException.OSGLOCGoogleServicesException).resolvable)
                }
                expectNoEvents()
            }
        }

    @Test
    fun `given user resolves location settings, when addWatch is called, the location is returned in flow`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            givenResolvableApiException(Activity.RESULT_OK)

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                advanceUntilIdle()  // to wait until locationCallback is instantiated
                emitLocations(listOf(mockAndroidLocation))
                val result = awaitItem()

                assertTrue(result.isSuccess)
                assertEquals(listOf(locationResult), result.getOrNull())
                expectNoEvents()
            }
        }

    @Test
    fun `given user does not resolve location settings, when addWatch is called, OSGLOCRequestDeniedException returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            givenResolvableApiException(Activity.RESULT_CANCELED)

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                testScheduler.advanceTimeBy(DELAY)
                val result = awaitItem()

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is OSGLOCException.OSGLOCRequestDeniedException)
                expectNoEvents()
            }
        }

    @Test
    fun `given location settings check fails, when addWatch is called, OSGLOCSettingsException is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            val error = RuntimeException()
            coEvery { locationSettingsTask.await() } throws error

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                testScheduler.advanceTimeBy(DELAY)
                val result = awaitItem()

                assertTrue(result.isFailure)
                result.exceptionOrNull().let { exception ->
                    assertTrue(exception is OSGLOCException.OSGLOCSettingsException)
                    assertEquals(
                        error,
                        (exception as OSGLOCException.OSGLOCSettingsException).cause
                    )
                }
                expectNoEvents()
            }
        }
    // endregion addWatch tests

    // region clearWatch tests
    @Test
    fun `given watch was added, when clearWatch is called, true is returned`() = runTest {
        val watchId = "id"
        givenSuccessConditions()
        sut.addWatch(mockk<Activity>(), locationOptions, watchId).test {
            advanceUntilIdle()  // to wait until locationCallback is instantiated

            val result = sut.clearWatch(watchId)

            assertTrue(result)
            expectNoEvents()
        }
        verify { fusedLocationProviderClient.removeLocationUpdates(locationCallback) }
    }

    @Test
    fun `given watch not added added, when clearWatch is called, false is returned`() = runTest {
        val watchId = "id"
        givenSuccessConditions()

        val result = sut.clearWatch(watchId)

        assertFalse(result)
        verify(inverse = true) { fusedLocationProviderClient.removeLocationUpdates(any<LocationCallback>()) }
    }

    @Test
    fun `given clearWatch called, when addWatch is called, the location is not emitted in flow`() =
        runTest {
            val watchId = "id"
            givenSuccessConditions()
            sut.clearWatch(watchId)

            sut.addWatch(mockk<Activity>(), locationOptions, watchId).test {
                advanceUntilIdle()  // to wait until locationCallback is instantiated

                emitLocations(listOf(mockAndroidLocation))

                ensureAllEventsConsumed()
            }
        }
    // endregion clearWatch tests

    private fun givenSuccessConditions() {
        every { googleApiAvailability.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SUCCESS
        every { locationSettingsClient.checkLocationSettings(any()) } returns locationSettingsTask
        coEvery { locationSettingsTask.await() } returns LocationSettingsResponse(
            LocationSettingsResult(
                Status.RESULT_SUCCESS,
                null
            )
        )

        every {
            fusedLocationProviderClient.getCurrentLocation(any<CurrentLocationRequest>(), any())
        } returns currentLocationTask
        coEvery { currentLocationTask.await() } returns mockAndroidLocation

        every {
            fusedLocationProviderClient.requestLocationUpdates(
                any(),
                any<LocationCallback>(),
                any()
            )
        } answers {
            locationCallback = args[1] as LocationCallback
            voidTask
        }

        every { fusedLocationProviderClient.removeLocationUpdates(any<LocationCallback>()) } returns voidTask
    }

    private fun givenPlayServicesNotAvailableWithResolvableError() {
        every { googleApiAvailability.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.RESOLUTION_REQUIRED
        every { googleApiAvailability.isUserResolvableError(any()) } returns true
        every {
            googleApiAvailability.getErrorDialog(any<Activity>(), any(), any())
        } returns null
    }

    private fun givenPlayServicesNotAvailableWithUnResolvableError() {
        every { googleApiAvailability.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.API_DISABLED
        every { googleApiAvailability.isUserResolvableError(any()) } returns false
    }

    private fun CoroutineScope.givenResolvableApiException(resultCode: Int) {
        coEvery { locationSettingsTask.await() } throws mockk<ResolvableApiException> {
            every { resolution } returns mockk<PendingIntent>(relaxed = true)
        }
        coEvery { activityResultLauncher.launch(any()) } coAnswers {
            launch {
                delay(DELAY) // simulate user delay in turning on location settings
                sut.onResolvableExceptionResult(resultCode)
            }
        }
    }

    private fun mockkLocation(overrideDefaultMocks: Location.() -> Unit = {}): Location =
        mockk<Location>(relaxed = true) {
            every { latitude } returns 1.0
            every { longitude } returns 2.0
            every { altitude } returns 3.0
            every { accuracy } returns 0.5f
            every { verticalAccuracyMeters } returns 1.5f
            every { bearing } returns 4.0f
            every { speed } returns 0.2f
            every { time } returns 1L
            overrideDefaultMocks()
        }

    private fun emitLocations(locationList: List<Location>) {
        locationCallback.onLocationResult(
            mockk<LocationResult>(relaxed = true) {
                every { locations } returns locationList.toMutableList()
            }
        )
    }

    companion object {
        private const val DELAY = 3_000L

        private val locationOptions = OSGLOCLocationOptions(
            timeout = 5000,
            maximumAge = 3000,
            enableHighAccuracy = true,
            minUpdateInterval = 2000L
        )

        private val locationResult = OSGLOCLocationResult(
            latitude = 1.0,
            longitude = 2.0,
            altitude = 3.0,
            accuracy = 0.5f,
            altitudeAccuracy = 1.5f,
            heading = 4.0f,
            speed = 0.2f,
            timestamp = 1L
        )
    }
}