# Phone UI Implementation Guide

This document provides detailed guidance for implementing the phone-specific UI components that were commented out during the WearOS to phone porting process.

## Overview

The core FIDO authentication business logic has been successfully ported from WearOS and compiles without UI dependencies. However, several UI-dependent functions need phone-specific implementations to provide a complete user experience.

## Required UI Components

### 1. ConfirmDeviceCredentialActivity

**Purpose**: Prompt user for device credentials during FIDO authentication

**Implementation Requirements**:
- Create a new Activity class extending AppCompatActivity
- Use BiometricPrompt API for modern authentication UI
- Support PIN, pattern, password, fingerprint, and face unlock
- Handle authentication success/failure callbacks
- Return results via ResultReceiver

**UI Design Guidelines**:
```kotlin
class ConfirmDeviceCredentialActivity : AppCompatActivity() {
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var resultReceiver: ResultReceiver
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        resultReceiver = intent.getParcelableExtra(EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER)
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("FIDO Authentication Required")
            .setSubtitle("Verify your identity to complete authentication")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
            
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    resultReceiver.send(Activity.RESULT_OK, null)
                    finish()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    resultReceiver.send(Activity.RESULT_CANCELED, null)
                    finish()
                }
            })
            
        biometricPrompt.authenticate(promptInfo)
    }
}
```

### 2. CredentialChooserDialog

**Purpose**: Allow user to select from multiple stored FIDO credentials

**Implementation Requirements**:
- Create DialogFragment or BottomSheetDialogFragment
- Use RecyclerView to display credential list
- Show RP name, user name, and creation date for each credential
- Handle user selection and cancellation
- Return selected credential via callback

**UI Design Guidelines**:
```kotlin
class CredentialChooserDialog(
    private val credentials: Array<WebAuthnCredential>,
    private val context: Context,
    private val onCredentialSelected: (WebAuthnCredential?) -> Unit
) : DialogFragment() {
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val adapter = CredentialAdapter(credentials) { credential ->
            onCredentialSelected(credential)
            dismiss()
        }
        
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        
        return AlertDialog.Builder(requireContext())
            .setTitle("Select Credential")
            .setView(recyclerView)
            .setNegativeButton("Cancel") { _, _ ->
                onCredentialSelected(null)
            }
            .create()
    }
}
```

### 3. ManageSpaceActivity

**Purpose**: Provide settings interface for managing authenticator data

**Implementation Requirements**:
- Create settings Activity with reset functionality
- Show list of stored credentials and RPs
- Provide clear warning about data loss
- Require user confirmation before reset
- Show progress during reset operation

**UI Design Guidelines**:
```kotlin
class ManageSpaceActivity : AppCompatActivity() {
    private lateinit var resultReceiver: ResultReceiver
    
    private fun showResetConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Reset All Data")
            .setMessage("This will permanently delete all stored credentials. This action cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                performReset()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performReset() {
        // Show progress dialog
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Resetting authenticator data...")
            setCancelable(false)
            show()
        }
        
        lifecycleScope.launch {
            try {
                AuthenticatorContext.deleteAllData(this@ManageSpaceActivity)
                resultReceiver.send(Activity.RESULT_OK, null)
            } catch (e: Exception) {
                resultReceiver.send(Activity.RESULT_CANCELED, null)
            } finally {
                progressDialog.dismiss()
                finish()
            }
        }
    }
}
```

### 4. wink() Function

**Purpose**: Provide user feedback during authentication

**Implementation Requirements**:
- Create utility function for user feedback
- Use appropriate phone feedback mechanisms
- Consider accessibility requirements

**Implementation Example**:
```kotlin
fun wink(context: Context) {
    // Option 1: Camera flash (if available)
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
        val cameraId = cameraManager.cameraIdList[0]
        cameraManager.setTorchMode(cameraId, true)
        Handler(Looper.getMainLooper()).postDelayed({
            cameraManager.setTorchMode(cameraId, false)
        }, 200)
    } catch (e: Exception) {
        // Fall back to vibration
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(200)
        }
    }
}
```

### 5. KeyguardManager Extension

**Purpose**: Provide easy access to device security settings

**Implementation**:
```kotlin
val Context.keyguardManager: KeyguardManager?
    get() = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
```

## Required Constants

Add these constants to your constants file:

```kotlin
const val EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER = "confirm_device_credential_receiver"
const val EXTRA_MANAGE_SPACE_RECEIVER = "manage_space_receiver"
```

## AndroidManifest.xml Updates

Add the new activities to your manifest:

```xml
<activity
    android:name=".ui.ConfirmDeviceCredentialActivity"
    android:theme="@style/Theme.Transparent"
    android:exported="false" />
    
<activity
    android:name=".ui.ManageSpaceActivity"
    android:label="Manage Authenticator"
    android:exported="false" />
```

## Dependencies

Add these dependencies to your build.gradle:

```gradle
implementation "androidx.biometric:biometric:1.1.0"
implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.6.2"
```

## Testing Strategy

1. **Unit Tests**: Test business logic without UI dependencies
2. **UI Tests**: Use Espresso to test UI components
3. **Integration Tests**: Test complete authentication flows
4. **Manual Testing**: Test with real FIDO requests from web browsers

## Security Considerations

1. Always validate user authentication before sensitive operations
2. Use secure storage for credential data
3. Implement proper error handling to avoid information leakage
4. Follow Android security best practices for biometric authentication
5. Consider implementing app-level authentication for additional security

## Next Steps

1. Implement the UI components in the order listed above
2. Test each component individually
3. Integrate with the existing FIDO business logic
4. Perform end-to-end testing with real FIDO requests
5. Add proper error handling and user feedback
6. Implement accessibility features
7. Add comprehensive logging for debugging

This implementation will provide a complete phone-based FIDO authenticator experience while maintaining the robust business logic from the original WearOS implementation.
