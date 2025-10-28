import Combine
import CoreLocation

public typealias IONGLOCService = IONGLOCServicesChecker & IONGLOCAuthorisationHandler & IONGLOCSingleLocationHandler & IONGLOCMonitorLocationHandler

public struct IONGLOCServicesValidator: IONGLOCServicesChecker {
    public init() {}
    
    public func areLocationServicesEnabled() -> Bool {
        CLLocationManager.locationServicesEnabled()
    }
}

public class IONGLOCManagerWrapper: NSObject, IONGLOCService {
    @Published public var authorisationStatus: IONGLOCAuthorisation
    public var authorisationStatusPublisher: Published<IONGLOCAuthorisation>.Publisher { $authorisationStatus }

    @Published public var currentLocation: IONGLOCPositionModel?
    private var timeoutCancellable: AnyCancellable?
    public var currentLocationPublisher: AnyPublisher<IONGLOCPositionModel, IONGLOCLocationError> {
        Publishers.Merge($currentLocation, currentLocationForceSubject)
            .dropFirst()    // ignore the first value as it's the one set on the constructor.
            .tryMap { location in
                guard let location else { throw IONGLOCLocationError.locationUnavailable }
                return location
            }
            .mapError { $0 as? IONGLOCLocationError ?? .other($0) }
            .eraseToAnyPublisher()
    }
    
    public var locationTimeoutPublisher: AnyPublisher<IONGLOCLocationError, Never> {
        locationTimeoutSubject.eraseToAnyPublisher()
    }
    
    private let currentLocationForceSubject = PassthroughSubject<IONGLOCPositionModel?, Never>()
    private let locationTimeoutSubject = PassthroughSubject<IONGLOCLocationError, Never>()
    
    private let locationManager: CLLocationManager
    private let servicesChecker: IONGLOCServicesChecker
    
    private var isMonitoringLocation = false

    // Flag used to indicate that the location request has timed out.
    // When `true`, the wrapper ignores any location updates received from CLLocationManager.
    // This prevents "stale" or "ghost" events from being sent to subscribers after the timeout has occurred.
    private var timeoutTriggered = false

    public init(locationManager: CLLocationManager = .init(), servicesChecker: IONGLOCServicesChecker = IONGLOCServicesValidator()) {
        self.locationManager = locationManager
        self.servicesChecker = servicesChecker
        self.authorisationStatus = locationManager.currentAuthorisationValue

        super.init()
        locationManager.delegate = self
    }

    public func requestAuthorisation(withType authorisationType: IONGLOCAuthorisationRequestType) {
        authorisationType.requestAuthorization(using: locationManager)
    }
    
    public func startMonitoringLocation(options: IONGLOCRequestOptionsModel) {
        timeoutTriggered = false
        isMonitoringLocation = true
        locationManager.startUpdatingLocation()
        self.startTimer(timeout: options.timeout)
    }
    
    public func startMonitoringLocation() {
        guard !timeoutTriggered else {
            return
        }
        
        isMonitoringLocation = true
        locationManager.startUpdatingLocation()
    }

    public func stopMonitoringLocation() {
        isMonitoringLocation = false
        locationManager.stopUpdatingLocation()
    }
    
    public func requestSingleLocation(options: IONGLOCRequestOptionsModel) {
        timeoutTriggered = false
        // If monitoring is active meaning the location service is already running
        // and calling .requestLocation() will not trigger a new location update,
        // we can just return the current location.
        if isMonitoringLocation, let location = currentLocation {
            currentLocationForceSubject.send(location)
            return
        }
        
        self.locationManager.requestLocation()
        self.startTimer(timeout: options.timeout)
    }
    
    private func startTimer(timeout: Int) {
        timeoutCancellable?.cancel()
        timeoutCancellable = nil
        timeoutCancellable = Just(())
            .delay(for: .milliseconds(timeout), scheduler: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self = self else { return }
                self.timeoutTriggered = true
                self.locationTimeoutSubject.send(.timeout)
                
                if self.isMonitoringLocation {
                    self.isMonitoringLocation = false
                    self.stopMonitoringLocation()
                }
                
                self.timeoutCancellable?.cancel()
                self.timeoutCancellable = nil
            }
    }
    
    public func updateConfiguration(_ configuration: IONGLOCConfigurationModel) {
        locationManager.desiredAccuracy = configuration.enableHighAccuracy ? kCLLocationAccuracyBest : kCLLocationAccuracyThreeKilometers
        configuration.minimumUpdateDistanceInMeters.map {
            locationManager.distanceFilter = $0
        }
    }

    public func areLocationServicesEnabled() -> Bool {
        servicesChecker.areLocationServicesEnabled()
    }
}

extension IONGLOCManagerWrapper: CLLocationManagerDelegate {
    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorisationStatus = manager.currentAuthorisationValue
    }
    
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard !timeoutTriggered else {
            return
        }
     
        timeoutCancellable?.cancel()
        timeoutCancellable = nil
        guard let latestLocation = locations.last else {
            currentLocation = nil
            return
        }
        currentLocation = IONGLOCPositionModel.create(from: latestLocation)
    }
    
    public func locationManager(_ manager: CLLocationManager, didFailWithError error: any Error) {
        timeoutCancellable?.cancel()
        timeoutCancellable = nil
        currentLocation = nil
    }
}
