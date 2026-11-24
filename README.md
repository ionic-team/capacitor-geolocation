# @capacitor/geolocation

The Geolocation API provides simple methods for getting and tracking the current position of the device using GPS, along with altitude, heading, and speed information if available.

## Install

```bash
npm install @capacitor/geolocation
npx cap sync
```

## iOS

Apple requires privacy descriptions to be specified in `Info.plist` for location information:

- `NSLocationAlwaysAndWhenInUseUsageDescription` (`Privacy - Location Always and When In Use Usage Description`)
- `NSLocationWhenInUseUsageDescription` (`Privacy - Location When In Use Usage Description`)

:::info[Background Location Usage Strings]

This Capacitor plugin does not support background geolocation directly. However, it relies on
[`ion-ios-geolocation`](https://github.com/ionic-team/ion-ios-geolocation), which can report
location in the background. As a result, Apple requires you to include a
`NSLocationAlwaysAndWhenInUseUsageDescription` entry in your `Info.plist`. Since this permission
prompt won’t appear to users, you can safely use the same description string as for
`NSLocationWhenInUseUsageDescription`.

:::

Read about [Configuring `Info.plist`](https://capacitorjs.com/docs/ios/configuration#configuring-infoplist) in the [iOS Guide](https://capacitorjs.com/docs/ios) for more information on setting iOS permissions in Xcode

## Android

This plugin requires the following permissions be added to your `AndroidManifest.xml`:

```xml
<!-- Geolocation Plugin -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-feature android:name="android.hardware.location.gps" />
```

The first two permissions ask for location data, both fine and coarse, and the last line is optional but necessary if your app _requires_ GPS to function. You may leave it out, though keep in mind that this may mean your app is installed on devices lacking GPS hardware.

:::note

If you only require approximate location (variable accuracy but usually around 2 kilometers), you may just declare `ACCESS_COARSE_LOCATION` and `<uses-feature`, and use `enableHighAccuracy=false` when requesting location.

If you're trying to comply with the Location Button policy, please read the [location button section](#location-button) below.

:::

Read about [Setting Permissions](https://capacitorjs.com/docs/android/configuration#setting-permissions) in the [Android Guide](https://capacitorjs.com/docs/android) for more information on setting Android permissions.

## Location Button

`@capacitor/geolocation` includes `<os-location-button>`, the plugin's way to embed Android's system location button inside your web UI. When the user taps
it, your app gets precise location for the current session, without asking for the broader precise-location
permission. It is Android only: on iOS and web the element renders nothing.

### Do you need it?

The main reason to use it is Google Play. If your app uses Capacitor 9 or later (target Android API 37) and only needs precise
location for momentary, user-initiated features (such as "search near me" or sharing your location once),
Google Play policy requires you to use the location button, or to submit a declaration explaining why it
isn't enough. See
[Google Play's location permissions policy](https://support.google.com/googleplay/android-developer/answer/17033915)
and [Android's location button guide](https://developer.android.com/guide/topics/permissions/private-alternatives/location-button)
for the current rules and dates.

You can skip it if your app isn't distributed through Google Play, or if
it only needs approximate location. The rest of the plugin works as before either way: `getCurrentPosition()`,
`watchPosition()`, `checkPermissions()` and `requestPermissions()` are unchanged, and an app that doesn't add
the element behaves as before. The button's native code, including Jetpack Compose, is part of every Android
build that includes the plugin, so updating makes the app larger even if you never use the element.

### Using it in a new integration

1. Declare the location permissions in your `AndroidManifest.xml` as described in [Android](#android). To get
   precise location only through the button, also see
   [Limiting precise location to the button](#limiting-precise-location-to-the-button).
2. Add the element and listen to its events. Use
   [`getLocationButtonState()`](#getlocationbuttonstate) to show your own control where the button is
   `unavailable`:

```html
<os-location-button id="location-button"></os-location-button>
```

```ts
const { state } = await Geolocation.getLocationButtonState();
if (state === 'unavailable') {
  // Show your own control.
}

const button = document.querySelector('#location-button');
button.addEventListener('location-grant', (event) => console.log(event.detail.granted));
button.addEventListener('location-position', (event) => console.log(event.detail.coords));
button.addEventListener('location-error', (event) => console.log(event.detail.code, event.detail.reason));
```

On Android 17 and later (`system`) the element shows the system location button. On Android 16 and older
(`compat`) it shows Google's compatibility button, which triggers the standard permission prompt, so the same
code works on every Android version.

### Migrating an existing app

Updating the plugin changes nothing visible until you add the element, so you can migrate screen by screen.

1. On each screen that asks for precise location from a user action (for example a "Use my location" button),
   replace your own control with `<os-location-button>`:

   |                          | Before                                                                          | With the button                                         |
   | ------------------------ | ------------------------------------------------------------------------------- | ------------------------------------------------------- |
   | Ask for precise location | `requestPermissions()`, then `getCurrentPosition({ enableHighAccuracy: true })` | The user taps the element                               |
   | Permission result        | The result of `requestPermissions()`                                            | The `location-grant` event                              |
   | Position                 | The result of `getCurrentPosition()`                                            | The `location-position` event (same shape as `Position`) |
   | Errors                   | A rejected promise                                                              | The `location-error` event                              |

2. Keep your existing flow where the button isn't available. When `getLocationButtonState()` returns
   `unavailable` (iOS and web), the element renders nothing. When it returns `compat` (Android 16 and older),
   the element works the same way but is optional, so you can keep your existing flow there if you prefer.
3. Leave other location code alone. The button covers one-time, foreground requests; for continuous tracking
   with `watchPosition()`, see Google Play's policy above for what you need to declare.
4. Decide whether to add the `onlyForLocationButton` flag (below). It is optional. Without it, nothing else
   changes. With it, on Android 17 and later the rest of the plugin only gets approximate location until the user
   taps the button, so code that expects `checkPermissions()` to report `location: 'granted'` needs updating. Add
   it only once every precise-location use in your app goes through the button.

### Attributes

Set these on the element: `text-type` (`use-precise-location` by default, or `share-precise-location`,
`near-my-precise-location`, `near-your-precise-location`, `precise-location`, `none`), `timeout`
(10000 ms), `maximum-age` (0 ms) and `enable-location-fallback` (`true`). The system translates
the label, so it can differ from your app's language.

### Limiting precise location to the button

To grant precise location only through the button, keep `ACCESS_COARSE_LOCATION` and declare
`ACCESS_FINE_LOCATION` with the `onlyForLocationButton` flag:

```xml
<uses-permission
    android:name="android.permission.ACCESS_FINE_LOCATION"
    android:usesPermissionFlags="onlyForLocationButton" />
```

With this flag, on Android 17 and later, your app gets precise location only through the button.
The rest of the plugin works with approximate location:

- When `requestPermissions()`, `getCurrentPosition()` or `watchPosition()` ask for permission, the
  system dialog only offers approximate location.
- `getCurrentPosition()` and `watchPosition()` return approximate positions.
- `checkPermissions()` returns `location: 'denied'` and `coarseLocation: 'granted'`.

Once the user taps the button and allows precise location, the rest of the plugin also gets precise
location until the app is closed.

Below Android 17 there is no system button and the `onlyForLocationButton` flag is ignored, so the plugin's
other methods can still get precise location.

### Styling

Style the element with ordinary CSS. The icon can inherit `color` or use its
component-specific custom property:

```css
os-location-button {
  background-color: #0b57d0;
  color: #ffffff;
  --os-location-button-icon-color: #ffffff;
  border-color: #000000;
  border-width: 1px;
  border-radius: 16px;
  z-index: 2;
}
```

The native button follows these rules:

- Colors are opaque hexadecimal or RGB. A color with transparency (an alpha below 1) isn't supported and
  the default is used.
- Border width supports `0px` through `3px` and the corner radius `0px` through `68px`. A value outside
  that range, or a radius that isn't in pixels (such as `50%`), isn't clamped: the default (no border, a
  `16px` radius) is used.
- The border color and width are read from the element's top border and the radius from its top-left
  corner, and apply to the whole button.
- CSS filters, blend modes and masks on the element or its ancestors aren't applied.
- Android draws no button smaller than 48 by 48 pixels, or taller than 136 pixels (a taller element
  leaves empty space around the button).
- By default the element is as wide as its container and `3rem` tall (48 pixels at the default font
  size). Your own CSS overrides these defaults.
- The button is drawn slightly smaller than the element's box: 4 pixels inside it are kept for the touch
  target.

### When the button is covered

Android only lets the system button respond to taps when nothing covers it:

- The plugin clips the button around web content that overlaps its edges, such as a header or a sidebar.
- It hides the button while a cover it can't clip around remains, for example a modal dialog, a popup, a
  bottom sheet or a moving overlay, and shows it again when the cover is gone.
- Hiding the button doesn't emit `location-error` or any other event, so keep it out of areas other
  content can cover, or keep another way to ask for location.

## API

<docgen-index>

* [`getCurrentPosition(...)`](#getcurrentposition)
* [`watchPosition(...)`](#watchposition)
* [`clearWatch(...)`](#clearwatch)
* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions(...)`](#requestpermissions)
* [`getLocationButtonState()`](#getlocationbuttonstate)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

For list of error codes, see [Errors](#errors)

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### getCurrentPosition(...)

```typescript
getCurrentPosition(options?: PositionOptions | undefined) => Promise<Position>
```

Get the current GPS location of the device

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#positionoptions">PositionOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#position">Position</a>&gt;</code>

**Since:** 1.0.0

--------------------


### watchPosition(...)

```typescript
watchPosition(options: PositionOptions, callback: WatchPositionCallback) => Promise<CallbackID>
```

Set up a watch for location changes. Note that watching for location changes
can consume a large amount of energy. Be smart about listening only when you need to.

| Param          | Type                                                                    |
| -------------- | ----------------------------------------------------------------------- |
| **`options`**  | <code><a href="#positionoptions">PositionOptions</a></code>             |
| **`callback`** | <code><a href="#watchpositioncallback">WatchPositionCallback</a></code> |

**Returns:** <code>Promise&lt;string&gt;</code>

**Since:** 1.0.0

--------------------


### clearWatch(...)

```typescript
clearWatch(options: ClearWatchOptions) => Promise<void>
```

Clear a given watch

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code><a href="#clearwatchoptions">ClearWatchOptions</a></code> |

**Since:** 1.0.0

--------------------


### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionStatus>
```

Check location permissions.  Will throw if system location services are disabled.

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

**Since:** 1.0.0

--------------------


### requestPermissions(...)

```typescript
requestPermissions(permissions?: GeolocationPluginPermissions | undefined) => Promise<PermissionStatus>
```

Request location permissions.  Will throw if system location services are disabled.

Not available on web.

| Param             | Type                                                                                  |
| ----------------- | ------------------------------------------------------------------------------------- |
| **`permissions`** | <code><a href="#geolocationpluginpermissions">GeolocationPluginPermissions</a></code> |

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

**Since:** 1.0.0

--------------------


### getLocationButtonState()

```typescript
getLocationButtonState() => Promise<LocationButtonState>
```

Check which location button this device supports.

The button is Android only. Use this to show your own control where it is `unavailable`, or to
hide the `compat` button on older Android versions.

**Returns:** <code>Promise&lt;<a href="#locationbuttonstate">LocationButtonState</a>&gt;</code>

**Since:** 9.0.0

--------------------


### Interfaces


#### Position

| Prop            | Type                                                                                                                                                                                                                                                                                                       | Description                                             | Since |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- | ----- |
| **`timestamp`** | <code>number</code>                                                                                                                                                                                                                                                                                        | Creation timestamp for coords                           | 1.0.0 |
| **`coords`**    | <code>{ latitude: number; longitude: number; accuracy: number; altitudeAccuracy: number \| null; altitude: number \| null; speed: number \| null; heading: number \| null; magneticHeading: number \| null; trueHeading: number \| null; headingAccuracy: number \| null; course: number \| null; }</code> | The GPS coordinates along with the accuracy of the data | 1.0.0 |


#### PositionOptions

| Prop                         | Type                 | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Default                | Since |
| ---------------------------- | -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------- | ----- |
| **`enableHighAccuracy`**     | <code>boolean</code> | High accuracy mode (such as GPS, if available) On Android 12+ devices it will be ignored if users didn't grant ACCESS_FINE_LOCATION permissions (can be checked with location alias).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | <code>false</code>     | 1.0.0 |
| **`timeout`**                | <code>number</code>  | The maximum wait time in milliseconds for location updates.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | <code>10000</code>     | 1.0.0 |
| **`maximumAge`**             | <code>number</code>  | The maximum age in milliseconds of a possible cached position that is acceptable to return                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | <code>0</code>         | 1.0.0 |
| **`minimumUpdateInterval`**  | <code>number</code>  | The minimum update interval for `watchPosition`. Not to be confused with `interval`. If location updates are available faster than this interval then an update will only occur if the minimum update interval has expired since the last location update. This parameter is only available for Android. It has no effect on iOS or Web platforms.                                                                                                                                                                                                                                                                                                                                                                                                  | <code>5000</code>      | 6.1.0 |
| **`interval`**               | <code>number</code>  | Desired interval in milliseconds to receive location updates in `watchPosition`. For very low values of `interval` (a couple seconds or less), the platform may not guarantee timely location updates - they may take longer than specified. The platform may also be able to provide location updates faster than `interval`. You may use `minimumUpdateInterval` to control that behavior. For backwards compatibility with version 7.1.x, if no value is passed, the default value of this parameter is that of `timeout`. This parameter is only available for Android. It has no effect on iOS or Web platforms.                                                                                                                               | <code>`timeout`</code> | 8.0.0 |
| **`enableLocationFallback`** | <code>boolean</code> | Whether to fall back to the Android framework's `LocationManager` in case Google Play Service's location settings checks fail. This can happen for multiple reasons - e.g. device has no Play Services or device has no network connection (Airplane Mode) If set to `false`, failures are propagated to the caller. Note that `LocationManager` may not be as effective as Google Play Services implementation. If the device's in airplane mode, only the GPS provider is used, which may take longer to return a location, depending on GPS signal. This means that to receive location in such circumstances, you may need to provide a higher timeout. This parameter is only available for Android. It has no effect on iOS or Web platforms. | <code>true</code>      | 8.0.0 |


#### ClearWatchOptions

| Prop     | Type                                              |
| -------- | ------------------------------------------------- |
| **`id`** | <code><a href="#callbackid">CallbackID</a></code> |


#### PermissionStatus

| Prop                 | Type                                                        | Description                                                                                                                                                                                                                                                                                                                                                        | Since |
| -------------------- | ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----- |
| **`location`**       | <code><a href="#permissionstate">PermissionState</a></code> | Permission state for location alias. On Android it requests/checks both ACCESS_COARSE_LOCATION and ACCESS_FINE_LOCATION permissions. On iOS and web it requests/checks location permission.                                                                                                                                                                        | 1.0.0 |
| **`coarseLocation`** | <code><a href="#permissionstate">PermissionState</a></code> | Permission state for coarseLocation alias. On Android it requests/checks ACCESS_COARSE_LOCATION. On Android 12+, users can choose between Approximate location (ACCESS_COARSE_LOCATION) or Precise location (ACCESS_FINE_LOCATION), so this alias can be used if the app doesn't need high accuracy. On iOS and web it will have the same value as location alias. | 1.2.0 |


#### GeolocationPluginPermissions

| Prop              | Type                                     |
| ----------------- | ---------------------------------------- |
| **`permissions`** | <code>GeolocationPermissionType[]</code> |


#### LocationButtonState

| Prop        | Type                                               | Description                                                                                                                                                                                                                              | Since |
| ----------- | -------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- |
| **`state`** | <code>'system' \| 'compat' \| 'unavailable'</code> | `system` on Android 17 and later, where the system draws the button. `compat` on older Android versions, which show Google's compatibility button. `unavailable` on iOS and web, where the `os-location-button` element renders nothing. | 9.0.0 |


### Type Aliases


#### WatchPositionCallback

<code>(position: <a href="#position">Position</a> | null, err?: any): void</code>


#### CallbackID

<code>string</code>


#### PermissionState

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>


#### GeolocationPermissionType

<code>'location' | 'coarseLocation'</code>

</docgen-api>

### Errors

The plugin returns specific errors with specific codes on native Android and iOS. Web does not follow this standard for errors. 

The following table list all the plugin errors:

| Error code           | Platform(s)  | Message                                  |
| -------------------- | ------------ | ---------------------------------------- |
| OS-PLUG-GLOC-0002 | Android, iOS | There was en error trying to obtain the location. |
| OS-PLUG-GLOC-0003 | Android, iOS | Location permission request was denied. |
| OS-PLUG-GLOC-0004 | iOS          | The 'getCurrentPosition' input parameters aren't valid. |
| OS-PLUG-GLOC-0005 | iOS          | The 'watchPosition' input parameters aren't valid. |
| OS-PLUG-GLOC-0006 | iOS          | The 'clearWatch' input parameters aren't valid. |
| OS-PLUG-GLOC-0007 | Android, iOS | Location services are not enabled. |
| OS-PLUG-GLOC-0008 | iOS          | Application's use of location services was restricted. |
| OS-PLUG-GLOC-0009 | Android      | Request to enable location was denied. |
| OS-PLUG-GLOC-0010 | Android, iOS | Could not obtain location in time. Try with a higher timeout. |
| OS-PLUG-GLOC-0011 | Android      | Timeout needs to be a positive value. |
| OS-PLUG-GLOC-0012 | Android      | WatchId not found. |
| OS-PLUG-GLOC-0013 | Android      | WatchId needs to be provided. |
| OS-PLUG-GLOC-0014 | Android      | Google Play Services error user resolvable. |
| OS-PLUG-GLOC-0015 | Android      | Google Play Services error. |
| OS-PLUG-GLOC-0016 | Android      | Location settings error. |
| OS-PLUG-GLOC-0017 | Android      | Unable to retrieve location because device has both Network and Location turned off. |
| OS-PLUG-GLOC-0018 | Android      | Location permissions are not declared in manifest. Make sure at least ACCESS_COARSE_LOCATION is declared in AndroidManifest.xml, and optionally ACCESS_FINE_LOCATION if you require precise location access. |
| OS-PLUG-GLOC-0019 | Android      | The location button reported an error. |
