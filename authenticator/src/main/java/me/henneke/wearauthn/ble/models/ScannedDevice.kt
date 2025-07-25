package me.henneke.wearauthn.ble.models

import android.bluetooth.BluetoothDevice
import android.content.Context
import me.henneke.wearauthn.R

/**
 * Data class representing a scanned BLE device with its advertisement information.
 */
data class ScannedDevice(
    val device: BluetoothDevice,
    val deviceName: String?,
    val deviceAddress: String,
    val rssi: Int,
    val advertisementData: ByteArray?,
    val scanTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Get a display name for the device (name or address if name is null)
     */
    val displayName: String
        get() = deviceName?.takeIf { it.isNotBlank() } ?: deviceAddress

    /**
     * Get RSSI as a signal strength description
     */
    fun getSignalStrength(context: Context): String {
        return when {
            rssi >= -50 -> context.getString(R.string.signal_strength_excellent)
            rssi >= -60 -> context.getString(R.string.signal_strength_good)
            rssi >= -70 -> context.getString(R.string.signal_strength_fair)
            rssi >= -80 -> context.getString(R.string.signal_strength_weak)
            else -> context.getString(R.string.signal_strength_very_weak)
        }
    }

    /**
     * Get a formatted string for display
     */
    fun getDisplayInfo(context: Context): String {
        return context.getString(
            R.string.device_info_format,
            displayName,
            deviceAddress,
            rssi,
            getSignalStrength(context)
        )
    }

    /**
     * Get formatted RSSI string
     */
    fun getRssiString(context: Context): String {
        return context.getString(R.string.device_rssi_format, rssi, getSignalStrength(context))
    }

    /**
     * Check if this device has a specific service UUID in its advertisement
     */
    fun hasServiceUuid(serviceUuid: String): Boolean {
        // This would require parsing the advertisement data
        // For now, just return false - can be enhanced later
        return false
    }

    /**
     * Custom equals method to handle BluetoothDevice and ByteArray comparison
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScannedDevice

        if (device != other.device) return false
        if (deviceName != other.deviceName) return false
        if (deviceAddress != other.deviceAddress) return false
        if (rssi != other.rssi) return false
        if (advertisementData != null) {
            if (other.advertisementData == null) return false
            if (!advertisementData.contentEquals(other.advertisementData)) return false
        } else if (other.advertisementData != null) return false

        return true
    }

    /**
     * Custom hashCode method to handle BluetoothDevice and ByteArray
     */
    override fun hashCode(): Int {
        var result = device.hashCode()
        result = 31 * result + (deviceName?.hashCode() ?: 0)
        result = 31 * result + deviceAddress.hashCode()
        result = 31 * result + rssi
        result = 31 * result + (advertisementData?.contentHashCode() ?: 0)
        return result
    }

    /**
     * String representation for debugging
     */
    override fun toString(): String {
        return "ScannedDevice(name='$deviceName', address='$deviceAddress', rssi=$rssi, dataSize=${advertisementData?.size ?: 0})"
    }
}
