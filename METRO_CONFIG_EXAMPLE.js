// Add this to your consuming app's metro.config.js
// This prevents Metro from processing node_modules from locally linked packages

const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

const config = {
  resolver: {
    // Block Metro from processing node_modules in locally linked packages
    blockList: [
      // Block React Native source files from locally linked packages
      /.*\/react-native-app-monitor\/node_modules\/react-native\/.*/,
      // Or block all node_modules from the linked package
      /.*\/react-native-app-monitor\/node_modules\/.*/,
    ],
  },
  watchFolders: [
    // Add the local package path if needed
    // path.resolve(__dirname, '../react-native-app-monitor'),
  ],
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
