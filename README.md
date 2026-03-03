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

The Podfile automatically detects and uses the working authentication method:
- **Tries HTTPS first** (works with PAT, credential helper, or GitHub CLI)
- **Falls back to SSH** if HTTPS fails
- **No manual configuration needed** for most users

**For most users (zero steps needed):**
- If you have GitHub CLI: Already configured, works automatically
- If you use Git credential helper: Already configured, works automatically  
- If you use SSH keys: Works automatically (fallback)

**Optional one-time setup (only if automatic detection fails):**
- HTTPS with PAT: `git config --global url."https://YOUR_PAT@github.com/".insteadOf "https://github.com/"`
- Or use GitHub CLI: `gh auth setup-git`

Then run:
```sh
cd ios && pod install
```

**Note:** If you're using the example app, the Podfile is already configured with auto-detection.

**For your own app**, you have two options:

**Option 1: Use auto-detection (recommended - works for both PAT and SSH users)**

Add this to your Podfile (auto-detects and uses the working authentication method):

```ruby
# Auto-detect working authentication method for private spec repo
def get_cocoapods_spec_repo_url
  require 'timeout'
  https_url = 'https://github.com/nammayatri/ny-cocoapods-specs.git'
  ssh_url = 'git@github.com:nammayatri/ny-cocoapods-specs.git'
  
  # Test HTTPS access (with timeout to avoid hanging)
  https_works = false
  begin
    Timeout.timeout(5) do
      result = system("git ls-remote #{https_url} > /dev/null 2>&1")
      https_works = result == true
    end
  rescue Timeout::Error, StandardError
    https_works = false
  end
  
  if https_works
    Pod::UI.puts "✓ Using HTTPS for CocoaPods spec repo".green
    return https_url
  else
    # Test SSH access
    ssh_works = false
    begin
      Timeout.timeout(5) do
        result = system("git ls-remote #{ssh_url} > /dev/null 2>&1")
        ssh_works = result == true
      end
    rescue Timeout::Error, StandardError
      ssh_works = false
    end
    
    if ssh_works
      Pod::UI.puts "✓ Using SSH for CocoaPods spec repo (HTTPS failed)".yellow
      return ssh_url
    else
      Pod::UI.puts "⚠ Warning: Could not access private spec repo with HTTPS or SSH".yellow
      Pod::UI.puts "  Falling back to HTTPS. Ensure you have proper authentication configured.".yellow
      return https_url
    end
  end
rescue => e
  Pod::UI.puts "⚠ Could not test repo access, defaulting to HTTPS".yellow
  return https_url
end

# Add the private spec repo (auto-detects HTTPS or SSH)
source get_cocoapods_spec_repo_url
source 'https://cdn.cocoapods.org/'
```

**Option 2: Manual configuration**
If you prefer manual setup, add the appropriate source based on your authentication method:

```ruby
# For HTTPS users:
source 'https://github.com/nammayatri/ny-cocoapods-specs.git'

# For SSH users:
source 'git@github.com:nammayatri/ny-cocoapods-specs.git'

source 'https://cdn.cocoapods.org/'
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
