import assert from 'node:assert/strict';
import test from 'node:test';

import { installFakeDom, locationButtonUrl, removeFakeDom, settle, skip, throwingPlugin, webUrl } from './helpers.mjs';

test('web: getLocationButtonState reports the button as unavailable', { skip }, async () => {
  const { GeolocationWeb } = await import(webUrl);

  assert.deepEqual(await new GeolocationWeb().getLocationButtonState(), { state: 'unavailable' });
});

test('outside Android: the element is hidden and the native state is not queried', { skip }, async (t) => {
  const { defined } = installFakeDom({ android: false });
  t.after(removeFakeDom);
  const { registerLocationButtonElement } = await import(locationButtonUrl);

  registerLocationButtonElement(throwingPlugin());

  const Hidden = defined.get('os-location-button');
  assert.equal(typeof Hidden, 'function');
  const styles = {};
  const element = new Hidden();
  element.style = { setProperty: (name, value, priority) => (styles[name] = [value, priority]) };
  element.connectedCallback();
  assert.deepEqual(styles, { display: ['none', 'important'] });
});

test('outside Android: an element that is already defined is left alone', { skip }, async (t) => {
  const { defined } = installFakeDom({ android: false });
  t.after(removeFakeDom);
  const existing = class {};
  defined.set('os-location-button', existing);
  const { registerLocationButtonElement } = await import(locationButtonUrl);

  registerLocationButtonElement(throwingPlugin());

  assert.equal(defined.get('os-location-button'), existing);
});

test(
  'Android: when the state cannot be read, the element is hidden and nothing is left unhandled',
  { skip },
  async (t) => {
    const { defined } = installFakeDom({ android: true });
    t.after(removeFakeDom);
    const unhandled = [];
    const onUnhandled = (reason) => unhandled.push(reason);
    process.on('unhandledRejection', onUnhandled);
    t.after(() => process.off('unhandledRejection', onUnhandled));
    const { registerLocationButtonElement } = await import(locationButtonUrl);
    const plugin = { ...throwingPlugin(), getLocationButtonState: () => Promise.reject(new Error('UNIMPLEMENTED')) };

    registerLocationButtonElement(plugin);
    await settle();

    assert.deepEqual(unhandled, []);
    const Hidden = defined.get('os-location-button');
    assert.equal(typeof Hidden, 'function');
    assert.equal(Hidden.observedAttributes, undefined, 'the hidden element is not the native one');
  },
);
