module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath: 'import com.appmonitor.AppMonitorPackage;',
        packageInstance: 'new AppMonitorPackage()',
      },
      ios: {
        podspecPath: './RNAppMonitor.podspec',
      },
    },
  },
};
