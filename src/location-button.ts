import { Capacitor } from '@capacitor/core';
import {
  createCapacitorTransport,
  defineNativeIsland,
  initializeNativeIslands,
  NATIVE_ISLANDS_TRANSPORT_PRIORITY,
} from '@capacitor/native-islands/internal';

import type { GeolocationPlugin, Position } from './definitions.js';

export interface LocationButtonGrantDetail {
  granted: boolean;
}

export type LocationButtonPositionDetail = Position;

export interface LocationButtonErrorDetail {
  reason: string;
  code?: string;
}

declare global {
  interface HTMLElementEventMap {
    'location-grant': CustomEvent<LocationButtonGrantDetail>;
    'location-position': CustomEvent<LocationButtonPositionDetail>;
    'location-error': CustomEvent<LocationButtonErrorDetail>;
  }
}

const TEXT_TYPES = [
  'use-precise-location',
  'share-precise-location',
  'near-my-precise-location',
  'near-your-precise-location',
  'precise-location',
  'none',
];

const STYLE_PROPERTIES = {
  backgroundColor: 'background-color',
  textColor: 'color',
  iconTint: '--os-location-button-icon-color',
  strokeColor: 'border-top-color',
  strokeWidth: 'border-top-width',
} as const;
const OBSERVED_ATTRIBUTES = ['text-type', 'maximum-age', 'timeout', 'enable-location-fallback'];
const OBSERVED_STYLES = [
  STYLE_PROPERTIES.backgroundColor,
  STYLE_PROPERTIES.textColor,
  STYLE_PROPERTIES.iconTint,
  STYLE_PROPERTIES.strokeColor,
  STYLE_PROPERTIES.strokeWidth,
  'border-top-left-radius',
];

const HEX_COLOR = /^#[0-9A-Fa-f]{6}$/;
const RGB_COLOR = /^rgba?\((.+)\)$/;

function textType(element: HTMLElement): string {
  const value = element.getAttribute('text-type') ?? 'use-precise-location';
  return TEXT_TYPES.includes(value) ? value : 'use-precise-location';
}

function colorStyle(style: CSSStyleDeclaration, name: string, fallback: string): string {
  const value = style.getPropertyValue(name).trim();
  if (HEX_COLOR.test(value)) return value.toUpperCase();
  const match = value.match(RGB_COLOR);
  if (!match) return fallback;
  const channels = match[1].match(/\d+(?:\.\d+)?/g)?.map(Number);
  if (!channels || channels.length < 3 || channels.slice(0, 3).some((channel) => channel < 0 || channel > 255)) {
    return fallback;
  }
  if (channels.length > 3 && channels[3] < 1) return fallback;
  return `#${channels
    .slice(0, 3)
    .map((channel) => Math.round(channel).toString(16).padStart(2, '0'))
    .join('')
    .toUpperCase()}`;
}

function pixelStyle(
  style: CSSStyleDeclaration,
  name: string,
  minimum: number,
  maximum: number,
  fallback: number,
): number {
  const value = style.getPropertyValue(name).trim();
  if (!value.endsWith('px')) return fallback;
  const number = Number.parseFloat(value);
  return Number.isFinite(number) && number >= minimum && number <= maximum ? number : fallback;
}

function nonNegativeIntegerAttribute(element: HTMLElement, name: string, fallback: number, minimum = 0): number {
  const value = element.getAttribute(name);
  if (value === null) return fallback;
  const number = Number.parseInt(value, 10);
  return Number.isFinite(number) && number >= minimum ? number : fallback;
}

function booleanAttribute(element: HTMLElement, name: string, fallback: boolean): boolean {
  const value = element.getAttribute(name);
  if (value === null) return fallback;
  if (value === 'true') return true;
  if (value === 'false') return false;
  return fallback;
}

const installedStyleRoots = new WeakSet<Document | ShadowRoot>();

// The element can live inside a shadow root, which CSS in document.head never reaches. Without its
// sizing CSS it collapses to zero size and the native button has nothing to fill.
function styleRootFor(element: HTMLElement): Document | ShadowRoot {
  const root = element.getRootNode();
  return root instanceof ShadowRoot ? root : document;
}

function installStyles(root: Document | ShadowRoot = document): void {
  if (installedStyleRoots.has(root)) return;
  installedStyleRoots.add(root);
  if (root.querySelector('style[data-os-location-button]')) return;

  const style = document.createElement('style');
  style.dataset.osLocationButton = '';
  style.textContent = `
    :where(os-location-button) {
      display: inline-block;
      inline-size: 100%;
      min-inline-size: 3rem;
      block-size: 3rem;
      min-block-size: 3rem;
      max-block-size: 136px;
      box-sizing: border-box;
      overflow: hidden;
      border: 0 solid #000000;
      border-radius: 16px;
      background-color: #0b57d0;
      color: #ffffff;
    }

    :where(os-location-button[data-native-islands-fallback]) {
      background-clip: text !important;
      border-image-source: linear-gradient(transparent, transparent) !important;
      border-image-slice: 1 !important;
    }
  `;

  if (root instanceof ShadowRoot) {
    root.append(style);
    return;
  }

  if (document.head) {
    document.head.append(style);
    return;
  }

  document.addEventListener(
    'DOMContentLoaded',
    () => {
      if (!document.querySelector('style[data-os-location-button]')) {
        document.head?.append(style);
      }
    },
    { once: true },
  );
}

function registerLocationButton(protectedSurface: boolean): void {
  if (
    typeof document === 'undefined' ||
    typeof HTMLElement === 'undefined' ||
    typeof customElements === 'undefined' ||
    customElements.get('os-location-button')
  ) {
    return;
  }

  defineNativeIsland({
    tagName: 'os-location-button',
    nativeComponent: 'os.locationButton',
    isInteractive: true,
    accessibility: 'native',
    requiresUnobscuredSurface: protectedSurface,
    supportsProtectedSurfaceClip: protectedSurface,
    observedAttributes: OBSERVED_ATTRIBUTES,
    observedStyles: OBSERVED_STYLES,
    getProperties: (element) => {
      // Runs synchronously during connectedCallback() (activateNative() -> send('create')),
      // before any async/rAF-scheduled geometry measurement — early enough to guarantee the
      // element has sizing CSS before it's first measured, even inside a shadow root.
      installStyles(styleRootFor(element));
      const style = getComputedStyle(element);
      const cornerRadius = pixelStyle(style, 'border-top-left-radius', 0, 68, 16);
      const textColor = colorStyle(style, STYLE_PROPERTIES.textColor, '#FFFFFF');
      return {
        textType: textType(element),
        backgroundColor: colorStyle(style, STYLE_PROPERTIES.backgroundColor, '#0B57D0'),
        textColor,
        iconTint: colorStyle(style, STYLE_PROPERTIES.iconTint, textColor),
        strokeColor: colorStyle(style, STYLE_PROPERTIES.strokeColor, '#000000'),
        cornerRadius,
        strokeWidth: pixelStyle(style, STYLE_PROPERTIES.strokeWidth, 0, 3, 0),
        maximumAge: nonNegativeIntegerAttribute(element, 'maximum-age', 3_000),
        timeout: nonNegativeIntegerAttribute(element, 'timeout', 5_000, 1),
        enableLocationFallback: booleanAttribute(element, 'enable-location-fallback', true),
      };
    },
    renderFallback: () => undefined,
    events: {
      grant: 'location-grant',
      position: 'location-position',
      buttonError: 'location-error',
    },
  });
}

export function registerLocationButtonElement(geolocationPlugin: GeolocationPlugin): void {
  if (Capacitor.getPlatform() !== 'android') {
    if (typeof customElements !== 'undefined' && !customElements.get('os-location-button')) {
      customElements.define(
        'os-location-button',
        class extends HTMLElement {
          connectedCallback(): void {
            this.style.setProperty('display', 'none', 'important');
          }
        },
      );
    }
    return;
  }

  const dedicatedNativeIslands = Capacitor.isPluginAvailable('NativeIslands');
  initializeNativeIslands(
    createCapacitorTransport(
      dedicatedNativeIslands
        ? {
            pluginName: 'NativeIslands',
            platforms: ['android'],
          }
        : {
            pluginName: 'Geolocation',
            plugin: geolocationPlugin,
            methods: {
              applyLayout: 'nativeIslandsApplyLayout',
              applyScrollOffsets: 'nativeIslandsApplyScrollOffsets',
              command: 'nativeIslandsCommand',
              reset: 'nativeIslandsReset',
            },
            eventPrefix: 'nativeIslands:',
            platforms: ['android'],
          },
    ),
    {
      identity: dedicatedNativeIslands ? '@capacitor/native-islands' : '@capacitor/geolocation/location-button',
      priority: dedicatedNativeIslands
        ? NATIVE_ISLANDS_TRANSPORT_PRIORITY.dedicated
        : NATIVE_ISLANDS_TRANSPORT_PRIORITY.carrier,
    },
  );

  void geolocationPlugin.getLocationButtonState().then(({ state }) => {
    installStyles();
    registerLocationButton(state === 'system');
  });
}
