import Combine

public protocol IONGLOCServicesChecker {
    func areLocationServicesEnabled() -> Bool
}

public protocol IONGLOCAuthorisationHandler {
    var authorisationStatus: IONGLOCAuthorisation { get }
    var authorisationStatusPublisher: Published<IONGLOCAuthorisation>.Publisher { get }

    func requestAuthorisation(withType authorisationType: IONGLOCAuthorisationRequestType)
}

public enum IONGLOCLocationError: Error {
    case locationUnavailable
    case timeout
    case other(_ error: Error)
}

public protocol IONGLOCLocationHandler {
    var currentLocation: IONGLOCPositionModel? { get }
    var currentLocationPublisher: AnyPublisher<IONGLOCPositionModel, IONGLOCLocationError> { get }
    var locationTimeoutPublisher: AnyPublisher<IONGLOCLocationError, Never> { get }
    func updateConfiguration(_ configuration: IONGLOCConfigurationModel)
}

public protocol IONGLOCSingleLocationHandler: IONGLOCLocationHandler {
    func requestSingleLocation(timeout: Int?)
}

public protocol IONGLOCMonitorLocationHandler: IONGLOCLocationHandler {
    func startMonitoringLocation(timeout: Int?)
    func startMonitoringLocation()
    func stopMonitoringLocation()
}

public struct IONGLOCConfigurationModel {
    private(set) var enableHighAccuracy: Bool
    private(set) var minimumUpdateDistanceInMeters: Double?

    public init(enableHighAccuracy: Bool, minimumUpdateDistanceInMeters: Double? = nil) {
        self.enableHighAccuracy = enableHighAccuracy
        self.minimumUpdateDistanceInMeters = minimumUpdateDistanceInMeters
    }
}
