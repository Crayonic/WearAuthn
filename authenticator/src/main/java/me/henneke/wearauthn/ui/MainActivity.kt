package me.henneke.wearauthn.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.henneke.wearauthn.R
import me.henneke.wearauthn.ble.models.ScannedDevice
import me.henneke.wearauthn.databinding.ActivityMainBinding
import me.henneke.wearauthn.fido.context.AuthenticatorContext
import me.henneke.wearauthn.fido.context.AuthenticatorSpecialStatus
import me.henneke.wearauthn.fido.context.RequestInfo
import me.henneke.wearauthn.fido.context.WebAuthnCredential
import me.henneke.wearauthn.fido.context.generateWebAuthnCredential
import me.henneke.wearauthn.fido.ctap2.CborTextStringMap
import me.henneke.wearauthn.fido.ctap2.CborTextString
import me.henneke.wearauthn.fido.ctap2.CborByteString
import me.henneke.wearauthn.sha256
import me.henneke.wearauthn.base64
import me.henneke.wearauthn.i

import me.henneke.wearauthn.ui.UiConstants.EXTRA_MANAGE_SPACE_RECEIVER

import timber.log.Timber

/**
 * Main activity for the FIDO authenticator app.
 * Provides an overview of the authenticator status and access to management functions.
 */
@kotlin.ExperimentalUnsignedTypes
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_ENABLE_BLUETOOTH = 1002
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1003
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceListAdapter: DeviceListAdapter
    private lateinit var permissionHelper: PermissionDialogHelper
    private var bluetoothAdapter: BluetoothAdapter? = null



    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)

        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Initialize permission helper
        permissionHelper = PermissionDialogHelper(this)

        // Initialize device list adapter
        deviceListAdapter = DeviceListAdapter { device ->
            viewModel.onDeviceSelected(device)
        }

        setupUI()
        setupObservers()
        loadStatus()
    }

    private fun updateAdvertiseButtonState() {
        // Button text is now handled by ViewModel observers

        // Debug logging for button state
        val bleSupported = isBluetoothAvailable() && isBleAdvertisingSupported()
        val hasPermissions = hasBluetoothPermissions()

        Timber.d("Button state check:")
        Timber.d("  - Bluetooth available: ${isBluetoothAvailable()}")
        Timber.d("  - BLE advertising supported: ${isBleAdvertisingSupported()}")
        Timber.d("  - Has permissions: $hasPermissions")
        Timber.d("  - Overall enabled: ${bleSupported && hasPermissions}")

        // Enable/disable button based on Bluetooth and BLE advertising availability
        // Keep button enabled if only permissions are missing (so user can request them)
        val canRequestPermissions = isBluetoothAvailable() && isBleAdvertisingSupported()
        binding.advertiseButton.isEnabled = canRequestPermissions

        if (!canRequestPermissions) {
            binding.advertiseButton.alpha = 0.5f
            // Show reason why button is disabled
            if (!isBluetoothAvailable()) {
                Timber.w("Button disabled: Bluetooth not available")
            } else if (!isBleAdvertisingSupported()) {
                Timber.w("Button disabled: BLE advertising not supported")
            }
        } else if (!hasPermissions) {
            // Button enabled but permissions missing - visual indication
            binding.advertiseButton.alpha = 0.8f
            Timber.i("Button enabled but permissions missing - will request on click")
        } else {
            binding.advertiseButton.alpha = 1.0f
        }
    }

    private fun isBleAdvertisingSupported(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        val advertiserAvailable = bluetoothAdapter?.bluetoothLeAdvertiser != null
        val multipleAdvSupported = bluetoothAdapter?.isMultipleAdvertisementSupported == true
        val bleFeatureSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

        Timber.d("BLE advertising support check:")
        Timber.d("  - BluetoothLeAdvertiser available: $advertiserAvailable")
        Timber.d("  - Multiple advertisement supported: $multipleAdvSupported")
        Timber.d("  - BLE feature supported: $bleFeatureSupported")

        return advertiserAvailable && multipleAdvSupported && bleFeatureSupported
    }

    private fun hasBluetoothPermissions(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            val advertiseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val scanGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

            Timber.d("Android 12+ permission check:")
            Timber.d("  - BLUETOOTH_ADVERTISE: $advertiseGranted")
            Timber.d("  - BLUETOOTH_CONNECT: $connectGranted")
            Timber.d("  - BLUETOOTH_SCAN: $scanGranted")

            advertiseGranted && connectGranted && scanGranted
        } else {
            // Legacy permissions (automatically granted if declared in manifest for API < 23)
            val bluetoothGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            val bluetoothAdminGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED

            Timber.d("Legacy permission check:")
            Timber.d("  - BLUETOOTH: $bluetoothGranted")
            Timber.d("  - BLUETOOTH_ADMIN: $bluetoothAdminGranted")

            bluetoothGranted && bluetoothAdminGranted
        }

        Timber.d("Overall permission result: $result")
        return result
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            // Legacy permissions
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS)
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh status when returning to the activity
        loadStatus()
        updateAdvertiseButtonState()
        // Auto refresh Bluetooth status when activity resumes
        refreshBluetoothStatus()
    }
    
    private fun setupUI() {
        binding.manageCredentialsButton.setOnClickListener {
            openManageCredentials()
        }
        
        binding.testAuthenticationButton.setOnClickListener {
            testAuthentication()
        }

        // Long press to add test credentials (for development)
        binding.testAuthenticationButton.setOnLongClickListener {
            addTestCredentials()
            true
        }

        // Bluetooth controls
        binding.bluetoothToggleButton.setOnClickListener {
            toggleBluetooth()
        }

        binding.advertiseButton.setOnClickListener {
            if (viewModel.isAdvertising.value == true) {
                viewModel.stopAdvertising()
            } else {
                // Check permissions before starting advertising
                if (permissionHelper.hasAllBluetoothPermissions()) {
                    viewModel.startAdvertising()
                } else {
                    showPermissionDialogForAdvertising()
                }
            }
        }

        binding.scanButton.setOnClickListener {
            if (bluetoothAdapter?.isEnabled == true) {
                openScanResults()
            } else {
                Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show()
            }
        }



        // Update button state based on Bluetooth availability
        updateAdvertiseButtonState()
        updateScanButton()

        // Initial Bluetooth status check
        refreshBluetoothStatus()
    }



    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                openSettings()
                true
            }
            R.id.action_refresh -> {
                refreshBluetoothStatus()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Setup LiveData observers
     */
    private fun setupObservers() {
        // Observe advertising state
        viewModel.isAdvertising.observe(this) { isAdvertising ->
            binding.advertiseButton.text = if (isAdvertising) {
                getString(R.string.stop_advertise_button)
            } else {
                getString(R.string.advertise_button)
            }
        }

        // Observe scanning state
        viewModel.isScanning.observe(this) { isScanning ->
            binding.scanButton.text = if (isScanning) {
                getString(R.string.scan_button_stop)
            } else {
                getString(R.string.scan_button_start)
            }
        }

        // Scan status and device list are now handled in ScanResultsActivity

        // Observe advertising name
        viewModel.advertisingName.observe(this) { name ->
            binding.advertisingNameTextView.text = name
        }

        // Observe toast messages
        viewModel.toastMessage.observe(this) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        // Observe Bluetooth status
        viewModel.bluetoothEnabled.observe(this) { enabled ->
            updateAdvertiseButtonState()
            updateScanButton()
        }
    }
    
    private fun loadStatus() {
        lifecycleScope.launch {
            println("Loading status")
            try {
                val credentials = withContext(Dispatchers.IO) {
                    AuthenticatorContext.getAllResidentCredentials(this@MainActivity)
                }
                
                val allCredentials = credentials.values.flatten()
                val count = allCredentials.size
                
                // Status is now shown in the status cards
                Timber.d("Loaded $count credentials")

            } catch (e: Exception) {
                Timber.e(e, "Error loading status")
            }
        }
    }
    
    private fun openManageCredentials() {
        val intent = Intent(this, ManageSpaceActivity::class.java).apply {
            putExtra(EXTRA_MANAGE_SPACE_RECEIVER, object : android.os.ResultReceiver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                    // Handle result from ManageSpaceActivity
                    when (resultCode) {
                        RESULT_OK -> {
                            // Data was modified, refresh the status
                            loadStatus()
                            Toast.makeText(this@MainActivity, "Authenticator data updated", Toast.LENGTH_SHORT).show()
                        }
                        RESULT_CANCELED -> {
                            // User canceled, no action needed
                        }
                    }
                }
            })
        }
        startActivity(intent)
    }
    
    private fun testAuthentication() {
        val intent = Intent(this, ConfirmDeviceCredentialActivity::class.java).apply {
            putExtra("confirm_device_credential_receiver", object : android.os.ResultReceiver(android.os.Handler()) {
                override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                    // Handle result from ConfirmDeviceCredentialActivity
                    when (resultCode) {
                        RESULT_OK -> {
                            Toast.makeText(this@MainActivity, "Authentication successful!", Toast.LENGTH_SHORT).show()
                        }
                        RESULT_CANCELED -> {
                            Toast.makeText(this@MainActivity, "Authentication canceled", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        Timber.i("Opening Settings activity")
    }

    private fun openScanResults() {
        // Check permissions before opening scan results
        if (permissionHelper.hasAllBluetoothPermissions()) {
            val intent = Intent(this, ScanResultsActivity::class.java)
            startActivity(intent)
            Timber.i("Opening Scan Results activity")
        } else {
            showPermissionDialogForScanning()
        }
    }

    private fun addTestCredentials() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Creating test credentials...", Toast.LENGTH_SHORT).show()

                val credentialsCreated = withContext(Dispatchers.IO) {
                    // Create a simple test implementation of AuthenticatorContext
                    @OptIn(ExperimentalUnsignedTypes::class)
                    val authenticatorContext = object : AuthenticatorContext(this@MainActivity, false) {
                        override fun notifyUser(info: RequestInfo) {
                            // No-op for test credentials
                        }

                        override fun handleSpecialStatus(specialStatus: AuthenticatorSpecialStatus) {
                            // No-op for test credentials
                        }

                        override suspend fun confirmRequestWithUser(info: RequestInfo): Boolean {
                            return true // Always confirm for test credentials
                        }

                        override suspend fun confirmTransactionWithUser(rpId: String, prompt: String): String? {
                            return null // No transaction confirmation needed
                        }
                    }
                    var count = 0

                    // Create test credentials for different sites
                    val testSites = listOf(
                        Triple("example.com", "testuser1", "Test User 1"),
                        Triple("demo.webauthn.io", "demouser", "Demo User"),
                        Triple("github.com", "developer", "Developer Account"),
                        Triple("google.com", "testaccount", "Test Account")
                    )

                    for ((rpId, username, displayName) in testSites) {
                        try {
                            Timber.d("Creating test credential for $rpId")

                            // Generate a real cryptographic key for this credential
                            // Use the low-level function to ensure we create a resident key
                            val keyAlias = try {
                                generateWebAuthnCredential(
                                    createResidentKey = true,
                                    createHmacSecret = false,
                                    attestationChallenge = null
                                )
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to generate WebAuthn credential for $rpId, trying fallback")
                                // Android 9 fallback - try without some features
                                try {
                                    generateWebAuthnCredential(
                                        createResidentKey = true,
                                        createHmacSecret = false,
                                        attestationChallenge = null
                                    )
                                } catch (e2: Exception) {
                                    Timber.e(e2, "Fallback also failed for $rpId")
                                    null
                                }
                            }

                            if (keyAlias != null) {

                                // Initialize the counter for this credential
                                authenticatorContext.initCounter(keyAlias)

                                // Create the WebAuthn credential object
                                val userId = username.toByteArray()
                                @OptIn(ExperimentalUnsignedTypes::class)
                                val credential = WebAuthnCredential(
                                    keyAlias = keyAlias,
                                    rpIdHash = rpId.toByteArray().sha256(),
                                    rpName = rpId,
                                    userId = userId,
                                    userDisplayName = displayName,
                                    userName = username
                                )

                                // Debug logging
                                Timber.d("Created credential for $rpId:")
                                Timber.d("  keyAlias: $keyAlias")
                                Timber.d("  isResident: ${credential.isResident}")
                                Timber.d("  userId: ${userId.contentToString()}")
                                Timber.d("  userName: $username")
                                Timber.d("  rpName: $rpId")

                                // Check if credential is actually resident before storing
                                if (!credential.isResident) {
                                    Timber.w("Credential for $rpId is not resident! Cannot store.")
                                    continue
                                }

                                // Store as resident credential manually (bypass user auth for test credentials)
                                try {
                                    Timber.d("Attempting to store resident credential for $rpId")

                                    // Manually store the credential without user authentication
                                    val rpIdHash = rpId.toByteArray().sha256()
                                    val encodedUserId = userId.base64()
                                    val encodedKeyHandle = credential.keyHandle.base64()

                                    // For test credentials, create a simplified serialization without encryption
                                    // This bypasses the user info encryption key that's causing issues on Android 9
                                    val serializedCredential = try {
                                        credential.serialize(false) // Don't include encrypted user info
                                    } catch (e: Exception) {
                                        Timber.w(e, "Failed to serialize with user verification, trying without")
                                        // Create a minimal serialization manually
                                        val basicMap = mutableMapOf<String, me.henneke.wearauthn.fido.ctap2.CborValue>(
                                            "keyAlias" to me.henneke.wearauthn.fido.ctap2.CborTextString(keyAlias),
                                            "userId" to me.henneke.wearauthn.fido.ctap2.CborByteString(userId)
                                        )
                                        rpId.let { basicMap["rpName"] = me.henneke.wearauthn.fido.ctap2.CborTextString(it) }
                                        me.henneke.wearauthn.fido.ctap2.CborTextStringMap(basicMap).toCbor().base64()
                                    }

                                    // Store in SharedPreferences directly (same as setResidentCredential but without auth)
                                    val rpIdHashString = rpIdHash.base64()

                                    // Add to RP ID hashes file
                                    this@MainActivity.getSharedPreferences("rp_id_hashes", Context.MODE_PRIVATE).edit().apply {
                                        putBoolean(rpIdHashString, true)
                                        apply()
                                    }

                                    // Store credential data
                                    val prefs = this@MainActivity.getSharedPreferences("rp_id_hash_$rpIdHashString", Context.MODE_PRIVATE)
                                    prefs.edit().apply {
                                        putString("rpId", rpId)
                                        putString("uid+$encodedUserId", serializedCredential)
                                        putString("kh+$encodedKeyHandle", encodedUserId)
                                        apply()
                                    }

                                    Timber.d("Successfully stored resident credential for $rpId")
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to store resident credential for $rpId")
                                    throw e
                                }

                                count++
                                Timber.d("Successfully created test credential for $rpId")
                            } else {
                                Timber.w("Failed to generate key for $rpId")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to create credential for $rpId")
                        }
                    }
                    count
                }

                if (credentialsCreated > 0) {
                    Toast.makeText(this@MainActivity, "Created $credentialsCreated test credentials!", Toast.LENGTH_LONG).show()
                    loadStatus() // Refresh the UI
                } else {
                    Toast.makeText(this@MainActivity, "Failed to create test credentials", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to add test credentials")
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }



    private fun isBluetoothAvailable(): Boolean {
        return viewModel.bluetoothEnabled.value == true
    }

    private fun isBluetoothEnabled(): Boolean {
        return viewModel.bluetoothEnabled.value == true
    }

    private fun promptEnableBluetooth() {
        try {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
            Toast.makeText(this, getString(R.string.bluetooth_enable_prompt), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Timber.e(e, "Failed to prompt for Bluetooth enable")
            Toast.makeText(this, getString(R.string.bluetooth_enable_failed), Toast.LENGTH_LONG).show()
        }
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Handle permission helper results
        permissionHelper.handlePermissionResult(
            requestCode = requestCode,
            permissions = permissions,
            grantResults = grantResults,
            onAllGranted = {
                Timber.i("✅ All permissions granted via dialog")
                Toast.makeText(this, "Permissions granted! You can now use all features.", Toast.LENGTH_SHORT).show()
                updateAdvertiseButtonState()
                updateScanButton()
                viewModel.updateBluetoothStatus()
            },
            onSomeGranted = { granted, denied ->
                Timber.w("⚠️ Some permissions granted: $granted, denied: $denied")
                Toast.makeText(this, "Some permissions were denied. Full functionality may not be available.", Toast.LENGTH_LONG).show()
                updateAdvertiseButtonState()
                updateScanButton()
                viewModel.updateBluetoothStatus()
            },
            onAllDenied = {
                Timber.e("❌ All permissions denied via dialog")
                permissionHelper.showPermissionDeniedDialog(
                    onSettingsClicked = {
                        permissionHelper.openAppSettings()
                    },
                    onDismissClicked = {
                        Toast.makeText(this, "Bluetooth features are disabled without permissions.", Toast.LENGTH_LONG).show()
                    }
                )
            }
        )

        // Handle legacy permission requests
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, getString(R.string.bluetooth_permissions_granted), Toast.LENGTH_SHORT).show()
                    Timber.i("Bluetooth permissions granted by user")
                    updateAdvertiseButtonState() // Update button state
                    // Try to start advertising again
                    viewModel.startAdvertising()
                } else {
                    Toast.makeText(this, getString(R.string.bluetooth_permissions_required), Toast.LENGTH_LONG).show()
                    Timber.w("User denied Bluetooth permissions")
                    updateAdvertiseButtonState() // Update button state
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_ENABLE_BLUETOOTH -> {
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, getString(R.string.bluetooth_enabled), Toast.LENGTH_SHORT).show()
                    Timber.i("Bluetooth enabled by user")
                    // Try to start advertising again
                    viewModel.startAdvertising()
                } else {
                    Toast.makeText(this, getString(R.string.bluetooth_enable_cancelled), Toast.LENGTH_LONG).show()
                    Timber.w("User cancelled Bluetooth enable request")
                }
            }
        }
    }

    /**
     * Update scan button text and state
     */
    private fun updateScanButton() {
        binding.scanButton.isEnabled = viewModel.bluetoothEnabled.value == true && hasBluetoothPermissions()
    }

    /**
     * Show permission dialog for advertising functionality
     */
    private fun showPermissionDialogForAdvertising() {
        Timber.i("🔐 Showing permission dialog for advertising")
        permissionHelper.showAppropriatePermissionDialog(
            onPermissionsGranted = {
                Timber.i("✅ Permissions granted, starting advertising")
                viewModel.startAdvertising()
            },
            onPermissionsDenied = {
                Timber.w("❌ Permissions denied for advertising")
                Toast.makeText(this, getString(R.string.toast_scan_permission_denied), Toast.LENGTH_SHORT).show()
            }
        )
    }

    /**
     * Show permission dialog for scanning functionality
     */
    private fun showPermissionDialogForScanning() {
        Timber.i("📡 Showing permission dialog for scanning")
        permissionHelper.showAppropriatePermissionDialog(
            onPermissionsGranted = {
                Timber.i("✅ Permissions granted, starting scan")
                viewModel.startScan()
            },
            onPermissionsDenied = {
                Timber.w("❌ Permissions denied for scanning")
                Toast.makeText(this, getString(R.string.toast_scan_permission_denied), Toast.LENGTH_SHORT).show()
            }
        )
    }

    /**
     * Refresh Bluetooth status and update UI
     */
    private fun refreshBluetoothStatus() {
        Timber.i("🔄 Refreshing Bluetooth status")

        val isBluetoothEnabled = bluetoothAdapter?.isEnabled == true
        val bluetoothStatus = when {
            bluetoothAdapter == null -> "Bluetooth: Not supported"
            isBluetoothEnabled -> "Bluetooth: ON"
            else -> "Bluetooth: OFF"
        }

        binding.bluetoothStatusTextView.text = bluetoothStatus

        // Update toggle button
        binding.bluetoothToggleButton.apply {
            when {
                bluetoothAdapter == null -> {
                    text = "N/A"
                    isEnabled = false
                }
                isBluetoothEnabled -> {
                    text = "Disable"
                    isEnabled = true
                }
                else -> {
                    text = "Enable"
                    isEnabled = true
                }
            }
        }

        // Update other buttons based on Bluetooth state
        updateAdvertiseButtonState()
        updateScanButton()

        Timber.d("Bluetooth status updated: $bluetoothStatus")
    }

    /**
     * Toggle Bluetooth on/off
     */
    private fun toggleBluetooth() {
        val bluetoothAdapter = this.bluetoothAdapter ?: return

        if (bluetoothAdapter.isEnabled) {
            // Disable Bluetooth
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.disable()
                Toast.makeText(this, "Disabling Bluetooth...", Toast.LENGTH_SHORT).show()
                Timber.i("📴 Disabling Bluetooth")
            } else {
                showBluetoothPermissionDialog()
            }
        } else {
            // Enable Bluetooth
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
                Timber.i("📶 Requesting Bluetooth enable")
            } else {
                showBluetoothPermissionDialog()
            }
        }

        // Refresh status after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            refreshBluetoothStatus()
        }, 1000)
    }

    /**
     * Show Bluetooth permission dialog
     */
    private fun showBluetoothPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Bluetooth Permission Required")
            .setMessage("This app needs Bluetooth permissions to enable/disable Bluetooth and communicate with security keys.")
            .setPositiveButton("Grant Permission") { _, _ ->
                permissionHelper.requestBluetoothPermissions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
