package me.henneke.wearauthn.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import me.henneke.wearauthn.R
import me.henneke.wearauthn.databinding.ActivityScanResultsBinding
import me.henneke.wearauthn.ble.models.ScannedDevice
import timber.log.Timber

/**
 * Activity to display BLE scan results in a dedicated screen
 */
class ScanResultsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityScanResultsBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceListAdapter: DeviceListAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityScanResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Scan Results"
        }
        
        // Initialize ViewModel (shared with MainActivity)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        Timber.i("ScanResultsActivity created")
    }
    
    private fun setupRecyclerView() {
        deviceListAdapter = DeviceListAdapter { device ->
            onDeviceSelected(device)
        }
        
        binding.deviceListRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ScanResultsActivity)
            adapter = deviceListAdapter
        }
    }
    
    private fun setupObservers() {
        // Observe scan results
        viewModel.scannedDevices.observe(this) { devices ->
            deviceListAdapter.submitList(devices)
            updateEmptyState(devices.isEmpty())
        }

        // Observe scanning state
        viewModel.isScanning.observe(this) { isScanning ->
            updateScanningState(isScanning)
        }

        // No scan status text needed
    }
    
    private fun setupClickListeners() {
        binding.scanButton.setOnClickListener {
            if (viewModel.isScanning.value == true) {
                viewModel.stopScan()
            } else {
                viewModel.startScan()
            }
        }
        
        binding.clearResultsButton.setOnClickListener {
            viewModel.clearScanResults()
        }
    }
    
    private fun updateScanningState(isScanning: Boolean) {
        binding.scanButton.text = if (isScanning) {
            getString(R.string.stop_scan_button)
        } else {
            getString(R.string.start_scan_button)
        }
        
        binding.scanProgressBar.visibility = if (isScanning) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = android.view.View.VISIBLE
            binding.deviceListRecyclerView.visibility = android.view.View.GONE
        } else {
            binding.emptyStateLayout.visibility = android.view.View.GONE
            binding.deviceListRecyclerView.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun onDeviceSelected(device: ScannedDevice) {
        Timber.i("Device selected: ${device.displayName} (${device.deviceAddress})")
        viewModel.onDeviceSelected(device)

        // Show device details or connect
        showDeviceDetailsDialog(device)
    }
    
    private fun showDeviceDetailsDialog(device: ScannedDevice) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Device Details")
            .setMessage(buildDeviceDetailsMessage(device))
            .setPositiveButton("Connect") { _, _ ->
                // TODO: Implement device connection
                Timber.i("Connecting to device: ${device.deviceAddress}")
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun buildDeviceDetailsMessage(device: ScannedDevice): String {
        return buildString {
            appendLine("Name: ${device.deviceName ?: "Unknown"}")
            appendLine("Address: ${device.deviceAddress}")
            appendLine("RSSI: ${device.rssi} dBm")
            appendLine("Signal: ${device.getSignalStrength(this@ScanResultsActivity)}")
            appendLine("Scan time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(device.scanTimestamp))}")

            if (device.advertisementData != null) {
                appendLine("\nAdvertisement Data:")
                val hexData = device.advertisementData.joinToString(" ") { "0x%02x".format(it) }
                appendLine("• Size: ${device.advertisementData.size} bytes")
                appendLine("• Data: $hexData")
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop scanning when leaving the activity
        if (viewModel.isScanning.value == true) {
            viewModel.stopScan()
        }
    }
}
