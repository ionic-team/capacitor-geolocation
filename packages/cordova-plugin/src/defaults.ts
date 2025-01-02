import { ClearWatchOptions, CurrentPositionOptions, WatchPositionOptions } from "./definitions";

export const CurrentPositionOptionsDefault: CurrentPositionOptions = {
    enableHighAccuracy: false,
    timeout: 1000,
    maximumAge: 0,
    minimumUpdateInterval: 5000
}

export const ClearWatchOptionsDefault: ClearWatchOptions = {
    id: "-1"
}

export const WatchPositionOptionsDefault: WatchPositionOptions = {
    ...CurrentPositionOptionsDefault, ...ClearWatchOptionsDefault
}