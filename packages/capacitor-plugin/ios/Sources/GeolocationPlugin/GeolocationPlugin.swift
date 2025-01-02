import Capacitor
import OSGeolocationLib

import Combine

@objc(GeolocationPlugin)
public class GeolocationPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "GeolocationPlugin"
    public let jsName = "GeolocationPlugin"
    public let pluginMethods: [CAPPluginMethod] = [
        .init(name: "getCurrentPosition", returnType: CAPPluginReturnPromise),
        .init(name: "watchPosition", returnType: CAPPluginReturnCallback),
        .init(name: "clearWatch", returnType: CAPPluginReturnPromise),
        .init(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        .init(name: "requestPermissions", returnType: CAPPluginReturnPromise)
    ]

    private var plugin: (any OSGLOCService)?
    private var cancellables = Set<AnyCancellable>()
    private var callbackManager: GeolocationCallbackManager?
    private var isInitialised: Bool = false

    public override func load() {
        self.plugin = OSGLOCManagerWrapper()
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
            plugin?.stopMonitoringLocation()
        }

        callbackManager?.sendSuccess(call)
    }

    public override func checkPermissions(_ call: CAPPluginCall) {
        checkIfLocationServicesAreEnabled()

        let status = switch plugin?.authorisationStatus {
        case .restricted, .denied: Constants.AuthorisationStatus.Status.denied
        case .granted: Constants.AuthorisationStatus.Status.granted
        default: Constants.AuthorisationStatus.Status.prompt
        }

        let callResultData = [
            Constants.AuthorisationStatus.ResultKey.location: status,
            Constants.AuthorisationStatus.ResultKey.coarseLocation: status
        ]
        callbackManager?.sendSuccess(call, with: callResultData)
    }

    public override func requestPermissions(_ call: CAPPluginCall) {
        checkIfLocationServicesAreEnabled()

        if plugin?.authorisationStatus == .notDetermined {
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
        plugin?.authorisationStatusPublisher
            .sink(receiveValue: { [weak self] status in
                guard let self else { return }

                do {
                    switch status {
                    case .denied: throw GeolocationError.permissionDenied
                    case .notDetermined: self.requestLocationAuthorisation()
                    case .restricted: throw GeolocationError.permissionRestricted
                    case .granted: self.requestLocation()
                    @unknown default: break
                    }
                } catch let error as GeolocationError {
                    self.callbackManager?.sendError(error)
                } catch {
                    self.callbackManager?.sendError(.other(error))
                }
            })
            .store(in: &cancellables)

        plugin?.currentLocationPublisher
            .sink { [weak self] position in
                guard let self else { return }

                if let position {
                    self.sendCurrentPosition(position)
                } else {
                    self.handlePositionUnavailability()
                }
            }
            .store(in: &cancellables)
    }

    func requestLocationAuthorisation() {
        DispatchQueue.global(qos: .background).async {
            self.checkIfLocationServicesAreEnabled()

            var requestType: OSGLOCAuthorisationRequestType?
            if Bundle.main.object(forInfoDictionaryKey: Constants.LocationUsageDescription.whenInUse) != nil {
                requestType = .whenInUse
            } else if Bundle.main.object(forInfoDictionaryKey: Constants.LocationUsageDescription.always) != nil {
                requestType = .always
            }

            guard let requestType else {
                self.callbackManager?.sendError(.missingUsageDescription)
                return
            }
            self.plugin?.requestAuthorisation(withType: requestType)
        }
    }

    func checkIfLocationServicesAreEnabled() {
        guard plugin?.areLocationServicesEnabled() ?? false else {
            callbackManager?.sendError(.locationServicesDisabled)
            return
        }
    }

    func requestLocation() {
        // should request if callbacks exist and are not empty
        let shouldRequestCurrentPosition = callbackManager?.locationCallbacks.isEmpty == false
        let shouldRequestLocationMonitoring = callbackManager?.watchCallbacks.isEmpty == false

        if shouldRequestCurrentPosition {
            plugin?.requestSingleLocation()
        }
        if shouldRequestLocationMonitoring {
            plugin?.startMonitoringLocation()
        }
    }

    func sendCurrentPosition(_ position: OSGLOCPositionModel) {
        callbackManager?.sendSuccess(with: position)
    }

    func handleLocationRequest(_ enableHighAccuracy: Bool, watchUUID: String? = nil, call: CAPPluginCall) {
        let configurationModel = OSGLOCConfigurationModel(enableHighAccuracy: enableHighAccuracy)
        plugin?.updateConfiguration(configurationModel)

        if let watchUUID {
            callbackManager?.addWatchCallback(watchUUID, capacitorCall: call)
        } else {
            callbackManager?.addLocationCallback(capacitorCall: call)
        }

        switch plugin?.authorisationStatus {
        case .granted: requestLocation()
        case .denied: callbackManager?.sendError(.permissionDenied)
        case .restricted: callbackManager?.sendError(.permissionRestricted)
        default: break
        }
    }

    func handlePositionUnavailability() {
        callbackManager?.sendError(.positionUnavailable)
        callbackManager?.clearAllCallbacks()
        plugin?.stopMonitoringLocation()
    }
}
