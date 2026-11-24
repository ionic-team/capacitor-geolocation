import assert from 'node:assert/strict';
import test from 'node:test';

import { installFakeDom, locationButtonUrl, removeFakeDom, settle, skip, throwingPlugin } from './helpers.mjs';

// The Native Islands runtime keeps a process-wide registry of the elements it defined, so each state has its own file
// (node runs every test file in its own process).
test('Android: with state "system" the native element is registered', { skip }, async (t) => {
  const { defined, styles } = installFakeDom({ android: true });
  t.after(removeFakeDom);
  const { registerLocationButtonElement } = await import(locationButtonUrl);
  const plugin = { ...throwingPlugin(), getLocationButtonState: async () => ({ state: 'system' }) };

  registerLocationButtonElement(plugin);
  await settle();

  const Native = defined.get('os-location-button');
  assert.equal(typeof Native, 'function');
  assert.ok(Native.observedAttributes?.includes('text-type'), 'the native element observes its attributes');
  assert.equal(styles.length, 1, 'the sizing styles are installed once');
});
