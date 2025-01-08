enum OSGeolocationMethod: String {
    case getCurrentPosition
    case watchPosition
    case clearWatch
}

enum OSGeolocationError: Error {
    case locationServicesDisabled
    case permissionDenied
    case permissionRestricted
    case positionUnavailable
    case inputArgumentsIssue(target: OSGeolocationMethod)

    func toDictionary() -> [String: String] {
        [
            "code": "OS-PLUG-GLOC-\(String(format: "%04d", code))",
            "description": description
        ]
    }
}

private extension OSGeolocationError {
    var code: Int {
        switch self {
        case .positionUnavailable: 4
        case .permissionDenied: 5
        case .locationServicesDisabled: 11
        case .permissionRestricted: 12
        case .inputArgumentsIssue(let target):
            switch target {
            case .getCurrentPosition: 13
            case .watchPosition: 14
            case .clearWatch: 15
            }
        }
    }

    var description: String {
        switch self {
        case .positionUnavailable: "There was en error trying to obtain the location."
        case .permissionDenied: "Location permission request was denied."
        case .locationServicesDisabled: "Location services are not enabled."
        case .permissionRestricted: "Application's use of location services was restricted."
        case .inputArgumentsIssue(let target): "The '\(target.rawValue)' input parameters aren't valid."
        }
    }
}
