import { registerPlugin } from '@capacitor/core';
import { exposeSynapse } from '@capacitor/synapse';

import type { IGeolocationPlugin } from './definitions';

const GeolocationPlugin = registerPlugin<IGeolocationPlugin>('GeolocationPlugin', {
  web: () => import('./web').then((m) => new m.GeolocationPluginWeb()),
});

exposeSynapse();

export * from './definitions';
export { GeolocationPlugin };
