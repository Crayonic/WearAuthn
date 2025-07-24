package me.henneke.wearauthn

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat

/**
 * Extension to get KeyguardManager system service
 */
val Context.keyguardManager: KeyguardManager
    get() = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

/**
 * Extension to get Vibrator system service with API level compatibility
 */
val Context.vibrator: Vibrator
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

/**
 * Provides user feedback during authentication (wink function)
 * This can be visual, haptic, or audio feedback to indicate authentication status
 */
fun Context.wink(success: Boolean = true) {
    try {
        val vibrator = this.vibrator
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = if (success) {
                // Success: Short vibration
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                // Error: Double vibration
                VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1)
            }
            vibrator.vibrate(pattern)
        } else {
            @Suppress("DEPRECATION")
            if (success) {
                vibrator.vibrate(100) // Short vibration for success
            } else {
                vibrator.vibrate(longArrayOf(0, 100, 100, 100), -1) // Double vibration for error
            }
        }
    } catch (e: Exception) {
        // Vibration might not be available, fail silently
        timber.log.Timber.w(e, "Failed to provide haptic feedback")
    }
}

/**
 * Check if device has secure lock screen
 */
fun Context.hasSecureLockScreen(): Boolean {
    return keyguardManager.isDeviceSecure
}

/**
 * Check if keyguard is currently locked
 */
fun Context.isKeyguardLocked(): Boolean {
    return keyguardManager.isKeyguardLocked
}
