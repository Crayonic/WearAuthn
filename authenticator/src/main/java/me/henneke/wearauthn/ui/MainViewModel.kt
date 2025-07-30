package me.henneke.wearauthn.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import me.henneke.wearauthn.R
import me.henneke.wearauthn.ble.FidoU2fBleService
import me.henneke.wearauthn.ble.models.ScannedDevice
import me.henneke.wearauthn.fido.context.AuthenticatorContext
import timber.log.Timber

/**
 * ViewModel for MainActivity handling BLE operations and UI state
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    
    // BLE components
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private lateinit var fidoU2fBleService: FidoU2fBleService
    
    // Scanning state
    private val scanHandler = Handler(Looper.getMainLooper())
    private val scanTimeout = 30000L // 30 seconds
    
    // LiveData for UI state
    private val _isAdvertising = MutableLiveData<Boolean>(false)
    val isAdvertising: LiveData<Boolean> = _isAdvertising
    
    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning
    
    private val _scanStatus = MutableLiveData<String>()
    val scanStatus: LiveData<String> = _scanStatus
    
    private val _scannedDevices = MutableLiveData<List<ScannedDevice>>(emptyList())
    val scannedDevices: LiveData<List<ScannedDevice>> = _scannedDevices
    
    private val _advertisingName = MutableLiveData<String>()
    val advertisingName: LiveData<String> = _advertisingName
    
    private val _authenticatorStatus = MutableLiveData<String>()
    val authenticatorStatus: LiveData<String> = _authenticatorStatus
    
    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage
    
    private val _bluetoothEnabled = MutableLiveData<Boolean>(false)
    val bluetoothEnabled: LiveData<Boolean> = _bluetoothEnabled
    
    // Internal device list for manipulation
    private val deviceList = mutableListOf<ScannedDevice>()
    
    init {
        initializeBluetooth()
        initializeBleService()
        updateScanStatus()
        updateAdvertisingName()
    }
    
    /**
     * Initialize Bluetooth components
     */
    private fun initializeBluetooth() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        _bluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Initialize BLE service
     */
    private fun initializeBleService() {
        fidoU2fBleService = FidoU2fBleService(context)
        val initialized = fidoU2fBleService.initialize()
        if (!initialized) {
            Timber.w("BLE service initialization failed")
        }
    }
    
    /**
     * Update scan status text
     */
    private fun updateScanStatus() {
        val status = when {
            _isScanning.value == true -> context.getString(R.string.scan_status_scanning)
            deviceList.isEmpty() -> context.getString(R.string.scan_status_idle)
            else -> context.getString(R.string.scan_status_found, deviceList.size)
        }
        _scanStatus.value = status
    }
    
    /**
     * Update advertising name display
     */
    private fun updateAdvertisingName() {
        val name = FidoU2fBleService.getAdvertisingName(context)
        _advertisingName.value = context.getString(R.string.device_name_loading).replace("Loading…", name)
    }
    
    /**
     * BLE scan callback
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            val device = result.device
            val deviceName = if (hasBluetoothConnectPermission()) {
                try {
                    device.name
                } catch (e: SecurityException) {
                    null
                }
            } else {
                null
            }
            
            val scannedDevice = ScannedDevice(
                device = device,
                deviceName = deviceName,
                deviceAddress = device.address,
                rssi = result.rssi,
                advertisementData = result.scanRecord?.bytes
            )
            
            // Update device list
            val existingIndex = deviceList.indexOfFirst { it.deviceAddress == scannedDevice.deviceAddress }
            if (existingIndex >= 0) {
                deviceList[existingIndex] = scannedDevice
            } else {
                deviceList.add(scannedDevice)
            }
            
            // Update LiveData
            _scannedDevices.value = deviceList.toList()
            updateScanStatus()
            
            Timber.d("📡 BLE device found: ${scannedDevice.displayName} (${scannedDevice.deviceAddress}) RSSI: ${scannedDevice.rssi}")
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("❌ BLE scan failed with error code: $errorCode")
            
            _isScanning.value = false
            _scanStatus.value = context.getString(R.string.scan_status_failed, errorCode)
            _toastMessage.value = context.getString(R.string.toast_scan_failed, errorCode)
        }
    }
    
    /**
     * Check if Bluetooth connect permission is granted
     */
    private fun hasBluetoothConnectPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if Bluetooth scan permission is granted
     */
    private fun hasBluetoothScanPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // On older Android versions, BLUETOOTH_SCAN is not required
            true
        }
    }
    
    /**
     * Start BLE scanning
     */
    fun startScan(): Boolean {
        if (_isScanning.value == true) return false
        
        // Check if Bluetooth is enabled
        if (bluetoothAdapter?.isEnabled != true) {
            _toastMessage.value = context.getString(R.string.bluetooth_enable_prompt)
            return false
        }

        // Check if BLUETOOTH_SCAN permission is granted
        if (!hasBluetoothScanPermission()) {
            Timber.e("❌ BLUETOOTH_SCAN permission not granted")
            _toastMessage.value = context.getString(R.string.toast_scan_permission_denied)
            return false
        }

        // Stop advertising if running (mutual exclusion)
        if (_isAdvertising.value == true) {
            stopAdvertising()
            _toastMessage.value = context.getString(R.string.toast_advertising_stopped_for_scan)
        }
        
        // Clear previous results
        deviceList.clear()
        _scannedDevices.value = emptyList()
        
        // Configure scan settings
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        
        return try {
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            _isScanning.value = true
            updateScanStatus()
            
            // Set timeout to stop scanning
            scanHandler.postDelayed({
                if (_isScanning.value == true) {
                    stopScan()
                    _toastMessage.value = context.getString(R.string.toast_scan_completed_timeout)
                }
            }, scanTimeout)
            
            Timber.i("🔍 BLE scan started")
            _toastMessage.value = context.getString(R.string.toast_scan_started)
            true
            
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException when starting BLE scan")
            _toastMessage.value = context.getString(R.string.toast_scan_permission_denied)
            false
        } catch (e: Exception) {
            Timber.e(e, "Failed to start BLE scan")
            _toastMessage.value = context.getString(R.string.toast_scan_failed_generic, e.message ?: "Unknown error")
            false
        }
    }
    
    /**
     * Stop BLE scanning
     */
    fun stopScan() {
        if (_isScanning.value != true) return
        
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            _isScanning.value = false
            updateScanStatus()
            
            // Remove timeout callback
            scanHandler.removeCallbacksAndMessages(null)
            
            Timber.i("🛑 BLE scan stopped")
            _toastMessage.value = context.getString(R.string.toast_scan_stopped)
            
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException when stopping BLE scan")
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop BLE scan")
        }
    }
    
    /**
     * Handle device selection
     */
    fun onDeviceSelected(device: ScannedDevice) {
        Timber.i("📱 Device selected: ${device.displayName} (${device.deviceAddress})")
        _toastMessage.value = context.getString(R.string.toast_device_selected, device.displayName)

        // Stop scanning when device is selected
        if (_isScanning.value == true) {
            stopScan()
        }
    }

    /**
     * Clear scan results
     */
    fun clearScanResults() {
        deviceList.clear()
        _scannedDevices.value = emptyList()
        updateScanStatus()
        Timber.i("🧹 Scan results cleared")
    }
    
    /**
     * Start BLE advertising
     */
    fun startAdvertising(): Boolean {
        // Stop scanning if running (mutual exclusion)
        if (_isScanning.value == true) {
            stopScan()
        }
        
        val success = fidoU2fBleService.startAdvertising()
        _isAdvertising.value = success
        
        if (success) {
            _toastMessage.value = context.getString(R.string.advertising_started)
        } else {
            _toastMessage.value = context.getString(R.string.advertising_failed)
        }
        
        return success
    }
    
    /**
     * Stop BLE advertising
     */
    fun stopAdvertising() {
        fidoU2fBleService.stopAdvertising()
        _isAdvertising.value = false
        _toastMessage.value = context.getString(R.string.advertising_stopped)
    }
    
    /**
     * Update Bluetooth enabled status
     */
    fun updateBluetoothStatus() {
        _bluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Load authenticator status
     */
    fun loadAuthenticatorStatus() {
        // Set a default status - this can be enhanced later
        _authenticatorStatus.value = context.getString(R.string.status_ready)
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Stop scanning if active
        if (_isScanning.value == true) {
            stopScan()
        }
        
        // Cleanup BLE service
        fidoU2fBleService.cleanup()
    }
}
