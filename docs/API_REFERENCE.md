# WearAuthn API Reference

## Core Classes and Interfaces

### AuthenticatorContext

The central class managing FIDO authenticator operations and user interactions.

```kotlin
abstract class AuthenticatorContext(
    protected val context: Context,
    protected val isNfc: Boolean
)
```

#### Key Methods

##### User Verification
```kotlin
suspend fun verifyUser(): Boolean
```
Initiates user verification through biometric or device credentials.

**Returns**: `true` if user verification successful, `false` otherwise

**Usage**:
```kotlin
val context = MyAuthenticatorContext(this, false)
if (context.verifyUser()) {
    // Proceed with authenticated operation
}
```

##### Credential Management
```kotlin
fun setResidentCredential(
    rpId: String,
    userId: ByteArray,
    credential: WebAuthnCredential,
    userVerified: Boolean
)
```
Stores a resident (discoverable) credential.

**Parameters**:
- `rpId`: Relying Party identifier
- `userId`: User identifier bytes
- `credential`: WebAuthn credential to store
- `userVerified`: Whether user verification was performed

```kotlin
companion object {
    fun getAllResidentCredentials(context: Context): Map<String, List<WebAuthnCredential>>
}
```
Retrieves all stored resident credentials grouped by RP ID.

**Returns**: Map of RP ID to list of credentials

##### Key Generation
```kotlin
fun getOrCreateFreshWebAuthnCredential(
    createResidentKey: Boolean,
    createHmacSecret: Boolean,
    attestationChallenge: ByteArray?
): Pair<String, AttestationType>?
```
Creates or retrieves a WebAuthn credential with cryptographic keys.

**Parameters**:
- `createResidentKey`: Whether to create a resident key
- `createHmacSecret`: Whether to support HMAC secret extension
- `attestationChallenge`: Challenge for attestation

**Returns**: Pair of key alias and attestation type, or null if failed

#### Abstract Methods (Must Implement)

```kotlin
abstract fun notifyUser(info: RequestInfo)
abstract fun handleSpecialStatus(specialStatus: AuthenticatorSpecialStatus)
abstract suspend fun confirmRequestWithUser(info: RequestInfo): Boolean
abstract suspend fun confirmTransactionWithUser(rpId: String, prompt: String): String?
```

### WebAuthnCredential

Represents a WebAuthn credential with associated cryptographic keys.

```kotlin
class WebAuthnCredential(
    keyAlias: String,
    rpIdHash: ByteArray,
    rpName: String?,
    userId: ByteArray,
    userDisplayName: String?,
    userName: String?,
    userIcon: String? = null
)
```

#### Key Properties

```kotlin
val keyHandle: ByteArray // Credential identifier
val isResident: Boolean  // Whether this is a resident key
val creationDate: Date?  // When credential was created
```

#### Authentication Methods

```kotlin
suspend fun assertWebAuthn(
    clientDataHash: ByteArray,
    extensionOutputs: Map<Extension, CborValue>,
    userPresent: Boolean,
    userVerified: Boolean,
    numberOfCredentials: Int,
    userSelected: Boolean,
    returnCredential: Boolean,
    context: AuthenticatorContext
): ByteArray
```
Performs WebAuthn assertion (authentication).

**Parameters**:
- `clientDataHash`: Hash of client data
- `extensionOutputs`: Extension outputs to include
- `userPresent`: Whether user presence was verified
- `userVerified`: Whether user verification was performed
- `numberOfCredentials`: Number of credentials available
- `userSelected`: Whether user explicitly selected this credential
- `returnCredential`: Whether to return credential ID
- `context`: Authenticator context

**Returns**: CBOR-encoded assertion response

#### Serialization

```kotlin
fun serialize(userVerified: Boolean): String
```
Serializes credential for storage.

**Parameters**:
- `userVerified`: Whether user verification was performed

**Returns**: Base64-encoded serialized credential

### Authenticator

Main FIDO2/CTAP2 protocol handler.

```kotlin
object Authenticator {
    suspend fun handle(context: AuthenticatorContext, rawRequest: ByteArray): ByteArray
}
```

Processes CTAP2 requests and returns responses.

**Parameters**:
- `context`: Authenticator context for operations
- `rawRequest`: Raw CTAP2 request bytes

**Returns**: CTAP2 response bytes

## Transport Interfaces

### HidDeviceApp

Abstract base for HID device implementations.

```kotlin
abstract class HidDeviceApp {
    abstract fun registerApp(inputHost: BluetoothProfile)
    abstract fun unregisterApp()
    
    fun setDevice(device: BluetoothDevice?)
    fun registerDeviceListener(listener: DeviceStateListener)
}
```

### DeviceStateListener

Interface for monitoring device connection state.

```kotlin
interface DeviceStateListener {
    fun onDeviceConnected(device: BluetoothDevice)
    fun onDeviceDisconnected(device: BluetoothDevice)
}
```

## Data Classes

### RequestInfo

Contains information about a FIDO request for user confirmation.

```kotlin
data class RequestInfo(
    val action: String,
    val rpId: String,
    val rpName: String?,
    val userName: String?,
    val userDisplayName: String?,
    val addResidentKeyHint: Boolean = false
)
```

### AuthenticatorStatus

Enum representing current authenticator state.

```kotlin
enum class AuthenticatorStatus {
    IDLE,
    PROCESSING,
    WAITING_FOR_USER_PRESENCE,
    WAITING_FOR_USER_VERIFICATION
}
```

### Extension

Enum for supported FIDO2 extensions.

```kotlin
enum class Extension(val identifier: String) {
    HmacSecret("hmac-secret"),
    CredProtect("credProtect"),
    LargeBlobKey("largeBlobKey")
}
```

## Utility Functions

### Cryptographic Utilities

```kotlin
// Hash functions
fun ByteArray.sha256(): ByteArray
fun String.sha256(): ByteArray

// Encoding functions
fun ByteArray.base64(): String
fun String.base64Decode(): ByteArray

// Key generation
fun generateWebAuthnCredential(
    createResidentKey: Boolean,
    createHmacSecret: Boolean,
    attestationChallenge: ByteArray?
): String?
```

### CBOR Utilities

```kotlin
// CBOR encoding/decoding
fun Any.toCbor(): ByteArray
fun ByteArray.fromCbor(): CborValue

// CBOR value types
class CborTextString(val value: String) : CborValue
class CborByteString(val value: ByteArray) : CborValue
class CborInteger(val value: Long) : CborValue
class CborMap(val value: Map<CborValue, CborValue>) : CborValue
```

## Error Handling

### CTAP Error Codes

```kotlin
enum class CtapError(val code: Byte) {
    Success(0x00),
    InvalidCommand(0x01),
    InvalidParameter(0x02),
    InvalidLength(0x03),
    InvalidSeq(0x04),
    Timeout(0x05),
    ChannelBusy(0x06),
    LockRequired(0x0A),
    InvalidChannel(0x0B),
    OperationDenied(0x27),
    KeyStoreFull(0x28),
    NoCredentials(0x2E),
    UserActionTimeout(0x2F),
    NotAllowed(0x30),
    PinInvalid(0x31),
    PinBlocked(0x32),
    PinAuthInvalid(0x33),
    PinAuthBlocked(0x34),
    PinNotSet(0x35),
    PinRequired(0x36),
    PinPolicyViolation(0x37),
    PinTokenExpired(0x38),
    RequestTooLarge(0x39),
    ActionTimeout(0x3A),
    UpRequired(0x3B),
    UvBlocked(0x3C),
    IntegrityFailure(0x3D),
    InvalidSubcommand(0x3E),
    UvInvalid(0x3F),
    UnauthorizedPermission(0x40),
    Other(0x7F)
}
```

### Exception Classes

```kotlin
class CtapErrorException(val error: CtapError, message: String? = null) : Exception(message)

// Helper function
fun CTAP_ERR(error: CtapError, message: String? = null): Nothing {
    throw CtapErrorException(error, message)
}
```

## Configuration

### Build Configuration

```kotlin
object BuildConfig {
    const val DEBUG: Boolean
    const val APPLICATION_ID: String
    const val BUILD_TYPE: String
    const val VERSION_CODE: Int
    const val VERSION_NAME: String
}
```

### Logging Configuration

```kotlin
// Initialize Timber logging
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}

// Logging methods
Timber.d("Debug message")
Timber.i("Info message")
Timber.w("Warning message")
Timber.e(exception, "Error message")
```

## Usage Examples

### Basic Credential Creation

```kotlin
class MyAuthenticatorContext(context: Context) : AuthenticatorContext(context, false) {
    override suspend fun confirmRequestWithUser(info: RequestInfo): Boolean {
        // Show confirmation dialog to user
        return showConfirmationDialog(info)
    }
    
    override fun notifyUser(info: RequestInfo) {
        // Show notification to user
        showNotification(info)
    }
    
    // ... implement other abstract methods
}

// Usage
val context = MyAuthenticatorContext(this)
val (keyAlias, attestationType) = context.getOrCreateFreshWebAuthnCredential(
    createResidentKey = true,
    createHmacSecret = false,
    attestationChallenge = challenge
) ?: throw Exception("Failed to create credential")

val credential = WebAuthnCredential(
    keyAlias = keyAlias,
    rpIdHash = rpId.toByteArray().sha256(),
    rpName = rpId,
    userId = userId,
    userDisplayName = displayName,
    userName = username
)

context.setResidentCredential(rpId, userId, credential, true)
```

### Processing CTAP Requests

```kotlin
val context = MyAuthenticatorContext(this)
val request = receivedCtapRequest // ByteArray from transport
val response = Authenticator.handle(context, request)
sendCtapResponse(response) // Send via transport
```

### Retrieving Stored Credentials

```kotlin
val allCredentials = AuthenticatorContext.getAllResidentCredentials(context)
for ((rpId, credentials) in allCredentials) {
    println("RP: $rpId has ${credentials.size} credentials")
    for (credential in credentials) {
        println("  User: ${credential.userName}")
    }
}
```
