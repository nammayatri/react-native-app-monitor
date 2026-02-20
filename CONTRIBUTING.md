# Contributing

Contributions are always welcome, no matter how large or small!

We want this community to be friendly and respectful to each other. Please follow it in all your interactions with the project. Before contributing, please read the [code of conduct](./CODE_OF_CONDUCT.md).

## Development workflow

This project is a monorepo managed using [Yarn workspaces](https://yarnpkg.com/features/workspaces). It contains the following packages:

- The library package in the root directory.
- An example app in the `example/` directory.

To get started with the project, make sure you have the correct version of [Node.js](https://nodejs.org/) installed. See the [`.nvmrc`](./.nvmrc) file for the version used in this project.

Run `yarn` in the root directory to install the required dependencies for each package:

```sh
yarn
```

> Since the project relies on Yarn workspaces, you cannot use [`npm`](https://github.com/npm/cli) for development without manually migrating.

The [example app](/example/) demonstrates usage of the library. You need to run it to test any changes you make.

It is configured to use the local version of the library, so any changes you make to the library's source code will be reflected in the example app. Changes to the library's JavaScript code will be reflected in the example app without a rebuild, but native code changes will require a rebuild of the example app.

If you want to use Android Studio or Xcode to edit the native code, you can open the `example/android` or `example/ios` directories respectively in those editors. To edit the Objective-C or Swift files, open `example/ios/AppMonitorExample.xcworkspace` in Xcode and find the source files at `Pods > Development Pods > react-native-app-monitor`.

To edit the Java or Kotlin files, open `example/android` in Android studio and find the source files at `react-native-app-monitor` under `Android`.

You can use various commands from the root directory to work with the project.

To start the packager:

```sh
yarn example start
```

To run the example app on Android:

```sh
yarn example android
```

To run the example app on iOS:

```sh
yarn example ios
```

To confirm that the app is running with the new architecture, you can check the Metro logs for a message like this:

```sh
Running "AppMonitorExample" with {"fabric":true,"initialProps":{"concurrentRoot":true},"rootTag":1}
```

Note the `"fabric":true` and `"concurrentRoot":true` properties.

Make sure your code passes TypeScript:

```sh
yarn typecheck
```

To check for linting errors, run the following:

```sh
yarn lint
```

To fix formatting errors, run the following:

```sh
yarn lint --fix
```

Remember to add tests for your change if possible. Run the unit tests by:

```sh
yarn test
```


### Commit message convention

We follow the [conventional commits specification](https://www.conventionalcommits.org/en) for our commit messages:

- `fix`: bug fixes, e.g. fix crash due to deprecated method.
- `feat`: new features, e.g. add new method to the module.
- `refactor`: code refactor, e.g. migrate from class components to hooks.
- `docs`: changes into documentation, e.g. add usage example for the module.
- `test`: adding or updating tests, e.g. add integration tests using detox.
- `chore`: tooling changes, e.g. change CI config.

Our pre-commit hooks verify that your commit message matches this format when committing.


### Publishing to npm

We use [release-it](https://github.com/release-it/release-it) to make it easier to publish new versions. It handles common tasks like bumping version based on semver, creating tags and releases etc.

#### Prerequisites

1. **npm authentication**: You must be logged in to npm with publish access:
   ```sh
   npm login
   ```

2. **GitHub token** (optional): For automated GitHub releases, set the `GITHUB_TOKEN` environment variable. Without it, release-it will fall back to web-based GitHub release.

#### Release Process

1. **Ensure your working directory is clean**:
   ```sh
   git status
   ```

2. **Run the release command**:
   ```sh
   yarn release
   ```

3. **Follow the prompts**:
   - Confirm the version bump (patch/minor/major)
   - Confirm publishing to npm
   - Confirm creating a GitHub release

#### What happens during release

1. **Version bump**: Updates `version` in `package.json`
2. **Build**: Runs `bob build` via the `prepack` script to generate the `lib/` folder
3. **npm publish**: Publishes the package to npm registry (includes `lib/` folder)
4. **Git commit & tag**: Commits the version bump and creates a git tag
5. **GitHub release**: Creates a GitHub release with changelog

#### Build Output

The library uses [react-native-builder-bob](https://github.com/callstack/react-native-builder-bob) to compile TypeScript to JavaScript.

- **Source**: `src/` directory (TypeScript)
- **Output**: `lib/` directory (compiled JavaScript + type definitions)
- **Note**: `lib/` is in `.gitignore` and not committed to git. It's only included in the npm package.

#### Troubleshooting 403 Forbidden (2FA)

If you encounter a `403 Forbidden` error during publishing, it's likely due to npm's Two-Factor Authentication (2FA) requirement.

**1. Using an OTP (One-Time Password)**
You can provide an OTP code directly to the release command:
```sh
yarn release --npm.otp=<your-otp-code>
```
*Note: The code expires quickly, so have your authenticator app ready.*

**2. Using a Granular Access Token**
If you use a token via the `NPM_TOKEN` environment variable or `.npmrc`, ensure it is a **Granular Access Token** with:
- `Read and write` access to this package.
- **"Bypass two-factor authentication for publishing"** enabled in the token settings on npmjs.com.

#### Manual Publishing (if needed)

If you need to publish manually without using release-it:

```sh
# 1. Update version in package.json manually

# 2. Build the library
yarn build

# 3. Verify the build output
npm pack
tar -tf react-native-app-monitor-*.tgz

# 4. Publish to npm
npm publish

# 5. Commit, tag, and push
git add package.json
git commit -m "chore: release x.x.x"
git tag vx.x.x
git push origin main --tags
```


### Scripts

The `package.json` file contains various scripts for common tasks:

- `yarn`: setup project by installing dependencies.
- `yarn typecheck`: type-check files with TypeScript.
- `yarn lint`: lint files with [ESLint](https://eslint.org/).
- `yarn test`: run unit tests with [Jest](https://jestjs.io/).
- `yarn example start`: start the Metro server for the example app.
- `yarn example android`: run the example app on Android.
- `yarn example ios`: run the example app on iOS.

### Sending a pull request

> **Working on your first pull request?** You can learn how from this _free_ series: [How to Contribute to an Open Source Project on GitHub](https://app.egghead.io/playlists/how-to-contribute-to-an-open-source-project-on-github).

When you're sending a pull request:

- Prefer small pull requests focused on one change.
- Verify that linters and tests are passing.
- Review the documentation to make sure it looks good.
- Follow the pull request template when opening a pull request.
- For pull requests that change the API or implementation, discuss with maintainers first by opening an issue.
