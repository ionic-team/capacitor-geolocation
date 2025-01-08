import OSGeolocationLib
import Combine

@objc(OSGeolocation)
final class OSGeolocation: CDVPlugin {
    private var locationService: (any OSGLOCService)?
    private var cancellables = Set<AnyCancellable>()
    private var callbackManager: OSGeolocationCallbackManager?

    override func pluginInitialize() {
        self.locationService = OSGLOCManagerWrapper()
        self.callbackManager = .init(commandDelegate: commandDelegate)
        setupBindings()
    }

    @objc(getCurrentPosition:)
    func getLocation(command: CDVInvokedUrlCommand) {
        guard let config: OSGeolocationCurrentPositionModel = createModel(for: command.argument(at: 0))
        else {
            callbackManager?.sendError(.inputArgumentsIssue(target: .getCurrentPosition))
            return
        }
        handleLocationRequest(config.enableHighAccuracy, command.callbackId)
    }

    @objc(watchPosition:)
    func addWatch(command: CDVInvokedUrlCommand) {
        guard let config: OSGeolocationWatchPositionModel = createModel(for: command.argument(at: 0))
        else {
            callbackManager?.sendError(.inputArgumentsIssue(target: .watchPosition))
            return
        }
        handleLocationRequest(config.enableHighAccuracy, watchUUID: config.id, command.callbackId)
    }

    @objc(clearWatch:)
    func clearWatch(command: CDVInvokedUrlCommand) {
        guard let config: OSGeolocationClearWatchModel = createModel(for: command.argument(at: 0))
        else {
            callbackManager?.sendError(.inputArgumentsIssue(target: .clearWatch))
            return
        }
        callbackManager?.clearWatchCallbackIfExists(config.id)

        if (callbackManager?.watchCallbacks.isEmpty) ?? false {
            locationService?.stopMonitoringLocation()
        }

        callbackManager?.sendSuccess(command.callbackId)
    }
}

private extension OSGeolocation {
    func setupBindings() {
        locationService?.authorisationStatusPublisher
            .sink { [weak self] status in
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
            }
            .store(in: &cancellables)

        locationService?.currentLocationPublisher
            .sink(receiveCompletion: { [weak self] completion in
                if case .failure(let error) = completion {
                    print("An error was found while retrieving the location: \(error)")
                    self?.callbackManager?.sendError(.positionUnavailable)
                }
            }, receiveValue: { [weak self] currentPosition in
                self?.callbackManager?.sendSuccess(with: currentPosition)
            })
            .store(in: &cancellables)
    }

    func requestLocationAuthorisation(type requestType: OSGLOCAuthorisationRequestType) {
        commandDelegate.run { [weak self] in
            guard let self else { return }

            guard locationService?.areLocationServicesEnabled() ?? false else {
                self.callbackManager?.sendError(.locationServicesDisabled)
                return
            }
            self.locationService?.requestAuthorisation(withType: requestType)
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

    func createModel<T: Decodable>(for inputArgument: Any?) -> T? {
        guard let argumentsDictionary = inputArgument as? [String: Any],
              let argumentsData = try? JSONSerialization.data(withJSONObject: argumentsDictionary),
              let argumentsModel = try? JSONDecoder().decode(T.self, from: argumentsData)
        else { return nil }
        return argumentsModel
    }

    func handleLocationRequest(_ enableHighAccuracy: Bool, watchUUID: String? = nil, _ callbackId: String) {
        let configurationModel = OSGLOCConfigurationModel.createWithAccuracy(enableHighAccuracy)
        locationService?.updateConfiguration(configurationModel)

        if let watchUUID {
            callbackManager?.addWatchCallback(watchUUID, callbackId)
        } else {
            callbackManager?.addLocationCallback(callbackId)
        }

        switch locationService?.authorisationStatus {
        case .authorisedAlways, .authorisedWhenInUse: requestLocation()
        case .denied: callbackManager?.sendError(.permissionDenied)
        case .restricted: callbackManager?.sendError(.permissionRestricted)
        default: break
        }
    }
}
