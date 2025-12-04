# react-native-app-monitor

Application monitoring tool

## Installation

```sh
npm install react-native-app-monitor
# or
yarn add react-native-app-monitor
```

### Local Development

If you're developing the library locally and linking it using `file:` protocol:

```json
{
  "dependencies": {
    "react-native-app-monitor": "file:/path/to/react-native-app-monitor"
  }
}
```

**Important:** You need to configure Metro to ignore the library's `node_modules` to avoid processing React Native source files. Add this to your consuming app's `metro.config.js`:

```js
const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');

const config = {
  resolver: {
    blockList: [
      /.*\/react-native-app-monitor\/node_modules\/react-native\/.*/,
    ],
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
```

Alternatively, you can use only the bundled code by ensuring the library is built (`yarn prepare`) and Metro will use `lib/module/index.js` instead of source files.

### iOS Setup

**Prerequisites**: This library requires access to the private CocoaPods spec repository where `AppMonitor ~> 1.0.0` is hosted.

Add the private spec repository to your `Podfile`:

```ruby
source 'https://github.com/nammayatri/ny-cocoapods-specs.git'  # Private spec repo
source 'https://cdn.cocoapods.org/'  # CocoaPods master
```

Then run:
```sh
cd ios && pod install
```

### Android Setup

**Prerequisites**: This library requires access to the private Maven repository where `com.movingtech:appmonitor:1.0.2@aar` is hosted.

Add the Maven repository to your app's `build.gradle`:

```gradle
repositories {
    maven {
        url "YOUR_MAVEN_REPO_URL"  // Replace with your Maven repository URL
    }
    google()
    mavenCentral()
}
```

The Android SDK will be automatically resolved once the repository is configured.

## Usage

```js
import AppMonitor from 'react-native-app-monitor';

// Add a metric
AppMonitor.addMetric('response_time', 150.5);

// Add an event
AppMonitor.addEvent('user_action', 'button_click', {
  buttonId: 'submit',
  screen: 'home'
});

// Add a log
AppMonitor.addLog('info', 'User logged in', 'Auth', {
  userId: '123',
  timestamp: Date.now().toString()
});

// Get session ID
const sessionId = AppMonitor.getSessionId();

// Replace user ID (async)
await AppMonitor.replaceUserId('new-user-id');

// Reset user ID
AppMonitor.resetUserId();

// Generate new session
const newSessionId = AppMonitor.generateNewSession();
```

## Troubleshooting

### SyntaxError with React Native internal files

If you encounter errors like `SyntaxError: ';' expected` in React Native's internal files (e.g., `VirtualView.js` with `match` syntax), this is usually related to:

1. **Node.js version compatibility:**
   - This package requires Node.js >= 18.0.0
   - Node.js 22.x may have different module resolution behavior
   - Try using Node.js 18.x or 20.x LTS versions if you encounter issues

2. **React Native version mismatch:**
   - Ensure your app uses React Native 0.81.1 (or compatible version)
   - If you see `node_modules/react-native-app-monitor/node_modules/react-native/`, there's a version mismatch
   - Remove nested node_modules: `rm -rf node_modules/react-native-app-monitor/node_modules`

3. **Clear caches and reinstall:**
   ```sh
   # Clear Metro bundler cache
   npx react-native start --reset-cache
   
   # Clear watchman
   watchman watch-del-all
   
   # Remove nested React Native if it exists
   rm -rf node_modules/react-native-app-monitor/node_modules/react-native
   
   # Clear node_modules and reinstall
   rm -rf node_modules
   yarn install  # or npm install
   ```

4. **Check Metro configuration:**
   - Ensure your `metro.config.js` properly excludes React Native's internal files from transpilation
   - React Native files should be handled by React Native's own Babel preset
   - Add this to your `metro.config.js` if needed:
   ```js
   resolver: {
     blockList: [
       /node_modules\/react-native-app-monitor\/node_modules\/react-native\/.*/,
     ],
   },
   ```


## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
