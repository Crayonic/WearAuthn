package me.henneke.wearauthn.bthid

import android.content.Context
import me.henneke.wearauthn.fido.hid.HID_REPORT_SIZE
import me.henneke.wearauthn.ble.FidoU2fBleService

object Constants {
    const val SDP_DESCRIPTION = "FIDO2/U2F Security Key"

    // Fallback constants for backward compatibility (when context is not available)
    const val SDP_NAME = "CrayonicB00000"  // Default fallback name
    const val SDP_PROVIDER = "CrayonicB00000"  // Default fallback provider

    // Dynamic names that include device identifier (preferred when context is available)
    fun getSdpName(context: Context): String = FidoU2fBleService.getAdvertisingName(context)
    fun getSdpProvider(context: Context): String = FidoU2fBleService.getAdvertisingName(context)
    const val QOS_TOKEN_RATE = 1000
    const val QOS_TOKEN_BUCKET_SIZE = HID_REPORT_SIZE + 1
    const val QOS_PEAK_BANDWIDTH = 2000
    const val QOS_LATENCY = 5000
}
