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
        let headingValue = trueHeading != -1.0 ? trueHeading : (magneticHeading != -1.0 ? magneticHeading : course)
        return [
            Constants.Position.altitude: altitude,
            Constants.Position.heading: headingValue != -1.0 ? headingValue : NSNull(),
            Constants.Position.magneticHeading: magneticHeading != -1.0 ? magneticHeading : NSNull(),
            Constants.Position.trueHeading: trueHeading != -1.0 ? trueHeading : NSNull(),
            Constants.Position.headingAccuracy: headingAccuracy != -1.0 ? headingAccuracy : NSNull(),
            Constants.Position.course: course != -1.0 ? course : NSNull(),
            Constants.Position.accuracy: horizontalAccuracy,
            Constants.Position.latitude: latitude,
            Constants.Position.longitude: longitude,
            Constants.Position.speed: speed,
            Constants.Position.altitudeAccuracy: verticalAccuracy
        ]
    }
}
