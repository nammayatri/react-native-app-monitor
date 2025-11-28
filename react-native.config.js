module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath: 'import com.movingtech.appmonitor.AppMonitorPackage;',
        packageInstance: 'new AppMonitorPackage()',
      },
      ios: {
        podspecPath: './react-native-app-monitor.podspec',
      },
    },
  },
};
