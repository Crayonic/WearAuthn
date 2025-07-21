# WearAuthn Development Guide

## Getting Started

### Prerequisites

- **Android Studio**: Arctic Fox (2020.3.1) or later
- **Java**: JDK 17 or later
- **Wear OS SDK**: API level 28+ (Android 9+)
- **Device**: Wear OS watch or emulator
- **Git**: For version control

### Environment Setup

1. **Install Android Studio**:
   - Download from [developer.android.com](https://developer.android.com/studio)
   - Install Wear OS SDK through SDK Manager

2. **Configure Java**:
   ```bash
   export JAVA_HOME="/path/to/jdk-17"
   # On Windows:
   set JAVA_HOME=C:\Program Files\Java\jdk-17
   ```

3. **Clone Repository**:
   ```bash
   git clone https://github.com/Crayonic/WearAuthn.git
   cd WearAuthn
   ```

4. **Open in Android Studio**:
   - File → Open → Select WearAuthn directory
   - Wait for Gradle sync to complete

### Project Structure

```
WearAuthn/
├── authenticator/          # Main Wear OS app module
│   ├── src/main/java/me/henneke/wearauthn/
│   │   ├── bthid/         # Bluetooth HID implementation
│   │   ├── fido/          # FIDO2/WebAuthn core logic
│   │   │   ├── context/   # Authenticator context and credentials
│   │   │   ├── ctap2/     # CTAP2 protocol implementation
│   │   │   └── u2f/       # U2F protocol implementation
│   │   ├── nfc/           # NFC transport implementation
│   │   ├── ui/            # User interface components
│   │   └── complication/  # Watch face complications
│   └── src/main/res/      # Resources (layouts, strings, etc.)
├── companion/             # Android phone companion app
├── metadata/              # FIDO Authenticator Metadata
└── docs/                  # Documentation files
```

## Development Workflow

### 1. Building the Project

#### Debug Build
```bash
./gradlew :authenticator:assembleDebug
```

#### Release Build
```bash
./gradlew :authenticator:assembleRelease
```

#### Install on Device
```bash
adb install authenticator/build/outputs/apk/nightly/debug/authenticator-nightly-debug.apk
```

### 2. Running Tests

#### Unit Tests
```bash
./gradlew :authenticator:testDebugUnitTest
```

#### Integration Tests
```bash
./gradlew :authenticator:connectedDebugAndroidTest
```

#### Lint Checks
```bash
./gradlew :authenticator:lintDebug
```

### 3. Debugging

#### Enable Debug Logging
The app uses Timber for logging. Debug builds automatically enable full logging:

```kotlin
// In WearAuthn.kt
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}
```

#### View Logs
```bash
# View all app logs
adb logcat | grep -E "(Timber|WearAuthn)"

# View specific component logs
adb logcat | grep "AuthenticatorContext"
adb logcat | grep "MainActivity"
```

#### Debug Features
- **Test Credentials**: Long press "Test Authentication" button to create test credentials
- **Detailed Logging**: All FIDO operations are logged in debug builds
- **Error Reporting**: Comprehensive error messages and stack traces

## Code Architecture

### MVVM Pattern

The app follows the Model-View-ViewModel pattern:

```kotlin
// Model: Data and business logic
class AuthenticatorContext { /* ... */ }
class WebAuthnCredential { /* ... */ }

// View: UI components
class MainActivity : AppCompatActivity() { /* ... */ }
class ConfirmDeviceCredentialActivity : AppCompatActivity() { /* ... */ }

// ViewModel: UI logic and state management
class MainViewModel : ViewModel() { /* ... */ }
```

### Dependency Injection

The app uses manual dependency injection for simplicity:

```kotlin
class AuthenticatorContextFactory {
    fun create(context: Context, isNfc: Boolean): AuthenticatorContext {
        return ConcreteAuthenticatorContext(context, isNfc)
    }
}
```

### Coroutines for Async Operations

All async operations use Kotlin coroutines:

```kotlin
class MainActivity : AppCompatActivity() {
    private fun loadCredentials() {
        lifecycleScope.launch {
            try {
                val credentials = withContext(Dispatchers.IO) {
                    AuthenticatorContext.getAllResidentCredentials(this@MainActivity)
                }
                updateUI(credentials)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }
}
```

## Adding New Features

### 1. Adding a New FIDO Extension

1. **Define Extension**:
   ```kotlin
   enum class Extension(val identifier: String) {
       // Existing extensions...
       MyNewExtension("myNewExtension")
   }
   ```

2. **Handle in Authenticator**:
   ```kotlin
   // In Authenticator.kt
   private fun handleExtensions(extensions: Map<String, CborValue>): Map<Extension, CborValue> {
       val result = mutableMapOf<Extension, CborValue>()
       
       extensions["myNewExtension"]?.let { value ->
           // Process extension
           result[Extension.MyNewExtension] = processMyExtension(value)
       }
       
       return result
   }
   ```

3. **Add Tests**:
   ```kotlin
   @Test
   fun testMyNewExtension() {
       val extensions = mapOf("myNewExtension" to CborBoolean(true))
       val result = handleExtensions(extensions)
       assertTrue(result.containsKey(Extension.MyNewExtension))
   }
   ```

### 2. Adding a New Transport Method

1. **Create Transport Interface**:
   ```kotlin
   interface MyTransport {
       suspend fun sendData(data: ByteArray)
       suspend fun receiveData(): ByteArray
       fun isConnected(): Boolean
   }
   ```

2. **Implement Transport**:
   ```kotlin
   class MyTransportImpl : MyTransport {
       override suspend fun sendData(data: ByteArray) {
           // Implementation
       }
       
       override suspend fun receiveData(): ByteArray {
           // Implementation
       }
       
       override fun isConnected(): Boolean {
           // Implementation
       }
   }
   ```

3. **Integrate with Authenticator**:
   ```kotlin
   class MyTransportAuthenticatorContext(
       context: Context,
       private val transport: MyTransport
   ) : AuthenticatorContext(context, false) {
       // Implementation
   }
   ```

### 3. Adding New UI Components

1. **Create Layout**:
   ```xml
   <!-- res/layout/activity_my_feature.xml -->
   <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
       android:layout_width="match_parent"
       android:layout_height="match_parent"
       android:orientation="vertical">
       
       <TextView
           android:id="@+id/titleTextView"
           android:layout_width="match_parent"
           android:layout_height="wrap_content"
           android:text="@string/my_feature_title" />
           
   </LinearLayout>
   ```

2. **Create Activity**:
   ```kotlin
   class MyFeatureActivity : AppCompatActivity() {
       private lateinit var binding: ActivityMyFeatureBinding
       
       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           binding = ActivityMyFeatureBinding.inflate(layoutInflater)
           setContentView(binding.root)
           
           setupUI()
       }
       
       private fun setupUI() {
           binding.titleTextView.text = getString(R.string.my_feature_title)
       }
   }
   ```

3. **Add to Manifest**:
   ```xml
   <activity
       android:name=".ui.MyFeatureActivity"
       android:exported="false"
       android:theme="@style/AppTheme" />
   ```

## Testing Guidelines

### Unit Testing

1. **Test Business Logic**:
   ```kotlin
   @Test
   fun testCredentialCreation() {
       val credential = WebAuthnCredential(
           keyAlias = "test-key",
           rpIdHash = "example.com".toByteArray().sha256(),
           rpName = "example.com",
           userId = "user123".toByteArray(),
           userDisplayName = "Test User",
           userName = "testuser"
       )
       
       assertNotNull(credential.keyHandle)
       assertEquals("example.com", credential.rpName)
   }
   ```

2. **Mock Dependencies**:
   ```kotlin
   @Test
   fun testAuthenticatorContext() {
       val mockContext = mockk<Context>()
       val authenticatorContext = TestAuthenticatorContext(mockContext, false)
       
       // Test methods
   }
   ```

### Integration Testing

1. **Test FIDO Flow**:
   ```kotlin
   @Test
   fun testMakeCredentialFlow() {
       val context = TestAuthenticatorContext(targetContext, false)
       val request = createMakeCredentialRequest()
       val response = Authenticator.handle(context, request)
       
       // Verify response format and content
   }
   ```

2. **Test UI Interactions**:
   ```kotlin
   @Test
   fun testMainActivityCredentialDisplay() {
       val scenario = ActivityScenario.launch(MainActivity::class.java)
       
       onView(withId(R.id.credentialCountTextView))
           .check(matches(isDisplayed()))
   }
   ```

### Manual Testing

1. **WebAuthn Demo Sites**:
   - https://webauthn.io/
   - https://webauthn-demo.appspot.com/

2. **Real Services**:
   - GitHub → Settings → Security → Passkeys
   - Google → Account → Security → 2-Step Verification

3. **Test Scenarios**:
   - Registration with resident key
   - Authentication with user verification
   - Multiple credentials per site
   - Cross-platform compatibility

## Code Style Guidelines

### Kotlin Style

1. **Naming Conventions**:
   ```kotlin
   // Classes: PascalCase
   class AuthenticatorContext
   
   // Functions and variables: camelCase
   fun verifyUser()
   val isResident: Boolean
   
   // Constants: SCREAMING_SNAKE_CASE
   const val MAX_CREDENTIAL_COUNT = 100
   ```

2. **Function Structure**:
   ```kotlin
   suspend fun processRequest(
       request: ByteArray,
       context: AuthenticatorContext
   ): ByteArray {
       // Validate input
       require(request.isNotEmpty()) { "Request cannot be empty" }
       
       // Process
       val result = withContext(Dispatchers.IO) {
           // Heavy computation
       }
       
       // Return result
       return result
   }
   ```

3. **Error Handling**:
   ```kotlin
   try {
       val result = riskyOperation()
       return result
   } catch (e: SpecificException) {
       Timber.w(e, "Specific error occurred")
       throw CustomException("User-friendly message", e)
   } catch (e: Exception) {
       Timber.e(e, "Unexpected error")
       throw e
   }
   ```

### Documentation

1. **Class Documentation**:
   ```kotlin
   /**
    * Manages WebAuthn credentials and cryptographic operations.
    * 
    * This class handles the creation, storage, and retrieval of WebAuthn
    * credentials using the Android Keystore for secure key management.
    * 
    * @param keyAlias Unique identifier for the cryptographic key
    * @param rpIdHash SHA-256 hash of the Relying Party identifier
    */
   class WebAuthnCredential(
       private val keyAlias: String,
       private val rpIdHash: ByteArray
   )
   ```

2. **Function Documentation**:
   ```kotlin
   /**
    * Performs WebAuthn assertion for authentication.
    * 
    * @param clientDataHash SHA-256 hash of the client data
    * @param userVerified Whether user verification was performed
    * @return CBOR-encoded assertion response
    * @throws CtapErrorException if assertion fails
    */
   suspend fun assertWebAuthn(
       clientDataHash: ByteArray,
       userVerified: Boolean
   ): ByteArray
   ```

## Security Considerations

### Cryptographic Operations

1. **Always Use Android Keystore**:
   ```kotlin
   // Good: Uses hardware-backed keystore
   val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
   
   // Bad: Software-only key storage
   val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
   ```

2. **Secure Key Generation**:
   ```kotlin
   val keyGenParameterSpec = KeyGenParameterSpec.Builder(
       keyAlias,
       KeyProperties.PURPOSE_SIGN
   )
   .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
   .setDigests(KeyProperties.DIGEST_SHA256)
   .setUserAuthenticationRequired(true)
   .setUserAuthenticationValidityDurationSeconds(validityDuration)
   .build()
   ```

### Data Protection

1. **Never Log Sensitive Data**:
   ```kotlin
   // Good: Log only non-sensitive information
   Timber.d("Processing request for RP: ${rpId}")
   
   // Bad: Logs private key material
   Timber.d("Private key: ${privateKey}")
   ```

2. **Validate All Inputs**:
   ```kotlin
   fun processRpId(rpId: String): String {
       require(rpId.isNotBlank()) { "RP ID cannot be blank" }
       require(rpId.length <= MAX_RP_ID_LENGTH) { "RP ID too long" }
       require(rpId.matches(RP_ID_PATTERN)) { "Invalid RP ID format" }
       return rpId.lowercase()
   }
   ```

## Performance Optimization

### Memory Management

1. **Use Appropriate Data Structures**:
   ```kotlin
   // For small, fixed collections
   val extensions = mapOf("hmac-secret" to true)
   
   // For large or dynamic collections
   val credentials = mutableListOf<WebAuthnCredential>()
   ```

2. **Avoid Memory Leaks**:
   ```kotlin
   class MyActivity : AppCompatActivity() {
       private var callback: (() -> Unit)? = null
       
       override fun onDestroy() {
           callback = null // Clear references
           super.onDestroy()
       }
   }
   ```

### Background Processing

1. **Use Appropriate Dispatchers**:
   ```kotlin
   // CPU-intensive work
   withContext(Dispatchers.Default) {
       computeHash(data)
   }
   
   // I/O operations
   withContext(Dispatchers.IO) {
       readFromStorage()
   }
   
   // UI updates
   withContext(Dispatchers.Main) {
       updateUI()
   }
   ```

2. **Optimize Database Operations**:
   ```kotlin
   // Batch operations when possible
   val credentials = getAllCredentials() // Single query
   credentials.forEach { processCredential(it) }
   ```

## Troubleshooting Common Issues

### Build Issues

1. **Gradle Sync Failures**:
   - Clean project: `./gradlew clean`
   - Invalidate caches: File → Invalidate Caches and Restart
   - Check Java version: `java -version`

2. **Dependency Conflicts**:
   - Check `gradle.properties` for version conflicts
   - Use `./gradlew dependencies` to analyze dependency tree

### Runtime Issues

1. **Keystore Errors**:
   - Verify device has lock screen security
   - Check keystore availability: `KeyStore.getInstance("AndroidKeyStore")`
   - Handle keystore exceptions gracefully

2. **Authentication Failures**:
   - Verify biometric enrollment
   - Check device credential setup
   - Handle authentication timeouts

### Testing Issues

1. **Emulator Limitations**:
   - Use physical device for NFC testing
   - Enable hardware acceleration for better performance
   - Configure emulator with appropriate API level

2. **WebAuthn Compatibility**:
   - Test with multiple browsers
   - Verify HTTPS requirement
   - Check FIDO conformance with official tools

---

For more detailed information, refer to the [API Reference](API_REFERENCE.md) and main [Documentation](../DOCUMENTATION.md).
