import Capacitor
import IONGeolocationLib

extension IONGLOCPositionModel {
    func toJSObject() -> JSObject {
        [
            Constants.Position.timestamp: timestamp,
            Constants.Position.coords: coordsJSObject
        ]
    }

    private var coordsJSObject: JSObject {
        [
            Constants.Position.altitude: altitude,
            Constants.Position.heading: course,
            Constants.Position.accuracy: horizontalAccuracy,
            Constants.Position.latitude: latitude,
            Constants.Position.longitude: longitude,
            Constants.Position.speed: speed,
            Constants.Position.altitudeAccuracy: verticalAccuracy
        ]
    }
}
