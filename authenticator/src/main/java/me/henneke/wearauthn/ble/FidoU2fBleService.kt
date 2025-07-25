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
import me.henneke.wearauthn.ble.models.BleFrameStatus
import me.henneke.wearauthn.ble.models.CtapBleConstants
import me.henneke.wearauthn.ble.models.CtapBleConstants.getCtapCommandName
import me.henneke.wearauthn.ble.models.CtapBleConstants.getBridgeCommandName
import me.henneke.wearauthn.ble.models.U2fBleConstants
import me.henneke.wearauthn.ble.models.U2fBleConstants.FIDO_U2F_SERVICE_UUID
import me.henneke.wearauthn.ble.models.U2fBleConstants.U2F_CONTROL_POINT_UUID
import me.henneke.wearauthn.ble.models.U2fBleConstants.U2F_STATUS_UUID
import me.henneke.wearauthn.ble.models.U2fBleConstants.U2F_CONTROL_POINT_LENGTH_UUID
import me.henneke.wearauthn.ble.models.U2fBleConstants.U2F_SERVICE_REVISION_UUID
import me.henneke.wearauthn.ble.models.U2fBleConstants.U2F_SERVICE_REVISION_BITFIELD_UUID
import me.henneke.wearauthn.ble.models.U2fBleConstants.PROXIMITY_LOGIN_BITFIELD_UUID
import me.henneke.wearauthn.ble.models.U2fBleConstants.U2F_STATUS_IDLE
import me.henneke.wearauthn.ble.models.U2fBleConstants.U2F_STATUS_PROCESSING
import me.henneke.wearauthn.ble.models.U2fBleConstants.U2F_STATUS_NEED_PRESENCE
import me.henneke.wearauthn.ble.models.U2fBleConstants.SERVICE_REVISION
import me.henneke.wearauthn.ble.models.U2fBleConstants.SERVICE_REVISION_BITFIELD
import me.henneke.wearauthn.ble.models.U2fBleConstants.PROXIMITY_LOGIN_BITFIELD_DESCRIPTION
import me.henneke.wearauthn.ble.models.U2fBleConstants.PROXIMITY_LOGIN_BITFIELD_VALUE
import me.henneke.wearauthn.ble.models.U2fBleConstants.U2F_CONTROL_POINT_LENGTH
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
        // Device identifier preferences
        private const val PREFS_NAME = "wearauthn_device_prefs"
        private const val DEVICE_ID_KEY = "device_identifier"

        /**
         * Gets or generates a unique 5-digit device identifier for this device.
         * The identifier is stored persistently and will be the same across app restarts.
         */
        fun getDeviceIdentifier(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var deviceId = prefs.getString(DEVICE_ID_KEY, null)

            if (deviceId == null) {
                // Generate a new 5-digit identifier based on device characteristics
                val androidId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"

                // Create a hash and take first 5 digits
                val hash = androidId.hashCode()
                deviceId = String.format("%05d", kotlin.math.abs(hash) % 100000)

                // Store for future use
                prefs.edit().putString(DEVICE_ID_KEY, deviceId).apply()
                Timber.i("Generated new device identifier: $deviceId")
            }

            return deviceId
        }

        /**
         * Gets the complete advertising name with device identifier.
         */
        fun getAdvertisingName(context: Context): String {
            val deviceId = getDeviceIdentifier(context)
            return "CrayonicB$deviceId"
        }




    }
    
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    
    private var isAdvertising = false
    private var currentStatus = U2F_STATUS_IDLE

    // Store echo data for Crayonic Bridge responses
    private var echoResponseData: ByteArray? = null
    
    // Characteristics
    private lateinit var controlPointCharacteristic: BluetoothGattCharacteristic
    private lateinit var statusCharacteristic: BluetoothGattCharacteristic
    private lateinit var controlPointLengthCharacteristic: BluetoothGattCharacteristic
    private lateinit var serviceRevisionCharacteristic: BluetoothGattCharacteristic
    private lateinit var serviceRevisionBitfieldCharacteristic: BluetoothGattCharacteristic
    private lateinit var proximityLoginBitfieldCharacteristic: BluetoothGattCharacteristic
    
    // Connected device
    private var connectedDevice: BluetoothDevice? = null

    // Callback for U2F data
    var onU2fDataReceived: ((ByteArray) -> Unit)? = null

    // Advertisement callback
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            isAdvertising = true
            Timber.i("✅ BLE advertising started successfully with settings: $settingsInEffect")
            Timber.i("📡 Advertising FIDO U2F service UUID: $FIDO_U2F_SERVICE_UUID")
            Timber.i("🔍 Device should be discoverable as '${getAdvertisingName(context)}'")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            val errorMessage = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else -> "Unknown error ($errorCode)"
            }
            Timber.e("❌ BLE advertising failed: $errorMessage")

            // If too many advertisers, try to force cleanup and retry once
            if (errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) {
                Timber.w("🔄 Too many advertisers detected, attempting cleanup and retry...")
                forceStopAllAdvertisements()
                // Note: We don't retry automatically here to avoid infinite loops
                // The caller should handle retry logic if needed
            }

            isAdvertising = false
        }
    }
    
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

                // Log detailed read request information
                Timber.d("📖 BLE READ REQUEST:")
                Timber.d("  Device: ${device?.address} (${device?.name})")
                Timber.d("  Request ID: $requestId")
                Timber.d("  Offset: $offset")
                Timber.d("  Characteristic UUID: ${characteristic?.uuid}")
                Timber.d("  Characteristic Properties: ${characteristic?.properties}")
                Timber.d("  Characteristic Permissions: ${characteristic?.permissions}")

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
                    U2F_CONTROL_POINT_UUID -> {
                        Timber.i("📖 CONTROL POINT READ REQUEST:")
                        Timber.i("  Device: ${device?.address} (${device?.name})")
                        Timber.i("  Request ID: $requestId")
                        Timber.i("  Echo data available: ${echoResponseData != null}")

                        if (echoResponseData != null) {
                            Timber.i("  Echo data size: ${echoResponseData!!.size} bytes")
                            Timber.i("  Echo data: ${echoResponseData!!.joinToString(" ") { "0x%02x".format(it) }}")
                        }

                        // Return echo data if available, otherwise return empty
                        val responseData = echoResponseData ?: byteArrayOf()
                        Timber.i("📤 SENDING CONTROL POINT RESPONSE:")
                        Timber.i("  Response size: ${responseData.size} bytes")
                        Timber.i("  Response data: ${responseData.joinToString(" ") { "0x%02x".format(it) }}")

                        if (echoResponseData != null) {
                            Timber.i("🔄 Returning Crayonic Bridge echo data (${responseData.size} bytes)")
                            // Clear the echo data after reading
                            echoResponseData = null
                            Timber.i("🧹 Echo data cleared after reading")
                        } else {
                            Timber.w("📭 No echo data available, returning empty response")
                        }

                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, responseData)
                            Timber.i("✅ Control Point response sent successfully")
                            Timber.i("📤 Actual sent data: ${responseData.joinToString(" ") { "0x%02x".format(it) }}")
                        } catch (e: SecurityException) {
                            Timber.e(e, "❌ SecurityException when sending control point response")
                        }
                    }
                    U2F_STATUS_UUID -> {
                        val statusData = byteArrayOf(currentStatus)
                        Timber.d("📤 Sending U2F Status: ${statusData.joinToString { "0x%02x".format(it) }} (status: $currentStatus)")
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, statusData)
                            Timber.d("✅ Status response sent successfully")
                        } catch (e: SecurityException) {
                            Timber.e(e, "❌ SecurityException when sending status response")
                        }
                    }
                    U2F_CONTROL_POINT_LENGTH_UUID -> {
                        // Maximum length for U2F messages (typically 64 bytes)
                        val lengthBytes = byteArrayOf(0x00, 0x40) // 64 bytes in big-endian
                        Timber.d("📤 Sending Control Point Length: ${lengthBytes.joinToString { "0x%02x".format(it) }} (64 bytes)")
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, lengthBytes)
                            Timber.d("✅ Length response sent successfully")
                        } catch (e: SecurityException) {
                            Timber.e(e, "❌ SecurityException when sending length response")
                        }
                    }
                    U2F_SERVICE_REVISION_UUID -> {
                        val revisionData = SERVICE_REVISION.toByteArray()
                        Timber.d("📤 Sending Service Revision: ${revisionData.joinToString { "0x%02x".format(it) }} (\"$SERVICE_REVISION\")")
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, revisionData)
                            Timber.d("✅ Service revision response sent successfully")
                        } catch (e: SecurityException) {
                            Timber.e(e, "❌ SecurityException when sending revision response")
                        }
                    }
                    U2F_SERVICE_REVISION_BITFIELD_UUID -> {
                        val bitfieldData = byteArrayOf(SERVICE_REVISION_BITFIELD)
                        Timber.d("📤 Sending Service Revision Bitfield: ${bitfieldData.joinToString { "0x%02x".format(it) }} (bitfield: $SERVICE_REVISION_BITFIELD)")
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, bitfieldData)
                            Timber.d("✅ Service revision bitfield response sent successfully")
                        } catch (e: SecurityException) {
                            Timber.e(e, "❌ SecurityException when sending bitfield response")
                        }
                    }
                    PROXIMITY_LOGIN_BITFIELD_UUID -> {
                        val proximityData = byteArrayOf(PROXIMITY_LOGIN_BITFIELD_VALUE)
                        Timber.d("📤 Sending Proximity Login Bitfield: ${proximityData.joinToString { "0x%02x".format(it) }} (value: $PROXIMITY_LOGIN_BITFIELD_VALUE)")
                        Timber.d("📝 Description: $PROXIMITY_LOGIN_BITFIELD_DESCRIPTION")
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, proximityData)
                            Timber.d("✅ Proximity login bitfield response sent successfully")
                        } catch (e: SecurityException) {
                            Timber.e(e, "❌ SecurityException when sending proximity bitfield response")
                        }
                    }
                    else -> {
                        Timber.w("❌ Unknown characteristic read request: ${characteristic?.uuid}")
                        Timber.w("📤 Sending READ_NOT_PERMITTED response")
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null)
                            Timber.d("✅ READ_NOT_PERMITTED response sent")
                        } catch (e: SecurityException) {
                            Timber.e(e, "❌ SecurityException when sending error response")
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
                // DON'T call super - we handle everything ourselves
                // super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

                // Log detailed write request information
                Timber.i("✍️ BLE WRITE REQUEST RECEIVED:")
                Timber.i("  Device: ${device?.address} (${device?.name})")
                Timber.i("  Request ID: $requestId")
                Timber.i("  Characteristic UUID: ${characteristic?.uuid}")
                Timber.i("  Prepared Write: $preparedWrite")
                Timber.i("  Response Needed: $responseNeeded")
                Timber.i("  Offset: $offset")
                Timber.i("  Data Length: ${value?.size ?: 0} bytes")
                Timber.i("  Raw Data Bytes: ${value?.contentToString() ?: "null"}")
                if (value != null && value.isNotEmpty()) {
                    Timber.i("  Data Hex: ${value.joinToString(" ") { "0x%02x".format(it) }}")
                    if (value.size <= 32) { // Only show ASCII for small payloads
                        val asciiData = value.map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")
                        Timber.i("  ASCII: \"$asciiData\"")
                    }
                } else {
                    Timber.w("  ⚠️ Received null or empty data!")
                }
                
                when (characteristic?.uuid) {
                    U2F_CONTROL_POINT_UUID -> {
                        Timber.d("📝 Processing U2F Control Point write")
                        if (value != null && value.isNotEmpty()) {
                            Timber.d("📥 Received U2F data: ${value.joinToString { "0x%02x".format(it) }}")

                            // Check if this is a custom vendor command
                            val cmd = value[0]
                            val cmdName = getCtapCommandName(cmd)
                            Timber.d("🔍 Command: $cmdName (0x%02x)".format(cmd))

                            when (cmd) {
                                CtapBleConstants.CTAPBLE_CRAYONIC_BRIDGE -> {
                                    handleCrayonicBridgeCommand(device, requestId, value, responseNeeded)
                                    return // Early return, response handled in method
                                }
                                CtapBleConstants.CTAPBLE_SMARTCARD, CtapBleConstants.CTAPBLE_SMARTCARD_AUX -> {
                                    handleSmartcardCommand(device, requestId, cmd, value, responseNeeded)
                                    return // Early return, response handled in method
                                }
                                CtapBleConstants.CTAPBLE_PING, CtapBleConstants.CTAPBLE_KEEPALIVE, CtapBleConstants.CTAPBLE_MSG, CtapBleConstants.CTAPBLE_CANCEL, CtapBleConstants.CTAPBLE_ERROR -> {
                                    Timber.d("📋 Standard FIDO CTAP command: $cmdName")
                                    // Continue with standard U2F processing
                                }
                                else -> {
                                    Timber.d("📋 Unknown/Standard U2F command: 0x%02x".format(cmd))
                                    // Continue with standard U2F processing
                                }
                            }

                            Timber.d("🔄 Changing status from $currentStatus to $U2F_STATUS_PROCESSING")
                            currentStatus = U2F_STATUS_PROCESSING
                            notifyStatusChange()

                            // Process U2F data
                            Timber.d("🚀 Invoking U2F data handler")
                            onU2fDataReceived?.invoke(value)
                        } else {
                            Timber.w("⚠️ Received null or empty value for U2F Control Point")
                        }

                        if (responseNeeded) {
                            Timber.d("📤 Sending SUCCESS response for U2F Control Point write")
                            try {
                                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                                Timber.d("✅ SUCCESS response sent")
                            } catch (e: SecurityException) {
                                Timber.e(e, "❌ SecurityException when sending SUCCESS response")
                            }
                        } else {
                            Timber.d("ℹ️ No response needed for U2F Control Point write")
                        }
                    }
                    else -> {
                        Timber.w("❌ Unknown characteristic write request: ${characteristic?.uuid}")
                        if (responseNeeded) {
                            Timber.d("📤 Sending WRITE_NOT_PERMITTED response")
                            try {
                                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, 0, null)
                                Timber.d("✅ WRITE_NOT_PERMITTED response sent")
                            } catch (e: SecurityException) {
                                Timber.e(e, "❌ SecurityException when sending WRITE_NOT_PERMITTED response")
                            }
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

        // Proximity Login Bitfield Characteristic (Read)
        proximityLoginBitfieldCharacteristic = BluetoothGattCharacteristic(
            PROXIMITY_LOGIN_BITFIELD_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        // Set initial value
        proximityLoginBitfieldCharacteristic.value = byteArrayOf(PROXIMITY_LOGIN_BITFIELD_VALUE)
        service.addCharacteristic(proximityLoginBitfieldCharacteristic)

        gattServer?.addService(service)
    }
    
    fun startAdvertising(): Boolean {
        Timber.i("🚀 STARTING BLE ADVERTISING")

        // Force stop any existing advertisements first
        if (isAdvertising) {
            Timber.w("⚠️ Already advertising, stopping previous advertisement first")
            forceStopAllAdvertisements()
            // Small delay to ensure cleanup completes
            Thread.sleep(100)
        }

        if (!hasBluetoothPermissions()) {
            Timber.e("❌ Missing Bluetooth permissions for advertising")
            return false
        }

        if (bluetoothLeAdvertiser == null) {
            Timber.e("❌ BluetoothLeAdvertiser is null")
            return false
        }

        // Set a custom device name for FIDO U2F with unique identifier
        val advertisingName = getAdvertisingName(context)

        Timber.d("📋 Advertisement setup:")
        Timber.d("  Device name: $advertisingName")
        Timber.d("  Service UUID: $FIDO_U2F_SERVICE_UUID")
        Timber.d("  Multiple advertisement supported: ${bluetoothAdapter?.isMultipleAdvertisementSupported}")

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // More aggressive for better discovery
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // Higher power for better range
            .setConnectable(true)
            .setTimeout(0) // Advertise indefinitely
            .build()
        try {
            bluetoothAdapter?.name = advertisingName
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

        // Using the class-level advertiseCallback defined above

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
            Timber.d("🛑 Not currently advertising, nothing to stop")
            return
        }

        Timber.i("🛑 STOPPING BLE ADVERTISING")
        try {
            // Use the same callback that was used to start advertising
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Timber.i("✅ BLE advertising stopped successfully")
        } catch (e: SecurityException) {
            Timber.e(e, "❌ SecurityException when stopping advertising")
            isAdvertising = false // Reset state even if stop failed
        } catch (e: Exception) {
            Timber.e(e, "❌ Exception when stopping advertising")
            isAdvertising = false // Reset state even if stop failed
        }
    }
    
    fun sendU2fResponse(data: ByteArray) {
        connectedDevice?.let { device ->
            Timber.d("📤 SENDING U2F RESPONSE:")
            Timber.d("  Device: ${device.address} (${device.name})")
            Timber.d("  Data Length: ${data.size} bytes")
            Timber.d("  Data: ${data.joinToString(" ") { "0x%02x".format(it) }}")

            currentStatus = U2F_STATUS_IDLE
            Timber.d("🔄 Status changed to IDLE after response")
            notifyStatusChange()

            // In a real implementation, you would send the response data
            // through a response characteristic or by modifying the control point
            Timber.d("✅ U2F response processing completed")
        } ?: run {
            Timber.w("⚠️ Cannot send U2F response - no connected device")
        }
    }
    
    fun setUserPresenceRequired() {
        Timber.d("👤 USER PRESENCE REQUIRED:")
        Timber.d("  Previous Status: $currentStatus")
        currentStatus = U2F_STATUS_NEED_PRESENCE
        Timber.d("  New Status: $currentStatus (NEED_PRESENCE)")
        Timber.d("🔔 Notifying client that user presence is required")
        notifyStatusChange()
    }
    
    private fun notifyStatusChange() {
        connectedDevice?.let { device ->
            val statusData = byteArrayOf(currentStatus)
            statusCharacteristic.value = statusData

            Timber.d("📢 NOTIFYING STATUS CHANGE:")
            Timber.d("  Device: ${device.address} (${device.name})")
            Timber.d("  Status: $currentStatus (0x%02x)".format(currentStatus))
            Timber.d("  Status Meaning: ${getStatusDescription(currentStatus)}")

            try {
                val success = gattServer?.notifyCharacteristicChanged(device, statusCharacteristic, false)
                if (success == true) {
                    Timber.d("✅ Status notification sent successfully")
                } else {
                    Timber.w("⚠️ Status notification may have failed (returned $success)")
                }
            } catch (e: SecurityException) {
                Timber.e(e, "❌ SecurityException when sending status notification")
            }
        } ?: run {
            Timber.w("⚠️ Cannot notify status change - no connected device")
        }
    }

    private fun getStatusDescription(status: Byte): String {
        return when (status) {
            U2F_STATUS_IDLE -> "IDLE - Ready for requests"
            U2F_STATUS_PROCESSING -> "PROCESSING - Handling request"
            U2F_STATUS_NEED_PRESENCE -> "NEED_PRESENCE - Waiting for user interaction"
            else -> "UNKNOWN - Status code $status"
        }
    }
    
    fun forceStopAllAdvertisements() {
        Timber.i("🧹 FORCE STOPPING ALL BLE ADVERTISEMENTS")
        try {
            // Stop with our callback
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)

            // Also try to stop with a generic callback to catch any orphaned advertisements
            bluetoothLeAdvertiser?.stopAdvertising(object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Timber.d("Generic callback: Advertisement stopped")
                }

                override fun onStartFailure(errorCode: Int) {
                    Timber.d("Generic callback: Stop failed with code $errorCode")
                }
            })

            isAdvertising = false
            Timber.i("✅ Force stop completed")
        } catch (e: SecurityException) {
            Timber.e(e, "❌ SecurityException during force stop")
            isAdvertising = false
        } catch (e: Exception) {
            Timber.e(e, "❌ Exception during force stop")
            isAdvertising = false
        }
    }

    fun cleanup() {
        Timber.i("🧹 CLEANING UP BLE SERVICE")
        forceStopAllAdvertisements()

        try {
            gattServer?.close()
            Timber.d("✅ GATT server closed")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error closing GATT server")
        }

        gattServer = null
        bluetoothLeAdvertiser = null
        Timber.i("✅ BLE service cleanup completed")
    }
    
    fun isAdvertising(): Boolean = isAdvertising

    fun restartAdvertising(): Boolean {
        Timber.i("🔄 RESTARTING BLE ADVERTISING")
        forceStopAllAdvertisements()
        Thread.sleep(200) // Give more time for cleanup
        return startAdvertising()
    }

    fun setProximityLoginEnabled(enabled: Boolean) {
        val newValue: Byte = if (enabled) 0x01 else 0x00
        proximityLoginBitfieldCharacteristic.value = byteArrayOf(newValue)

        Timber.i("🔧 PROXIMITY LOGIN SETTING CHANGED:")
        Timber.i("  Enabled: $enabled")
        Timber.i("  Bitfield Value: 0x%02x".format(newValue))

        // Notify connected devices of the change
        connectedDevice?.let { device ->
            try {
                val success = gattServer?.notifyCharacteristicChanged(device, proximityLoginBitfieldCharacteristic, false)
                if (success == true) {
                    Timber.d("✅ Proximity login bitfield change notification sent")
                } else {
                    Timber.w("⚠️ Proximity login bitfield notification may have failed")
                }
            } catch (e: SecurityException) {
                Timber.e(e, "❌ SecurityException when notifying proximity login change")
            }
        }
    }

    fun isProximityLoginEnabled(): Boolean {
        return proximityLoginBitfieldCharacteristic.value?.get(0) == 0x01.toByte()
    }

    /**
     * Gets the current advertising name for this device.
     */
    fun getCurrentAdvertisingName(): String {
        return getAdvertisingName(context)
    }

    /**
     * Handle CTAPBLE_CRAYONIC_BRIDGE command - respond with array of 10 0xCC bytes
     */
    private fun handleCrayonicBridgeCommand(
        device: BluetoothDevice?,
        requestId: Int,
        value: ByteArray,
        responseNeeded: Boolean
    ) {
        Timber.i("🌉 CRAYONIC BRIDGE COMMAND RECEIVED:")
        Timber.i("  Device: ${device?.address} (${device?.name})")
        Timber.i("  Data Length: ${value.size} bytes")
        Timber.i("  Raw Data: ${value.joinToString(" ") { "0x%02x".format(it) }}")

        // Try to parse as BLE frame status
        val frameStatus = BleFrameStatus.fromByteArray(value)
        if (frameStatus != null) {
            Timber.i("📋 PARSED BLE FRAME STATUS:")
            Timber.i("  Command: ${getCtapCommandName(frameStatus.cmd)} (0x%02x)".format(frameStatus.cmd))
            Timber.i("  Length: ${frameStatus.length} (0x%02x%02x)".format(frameStatus.lenH, frameStatus.lenL))
            Timber.i("  Bridge Command: ${getBridgeCommandName(frameStatus.bridgeCmd)} (0x%02x)".format(frameStatus.bridgeCmd))
            Timber.i("  Version: ${frameStatus.version}")
            Timber.i("  Active: ${frameStatus.active}")
            if (frameStatus.data.isNotEmpty()) {
                Timber.i("  Data: ${frameStatus.data.joinToString(" ") { "0x%02x".format(it) }}")
                val dataString = frameStatus.data.map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")
                Timber.i("  Data ASCII: \"$dataString\"")
            }
        } else {
            Timber.w("⚠️ Could not parse as BLE frame status structure")
        }

        // Send back array of 10 0xCC bytes instead of echoing
        val responseData = ByteArray(10) { 0xCC.toByte() }
        Timber.i("🔄 SENDING CUSTOM RESPONSE DATA:")
        Timber.i("  Response Data: ${responseData.joinToString(" ") { "0x%02x".format(it) }}")
        Timber.i("  Response Size: ${responseData.size} bytes")
        Timber.i("  Request ID: $requestId")
        Timber.i("  Response Needed Flag: $responseNeeded (ignored for bridge command)")
        Timber.i("  GATT Server Available: ${gattServer != null}")

        try {
            // Store the response data for subsequent reads
            echoResponseData = responseData.copyOf()
            Timber.i("💾 RESPONSE DATA STORAGE:")
            Timber.i("  Stored data size: ${echoResponseData?.size} bytes")
            Timber.i("  Stored data: ${echoResponseData?.joinToString(" ") { "0x%02x".format(it) }}")
            Timber.i("  Storage successful: ${echoResponseData != null}")

            // First, acknowledge the write request (standard BLE practice)
            Timber.i("🚀 SENDING WRITE ACKNOWLEDGMENT:")
            Timber.i("  Device: ${device?.address}")
            Timber.i("  Request ID: $requestId")
            Timber.i("  Status: GATT_SUCCESS")

            val ackResult = gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            Timber.i("✅ Write acknowledgment sent - Result: $ackResult")

            // Set the control point characteristic value to the response data
            controlPointCharacteristic.value = responseData
            Timber.i("📝 Control point characteristic value set to response data")
            Timber.i("  Characteristic value size: ${controlPointCharacteristic.value?.size ?: 0} bytes")
            Timber.i("  Characteristic value: ${controlPointCharacteristic.value?.joinToString(" ") { "0x%02x".format(it) } ?: "null"}")

            // Send the response data via notification (primary method)
            Timber.i("📢 SENDING NOTIFICATION WITH RESPONSE DATA:")
            val notificationSent = gattServer?.notifyCharacteristicChanged(device, controlPointCharacteristic, false)

            if (notificationSent == true) {
                Timber.i("✅ Notification sent successfully")
                Timber.i("📤 Notification data: ${responseData.joinToString(" ") { "0x%02x".format(it) }}")
                Timber.i("📤 Notification sent to device: ${device?.address} (${device?.name})")
            } else {
                Timber.w("⚠️ Notification failed - client may not be subscribed")
                Timber.w("💡 Response data is available for reading from control point characteristic")
            }

            // Also notify status change to indicate processing is complete
            currentStatus = U2F_STATUS_IDLE
            notifyStatusChange()
            Timber.i("🔄 Status changed to IDLE after Crayonic Bridge response")

        } catch (e: SecurityException) {
            Timber.e(e, "❌ SecurityException when sending Crayonic Bridge response")
        } catch (e: Exception) {
            Timber.e(e, "❌ Exception when sending Crayonic Bridge response")
        }
    }

    /**
     * Handle CTAPBLE_SMARTCARD and CTAPBLE_SMARTCARD_AUX commands
     */
    private fun handleSmartcardCommand(
        device: BluetoothDevice?,
        requestId: Int,
        cmd: Byte,
        value: ByteArray,
        responseNeeded: Boolean
    ) {
        val cmdName = getCtapCommandName(cmd)
        Timber.i("💳 SMARTCARD COMMAND RECEIVED:")
        Timber.i("  Command: $cmdName (0x%02x)".format(cmd))
        Timber.i("  Device: ${device?.address} (${device?.name})")
        Timber.i("  Data Length: ${value.size} bytes")
        Timber.i("  Data: ${value.joinToString(" ") { "0x%02x".format(it) }}")

        // For now, just acknowledge the command
        if (responseNeeded) {
            try {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                Timber.i("✅ Smartcard command acknowledged")
            } catch (e: SecurityException) {
                Timber.e(e, "❌ SecurityException when sending Smartcard response")
            }
        } else {
            Timber.d("ℹ️ No response needed for Smartcard command")
        }
    }
}
