package me.henneke.wearauthn.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import timber.log.Timber
import java.util.*

/**
 * FIDO U2F Bluetooth Low Energy service implementation.
 * 
 * Implements the FIDO U2F BLE specification with the following characteristics:
 * - U2F Control Point: F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB
 * - U2F Status: F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB  
 * - U2F Control Point Length: F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB
 * - U2F Service Revision: 0x2A28
 * - U2F Service Revision Bitfield: (vendor-defined)
 */
class FidoU2fBleService(private val context: Context) {
    
    companion object {
        // FIDO U2F Service UUID (0xFFFD)
        val FIDO_U2F_SERVICE_UUID: UUID = UUID.fromString("0000FFFD-0000-1000-8000-00805F9B34FB")
        
        // U2F Characteristic UUIDs
        val U2F_CONTROL_POINT_UUID: UUID = UUID.fromString("F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB")
        val U2F_STATUS_UUID: UUID = UUID.fromString("F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB")
        val U2F_CONTROL_POINT_LENGTH_UUID: UUID = UUID.fromString("F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB")
        val U2F_SERVICE_REVISION_UUID: UUID = UUID.fromString("00002A28-0000-1000-8000-00805F9B34FB")
        val U2F_SERVICE_REVISION_BITFIELD_UUID: UUID = UUID.fromString("F1D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB")
        
        // U2F Status values
        const val U2F_STATUS_IDLE: Byte = 0x00
        const val U2F_STATUS_PROCESSING: Byte = 0x01
        const val U2F_STATUS_NEED_PRESENCE: Byte = 0x02
        
        // Service revision
        const val SERVICE_REVISION = "1.0"
        const val SERVICE_REVISION_BITFIELD: Byte = 0x80.toByte() // Bit 7: supports U2F 1.2
    }
    
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    
    private var isAdvertising = false
    private var currentStatus = U2F_STATUS_IDLE
    
    // Characteristics
    private lateinit var controlPointCharacteristic: BluetoothGattCharacteristic
    private lateinit var statusCharacteristic: BluetoothGattCharacteristic
    private lateinit var controlPointLengthCharacteristic: BluetoothGattCharacteristic
    private lateinit var serviceRevisionCharacteristic: BluetoothGattCharacteristic
    private lateinit var serviceRevisionBitfieldCharacteristic: BluetoothGattCharacteristic
    
    // Connected device
    private var connectedDevice: BluetoothDevice? = null
    
    // Callback for U2F data
    var onU2fDataReceived: ((ByteArray) -> Unit)? = null
    
    fun initialize(): Boolean {
        // Check Bluetooth permissions
        if (!hasBluetoothPermissions()) {
            Timber.e("Missing required Bluetooth permissions")
            return false
        }

        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Timber.e("Bluetooth adapter not available")
            return false
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Timber.e("Bluetooth not enabled")
            return false
        }

        bluetoothLeAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Timber.e("BLE advertising not supported on this device")
            Timber.e("Device info: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            Timber.e("This is common on Samsung devices and some Android TV devices")
            return false
        }

        // Log device capabilities
        Timber.i("Bluetooth adapter: ${bluetoothAdapter!!.name}")
        Timber.i("Bluetooth address: ${bluetoothAdapter!!.address}")
        Timber.i("BLE advertising supported: ${bluetoothLeAdvertiser != null}")
        Timber.i("Multiple advertisement supported: ${bluetoothAdapter!!.isMultipleAdvertisementSupported}")

        setupGattServer()
        return true
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // Legacy permissions
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun setupGattServer() {
        val gattServerCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                super.onConnectionStateChange(device, status, newState)
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Timber.i("Device connected: ${device?.address}")
                        connectedDevice = device
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Timber.i("Device disconnected: ${device?.address}")
                        connectedDevice = null
                    }
                }
            }
            
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)

                // Check permissions before handling request
                if (!hasBluetoothPermissions()) {
                    Timber.w("Missing Bluetooth permissions for read request")
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION, 0, null)
                    } catch (e: SecurityException) {
                        Timber.e(e, "SecurityException when sending GATT response")
                    }
                    return
                }

                when (characteristic?.uuid) {
                    U2F_STATUS_UUID -> {
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf(currentStatus))
                        } catch (e: SecurityException) {
                            Timber.e(e, "SecurityException when sending status response")
                        }
                    }
                    U2F_CONTROL_POINT_LENGTH_UUID -> {
                        // Maximum length for U2F messages (typically 64 bytes)
                        val lengthBytes = byteArrayOf(0x00, 0x40) // 64 bytes in big-endian
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, lengthBytes)
                        } catch (e: SecurityException) {
                            Timber.e(e, "SecurityException when sending length response")
                        }
                    }
                    U2F_SERVICE_REVISION_UUID -> {
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, SERVICE_REVISION.toByteArray())
                        } catch (e: SecurityException) {
                            Timber.e(e, "SecurityException when sending revision response")
                        }
                    }
                    U2F_SERVICE_REVISION_BITFIELD_UUID -> {
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf(SERVICE_REVISION_BITFIELD))
                        } catch (e: SecurityException) {
                            Timber.e(e, "SecurityException when sending bitfield response")
                        }
                    }
                    else -> {
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null)
                        } catch (e: SecurityException) {
                            Timber.e(e, "SecurityException when sending error response")
                        }
                    }
                }
            }
            
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
                
                when (characteristic?.uuid) {
                    U2F_CONTROL_POINT_UUID -> {
                        if (value != null) {
                            Timber.d("Received U2F data: ${value.joinToString { "%02x".format(it) }}")
                            currentStatus = U2F_STATUS_PROCESSING
                            notifyStatusChange()
                            
                            // Process U2F data
                            onU2fDataReceived?.invoke(value)
                        }
                        
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }
                    }
                    else -> {
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, 0, null)
                        }
                    }
                }
            }
            
            override fun onDescriptorWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                descriptor: BluetoothGattDescriptor?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
                
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
        
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
        
        // Create FIDO U2F service
        val service = BluetoothGattService(FIDO_U2F_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        // U2F Control Point Characteristic (Write)
        controlPointCharacteristic = BluetoothGattCharacteristic(
            U2F_CONTROL_POINT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(controlPointCharacteristic)
        
        // U2F Status Characteristic (Read, Notify)
        statusCharacteristic = BluetoothGattCharacteristic(
            U2F_STATUS_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        // Add Client Characteristic Configuration Descriptor for notifications
        val statusDescriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        statusCharacteristic.addDescriptor(statusDescriptor)
        service.addCharacteristic(statusCharacteristic)
        
        // U2F Control Point Length Characteristic (Read)
        controlPointLengthCharacteristic = BluetoothGattCharacteristic(
            U2F_CONTROL_POINT_LENGTH_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(controlPointLengthCharacteristic)
        
        // U2F Service Revision Characteristic (Read)
        serviceRevisionCharacteristic = BluetoothGattCharacteristic(
            U2F_SERVICE_REVISION_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(serviceRevisionCharacteristic)
        
        // U2F Service Revision Bitfield Characteristic (Read)
        serviceRevisionBitfieldCharacteristic = BluetoothGattCharacteristic(
            U2F_SERVICE_REVISION_BITFIELD_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(serviceRevisionBitfieldCharacteristic)
        
        gattServer?.addService(service)
    }
    
    fun startAdvertising(): Boolean {
        if (isAdvertising) {
            Timber.w("Already advertising")
            return true
        }

        if (!hasBluetoothPermissions()) {
            Timber.e("Missing Bluetooth permissions for advertising")
            return false
        }

        if (bluetoothLeAdvertiser == null) {
            Timber.e("BluetoothLeAdvertiser is null")
            return false
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // More aggressive for better discovery
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // Higher power for better range
            .setConnectable(true)
            .setTimeout(0) // Advertise indefinitely
            .build()

        // Set a recognizable device name for FIDO U2F
        try {
            bluetoothAdapter?.name = "WearAuthn-FIDO"
        } catch (e: SecurityException) {
            Timber.w(e, "Cannot set Bluetooth device name")
        }

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true) // Include TX power for better RSSI calculation
            .addServiceUuid(ParcelUuid(FIDO_U2F_SERVICE_UUID))
            .build()

        // Optional scan response data for more information
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Already in main data
            .addServiceUuid(ParcelUuid(FIDO_U2F_SERVICE_UUID))
            .build()

        val advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                isAdvertising = true
                Timber.i("BLE advertising started successfully with settings: $settingsInEffect")
                Timber.i("Advertising FIDO U2F service UUID: $FIDO_U2F_SERVICE_UUID")
                Timber.i("Device should be discoverable as FIDO U2F device")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                isAdvertising = false
                val errorMessage = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                    else -> "Unknown error ($errorCode)"
                }
                Timber.e("BLE advertising failed: $errorMessage")
            }
        }

        try {
            Timber.i("Starting BLE advertising with FIDO U2F service...")
            bluetoothLeAdvertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
            return true
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException when starting advertising")
            return false
        } catch (e: Exception) {
            Timber.e(e, "Exception when starting advertising")
            return false
        }
    }
    
    fun stopAdvertising() {
        if (!isAdvertising) {
            return
        }
        
        bluetoothLeAdvertiser?.stopAdvertising(object : AdvertiseCallback() {})
        isAdvertising = false
        Timber.i("BLE advertising stopped")
    }
    
    fun sendU2fResponse(data: ByteArray) {
        connectedDevice?.let { device ->
            currentStatus = U2F_STATUS_IDLE
            notifyStatusChange()
            
            // In a real implementation, you would send the response data
            // through a response characteristic or by modifying the control point
            Timber.d("Sending U2F response: ${data.joinToString { "%02x".format(it) }}")
        }
    }
    
    fun setUserPresenceRequired() {
        currentStatus = U2F_STATUS_NEED_PRESENCE
        notifyStatusChange()
    }
    
    private fun notifyStatusChange() {
        connectedDevice?.let { device ->
            statusCharacteristic.value = byteArrayOf(currentStatus)
            gattServer?.notifyCharacteristicChanged(device, statusCharacteristic, false)
        }
    }
    
    fun cleanup() {
        stopAdvertising()
        gattServer?.close()
        gattServer = null
    }
    
    fun isAdvertising(): Boolean = isAdvertising
}
