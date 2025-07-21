# WearAuthn - Complete Developer Documentation

## Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Key Components](#key-components)
4. [Setup & Installation](#setup--installation)
5. [Development Guide](#development-guide)
6. [API Reference](#api-reference)
7. [Testing](#testing)
8. [Troubleshooting](#troubleshooting)
9. [Contributing](#contributing)

## Project Overview

**WearAuthn** is a FIDO2/WebAuthn-compliant security key implementation for Wear OS smartwatches. It transforms your smartwatch into a hardware security key that can be used for two-factor authentication (2FA) or passwordless login on websites and services.

### Key Features
- **FIDO2/WebAuthn Compliance**: Full implementation of CTAP 2.1 specification
- **Multiple Transport Methods**: Bluetooth HID and NFC support
- **Resident Keys**: Support for discoverable credentials (passwordless login)
- **User Verification**: Biometric and device credential authentication
- **Cross-Platform**: Works with any WebAuthn-compatible service
- **Privacy-Focused**: No internet connectivity, no tracking, local credential storage

### Supported Platforms
- **Wear OS**: API level 28+ (Android 9+)
- **Transport**: Bluetooth HID, NFC (Google Pay enabled watches)
- **Authentication**: Biometric (fingerprint, face), PIN, password, pattern

## Architecture

WearAuthn follows a modular architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   MainActivity  │  │ ConfirmActivity │  │ ManageSpace  │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                    FIDO/CTAP Layer                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  Authenticator  │  │ AuthenticatorCtx│  │ WebAuthnCred │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                   Transport Layer                           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   Bluetooth     │  │      NFC        │  │   HID Device │ │
│  │     HID         │  │   Transport     │  │     App      │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                   Security Layer                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ Android Keystore│  │  Biometric Auth │  │ Secure Store │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. FIDO Implementation (`fido` package)

#### `Authenticator.kt`
- **Purpose**: Core FIDO2/CTAP2 protocol implementation
- **Key Methods**:
  - `handle()`: Main entry point for CTAP requests
  - `makeCredential()`: Creates new WebAuthn credentials
  - `getAssertion()`: Performs authentication assertions
- **Standards Compliance**: Implements CTAP 2.1 specification

#### `AuthenticatorContext.kt`
- **Purpose**: Manages authenticator state and user interactions
- **Key Responsibilities**:
  - User verification and presence checking
  - Credential storage and retrieval
  - Transport-specific status handling
- **Key Methods**:
  - `verifyUser()`: Handles biometric/device authentication
  - `setResidentCredential()`: Stores discoverable credentials
  - `getAllResidentCredentials()`: Retrieves stored credentials

#### `WebAuthnCredential.kt`
- **Purpose**: Represents a WebAuthn credential with cryptographic keys
- **Key Features**:
  - Secure key generation using Android Keystore
  - Credential serialization/deserialization
  - Signature generation for authentication
- **Security**: All private keys stored in hardware-backed keystore

### 2. Transport Layer

#### Bluetooth HID (`bthid` package)
- **Purpose**: Emulates a Bluetooth HID device for FIDO communication
- **Key Classes**:
  - `HidDeviceApp.kt`: Abstract HID device implementation
  - `InputHostWrapper.kt`: Bluetooth HID host interface
- **Protocol**: Implements FIDO HID protocol over Bluetooth

#### NFC Transport (`nfc` package)
- **Purpose**: Provides NFC-based FIDO communication
- **Requirements**: Google Pay enabled watches only
- **Protocol**: ISO 14443-4 based communication

### 3. User Interface (`ui` package)

#### `MainActivity.kt`
- **Purpose**: Main app interface showing status and credentials
- **Features**:
  - Credential count display
  - Authentication testing
  - Management access
  - Development tools (test credential creation)

#### `ConfirmDeviceCredentialActivity.kt`
- **Purpose**: Handles user authentication for FIDO operations
- **Features**:
  - Biometric authentication (fingerprint, face)
  - Device credential fallback (PIN, password, pattern)
  - Android 9+ compatibility

### 4. Security & Storage

#### Keystore Integration
- **Android Keystore**: Hardware-backed key storage
- **Key Types**: EC P-256 keys for WebAuthn
- **Protection**: User authentication required, hardware attestation

#### Credential Storage
- **Method**: Encrypted SharedPreferences
- **Structure**: RP ID hash → User ID → Credential data
- **Security**: User info encrypted with keystore-protected keys

## Setup & Installation

### Prerequisites
- Android Studio Arctic Fox or later
- Wear OS SDK (API level 28+)
- Java 17 or later
- Wear OS device or emulator

### Build Instructions

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Crayonic/WearAuthn.git
   cd WearAuthn
   ```

2. **Set Java version**:
   ```bash
   export JAVA_HOME="/path/to/java-17"
   ```

3. **Build the project**:
   ```bash
   ./gradlew :authenticator:assembleDebug
   ```

4. **Install on device**:
   ```bash
   adb install authenticator/build/outputs/apk/nightly/debug/authenticator-nightly-debug.apk
   ```

### Configuration

#### Build Variants
- **nightly**: Development builds with debug features
- **playStore**: Production builds for Google Play Store

#### Logging
- **Timber**: Structured logging framework
- **Debug builds**: Full logging enabled
- **Release builds**: Logging automatically stripped

## Development Guide

### Adding New Features

1. **FIDO Extensions**: Extend `Authenticator.kt` and add extension handling
2. **Transport Methods**: Implement new transport in dedicated package
3. **UI Components**: Follow Material Design guidelines for Wear OS
4. **Security Features**: Always use Android Keystore for cryptographic operations

### Code Style
- **Language**: Kotlin with coroutines for async operations
- **Architecture**: MVVM pattern with lifecycle-aware components
- **Testing**: Unit tests for business logic, integration tests for FIDO compliance

### Debugging

#### Enable Debug Logging
```kotlin
// In WearAuthn.kt
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}
```

#### View Logs
```bash
adb logcat | grep -E "(Timber|WearAuthn)"
```

#### Test Credentials
Long press the "Test Authentication" button in MainActivity to create test credentials for development.

## API Reference

### Core Classes

#### `AuthenticatorContext`
```kotlin
abstract class AuthenticatorContext(
    context: Context,
    isNfc: Boolean
) {
    // User verification
    suspend fun verifyUser(): Boolean
    
    // Credential management
    fun setResidentCredential(rpId: String, userId: ByteArray, credential: WebAuthnCredential, userVerified: Boolean)
    
    // Status management
    var status: AuthenticatorStatus
}
```

#### `WebAuthnCredential`
```kotlin
class WebAuthnCredential(
    keyAlias: String,
    rpIdHash: ByteArray,
    rpName: String?,
    userId: ByteArray,
    userDisplayName: String?,
    userName: String?,
    userIcon: String? = null
) {
    // Authentication
    suspend fun assertWebAuthn(clientDataHash: ByteArray, ...): ByteArray
    
    // Serialization
    fun serialize(userVerified: Boolean): String
}
```

### Extension Points

#### Custom Transport
```kotlin
abstract class CustomTransport : TransportInterface {
    abstract suspend fun sendResponse(data: ByteArray)
    abstract suspend fun receiveRequest(): ByteArray
}
```

#### Custom Authentication
```kotlin
interface AuthenticationProvider {
    suspend fun authenticate(): Boolean
    fun isAvailable(): Boolean
}
```

## Testing

### Unit Tests
```bash
./gradlew :authenticator:testDebugUnitTest
```

### Integration Tests
```bash
./gradlew :authenticator:connectedDebugAndroidTest
```

### FIDO Conformance Tests
Use the metadata in `/metadata` directory with official FIDO conformance tools.

### Manual Testing

#### WebAuthn Demo Sites
- https://webauthn.io/
- https://webauthn-demo.appspot.com/
- https://demo.webauthn.io/

#### Real Services
- GitHub (Security settings → Passkeys)
- Google (Account security → 2-Step Verification)
- Microsoft (Security settings → Passwordless sign-in)

## Troubleshooting

### Common Issues

#### "Invalid user authentication validity duration" (Android 9)
**Solution**: The app automatically adjusts authentication validity duration for Android 9 compatibility.

#### Bluetooth pairing issues
**Solution**: 
1. Clear Bluetooth cache
2. Re-pair devices
3. Ensure HID profile is supported

#### NFC not working
**Solution**:
1. Verify Google Pay is set up
2. Check NFC is enabled
3. Ensure watch supports NFC

#### Credential creation fails
**Solution**:
1. Verify device has lock screen security
2. Check keystore availability
3. Ensure sufficient storage space

### Debug Commands

#### Check device capabilities
```bash
adb shell pm list features | grep -E "(nfc|bluetooth)"
```

#### View stored credentials
```bash
adb shell run-as me.henneke.wearauthn.phone.nightly ls /data/data/me.henneke.wearauthn.phone.nightly/shared_prefs/
```

#### Clear app data
```bash
adb shell pm clear me.henneke.wearauthn.phone.nightly
```

## Contributing

### Development Workflow
1. Fork the repository
2. Create feature branch: `git checkout -b feature/new-feature`
3. Make changes with tests
4. Run full test suite
5. Submit pull request

### Code Review Guidelines
- All changes must include tests
- Follow existing code style
- Update documentation for API changes
- Ensure FIDO compliance for protocol changes

### Security Considerations
- Never log sensitive data (private keys, user info)
- Always use Android Keystore for cryptographic operations
- Validate all inputs from external sources
- Follow secure coding practices

---

For more information, see the [README.md](README.md) and [PRIVACY.md](PRIVACY.md) files.
