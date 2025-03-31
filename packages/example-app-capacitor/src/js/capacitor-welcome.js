import { SplashScreen } from '@capacitor/splash-screen';
import { Geolocation } from '@capacitor/geolocation';

window.customElements.define(
  'capacitor-welcome',
  class extends HTMLElement {
    constructor() {
      super();

      SplashScreen.hide();

      const root = this.attachShadow({ mode: 'open' });

      root.innerHTML = `
    <style>
      :host {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        display: block;
        width: 100%;
        height: 100%;
      }
      h1, h2, h3, h4, h5 {
        text-transform: uppercase;
      }
      .button {
        display: inline-block;
        padding: 16px;
        background-color: #73B5F6;
        color: #fff;
        font-size: 1.1em;
        border: 0;
        border-radius: 3px;
        text-decoration: none;
        cursor: pointer;
      }
      main {
        padding: 15px;
      }
      main hr { height: 1px; background-color: #eee; border: 0; }
      main h1 {
        font-size: 1.4em;
        text-transform: uppercase;
        letter-spacing: 1px;
      }
      main h2 {
        font-size: 1.1em;
      }
      main h3 {
        font-size: 0.9em;
      }
      main p {
        color: #333;
      }
      main pre {
        white-space: pre-line;
      }
    </style>
    <div>
      <capacitor-welcome-titlebar>
        <h1>Capacitor Geolocation Example App</h1>
      </capacitor-welcome-titlebar>
      <main>
        <p>Below are the several features for the capacitor geolocation plugin. You may be prompted to grant location permission to the application.</p>
        <button id="check-permission" class="button">Check permission</button>
        <button id="request-permission" class="button">Request permission</button>
        <br><br>
        <label for="timeoutInput">timeout: </label>
        <input type="number" id="timeoutInput" name="timeoutInput"><br>
        <label for="ageInput">maximumAge: </label>
        <input type="number" id="ageInput" name="ageInput"><br>
        <input type="checkbox" id="highaccuracyCheck" name="highaccuracyCheck" checked="true"">
        <label for="highaccuracyCheck">enableHighAccuracy</label><br><br>
        <button id="current-location" class="button">Get Current (single) position</button>
        <br><br>
        <button id="watch-location" class="button">Watch position (updates)</button>
        <br><br>

        <!-- List to hold watch IDs -->
        <div id="watch-ids-container">
          <h2>Active watches (click one to stop receiving updates):</h2>
          <ul id="watch-id-list"></ul>
        </div>

        <br>

        <div id="watch-position-updates-container">
          <h2>Position updates are shown below:</h2>
          <button id="clear-position-updates" class="button">Clear list</button>
          <ul id="watch-position-updates-list"></ul>
        </div>
      </main>
    </div>
    `;
    }

    connectedCallback() {
      const self = this;

      self.shadowRoot.querySelector('#check-permission').addEventListener('click', async function (e) {
        const permissionStatus = await Geolocation.checkPermissions();
        alert(`Permissions are:\nlocation = ${permissionStatus.location}`)
      });
      self.shadowRoot.querySelector('#request-permission').addEventListener('click', async function (e) {
        const permissionStatus = await Geolocation.requestPermissions();
        alert(`Permissions are:\nlocation = ${permissionStatus.location}`)
      });

      self.shadowRoot.querySelector('#current-location').addEventListener('click', async function (e) {
        try {
          let options = createLocationOptions();
          const currentLocation = await Geolocation.getCurrentPosition(
            options
          );
          const locationString = locationToString(currentLocation, '')
          alert(locationString)
        } catch (exception) {
          alert(`Error getting current position:\n\t code=${exception.code}\n\t message=\"${exception.message}\"`)
        }
      });

      self.shadowRoot.querySelector('#watch-location').addEventListener('click', async function (e) {
        let watchId = ""
        try {
          let shouldAppendWatchId = true
          let options = createLocationOptions()
          watchId = await Geolocation.watchPosition(
            options,
            (position, err) => {
              if (err) {
                alert(`Error getting current position:\n\t code=${err.code}\n\t message=\"${err.message}\"`)
              } else {
                const locationString = locationToString(position, watchId)
                if (shouldAppendWatchId && watchId) {
                  shouldAppendWatchId = false
                  onWatchAdded(watchId);
                }
                const positionUpdatesList = self.shadowRoot.querySelector('#watch-position-updates-list');
                const newListItem = document.createElement('li');
                newListItem.textContent = locationString;
                 // 'pre-wrap' to make \n's count as line breaks
                newListItem.style.whiteSpace = 'pre-wrap'; 
                newListItem.style.padding = '10px';
                newListItem.style.borderBottom = '1px solid #ddd';
                // add to top of list
                if (positionUpdatesList.firstChild) {
                  positionUpdatesList.insertBefore(newListItem, positionUpdatesList.firstChild);
                } else {
                  positionUpdatesList.appendChild(newListItem);
                }
                console.log(locationString)
              }
            },
          );
        } catch (exception) {
          alert(`Error getting current position:\n\t code=${exception.code}\n\t message=\"${exception.message}\"`)
        }
      });

      self.shadowRoot.querySelector('#clear-position-updates').addEventListener('click', () => {
        const wacthesList = self.shadowRoot.querySelector('#watch-position-updates-list');
        wacthesList.innerHTML = '';
      });

      function createLocationOptions() {
        const enableHighAccuracyValue = self.shadowRoot.getElementById('highaccuracyCheck').checked;
        let options = { enableHighAccuracy: enableHighAccuracyValue }
        const timeoutValue = self.shadowRoot.getElementById('timeoutInput').value;
        if (timeoutValue) {
          options.timeout = Number(timeoutValue);
        }
        const ageValue = self.shadowRoot.getElementById('ageInput').value;
        if (ageValue) {
          options.maximumAge = Number(ageValue);
        }
        return options
      }

      function onWatchAdded(watchId) {
        // Append the watchId as a button to the list
        const watchIdListElement = self.shadowRoot.querySelector('#watch-id-list');
        const newListItem = document.createElement('li');
        const watchIdButton = document.createElement('button');
        watchIdButton.textContent = `Watch ID: ${watchId}`;
        watchIdButton.classList.add('watch-id-button');
        watchIdButton.style.cursor = 'pointer';

        watchIdButton.addEventListener('click',  async function (e) {
          // for simplicity, watch is always removed visually, regardless of clearWatch result
          newListItem.remove();
          await Geolocation.clearWatch({id: watchId});
        });

        newListItem.appendChild(watchIdButton);
        watchIdListElement.appendChild(newListItem);
      }

      function locationToString(location, watchId) {
        if (location == null || location == undefined) {
          return ""
        }
        let stringRepresentation = 'Position'
        if (watchId) {
          stringRepresentation += ` for watch ${watchId}:\n`
        } else {
          stringRepresentation += ':\n'
        }
        const timeRepresentation = location.timestamp ? new Date(location.timestamp).toISOString() : '-'
        stringRepresentation += `- Time: ${timeRepresentation}\n`
        stringRepresentation += `- Latitute: ${location?.coords.latitude}\n- Longitude: ${location?.coords.longitude}\n`
        if (location?.coords.altitude || location?.coords.heading || location?.coords.speed) {
          stringRepresentation += `- Altitude: ${location?.coords.altitude}\n- Heading: ${location?.coords.heading}\n- Speed: ${location?.coords.speed}\n`
        }
        stringRepresentation += `- Accuracy: ${location?.coords.accuracy}\n`
        if (location?.coords.altitudeAccuracy) {
          stringRepresentation += `- Altitude accuracy: ${location?.coords.altitudeAccuracy}\n`
        }
        return stringRepresentation
      }
    }
  }
);

window.customElements.define(
  'capacitor-welcome-titlebar',
  class extends HTMLElement {
    constructor() {
      super();
      const root = this.attachShadow({ mode: 'open' });
      root.innerHTML = `
    <style>
      :host {
        position: relative;
        display: block;
        padding: 60px 15px 15px 15px;
        text-align: center;
        background-color: #73B5F6;
      }
      ::slotted(h1) {
        margin: 0;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        font-size: 0.9em;
        font-weight: 600;
        color: #fff;
      }
    </style>
    <slot></slot>
    `;
    }
  }
);
