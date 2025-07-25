package me.henneke.wearauthn.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.henneke.wearauthn.R
import timber.log.Timber

/**
 * Helper class for handling Bluetooth permission dialogs and requests
 */
class PermissionDialogHelper(private val activity: Activity) {
    
    companion object {
        const val BLUETOOTH_PERMISSION_REQUEST_CODE = 1001
        
        /**
         * Get required Bluetooth permissions based on Android version
         */
        fun getRequiredBluetoothPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ permissions
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_ADVERTISE,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN
                )
            } else {
                // Legacy permissions for older Android versions
                arrayOf(
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }
    }
    
    /**
     * Check if all required Bluetooth permissions are granted
     */
    fun hasAllBluetoothPermissions(): Boolean {
        val permissions = getRequiredBluetoothPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get list of missing permissions
     */
    fun getMissingPermissions(): List<String> {
        val permissions = getRequiredBluetoothPermissions()
        return permissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if we should show rationale for any of the permissions
     */
    fun shouldShowRationale(): Boolean {
        val permissions = getRequiredBluetoothPermissions()
        return permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
    
    /**
     * Show permission rationale dialog explaining why permissions are needed
     */
    fun showPermissionRationale(onGrantClicked: () -> Unit, onDenyClicked: () -> Unit) {
        Timber.i("📋 Showing permission rationale dialog")
        
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.permission_rationale_title))
            .setMessage(activity.getString(R.string.permission_rationale_message))
            .setPositiveButton(activity.getString(R.string.permission_dialog_grant)) { dialog, _ ->
                dialog.dismiss()
                onGrantClicked()
            }
            .setNegativeButton(activity.getString(R.string.permission_dialog_deny)) { dialog, _ ->
                dialog.dismiss()
                onDenyClicked()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show initial permission request dialog
     */
    fun showPermissionRequestDialog(onGrantClicked: () -> Unit, onDenyClicked: () -> Unit) {
        Timber.i("🔐 Showing permission request dialog")
        
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.permission_dialog_title))
            .setMessage(activity.getString(R.string.permission_dialog_message))
            .setPositiveButton(activity.getString(R.string.permission_dialog_grant)) { dialog, _ ->
                dialog.dismiss()
                onGrantClicked()
            }
            .setNegativeButton(activity.getString(R.string.permission_dialog_deny)) { dialog, _ ->
                dialog.dismiss()
                onDenyClicked()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show permission denied dialog with option to go to settings
     */
    fun showPermissionDeniedDialog(onSettingsClicked: () -> Unit, onDismissClicked: () -> Unit) {
        Timber.i("❌ Showing permission denied dialog")
        
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.permission_denied_title))
            .setMessage(activity.getString(R.string.permission_denied_message))
            .setPositiveButton(activity.getString(R.string.permission_dialog_settings)) { dialog, _ ->
                dialog.dismiss()
                onSettingsClicked()
            }
            .setNegativeButton(activity.getString(R.string.permission_dialog_deny)) { dialog, _ ->
                dialog.dismiss()
                onDismissClicked()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Request Bluetooth permissions from the system
     */
    fun requestBluetoothPermissions() {
        val permissions = getRequiredBluetoothPermissions()
        val missingPermissions = getMissingPermissions()
        
        Timber.i("🔒 Requesting Bluetooth permissions:")
        missingPermissions.forEach { permission ->
            Timber.i("  - $permission")
        }
        
        ActivityCompat.requestPermissions(
            activity,
            missingPermissions.toTypedArray(),
            BLUETOOTH_PERMISSION_REQUEST_CODE
        )
    }
    
    /**
     * Open app settings page
     */
    fun openAppSettings() {
        Timber.i("⚙️ Opening app settings")
        
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open app settings")
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                activity.startActivity(intent)
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to open settings")
            }
        }
    }
    
    /**
     * Handle the result of permission request
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onAllGranted: () -> Unit,
        onSomeGranted: (granted: List<String>, denied: List<String>) -> Unit,
        onAllDenied: () -> Unit
    ) {
        if (requestCode != BLUETOOTH_PERMISSION_REQUEST_CODE) return
        
        Timber.i("📋 Permission result received:")
        
        val grantedPermissions = mutableListOf<String>()
        val deniedPermissions = mutableListOf<String>()
        
        permissions.forEachIndexed { index, permission ->
            val granted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
            Timber.i("  - $permission: ${if (granted) "GRANTED" else "DENIED"}")
            
            if (granted) {
                grantedPermissions.add(permission)
            } else {
                deniedPermissions.add(permission)
            }
        }
        
        when {
            deniedPermissions.isEmpty() -> {
                Timber.i("✅ All permissions granted")
                onAllGranted()
            }
            grantedPermissions.isEmpty() -> {
                Timber.w("❌ All permissions denied")
                onAllDenied()
            }
            else -> {
                Timber.w("⚠️ Some permissions granted, some denied")
                onSomeGranted(grantedPermissions, deniedPermissions)
            }
        }
    }
    
    /**
     * Show appropriate permission dialog based on current state
     */
    fun showAppropriatePermissionDialog(
        onPermissionsGranted: () -> Unit,
        onPermissionsDenied: () -> Unit
    ) {
        when {
            hasAllBluetoothPermissions() -> {
                Timber.d("✅ All permissions already granted")
                onPermissionsGranted()
            }
            shouldShowRationale() -> {
                showPermissionRationale(
                    onGrantClicked = { requestBluetoothPermissions() },
                    onDenyClicked = onPermissionsDenied
                )
            }
            else -> {
                showPermissionRequestDialog(
                    onGrantClicked = { requestBluetoothPermissions() },
                    onDenyClicked = onPermissionsDenied
                )
            }
        }
    }
}
