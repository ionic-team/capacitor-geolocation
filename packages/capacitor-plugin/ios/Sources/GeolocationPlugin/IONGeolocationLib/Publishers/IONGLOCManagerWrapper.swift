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
    private var shouldRunTimeout = false
    
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
    //TODO changes here
    public func startMonitoringLocation(timeout: Int? = nil) {
        let timeoutValue = timeout ?? 5000
        isMonitoringLocation = true
        
        print("======================> startMonitoringLocation timeout: ", timeoutValue)
        self.startTimer(timeout: timeoutValue)
        locationManager.startUpdatingLocation()
    }
    
    public func startMonitoringLocation() {
        isMonitoringLocation = true
        locationManager.startUpdatingLocation()
        print("======================> startMonitoringLocation sem timer ")
    }
    
    public func stopMonitoringLocation() {
        isMonitoringLocation = false
        locationManager.stopUpdatingLocation()
    }
    
    //TODO changes here
    public func requestSingleLocation(timeout: Int? = nil) {
        // Fallback to default timeout (5000) when the parameter is nil,
        // since optional defaults in Swift don't apply when nil is explicitly passed.
        let timeoutValue = timeout ?? 5000
        
        // If monitoring is active meaning the location service is already running
        // and calling .requestLocation() will not trigger a new location update,
        // we can just return the current location.
        if isMonitoringLocation, let location = currentLocation {
            currentLocationForceSubject.send(location)
            return
        }
        
        self.startTimer(timeout: timeoutValue)
        self.locationManager.requestLocation()
        print("======================> requestLocation")
    }
    
    private func startTimer(timeout: Int) {
        timeoutCancellable?.cancel()
        timeoutCancellable = nil
        timeoutCancellable = Just(())
            .delay(for: .milliseconds(timeout), scheduler: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self = self else { return }
                print("======================> Native lib: timeout triggered (\(timeout)ms)")
                self.locationTimeoutSubject.send(.timeout)
                print("======================> timeoutCancellable 4")
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
        timeoutCancellable?.cancel()
        timeoutCancellable = nil
        print("======================> timeoutCancellable 2")
        
        guard let latestLocation = locations.last else {
            
            print("======================> currentLocation 1")
            currentLocation = nil
            return
        }
        
        print("======================> currentLocation 2")
        currentLocation = IONGLOCPositionModel.create(from: latestLocation)
    }
    
    public func locationManager(_ manager: CLLocationManager, didFailWithError error: any Error) {
        print("======================> timeoutCancellable 3")
        timeoutCancellable?.cancel()
        timeoutCancellable = nil
        currentLocation = nil
    }
}
