import { registerPlugin } from '@capacitor/core';
import { exposeSynapse } from '@capacitor/synapse';

import type { GeolocationPlugin } from './definitions';

const Geolocation = registerPlugin<GeolocationPlugin>('Geolocation', {
  web: () => import('./web').then((m) => new m.GeolocationWeb()),
});

exposeSynapse();

export * from './definitions';
export { Geolocation };
