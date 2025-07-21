# WearAuthn User Guide

## Getting Started

### What is WearAuthn?

WearAuthn transforms your Wear OS smartwatch into a FIDO2/WebAuthn security key, allowing you to:
- **Authenticate** to websites and services with a tap on your watch
- **Enable passwordless login** using resident keys
- **Secure your accounts** with hardware-backed two-factor authentication
- **Replace physical security keys** with your always-available smartwatch

### Requirements

- **Wear OS device** running Android 9+ (API level 28+)
- **Device security** enabled (PIN, password, pattern, or biometric)
- **Bluetooth** for most authentication scenarios
- **NFC** for NFC-enabled authentication (Google Pay setup required)

## Installation & Setup

### 1. Install WearAuthn

**From Google Play Store:**
1. Open Google Play Store on your watch
2. Search for "WearAuthn"
3. Install the app

**Manual Installation:**
1. Download the APK from [GitHub Releases](https://github.com/Crayonic/WearAuthn/releases)
2. Install using ADB: `adb install wearauthn.apk`

### 2. Initial Setup

1. **Open WearAuthn** on your watch
2. **Set up device security** if not already configured:
   - Go to Settings → Security → Screen lock
   - Choose PIN, password, pattern, or biometric
3. **Test authentication** by tapping "Test Authentication"
4. **Pair with your phone** via Bluetooth if needed

### 3. First Registration

1. **Visit a WebAuthn demo site** like [webauthn.io](https://webauthn.io/)
2. **Start registration** for a new account
3. **Select your watch** when prompted for a security key
4. **Confirm on your watch** by authenticating
5. **Complete registration** on the website

## Using WearAuthn

### Authentication Flow

1. **Visit a website** that supports WebAuthn/FIDO2
2. **Enter your username** (if required)
3. **Click "Sign in with security key"** or similar
4. **Your watch will vibrate** and show an authentication prompt
5. **Authenticate on your watch** using your chosen method:
   - Biometric (fingerprint, face)
   - Device PIN/password/pattern
6. **Authentication completes** automatically

### Resident Keys (Passwordless Login)

For sites that support resident keys:
1. **Register normally** with your username
2. **Enable resident key** when prompted
3. **Future logins**: Just visit the site and authenticate on your watch
4. **No username required** - your watch provides the credential

### Managing Credentials

**View Credentials:**
- Open WearAuthn app
- See credential count on main screen
- Tap "Manage Credentials" for details

**Delete Credentials:**
- Go to Settings → Apps → WearAuthn
- Tap "Storage & cache"
- Tap "Clear storage" to remove all credentials

## Supported Services

### Demo Sites (for testing)
- [WebAuthn.io](https://webauthn.io/) - WebAuthn demonstration
- [WebAuthn Demo](https://webauthn-demo.appspot.com/) - Google's demo
- [Duo WebAuthn Demo](https://demo.webauthn.io/) - Duo's implementation

### Popular Services
- **GitHub** - Settings → Security → Passkeys
- **Google** - Account → Security → 2-Step Verification → Security keys
- **Microsoft** - Security settings → Passwordless sign-in
- **PayPal** - Security settings → Security keys
- **Dropbox** - Security → Security keys
- **Facebook** - Security and Login → Two-Factor Authentication
- **Twitter** - Settings → Security → Two-factor authentication
- **Discord** - User Settings → My Account → Two-Factor Auth

### Enterprise Services
- **Okta** - Security → Factors → Security Key
- **Azure AD** - Security info → Add method → Security key
- **AWS** - IAM → Users → Security credentials → MFA device

## Transport Methods

### Bluetooth HID

**Best for:**
- Desktop computers
- Laptops
- Most authentication scenarios

**Setup:**
1. Ensure Bluetooth is enabled on both devices
2. Pair your watch with your computer if needed
3. WearAuthn automatically handles HID communication

**Troubleshooting:**
- Ensure HID profile is supported
- Clear Bluetooth cache if connection issues occur
- Re-pair devices if necessary

### NFC

**Best for:**
- NFC-enabled phones and tablets
- Quick authentication
- Offline scenarios

**Requirements:**
- Google Pay must be set up on your watch
- NFC must be enabled on the target device

**Usage:**
1. Start authentication on the target device
2. Hold your watch near the NFC reader
3. Authenticate when prompted

## Troubleshooting

### Common Issues

**"No security key detected"**
- Ensure Bluetooth is enabled and paired
- Try moving devices closer together
- Restart Bluetooth on both devices

**"Authentication failed"**
- Verify you're using the correct credential
- Check that device security is properly set up
- Try authenticating again

**"Invalid user authentication validity duration" (Android 9)**
- This is automatically handled by the app
- No user action required

**Watch not responding**
- Ensure WearAuthn app is running
- Check battery level
- Restart the app if necessary

**NFC not working**
- Verify Google Pay is set up
- Check NFC is enabled on target device
- Ensure proper positioning near NFC reader

### Debug Information

**View Logs:**
```bash
adb logcat | grep -E "(Timber|WearAuthn)"
```

**Check Capabilities:**
```bash
adb shell pm list features | grep -E "(nfc|bluetooth)"
```

**Reset App Data:**
```bash
adb shell pm clear me.henneke.wearauthn.phone.nightly
```

### Getting Help

1. **Check this guide** for common solutions
2. **Search existing issues** on [GitHub](https://github.com/Crayonic/WearAuthn/issues)
3. **Create a new issue** with detailed information:
   - Device model and Wear OS version
   - Steps to reproduce the problem
   - Error messages or logs
   - Expected vs actual behavior

## Security Best Practices

### Account Security

1. **Always set up backup 2FA methods**:
   - SMS backup (less secure but better than nothing)
   - Authenticator app (Google Authenticator, Authy)
   - Backup security keys
   - Recovery codes

2. **Don't rely solely on your watch**:
   - Watches can be lost, stolen, or broken
   - Apps can be accidentally deleted
   - Always have alternative access methods

3. **Regularly review your security keys**:
   - Remove unused or old security keys
   - Update security settings when changing devices
   - Monitor account activity for suspicious access

### Device Security

1. **Keep your watch secure**:
   - Use a strong PIN/password/pattern
   - Enable biometric authentication if available
   - Keep Wear OS updated

2. **Protect your credentials**:
   - Don't share your watch with others during authentication
   - Be aware of shoulder surfing
   - Use private browsing for sensitive accounts

3. **Regular maintenance**:
   - Keep WearAuthn updated
   - Periodically review stored credentials
   - Clear unused credentials

## Advanced Features

### Development Mode

**Test Credentials:**
- Long press "Test Authentication" button
- Creates sample credentials for testing
- Useful for developers and testing

**Debug Logging:**
- Enabled automatically in debug builds
- Provides detailed operation logs
- Helps troubleshoot issues

### Custom Configuration

**Authentication Timeout:**
- Default: 30 seconds
- Configurable in app settings
- Balances security and usability

**Transport Priority:**
- Bluetooth HID preferred by default
- NFC as fallback option
- Automatic selection based on availability

## Privacy & Data

### What Data is Stored

**On Your Watch:**
- Cryptographic key pairs (in Android Keystore)
- Credential metadata (encrypted)
- Relying Party information
- User identifiers (encrypted)

**Not Stored:**
- Passwords or PINs
- Biometric data (handled by system)
- Personal information beyond what you provide
- Usage analytics or tracking data

### Data Protection

- **Hardware-backed encryption** using Android Keystore
- **Local storage only** - no cloud synchronization
- **User authentication required** for all operations
- **Automatic data clearing** when app is uninstalled

### Privacy Features

- **No internet connectivity** required for operation
- **No data collection** or analytics
- **No tracking** of user behavior
- **Minimal permissions** requested

## Frequently Asked Questions

**Q: Can I use WearAuthn with multiple devices?**
A: Yes, you can pair your watch with multiple devices and use it for authentication on all of them.

**Q: What happens if I lose my watch?**
A: You'll need to use your backup 2FA methods to access your accounts, then remove the lost watch as a security key from your account settings.

**Q: Can I backup my credentials?**
A: Currently, credentials cannot be backed up due to security restrictions. Each device maintains its own unique credentials.

**Q: Does WearAuthn work offline?**
A: Yes, authentication works offline. Only the initial registration requires internet connectivity.

**Q: Is WearAuthn compatible with all browsers?**
A: WearAuthn works with any browser that supports WebAuthn/FIDO2, including Chrome, Firefox, Safari, and Edge.

**Q: Can I use WearAuthn for enterprise/work accounts?**
A: Yes, WearAuthn is compatible with enterprise identity providers that support FIDO2/WebAuthn.

---

For technical documentation, see the [Complete Documentation](../DOCUMENTATION.md) and [API Reference](API_REFERENCE.md).
