import { existsSync } from 'node:fs';

// The tests run against the built plugin: `npm run build` first (`npm run verify` does).
export const locationButtonUrl = new URL('../dist/esm/location-button.js', import.meta.url);
export const webUrl = new URL('../dist/esm/web.js', import.meta.url);
export const skip = existsSync(locationButtonUrl) && existsSync(webUrl) ? false : 'run "npm run build" first';

// A minimal DOM: enough for the plugin to register its element and for the tests to look at what it defined and
// which stylesheets it added.
export function installFakeDom({ android }) {
  const defined = new Map();
  const styles = [];
  globalThis.window = globalThis;
  globalThis.addEventListener = () => {};
  globalThis.removeEventListener = () => {};
  globalThis.HTMLElement = class {};
  globalThis.ShadowRoot = class {};
  globalThis.customElements = {
    get: (name) => defined.get(name),
    define: (name, constructor) => defined.set(name, constructor),
  };
  globalThis.document = {
    head: { append: (element) => styles.push(element) },
    querySelector: () => null,
    createElement: () => ({ dataset: {} }),
    addEventListener() {},
    removeEventListener() {},
  };
  // Capacitor core detects an Android WebView through `window.androidBridge`.
  if (android) globalThis.androidBridge = {};
  else delete globalThis.androidBridge;
  return { defined, styles };
}

export function removeFakeDom() {
  for (const name of [
    'addEventListener',
    'androidBridge',
    'customElements',
    'document',
    'HTMLElement',
    'removeEventListener',
    'ShadowRoot',
    'window',
  ]) {
    delete globalThis[name];
  }
}

export function throwingPlugin() {
  return {
    addListener: async () => ({ remove: async () => {} }),
    getLocationButtonState: () => {
      throw new Error('getLocationButtonState must not be called');
    },
  };
}

// Lets promise callbacks, and the `unhandledRejection` check that follows them, run.
export const settle = () => new Promise((resolve) => setTimeout(resolve, 20));
