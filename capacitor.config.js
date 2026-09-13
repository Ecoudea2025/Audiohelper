/** @type {import('@capacitor/cli').CapacitorConfig} */
const config = {
  appId: 'com.cdmarket.listening',
  appName: 'CD Market',
  webDir: 'www',
  backgroundColor: '#0B0B14',
  android: {
    // Keep the bundled WebView on the default HTTPS localhost origin.
    // The page detects native mode (no SW, bundled audio) via hostname+port.
    allowMixedContent: false
  }
};

module.exports = config;
