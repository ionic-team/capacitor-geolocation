# Contributing

This guide provides instructions for contributing to this Capacitor plugin.

## Developing

### Local Setup

1. Fork and clone the repo.
2. If you plan to create a new feature or fix a bug, checkout `development` branch (in general Pull Requests should be open for that branch)
3. Install the dependencies.

    ```shell
    npm install
    ```

4. Install SwiftLint if you're on macOS.

    ```shell
    brew install swiftlint
    ```

### Scripts

#### `npm run build`

Build the plugin web assets and generate plugin API documentation using [`@capacitor/docgen`](https://github.com/ionic-team/capacitor-docgen).

It will compile the TypeScript code from `src/` into ESM JavaScript in `dist/esm/`. These files are used in apps with bundlers when your plugin is imported.

Then, Rollup will bundle the code into a single file at `dist/plugin.js`. This file is used in apps without bundlers by including it as a script in `index.html`.

#### `npm run verify`

Build and validate the web and native projects.

This is useful to run in CI to verify that the plugin builds for all platforms.

#### `npm run lint` / `npm run fmt`

Check formatting and code quality, autoformat/autofix if possible.

This template is integrated with ESLint, Prettier, and SwiftLint. Using these tools is completely optional, but the [Capacitor Community](https://github.com/capacitor-community/) strives to have consistent code style and structure for easier cooperation.

## Publishing

Publishing is automated based on the branch committed to. When a commit or merge is made to a branch a release that corresponds with the branch will be created:

| Branch Name | Build Type | NPM Tag | Example NPM Version |
|---|---|---|---|
| development | dev | dev | @capacitor/geolocation@7.1.0-dev.1 |
| next | next (these are betas/alphas) | next | @capacitor/geolocation@7.1.0-next.1 |
| main | latest | latest | @capacitor/geolocation@7.1.0 |

- Dev work should be done by creating and merging PR's into the `development` branch until a feature set is complete enough to form a release.
- When a feature set is complete enough to form a release, merge the `development` branch into the `next` branch where it becomes a beta/alpha tagged under `next` for testing teams to use before full release. In case a PR is opened from `development` to `next`, avoid squashing the commits, to keep the history.
- Upon completed testing the `next` branch is merged into `main` for a full release to be made. In case a PR is opened from `next` to `main`, avoid squashing the commits, to keep the history.
- The `main` branch should then be merged into `dev` and `next` to keep them up to date with the latest code base.

> **Note**: The [`files`](https://docs.npmjs.com/cli/v7/configuring-npm/package-json#files) array in `package.json` specifies which files get published. If you rename files/directories or add files elsewhere, you may need to update it.
