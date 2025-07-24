package me.henneke.wearauthn.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.henneke.wearauthn.databinding.DialogCredentialChooserBinding
import me.henneke.wearauthn.databinding.ItemCredentialBinding
import me.henneke.wearauthn.fido.context.WebAuthnCredential
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog for allowing user to select from multiple stored FIDO credentials.
 * Uses RecyclerView with view binding to display credential list showing
 * RP name, user name, and creation date for each credential.
 */
class CredentialChooserDialog : DialogFragment() {

    private var credentials: List<WebAuthnCredential> = emptyList()
    private var onCredentialSelected: ((WebAuthnCredential?) -> Unit)? = null
    private var onCancelled: (() -> Unit)? = null
    
    private var _binding: DialogCredentialChooserBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(
            credentials: List<WebAuthnCredential>,
            onCredentialSelected: (WebAuthnCredential?) -> Unit,
            onCancelled: () -> Unit = {}
        ): CredentialChooserDialog {
            return CredentialChooserDialog().apply {
                this.credentials = credentials
                this.onCredentialSelected = onCredentialSelected
                this.onCancelled = onCancelled
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCredentialChooserBinding.inflate(layoutInflater)
        
        setupRecyclerView()
        setupButtons()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }
    
    private fun setupRecyclerView() {
        val adapter = CredentialAdapter(credentials) { credential ->
            onCredentialSelected?.invoke(credential)
            dismiss()
        }

        binding.credentialsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
    }
    
    private fun setupButtons() {
        binding.cancelButton.setOnClickListener {
            onCancelled?.invoke()
            onCredentialSelected?.invoke(null)
            dismiss()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * RecyclerView adapter for displaying credentials with view binding
     */
    private class CredentialAdapter(
        private val credentials: List<WebAuthnCredential>,
        private val onCredentialClick: (WebAuthnCredential) -> Unit
    ) : RecyclerView.Adapter<CredentialAdapter.CredentialViewHolder>() {
        
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CredentialViewHolder {
            val binding = ItemCredentialBinding.inflate(
                LayoutInflater.from(parent.context), 
                parent, 
                false
            )
            return CredentialViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: CredentialViewHolder, position: Int) {
            holder.bind(credentials[position])
        }
        
        override fun getItemCount(): Int = credentials.size
        
        inner class CredentialViewHolder(
            private val binding: ItemCredentialBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(credential: WebAuthnCredential) {
                binding.apply {
                    // Set RP name (relying party/website name)
                    rpNameTextView.text = credential.rpName ?: "Unknown Site"

                    // Set user name
                    userNameTextView.text = credential.userName ?: "Unknown User"

                    // Set creation date
                    val creationDate = credential.creationDate?.let { date ->
                        dateFormat.format(date)
                    } ?: "Unknown Date"
                    creationDateTextView.text = "Created: $creationDate"
                    
                    // Set click listener
                    root.setOnClickListener {
                        onCredentialClick(credential)
                    }
                }
            }
        }
    }
}
