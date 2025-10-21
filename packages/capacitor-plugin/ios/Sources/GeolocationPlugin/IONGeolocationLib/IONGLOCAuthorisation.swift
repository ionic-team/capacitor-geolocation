import CoreLocation

public enum IONGLOCAuthorisation {
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
    var currentAuthorisationValue: IONGLOCAuthorisation {
        .init(from: authorizationStatus)
    }
}
