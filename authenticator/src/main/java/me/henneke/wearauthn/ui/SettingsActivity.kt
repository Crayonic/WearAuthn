package me.henneke.wearauthn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import me.henneke.wearauthn.R
import timber.log.Timber

/**
 * Settings activity for WearAuthn configuration
 * 
 * This activity provides a UI similar to the web interface for configuring
 * WearAuthn settings including timeouts, authentication methods, and features.
 */
class SettingsActivity : AppCompatActivity() {

    // ViewModel
    private lateinit var viewModel: SettingsViewModel

    // UI Components
    private lateinit var etStorageKey: EditText
    private lateinit var etStorageValue: EditText
    private lateinit var btnGet: Button
    private lateinit var btnSet: Button
    private lateinit var btnSelectAll: Button
    private lateinit var btnSelectNone: Button
    private lateinit var btnInvertSelection: Button
    private lateinit var btnLoad: Button
    private lateinit var btnStore: Button
    private lateinit var btnLoadForever: Button
    private lateinit var btnStopLoadForever: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvPolicyData: TextView
    private lateinit var btnCopyForManualEdit: Button
    private lateinit var btnDecodeAndLoad: Button
    private lateinit var llSettingsContainer: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        initializeViews()
        setupClickListeners()
        observeViewModel()
        createSettingsUI()

        Timber.i("SettingsActivity created")
    }
    
    private fun initializeViews() {
        etStorageKey = findViewById(R.id.etStorageKey)
        etStorageValue = findViewById(R.id.etStorageValue)
        btnGet = findViewById(R.id.btnGet)
        btnSet = findViewById(R.id.btnSet)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnSelectNone = findViewById(R.id.btnSelectNone)
        btnInvertSelection = findViewById(R.id.btnInvertSelection)
        btnLoad = findViewById(R.id.btnLoad)
        btnStore = findViewById(R.id.btnStore)
        btnLoadForever = findViewById(R.id.btnLoadForever)
        btnStopLoadForever = findViewById(R.id.btnStopLoadForever)
        tvStatus = findViewById(R.id.tvStatus)
        tvPolicyData = findViewById(R.id.tvPolicyData)
        btnCopyForManualEdit = findViewById(R.id.btnCopyForManualEdit)
        btnDecodeAndLoad = findViewById(R.id.btnDecodeAndLoad)
        llSettingsContainer = findViewById(R.id.llSettingsContainer)
    }
    
    private fun setupClickListeners() {
        // Back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        btnGet.setOnClickListener { viewModel.getStorageValue() }
        btnSet.setOnClickListener { viewModel.setStorageValue() }
        btnSelectAll.setOnClickListener { viewModel.selectAllSettings() }
        btnSelectNone.setOnClickListener { viewModel.selectNoneSettings() }
        btnInvertSelection.setOnClickListener { viewModel.invertSettingSelection() }
        btnLoad.setOnClickListener { viewModel.loadSettings() }
        btnStore.setOnClickListener { viewModel.storeSettings() }
        btnLoadForever.setOnClickListener { viewModel.loadSettingsForever() }
        btnStopLoadForever.setOnClickListener { viewModel.stopLoadForever() }
        btnCopyForManualEdit.setOnClickListener { handleCopyForManualEdit() }
        btnDecodeAndLoad.setOnClickListener { viewModel.decodeAndLoadPolicyData() }

        // Storage field listeners
        etStorageKey.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.updateStorageKey(etStorageKey.text.toString())
        }
        etStorageValue.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.updateStorageValue(etStorageValue.text.toString())
        }
    }

    private fun observeViewModel() {
        viewModel.statusMessage.observe(this) { status ->
            tvStatus.text = "Status: $status"
        }

        viewModel.policyData.observe(this) { policy ->
            tvPolicyData.text = policy
        }

        viewModel.storageKey.observe(this) { key ->
            if (etStorageKey.text.toString() != key) {
                etStorageKey.setText(key)
            }
        }

        viewModel.storageValue.observe(this) { value ->
            if (etStorageValue.text.toString() != value) {
                etStorageValue.setText(value)
            }
        }

        viewModel.selectedSettings.observe(this) { selected ->
            // Recreate UI when selection changes
            recreateSettingsUI()
        }
    }
    
    private fun createSettingsUI() {
        // Create sample settings similar to the web interface
        val settings = listOf(
            SettingItem("time", "Time", "Current device time", "readonly"),
            SettingItem("uv_timeout_index", "User verification timeout", "Timeout for user verification prompts", "dropdown"),
            SettingItem("msc_timeout_index", "Mass storage timeout", "Timeout for mass storage operations", "dropdown"),
            SettingItem("unlock_timeout_index", "Unlock timeout", "Device unlock timeout", "dropdown"),
            SettingItem("u2f_os", "U2F OS", "Target operating system for U2F", "dropdown"),
            SettingItem("u2f_up", "U2F User presence", "User presence verification methods for U2F", "multiselect"),
            SettingItem("fido_up", "FIDO User presence", "User presence verification methods for FIDO", "multiselect"),
            SettingItem("fido_uv", "FIDO User verification", "User verification methods for FIDO", "multiselect"),
            SettingItem("ble_enabled", "BLE Enabled", "Enable Bluetooth Low Energy", "boolean"),
            SettingItem("ble_use_rpas", "BLE Private Address", "Use random private addresses for BLE", "boolean"),
            SettingItem("export_fingerprints_always", "Export FPs always", "Always export fingerprints", "boolean"),
            SettingItem("export_handwriting_always", "Export Handwr. always", "Always export handwriting data", "boolean"),
            SettingItem("nfc_to_st_safe_bridge", "NFC to ST bridge", "Enable NFC to ST Safe bridge", "boolean"),
            SettingItem("client_pin", "Client PIN", "Enable client PIN authentication", "boolean"),
            SettingItem("logging_enabled", "USB Logging", "Enable USB logging", "boolean"),
            SettingItem("fido_over_usb", "FIDO over USB", "Enable FIDO authentication over USB", "boolean"),
            SettingItem("ccid_over_usb", "CCID over USB", "Enable CCID (smart card) over USB", "boolean")
        )
        
        for (setting in settings) {
            createSettingView(setting)
        }
    }
    
    private fun createSettingView(setting: SettingItem) {
        val settingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Line 1: Title with checkbox on left
        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val checkbox = CheckBox(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 12
            }
            isChecked = viewModel.selectedSettings.value?.get(setting.name) ?: true
            setOnCheckedChangeListener { _, isChecked ->
                viewModel.updateSettingSelection(setting.name, isChecked)
            }
        }

        val nameText = TextView(this).apply {
            text = setting.displayName
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 2
            setSingleLine(false)
        }

        titleLayout.addView(checkbox)
        titleLayout.addView(nameText)

        // Line 2: Subtitle/Description
        val descText = TextView(this).apply {
            text = setting.description
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4
                bottomMargin = 12
                marginStart = 44 // Align with title text (checkbox width + margin)
            }
            maxLines = 3
            setSingleLine(false)
        }

        // Line 3/4: Control based on type
        val control = when (setting.type) {
            "boolean" -> createBooleanControl(setting)
            "dropdown" -> createDropdownControl(setting)
            "multiselect" -> createMultiselectControl(setting)
            else -> createReadonlyControl(setting)
        }

        // Add margin to control to align with description
        control.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = 44 // Align with description
        }

        settingLayout.addView(titleLayout)
        settingLayout.addView(descText)
        settingLayout.addView(control)

        llSettingsContainer.addView(settingLayout)
        
        // Add divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                1
            )
            setBackgroundColor(resources.getColor(R.color.text_secondary, null))
        }
        llSettingsContainer.addView(divider)
    }
    
    private fun createBooleanControl(setting: SettingItem): View {
        return Button(this).apply {
            // Get initial value
            var currentValue = viewModel.settingsState.value?.get(setting.name) as? Boolean ?: true
            text = if (currentValue) "ON" else "OFF"

            setOnClickListener {
                // Toggle the value
                currentValue = !currentValue
                viewModel.updateSetting(setting.name, currentValue)
                text = if (currentValue) "ON" else "OFF"

                // Update button appearance
                alpha = if (currentValue) 1.0f else 0.7f
            }

            // Set initial appearance
            alpha = if (currentValue) 1.0f else 0.7f
        }
    }
    
    private fun createDropdownControl(setting: SettingItem): View {
        return Spinner(this).apply {
            val options = when (setting.name) {
                "uv_timeout_index", "msc_timeout_index", "unlock_timeout_index" ->
                    arrayOf(
                        "No limit",
                        "10 seconds", "20 seconds", "30 seconds", "45 seconds",
                        "1 minute", "2 minutes", "5 minutes", "10 minutes", "15 minutes", "20 minutes", "30 minutes", "45 minutes",
                        "1 hour", "2 hours", "3 hours", "4 hours", "5 hours", "8 hours", "12 hours", "24 hours"
                    )
                "u2f_os" -> arrayOf("U2F_OS_OTHER", "U2F_OS_LINUX_MACOS")
                else -> arrayOf("Option 1", "Option 2", "Option 3")
            }
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, options)

            // Set current selection
            val currentValue = viewModel.settingsState.value?.get(setting.name) as? Int ?: 0
            setSelection(currentValue)

            // Handle selection changes
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    viewModel.updateSetting(setting.name, position)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }
    
    private fun createMultiselectControl(setting: SettingItem): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            // Add text labels first
            val textLayout = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
            }

            // UP_UV_OPTIONS: AUTO, TOUCH, FINGERPRINT, PASSCODE, HANDWRITING, VOICE
            val options = viewModel.getUpUvLabels()

            // Add text labels
            for ((index, option) in options.withIndex()) {
                val textView = TextView(this@SettingsActivity).apply {
                    text = option.second // Use the full label text
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = 4
                    }
                    textSize = 11f
                    gravity = android.view.Gravity.CENTER
                    maxLines = 2
                    setSingleLine(false)
                }
                textLayout.addView(textView)
            }
            addView(textLayout)

            // Add icon buttons below
            val buttonLayout = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            // Get current selected values from ViewModel
            val currentValues = viewModel.settingsState.value?.get(setting.name) as? List<*>
            val selectedValues = mutableSetOf<Int>()
            currentValues?.forEach { value ->
                if (value is Int) selectedValues.add(value)
            }

            for ((index, option) in options.withIndex()) {
                val btn = Button(this@SettingsActivity).apply {
                    text = option.first // Use the icon/short text
                    layoutParams = LinearLayout.LayoutParams(0, 120, 1f).apply {
                        marginEnd = if (index < options.size - 1) 4 else 0
                    }
                    setPadding(4, 4, 4, 4)
                    textSize = 16f

                    // Set initial state
                    isSelected = selectedValues.contains(index)
                    alpha = if (isSelected) 1.0f else 0.5f

                    setOnClickListener {
                        // Handle exclusive values (AUTO is exclusive)
                        if (index == 0) { // AUTO
                            selectedValues.clear()
                            selectedValues.add(0)
                        } else {
                            selectedValues.remove(0) // Remove AUTO if selecting others
                            if (selectedValues.contains(index)) {
                                selectedValues.remove(index)
                            } else {
                                selectedValues.add(index)
                            }
                        }

                        // Update all buttons in the button layout
                        for (i in 0 until buttonLayout.childCount) {
                            val childBtn = buttonLayout.getChildAt(i) as Button
                            childBtn.isSelected = selectedValues.contains(i)
                            childBtn.alpha = if (childBtn.isSelected) 1.0f else 0.5f
                        }

                        // Store the selected values in ViewModel
                        viewModel.updateSetting(setting.name, selectedValues.toList())
                    }
                }
                buttonLayout.addView(btn)
            }
            addView(buttonLayout)

            // Store initial values in ViewModel if not already set
            if (selectedValues.isNotEmpty()) {
                viewModel.updateSetting(setting.name, selectedValues.toList())
            }
        }
    }
    
    private fun createReadonlyControl(setting: SettingItem): View {
        return TextView(this).apply {
            text = when (setting.name) {
                "time" -> "30/07/2025, 09:06:33"
                else -> "N/A"
            }
            textSize = 14f
        }
    }
    
    // Event handlers
    private fun handleCopyForManualEdit() {
        val policyData = viewModel.copyPolicyData()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Policy Data", policyData)
        clipboard.setPrimaryClip(clip)
    }
    
    private fun recreateSettingsUI() {
        llSettingsContainer.removeAllViews()
        createSettingsUI()
    }
    
    data class SettingItem(
        val name: String,
        val displayName: String,
        val description: String,
        val type: String
    )
}
