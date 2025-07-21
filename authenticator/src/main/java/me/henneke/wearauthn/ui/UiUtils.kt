package me.henneke.wearauthn.ui

import android.app.KeyguardManager
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import timber.log.Timber
import me.henneke.wearauthn.Logging

/**
 * Utility functions for UI operations and user feedback
 */

/**
 * Extension property to get KeyguardManager from Context
 */
val Context.keyguardManager: KeyguardManager?
    get() = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

/**
 * Provide user feedback (visual/haptic) during authentication.
 * Uses camera flash if available, falls back to vibration.
 */
fun wink(context: Context) {
    Timber.d("wink() called - providing user feedback")
    try {
        // Option 1: Camera flash (if available)
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager != null) {
            try {
                val cameraIdList = cameraManager.cameraIdList
                if (cameraIdList.isNotEmpty()) {
                    val cameraId = cameraIdList[0]
                    Timber.d("Using camera flash for feedback")
                    cameraManager.setTorchMode(cameraId, true)
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            cameraManager.setTorchMode(cameraId, false)
                        } catch (e: Exception) {
                            // Ignore errors when turning off flash
                        }
                    }, 200)
                    return // Successfully used flash, no need for vibration
                } else {
                    Timber.d("No camera available for flash")
                }
            } catch (e: Exception) {
                Timber.d("Flash not available, falling back to vibration: ${e.message}")
            }
        } else {
            Timber.d("CameraManager not available")
        }
        
        // Option 2: Vibration fallback
        Timber.d("Using vibration for feedback")
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                Timber.d("Vibration triggered (API 26+)")
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(200)
                Timber.d("Vibration triggered (legacy)")
            }
        } ?: run {
            Timber.w("No vibrator available")
        }
        
    } catch (e: Exception) {
        // Log error but don't crash
        Timber.e(e, "Error in wink function")
    }
}

/**
 * Constants for UI components
 */
object UiConstants {
    const val EXTRA_CONFIRM_DEVICE_CREDENTIAL_RECEIVER = "confirm_device_credential_receiver"
    const val EXTRA_MANAGE_SPACE_RECEIVER = "manage_space_receiver"
}
