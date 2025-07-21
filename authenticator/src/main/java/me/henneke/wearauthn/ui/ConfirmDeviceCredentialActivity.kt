package me.henneke.wearauthn.ui

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.ResultReceiver
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt as AndroidXBiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import me.henneke.wearauthn.databinding.ActivityConfirmDeviceCredentialBinding
import me.henneke.wearauthn.R
import me.henneke.wearauthn.Logging
import timber.log.Timber

/**
 * Activity for confirming device credentials during FIDO authentication.
 * Uses BiometricPrompt API for modern authentication UI supporting PIN, pattern, 
 * password, fingerprint, and face unlock.
 */
class ConfirmDeviceCredentialActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityConfirmDeviceCredentialBinding
    private var resultReceiver: ResultReceiver? = null
    
    companion object {
        const val EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER = "confirm_device_credential_receiver"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("onCreate() called")

        // Initialize view binding
        binding = ActivityConfirmDeviceCredentialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the result receiver from intent
        resultReceiver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER, ResultReceiver::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER)
        }

        Timber.d("ResultReceiver: ${if (resultReceiver != null) "found" else "null"}")

        setupUI()
        checkBiometricAvailability()
    }
    
    private fun setupUI() {
        Timber.d("setupUI() called")

        binding.authenticateButton.setOnClickListener {
            Timber.d("Authenticate button clicked")
            showBiometricPrompt()
        }

        binding.cancelButton.setOnClickListener {
            Timber.d("Cancel button clicked")
            finishWithResult(Activity.RESULT_CANCELED)
        }
    }
    
    private fun checkBiometricAvailability() {
        val biometricManager = BiometricManager.from(this)

        // For Android 9 compatibility, check device credential first
        val authenticators = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            // Android 9/10 compatibility - prefer device credential
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Authentication is available
                binding.messageTextView.text = getString(R.string.confirm_device_credential_message)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                binding.messageTextView.text = "No biometric hardware available. Please use device PIN/password."
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                binding.messageTextView.text = "Biometric hardware unavailable. Please use device PIN/password."
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                binding.messageTextView.text = "No biometric credentials enrolled. Please use device PIN/password."
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                binding.messageTextView.text = "Security update required for biometric authentication."
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                binding.messageTextView.text = "Biometric authentication not supported. Please use device PIN/password."
            }
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                binding.messageTextView.text = "Authentication status unknown. Please use device PIN/password."
            }
            else -> {
                binding.messageTextView.text = "Authentication not available. Please set up device security."
                binding.authenticateButton.isEnabled = false
            }
        }
    }
    
    private fun showBiometricPrompt() {
        Timber.d("showBiometricPrompt() called")

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = AndroidXBiometricPrompt(this as FragmentActivity, executor,
            object : AndroidXBiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Timber.e("Authentication error: code=$errorCode, message=$errString")

                    if (errorCode == AndroidXBiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == AndroidXBiometricPrompt.ERROR_CANCELED) {
                        Timber.d("User canceled authentication")
                        finishWithResult(Activity.RESULT_CANCELED)
                    } else {
                        // Show error and allow retry
                        binding.messageTextView.text = "Authentication error: $errString"
                    }
                }

                override fun onAuthenticationSucceeded(result: AndroidXBiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Timber.d("Authentication succeeded! Finishing with RESULT_OK")
                    finishWithResult(Activity.RESULT_OK)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Timber.w("Authentication failed - user can retry")
                    binding.messageTextView.text = "Authentication failed. Please try again."
                }
            })
        
        // Build prompt info with Android 9 compatibility
        val promptInfoBuilder = AndroidXBiometricPrompt.PromptInfo.Builder()
            .setTitle("FIDO Authentication")
            .setSubtitle("Authenticate to continue with security key operation")

        val promptInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ - use combined authenticators
            promptInfoBuilder
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
        } else {
            // Android 9/10 - prefer device credential with fallback
            promptInfoBuilder
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
        }
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    private fun finishWithResult(resultCode: Int) {
        Timber.d("finishWithResult() called with resultCode=$resultCode")

        if (resultReceiver != null) {
            Timber.d("Sending result to ResultReceiver")
            resultReceiver?.send(resultCode, Bundle())
        } else {
            Timber.w("ResultReceiver is null! Cannot send result")
        }

        Timber.d("Finishing activity")
        finish()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        finishWithResult(Activity.RESULT_CANCELED)
    }
}
