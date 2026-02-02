import { WebPlugin } from '@capacitor/core';

import type {
  CallbackID,
  GeolocationPlugin,
  PermissionStatus,
  Position,
  PositionOptions,
  WatchPositionCallback,
} from './definitions';

export class GeolocationWeb extends WebPlugin implements GeolocationPlugin {
  private latestOrientation: {
    magneticHeading: number | null;
    trueHeading: number | null;
    headingAccuracy: number | null;
  } | null = null;

  constructor() {
    super();
    if (typeof window !== 'undefined') {
      const win = window as any;
      if ('ondeviceorientationabsolute' in win) {
        win.addEventListener('deviceorientationabsolute', (event: any) => this.updateOrientation(event, true), true);
      } else if ('ondeviceorientation' in win) {
        win.addEventListener('deviceorientation', (event: any) => this.updateOrientation(event, false), true);
      }
    }
  }

  private updateOrientation(event: any, isAbsolute: boolean) {
    let trueHeading: number | null = null;
    let magneticHeading: number | null = null;
    let headingAccuracy: number | null = null;

    if (isAbsolute && event.alpha !== null) {
      trueHeading = (360 - event.alpha) % 360;
    } else if (event.webkitCompassHeading !== undefined && event.webkitCompassHeading !== null) {
      magneticHeading = event.webkitCompassHeading;
      headingAccuracy = event.webkitCompassAccuracy;
    } else if (event.alpha !== null && event.absolute === true) {
      trueHeading = (360 - event.alpha) % 360;
    } else if (event.alpha !== null) {
      magneticHeading = (360 - event.alpha) % 360;
    }

    if (trueHeading !== null || magneticHeading !== null) {
      this.latestOrientation = {
        trueHeading,
        magneticHeading,
        headingAccuracy,
      };
    }
  }

  private augmentPosition(pos: globalThis.GeolocationPosition): Position {
    const coords = pos.coords;
    const orientation = this.latestOrientation;

    const heading = orientation?.trueHeading ?? orientation?.magneticHeading ?? coords.heading ?? null;

    return {
      timestamp: pos.timestamp,
      coords: {
        latitude: coords.latitude,
        longitude: coords.longitude,
        accuracy: coords.accuracy,
        altitude: coords.altitude,
        altitudeAccuracy: (coords as any).altitudeAccuracy,
        speed: coords.speed,
        heading: heading,
        magneticHeading: orientation?.magneticHeading ?? null,
        trueHeading: orientation?.trueHeading ?? null,
        headingAccuracy: orientation?.headingAccuracy ?? null,
        course: coords.heading ?? null,
      },
    };
  }

  async getCurrentPosition(options?: PositionOptions): Promise<Position> {
    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        pos => {
          resolve(this.augmentPosition(pos));
        },
        err => {
          reject(err);
        },
        {
          enableHighAccuracy: false,
          timeout: 10000,
          maximumAge: 0,
          ...options,
        },
      );
    });
  }

  async watchPosition(options: PositionOptions, callback: WatchPositionCallback): Promise<CallbackID> {
    const id = navigator.geolocation.watchPosition(
      pos => {
        callback(this.augmentPosition(pos));
      },
      err => {
        callback(null, err);
      },
      {
        enableHighAccuracy: false,
        timeout: 10000,
        maximumAge: 0,
        minimumUpdateInterval: 5000,
        ...options,
      },
    );

    return `${id}`;
  }

  async clearWatch(options: { id: string }): Promise<void> {
    navigator.geolocation.clearWatch(parseInt(options.id, 10));
  }

  async checkPermissions(): Promise<PermissionStatus> {
    if (typeof navigator === 'undefined' || !navigator.permissions) {
      throw this.unavailable('Permissions API not available in this browser');
    }

    const permission = await navigator.permissions.query({
      name: 'geolocation',
    });
    return { location: permission.state, coarseLocation: permission.state };
  }

  async requestPermissions(): Promise<PermissionStatus> {
    throw this.unimplemented('Not implemented on web.');
  }
}

const Geolocation = new GeolocationWeb();

export { Geolocation };
