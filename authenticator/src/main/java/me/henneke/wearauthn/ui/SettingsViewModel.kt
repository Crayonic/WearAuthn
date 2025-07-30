package me.henneke.wearauthn.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import timber.log.Timber

/**
 * ViewModel for WearAuthn Settings
 * 
 * Manages settings state, encoding/decoding, and business logic
 */
class SettingsViewModel : ViewModel() {
    
    // Settings state
    private val _settingsState = MutableLiveData<MutableMap<String, Any>>(mutableMapOf())
    val settingsState: LiveData<MutableMap<String, Any>> = _settingsState
    
    private val _selectedSettings = MutableLiveData<MutableMap<String, Boolean>>(mutableMapOf())
    val selectedSettings: LiveData<MutableMap<String, Boolean>> = _selectedSettings
    
    // Status and messages
    private val _statusMessage = MutableLiveData<String>("Ready")
    val statusMessage: LiveData<String> = _statusMessage
    
    private val _policyData = MutableLiveData<String>()
    val policyData: LiveData<String> = _policyData
    
    // Storage operations
    private val _storageKey = MutableLiveData<String>("bitwarden masterPassword user@example.com")
    val storageKey: LiveData<String> = _storageKey
    
    private val _storageValue = MutableLiveData<String>("placeholder value")
    val storageValue: LiveData<String> = _storageValue
    
    init {
        initializeDefaultSettings()
        generatePolicyData()
    }
    
    /**
     * Initialize settings with default values matching JavaScript implementation
     */
    private fun initializeDefaultSettings() {
        val defaultSettings = mutableMapOf<String, Any>()
        val defaultSelected = mutableMapOf<String, Boolean>()
        
        // Set default values for all settings
        defaultSettings["time"] = System.currentTimeMillis() / 1000 // Current time in seconds
        defaultSettings["uv_timeout_index"] = 1 // 10 seconds (index 1)
        defaultSettings["msc_timeout_index"] = 0 // No limit (index 0)
        defaultSettings["unlock_timeout_index"] = 5 // 1 minute (index 5)
        defaultSettings["u2f_os"] = 0 // U2F_OS_OTHER
        defaultSettings["u2f_up"] = listOf(1) // TOUCH
        defaultSettings["fido_up"] = listOf(1) // TOUCH
        defaultSettings["fido_uv"] = listOf(2, 3) // FINGERPRINT + PASSCODE
        defaultSettings["ble_enabled"] = true
        defaultSettings["ble_use_rpas"] = true
        defaultSettings["export_fingerprints_always"] = true
        defaultSettings["export_handwriting_always"] = true
        defaultSettings["nfc_to_st_safe_bridge"] = false
        defaultSettings["client_pin"] = false
        defaultSettings["logging_enabled"] = true
        defaultSettings["fido_over_usb"] = true
        defaultSettings["ccid_over_usb"] = true
        
        // All settings selected by default
        val settingNames = listOf(
            "time", "uv_timeout_index", "msc_timeout_index", "unlock_timeout_index",
            "u2f_os", "u2f_up", "fido_up", "fido_uv", "ble_enabled", "ble_use_rpas",
            "export_fingerprints_always", "export_handwriting_always", "nfc_to_st_safe_bridge",
            "client_pin", "logging_enabled", "fido_over_usb", "ccid_over_usb"
        )
        
        settingNames.forEach { defaultSelected[it] = true }
        
        _settingsState.value = defaultSettings
        _selectedSettings.value = defaultSelected
        
        Timber.i("Settings initialized with defaults")
    }
    
    /**
     * Update a setting value
     */
    fun updateSetting(name: String, value: Any) {
        val currentSettings = _settingsState.value ?: mutableMapOf()
        currentSettings[name] = value
        _settingsState.value = currentSettings
        generatePolicyData()
        Timber.d("Setting updated: $name = $value")
    }
    
    /**
     * Update setting selection
     */
    fun updateSettingSelection(name: String, selected: Boolean) {
        val currentSelected = _selectedSettings.value ?: mutableMapOf()
        currentSelected[name] = selected
        _selectedSettings.value = currentSelected
        generatePolicyData()
        Timber.d("Setting selection updated: $name = $selected")
    }
    
    /**
     * Select all settings
     */
    fun selectAllSettings() {
        val currentSelected = _selectedSettings.value ?: mutableMapOf()
        currentSelected.keys.forEach { currentSelected[it] = true }
        _selectedSettings.value = currentSelected
        generatePolicyData()
        updateStatus("All settings selected")
    }
    
    /**
     * Deselect all settings
     */
    fun selectNoneSettings() {
        val currentSelected = _selectedSettings.value ?: mutableMapOf()
        currentSelected.keys.forEach { currentSelected[it] = false }
        _selectedSettings.value = currentSelected
        generatePolicyData()
        updateStatus("All settings deselected")
    }
    
    /**
     * Invert setting selection
     */
    fun invertSettingSelection() {
        val currentSelected = _selectedSettings.value ?: mutableMapOf()
        currentSelected.keys.forEach { 
            currentSelected[it] = !(currentSelected[it] ?: false)
        }
        _selectedSettings.value = currentSelected
        generatePolicyData()
        updateStatus("Selection inverted")
    }
    
    /**
     * Storage operations
     */
    fun updateStorageKey(key: String) {
        _storageKey.value = key
    }
    
    fun updateStorageValue(value: String) {
        _storageValue.value = value
    }
    
    fun getStorageValue() {
        val key = _storageKey.value ?: ""
        updateStatus("Getting storage for key: $key")
        Timber.i("Getting storage for key: $key")
        // TODO: Implement actual storage retrieval
    }
    
    fun setStorageValue() {
        val key = _storageKey.value ?: ""
        val value = _storageValue.value ?: ""
        updateStatus("Setting storage: $key = $value")
        Timber.i("Setting storage: $key = $value")
        // TODO: Implement actual storage setting
    }
    
    /**
     * Settings operations
     */
    fun loadSettings() {
        updateStatus("Settings loaded")
        Timber.i("Loading settings")
        // TODO: Implement settings loading from device
    }
    
    fun storeSettings() {
        updateStatus("Settings stored")
        Timber.i("Storing settings")
        // TODO: Implement settings storing to device
    }
    
    fun loadSettingsForever() {
        updateStatus("Load forever started")
        Timber.i("Load forever started")
        // TODO: Implement continuous loading
    }
    
    fun stopLoadForever() {
        updateStatus("Load forever stopped")
        Timber.i("Load forever stopped")
        // TODO: Implement stopping continuous loading
    }
    
    /**
     * Policy data operations
     */
    fun copyPolicyData(): String {
        val policy = _policyData.value ?: ""
        updateStatus("Policy data copied to clipboard")
        return policy
    }
    
    fun decodeAndLoadPolicyData() {
        val policy = _policyData.value ?: ""
        updateStatus("Byte representation decoded and loaded")
        Timber.i("Decoding and loading byte representation: $policy")
        // TODO: Implement policy decoding
    }
    
    /**
     * Generate policy data from current settings (simplified version)
     */
    private fun generatePolicyData() {
        val settings = _settingsState.value ?: return
        val selected = _selectedSettings.value ?: return
        
        // Simplified policy generation - in real implementation this would use the encoding logic
        val policyBytes = mutableListOf<String>()
        policyBytes.add("01") // Version 1
        
        // Add some sample bytes based on selected settings
        var byteCount = 0
        selected.forEach { (name, isSelected) ->
            if (isSelected && byteCount < 20) { // Limit for display
                policyBytes.add("%02x".format(byteCount))
                byteCount++
            }
        }
        
        // Pad to match the example
        while (policyBytes.size < 25) {
            policyBytes.add("00")
        }
        
        _policyData.value = policyBytes.joinToString(" ")
    }
    
    /**
     * Update status message
     */
    private fun updateStatus(message: String) {
        _statusMessage.value = message
        Timber.d("Status: $message")
    }
    
    /**
     * Get timeout label by index
     */
    fun getTimeoutLabel(index: Int): String {
        val timeoutLabels = arrayOf(
            "No limit",
            "10 seconds", "20 seconds", "30 seconds", "45 seconds",
            "1 minute", "2 minutes", "5 minutes", "10 minutes", "15 minutes", "20 minutes", "30 minutes", "45 minutes",
            "1 hour", "2 hours", "3 hours", "4 hours", "5 hours", "8 hours", "12 hours", "24 hours"
        )
        return if (index in timeoutLabels.indices) timeoutLabels[index] else "Unknown"
    }
    
    /**
     * Get U2F OS label by index
     */
    fun getU2fOsLabel(index: Int): String {
        val osLabels = arrayOf("U2F_OS_OTHER", "U2F_OS_LINUX_MACOS")
        return if (index in osLabels.indices) osLabels[index] else "Unknown"
    }
    
    /**
     * Get UP/UV option labels
     */
    fun getUpUvLabels(): Array<Pair<String, String>> {
        return arrayOf(
            Pair("🔄", "AUTO"),
            Pair("👆", "TOUCH"), 
            Pair("👆", "FINGERPRINT"),
            Pair("🔢", "PASSCODE"),
            Pair("✍️", "HANDWRITING"),
            Pair("🎤", "VOICE")
        )
    }
}
