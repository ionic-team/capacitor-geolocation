import Capacitor
import IONGeolocationLib

import Combine

@objc(GeolocationPlugin)
public class GeolocationPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "GeolocationPlugin"
    public let jsName = "Geolocation"
    public let pluginMethods: [CAPPluginMethod] = [
        .init(name: "getCurrentPosition", returnType: CAPPluginReturnPromise),
        .init(name: "watchPosition", returnType: CAPPluginReturnCallback),
        .init(name: "clearWatch", returnType: CAPPluginReturnPromise),
        .init(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        .init(name: "requestPermissions", returnType: CAPPluginReturnPromise)
    ]

    private var locationService: (any IONGLOCService)?
    private var cancellables = Set<AnyCancellable>()
    private var callbackManager: GeolocationCallbackManager?
    private var isInitialised: Bool = false

    override public func load() {
        self.locationService = IONGLOCManagerWrapper()
        self.callbackManager = .init(capacitorBridge: bridge)
    }

    @objc func getCurrentPosition(_ call: CAPPluginCall) {
        shouldSetupBindings()
        let enableHighAccuracy = call.getBool(Constants.Arguments.enableHighAccuracy, false)
        handleLocationRequest(enableHighAccuracy, call: call)
    }

    @objc func watchPosition(_ call: CAPPluginCall) {
        shouldSetupBindings()
        let enableHighAccuracy = call.getBool(Constants.Arguments.enableHighAccuracy, false)
        let watchUUID = call.callbackId
        handleLocationRequest(enableHighAccuracy, watchUUID: watchUUID, call: call)
    }

    @objc func clearWatch(_ call: CAPPluginCall) {
        shouldSetupBindings()
        guard let callbackId = call.getString(Constants.Arguments.id) else {
            callbackManager?.sendError(.inputArgumentsIssue(target: .clearWatch))
            return
        }
        callbackManager?.clearWatchCallbackIfExists(callbackId)

        if (callbackManager?.watchCallbacks.isEmpty) ?? false {
            locationService?.stopMonitoringLocation()
        }

        callbackManager?.sendSuccess(call)
    }

    override public func checkPermissions(_ call: CAPPluginCall) {
        checkIfLocationServicesAreEnabled()

        let status = switch locationService?.authorisationStatus {
        case .restricted, .denied: Constants.AuthorisationStatus.Status.denied
        case .authorisedAlways, .authorisedWhenInUse: Constants.AuthorisationStatus.Status.granted
        default: Constants.AuthorisationStatus.Status.prompt
        }

        let callResultData = [
            Constants.AuthorisationStatus.ResultKey.location: status,
            Constants.AuthorisationStatus.ResultKey.coarseLocation: status
        ]
        callbackManager?.sendSuccess(call, with: callResultData)
    }

    override public func requestPermissions(_ call: CAPPluginCall) {
        checkIfLocationServicesAreEnabled()

        if locationService?.authorisationStatus == .notDetermined {
            shouldSetupBindings()
        } else {
            checkPermissions(call)
        }
    }
}

private extension GeolocationPlugin {
    func shouldSetupBindings() {
        guard !isInitialised else { return }
        isInitialised = true
        setupBindings()
    }

    func setupBindings() {
        locationService?.authorisationStatusPublisher
            .sink(receiveValue: { [weak self] status in
                guard let self else { return }

                switch status {
                case .denied:
                    self.callbackManager?.sendError(.permissionDenied)
                case .notDetermined:
                    self.requestLocationAuthorisation(type: .whenInUse)
                case .restricted:
                    self.callbackManager?.sendError(.permissionRestricted)
                case .authorisedAlways, .authorisedWhenInUse:
                    self.requestLocation()
                @unknown default: break
                }
            })
            .store(in: &cancellables)

        locationService?.currentLocationPublisher
            .sink(receiveCompletion: { [weak self] completion in
                if case .failure(let error) = completion {
                    print("An error was found while retrieving the location: \(error)")
                    self?.callbackManager?.sendError(.positionUnavailable)
                }
            }, receiveValue: { [weak self] position in
                self?.callbackManager?.sendSuccess(with: position)
            })
            .store(in: &cancellables)
    }

    func requestLocationAuthorisation(type requestType: IONGLOCAuthorisationRequestType) {
        DispatchQueue.global(qos: .background).async {
            self.checkIfLocationServicesAreEnabled()
            self.locationService?.requestAuthorisation(withType: requestType)
        }
    }

    func checkIfLocationServicesAreEnabled() {
        guard locationService?.areLocationServicesEnabled() ?? false else {
            callbackManager?.sendError(.locationServicesDisabled)
            return
        }
    }

    func requestLocation() {
        // should request if callbacks exist and are not empty
        let shouldRequestCurrentPosition = callbackManager?.locationCallbacks.isEmpty == false
        let shouldRequestLocationMonitoring = callbackManager?.watchCallbacks.isEmpty == false

        if shouldRequestCurrentPosition {
            locationService?.requestSingleLocation()
        }
        if shouldRequestLocationMonitoring {
            locationService?.startMonitoringLocation()
        }
    }

    func handleLocationRequest(_ enableHighAccuracy: Bool, watchUUID: String? = nil, call: CAPPluginCall) {
        let configurationModel = IONGLOCConfigurationModel(enableHighAccuracy: enableHighAccuracy)
        locationService?.updateConfiguration(configurationModel)

        if let watchUUID {
            callbackManager?.addWatchCallback(watchUUID, capacitorCall: call)
        } else {
            callbackManager?.addLocationCallback(capacitorCall: call)
        }

        switch locationService?.authorisationStatus {
        case .authorisedAlways, .authorisedWhenInUse: requestLocation()
        case .denied: callbackManager?.sendError(.permissionDenied)
        case .restricted: callbackManager?.sendError(.permissionRestricted)
        default: break
        }
    }
}
