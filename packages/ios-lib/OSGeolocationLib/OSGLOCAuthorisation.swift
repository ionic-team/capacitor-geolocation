import CoreLocation

public enum OSGLOCAuthorisation {
    case notDetermined
    case restricted
    case denied
    case authorisedAlways
    case authorisedWhenInUse

    init(from status: CLAuthorizationStatus) {
        self = switch status {
        case .notDetermined: .notDetermined
        case .restricted: .restricted
        case .denied: .denied
        case .authorizedAlways: .authorisedAlways
        case .authorizedWhenInUse: .authorisedWhenInUse
        @unknown default: .notDetermined
        }
    }
}

extension CLLocationManager {
    var currentAuthorisationValue: OSGLOCAuthorisation {
        .init(from: authorizationStatus)
    }
}
