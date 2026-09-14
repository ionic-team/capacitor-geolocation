import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const transport = readFileSync(new URL('../src/location-button.ts', import.meta.url), 'utf8');

test('Native Islands subscribes before location-button registration can apply layout', () => {
  const registration = transport.slice(transport.indexOf('export function registerLocationButtonElement'));
  const initialize = registration.indexOf('initializeNativeIslands(');
  const register = registration.indexOf('requiresUnobscuredSurface(geolocationPlugin).then');

  assert.ok(initialize >= 0);
  assert.ok(register > initialize);
});
