package me.henneke.wearauthn.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.henneke.wearauthn.R
import me.henneke.wearauthn.databinding.ActivityManageSpaceBinding
import me.henneke.wearauthn.databinding.ItemCredentialBinding
import me.henneke.wearauthn.fido.context.AuthenticatorContext
import me.henneke.wearauthn.fido.context.WebAuthnCredential
import me.henneke.wearauthn.ui.UiConstants.EXTRA_MANAGE_SPACE_RECEIVER

import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for managing authenticator data and settings.
 * Provides interface for viewing stored credentials and resetting all data.
 */
class ManageSpaceActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityManageSpaceBinding
    private var resultReceiver: ResultReceiver? = null
    private lateinit var credentialAdapter: CredentialDisplayAdapter
    

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize view binding
        binding = ActivityManageSpaceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get the result receiver from intent
        resultReceiver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_MANAGE_SPACE_RECEIVER, ResultReceiver::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_MANAGE_SPACE_RECEIVER)
        }
        
        setupUI()
        loadCredentials()
    }
    
    private fun setupUI() {
        // Setup RecyclerView
        credentialAdapter = CredentialDisplayAdapter()
        binding.credentialsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ManageSpaceActivity)
            adapter = credentialAdapter
        }
        
        // Setup buttons
        binding.resetButton.setOnClickListener {
            showResetConfirmation()
        }
        
        binding.closeButton.setOnClickListener {
            finishWithResult(Activity.RESULT_CANCELED)
        }
    }
    
    private fun loadCredentials() {
        lifecycleScope.launch {
            try {
                val credentials = withContext(Dispatchers.IO) {
                    AuthenticatorContext.getAllResidentCredentials(this@ManageSpaceActivity)
                }
                
                val allCredentials = credentials.values.flatten()
                credentialAdapter.updateCredentials(allCredentials)
                
                // Update credential count
                val count = allCredentials.size
                binding.credentialCountTextView.text = if (count > 0) {
                    getString(R.string.credentials_count_format, count)
                } else {
                    getString(R.string.no_credentials_stored)
                }
                
            } catch (e: Exception) {
                binding.credentialCountTextView.text = "Error loading credentials: ${e.message}"
            }
        }
    }
    
    private fun showResetConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset All Data")
            .setMessage(getString(R.string.reset_warning_message))
            .setPositiveButton("Reset") { _, _ ->
                performReset()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performReset() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Clear all stored data
                    AuthenticatorContext.deleteAllData(this@ManageSpaceActivity)
                }
                val success = true
                
                if (success) {
                    finishWithResult(Activity.RESULT_OK)
                } else {
                    // Show error
                    MaterialAlertDialogBuilder(this@ManageSpaceActivity)
                        .setTitle("Reset Failed")
                        .setMessage("Failed to reset authenticator data. Please try again.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                // Show error
                MaterialAlertDialogBuilder(this@ManageSpaceActivity)
                    .setTitle("Reset Error")
                    .setMessage("Error during reset: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    private fun finishWithResult(resultCode: Int) {
        resultReceiver?.send(resultCode, Bundle())
        finish()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        finishWithResult(Activity.RESULT_CANCELED)
    }
    
    /**
     * RecyclerView adapter for displaying credentials (read-only)
     */
    private class CredentialDisplayAdapter : RecyclerView.Adapter<CredentialDisplayAdapter.ViewHolder>() {
        
        private var credentials: List<WebAuthnCredential> = emptyList()
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        
        fun updateCredentials(newCredentials: List<WebAuthnCredential>) {
            credentials = newCredentials
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCredentialBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(credentials[position])
        }
        
        override fun getItemCount(): Int = credentials.size
        
        class ViewHolder(private val binding: ItemCredentialBinding) : RecyclerView.ViewHolder(binding.root) {
            private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            
            fun bind(credential: WebAuthnCredential) {
                binding.apply {
                    rpNameTextView.text = credential.rpName ?: "Unknown Site"
                    userNameTextView.text = credential.userName ?: "Unknown User"

                    val creationDate = credential.creationDate?.let { date ->
                        dateFormat.format(date)
                    } ?: "Unknown Date"
                    creationDateTextView.text = "Created: $creationDate"
                    
                    // Disable click for display-only mode
                    root.isClickable = false
                    root.isFocusable = false
                }
            }
        }
    }
}
