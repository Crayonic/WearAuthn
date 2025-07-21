# WearAuthn Architecture Documentation

## Overview

WearAuthn is designed as a modular, secure, and extensible FIDO2/WebAuthn authenticator for Wear OS devices. The architecture follows clean architecture principles with clear separation of concerns and well-defined interfaces between layers.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   MainActivity  │  │ ConfirmActivity │  │ ManageSpace  │ │
│  │   (Status UI)   │  │ (Auth Dialog)   │  │ (Settings)   │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  Use Cases      │  │   Coordinators  │  │   Services   │ │
│  │ (FIDO Flows)    │  │ (UI Logic)      │  │ (Background) │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                     Domain Layer                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  Authenticator  │  │ AuthenticatorCtx│  │ WebAuthnCred │ │
│  │ (CTAP Protocol) │  │ (State Mgmt)    │  │ (Credentials)│ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                  Infrastructure Layer                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   Transport     │  │    Security     │  │   Storage    │ │
│  │ (BT HID, NFC)   │  │ (Keystore, Bio) │  │ (SharedPrefs)│ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Layer Details

### 1. Presentation Layer

#### Responsibilities
- User interface components
- User input handling
- Display of authenticator status
- Error presentation

#### Key Components

**MainActivity**
- Primary app interface
- Shows credential count and status
- Provides access to management functions
- Development tools (test credentials)

**ConfirmDeviceCredentialActivity**
- Handles user authentication dialogs
- Biometric and device credential prompts
- Android version compatibility handling

**ManageSpaceActivity**
- Credential management interface
- Storage cleanup utilities
- Debug information display

#### Design Patterns
- **MVVM**: Model-View-ViewModel for UI logic separation
- **Observer Pattern**: LiveData for reactive UI updates
- **Command Pattern**: Button click handlers and user actions

### 2. Application Layer

#### Responsibilities
- Orchestrates business logic
- Manages application state
- Coordinates between UI and domain layers
- Handles cross-cutting concerns

#### Key Components

**Use Cases**
- `MakeCredentialUseCase`: Handles credential creation flow
- `GetAssertionUseCase`: Manages authentication flow
- `ManageCredentialsUseCase`: Credential lifecycle management

**Coordinators**
- `AuthenticationCoordinator`: Manages auth flow state
- `TransportCoordinator`: Handles transport switching
- `UICoordinator`: Manages UI navigation

**Services**
- `BackgroundService`: Handles background operations
- `NotificationService`: User notifications
- `LoggingService`: Centralized logging

### 3. Domain Layer

#### Responsibilities
- Core business logic
- FIDO2/WebAuthn protocol implementation
- Credential management
- Security policy enforcement

#### Key Components

**Authenticator**
```kotlin
object Authenticator {
    suspend fun handle(context: AuthenticatorContext, rawRequest: ByteArray): ByteArray
}
```
- Central CTAP2 protocol handler
- Stateless operation processing
- Error handling and validation
- Extension support

**AuthenticatorContext**
```kotlin
abstract class AuthenticatorContext(
    protected val context: Context,
    protected val isNfc: Boolean
)
```
- Manages authenticator state
- User interaction coordination
- Transport-specific behavior
- Security policy enforcement

**WebAuthnCredential**
```kotlin
class WebAuthnCredential(
    keyAlias: String,
    rpIdHash: ByteArray,
    rpName: String?,
    userId: ByteArray,
    userDisplayName: String?,
    userName: String?
)
```
- Represents cryptographic credentials
- Handles key operations
- Serialization/deserialization
- Attestation generation

#### Domain Models

**RequestInfo**
- Contains FIDO request metadata
- User confirmation details
- Security context information

**AuthenticatorStatus**
- Current operation state
- User interaction requirements
- Error conditions

**Extension**
- FIDO2 extension definitions
- Extension processing logic
- Compatibility handling

### 4. Infrastructure Layer

#### Responsibilities
- External system integration
- Hardware abstraction
- Data persistence
- Security services

#### Transport Subsystem

**Bluetooth HID**
```kotlin
abstract class HidDeviceApp {
    abstract fun registerApp(inputHost: BluetoothProfile)
    abstract fun unregisterApp()
}
```
- Emulates Bluetooth HID device
- FIDO HID protocol implementation
- Device pairing and connection management
- Data transmission handling

**NFC Transport**
- ISO 14443-4 communication
- APDU command processing
- Secure element integration (where available)
- Power management

#### Security Subsystem

**Android Keystore Integration**
```kotlin
fun generateWebAuthnCredential(
    createResidentKey: Boolean,
    createHmacSecret: Boolean,
    attestationChallenge: ByteArray?
): String?
```
- Hardware-backed key generation
- Secure key storage
- User authentication enforcement
- Attestation support

**Biometric Authentication**
- AndroidX Biometric library integration
- Fallback to device credentials
- Android version compatibility
- Error handling and retry logic

#### Storage Subsystem

**Credential Storage**
- Encrypted SharedPreferences
- RP ID hash indexing
- User ID mapping
- Metadata persistence

**Configuration Storage**
- App settings and preferences
- Transport configuration
- Debug settings
- User preferences

## Data Flow

### 1. Credential Creation (MakeCredential)

```
Client Request → Transport → Authenticator → AuthenticatorContext
                                                      ↓
User Confirmation ← UI ← Application Layer ← Domain Layer
                                                      ↓
Key Generation → Android Keystore → WebAuthnCredential
                                                      ↓
Storage → SharedPreferences ← Credential Serialization
                                                      ↓
Response ← Transport ← Authenticator ← Assertion Generation
```

### 2. Authentication (GetAssertion)

```
Client Request → Transport → Authenticator → Credential Lookup
                                                      ↓
User Verification ← UI ← Application Layer ← AuthenticatorContext
                                                      ↓
Signature Generation → Android Keystore ← WebAuthnCredential
                                                      ↓
Response ← Transport ← Authenticator ← Assertion Response
```

## Security Architecture

### 1. Threat Model

**Assets to Protect**
- Private cryptographic keys
- User credentials and metadata
- Authentication decisions
- User privacy information

**Threat Actors**
- Malicious apps on device
- Network attackers
- Physical device access
- Compromised transport channels

**Attack Vectors**
- Key extraction attempts
- Credential enumeration
- Replay attacks
- Man-in-the-middle attacks

### 2. Security Controls

**Hardware Security**
- Android Keystore (hardware-backed when available)
- Secure Element integration (NFC)
- TEE (Trusted Execution Environment) usage
- Hardware attestation support

**Software Security**
- User authentication requirements
- Encrypted credential storage
- Input validation and sanitization
- Secure communication protocols

**Protocol Security**
- FIDO2/WebAuthn compliance
- CTAP 2.1 implementation
- Extension security validation
- Replay protection mechanisms

### 3. Security Boundaries

```
┌─────────────────────────────────────────────────────────────┐
│                    User Space                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   WearAuthn     │  │  Other Apps     │  │   System UI  │ │
│  │     App         │  │                 │  │              │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                  Android Framework                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   Keystore      │  │   Biometric     │  │  Bluetooth   │ │
│  │   Service       │  │    Manager      │  │   Service    │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                   Hardware Layer                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  Secure Element │  │      TEE        │  │   Hardware   │ │
│  │      (NFC)      │  │   (TrustZone)   │  │   Security   │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Extension Points

### 1. Transport Extensions

**Interface Definition**
```kotlin
interface TransportInterface {
    suspend fun sendResponse(data: ByteArray)
    suspend fun receiveRequest(): ByteArray
    fun isConnected(): Boolean
    fun getCapabilities(): TransportCapabilities
}
```

**Implementation Example**
```kotlin
class CustomTransport : TransportInterface {
    override suspend fun sendResponse(data: ByteArray) {
        // Custom transport implementation
    }
    
    override suspend fun receiveRequest(): ByteArray {
        // Custom transport implementation
    }
}
```

### 2. Authentication Extensions

**Interface Definition**
```kotlin
interface AuthenticationProvider {
    suspend fun authenticate(): Boolean
    fun isAvailable(): Boolean
    fun getCapabilities(): AuthenticationCapabilities
}
```

**Implementation Example**
```kotlin
class CustomAuthProvider : AuthenticationProvider {
    override suspend fun authenticate(): Boolean {
        // Custom authentication logic
    }
}
```

### 3. Storage Extensions

**Interface Definition**
```kotlin
interface CredentialStorage {
    suspend fun store(credential: WebAuthnCredential)
    suspend fun retrieve(rpId: String, userId: ByteArray): WebAuthnCredential?
    suspend fun list(rpId: String): List<WebAuthnCredential>
    suspend fun delete(rpId: String, userId: ByteArray)
}
```

## Performance Considerations

### 1. Memory Management

**Object Lifecycle**
- Short-lived objects for request processing
- Long-lived objects for credential storage
- Proper cleanup of cryptographic materials

**Memory Optimization**
- Lazy initialization of heavy objects
- Object pooling for frequent allocations
- Efficient data structures for collections

### 2. CPU Optimization

**Cryptographic Operations**
- Hardware acceleration when available
- Efficient algorithm implementations
- Minimal key operations

**Background Processing**
- Coroutines for async operations
- Appropriate dispatcher selection
- Work scheduling optimization

### 3. Storage Optimization

**Data Structure**
- Efficient indexing by RP ID hash
- Minimal metadata storage
- Compressed serialization

**Access Patterns**
- Batch operations when possible
- Caching frequently accessed data
- Lazy loading of credential details

## Testing Architecture

### 1. Unit Testing

**Test Structure**
```kotlin
class AuthenticatorTest {
    @Test
    fun testMakeCredential() {
        // Arrange
        val context = MockAuthenticatorContext()
        val request = createMakeCredentialRequest()
        
        // Act
        val response = Authenticator.handle(context, request)
        
        // Assert
        assertValidResponse(response)
    }
}
```

### 2. Integration Testing

**Test Scenarios**
- End-to-end FIDO flows
- Transport layer integration
- Security boundary validation
- Error handling verification

### 3. Security Testing

**Test Categories**
- Cryptographic operation validation
- Key isolation verification
- Attack simulation
- Compliance testing

---

This architecture provides a solid foundation for a secure, maintainable, and extensible FIDO2 authenticator while maintaining clear separation of concerns and following established security best practices.
