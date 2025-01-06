import { require } from "cordova";
import { ClearWatchOptions, OSGLOCPosition, PluginError, Position, CurrentPositionOptions, WatchPositionOptions } from "./definitions";
import { ClearWatchOptionsDefault, CurrentPositionOptionsDefault, WatchPositionOptionsDefault } from "./defaults";

var exec = require('cordova/exec');

function getCurrentPosition(options: CurrentPositionOptions, success: (output: Position) => void, error: (error: PluginError) => void): void {
  options = { ...CurrentPositionOptionsDefault, ...options };

  let convertOnSuccess = (position: OSGLOCPosition) => {
    let convertedPosition: Position = {
      coords: {
        latitude: position.latitude,
        longitude: position.longitude,
        altitude: position.altitude,
        accuracy: position.accuracy,
        heading: position.heading,
        speed: position.speed,
        altitudeAccuracy: position.altitudeAccuracy
      },
      timestamp: position.timestamp,
    }
    success(convertedPosition)
  }
  exec(convertOnSuccess, error, 'OSGeolocation', 'getCurrentPosition', [options]);
}

function watchPosition(options: WatchPositionOptions, success: (output: Position) => void, error: (error: PluginError) => void): void {
  options = { ...WatchPositionOptionsDefault, ...options };

  let convertOnSuccess = (position: OSGLOCPosition) => {
    let convertedPosition: Position = {
      coords: {
        latitude: position.latitude,
        longitude: position.longitude,
        altitude: position.altitude,
        accuracy: position.accuracy,
        heading: position.heading,
        speed: position.speed,
        altitudeAccuracy: position.altitudeAccuracy
      },
      timestamp: position.timestamp,
    }
    success(convertedPosition)
  }
  exec(convertOnSuccess, error, 'OSGeolocation', 'watchPosition', [options]);
}

function clearWatch(options: ClearWatchOptions, success: () => void, error: (error: PluginError) => void): void {
  options = { ...ClearWatchOptionsDefault, ...options };
  exec(success, error, "OSGeolocation", "clearWatch", [options]);
}

module.exports = {
  getCurrentPosition,
  watchPosition,
  clearWatch
};