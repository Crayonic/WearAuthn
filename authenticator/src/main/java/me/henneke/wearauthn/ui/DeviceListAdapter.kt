package me.henneke.wearauthn.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.henneke.wearauthn.R
import me.henneke.wearauthn.ble.models.ScannedDevice

/**
 * RecyclerView adapter for displaying scanned BLE devices.
 */
class DeviceListAdapter(
    private val onDeviceClick: (ScannedDevice) -> Unit
) : ListAdapter<ScannedDevice, DeviceListAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scanned_device, parent, false)
        return DeviceViewHolder(view, onDeviceClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder for individual device items
     */
    class DeviceViewHolder(
        itemView: View,
        private val onDeviceClick: (ScannedDevice) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val deviceNameTextView: TextView = itemView.findViewById(R.id.deviceNameTextView)
        private val deviceAddressTextView: TextView = itemView.findViewById(R.id.deviceAddressTextView)
        private val deviceRssiTextView: TextView = itemView.findViewById(R.id.deviceRssiTextView)

        fun bind(device: ScannedDevice) {
            val context = itemView.context

            deviceNameTextView.text = device.displayName
            deviceAddressTextView.text = device.deviceAddress
            deviceRssiTextView.text = device.getRssiString(context)

            // Set click listener
            itemView.setOnClickListener {
                onDeviceClick(device)
            }

            // Color coding based on signal strength
            val textColor = when {
                device.rssi >= -50 -> 0xFF4CAF50.toInt() // Green - Excellent
                device.rssi >= -60 -> 0xFF8BC34A.toInt() // Light Green - Good
                device.rssi >= -70 -> 0xFFFFC107.toInt() // Amber - Fair
                device.rssi >= -80 -> 0xFFFF9800.toInt() // Orange - Weak
                else -> 0xFFF44336.toInt() // Red - Very Weak
            }
            deviceRssiTextView.setTextColor(textColor)
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    private class DeviceDiffCallback : DiffUtil.ItemCallback<ScannedDevice>() {
        override fun areItemsTheSame(oldItem: ScannedDevice, newItem: ScannedDevice): Boolean {
            return oldItem.deviceAddress == newItem.deviceAddress
        }

        override fun areContentsTheSame(oldItem: ScannedDevice, newItem: ScannedDevice): Boolean {
            return oldItem.deviceName == newItem.deviceName &&
                    oldItem.rssi == newItem.rssi &&
                    oldItem.advertisementData?.contentEquals(newItem.advertisementData ?: byteArrayOf()) == true
        }
    }
}
