package me.henneke.wearauthn.ble.models

import java.util.*

/**
 * Constants for U2F BLE service and characteristics.
 *
 * This includes UUIDs, status values, and other constants used in the
 * FIDO U2F over BLE implementation using vendor-specific 128-bit UUID base.
 *
 * Base UUID: {0x4A, 0x8C, 0x3F, 0x91, 0xE5, 0xB2, 0x7D, 0x96, 0x12, 0x54, 0x8E, 0xA3, 0x00, 0x00, 0xC3, 0xE0}
 * Format: 4A8C3F91-E5B2-7D96-1254-8EA3[XXXX]C3E0 where XXXX is the 16-bit service/characteristic ID
 */
object U2fBleConstants {

    // Vendor-specific 128-bit UUID base (corrected byte order):
    // C definition: {0x4A, 0x8C, 0x3F, 0x91, 0xE5, 0xB2, 0x7D, 0x96, 0x12, 0x54, 0x8E, 0xA3, 0x00, 0x00, 0xC3, 0xE0}
    // UUID string: E0C30000-A38E-5412-967D-B2E5913F8C4A
    // The 16-bit values are inserted at positions 2-3 (0x0000 in the base)

    // FIDO U2F Service UUID (0xCCFD)
    val BLE_UUID_FIDO_SERVICE: UUID = UUID.fromString("E0C3CCFD-A38E-5412-967D-B2E5913F8C4A")

    // FIDO U2F Advertising UUID (16-bit for advertising to save space)
    // Using FIDO Alliance assigned 16-bit UUID: 0xFFFD for backward compatibility
    val FIDO_U2F_ADVERTISING_UUID: UUID = UUID.fromString("0000FFFD-0000-1000-8000-00805F9B34FB")

    // U2F Characteristic UUIDs using vendor-specific base (corrected byte order)
    val BLE_UUID_CONTROL_POINT_CHAR: UUID = UUID.fromString("E0C3CCF1-A38E-5412-967D-B2E5913F8C4A")
    val BLE_UUID_STATUS_CHAR: UUID = UUID.fromString("E0C3CCF2-A38E-5412-967D-B2E5913F8C4A")
    val BLE_UUID_CONTROL_POINT_LENGTH_CHAR: UUID = UUID.fromString("E0C3CCF3-A38E-5412-967D-B2E5913F8C4A")
    val BLE_UUID_SERVICE_REVISION_BITFIELD_CHAR: UUID = UUID.fromString("E0C3CCF4-A38E-5412-967D-B2E5913F8C4A")
    val BLE_UUID_PROXIMITY_LOGIN_BITFIELD: UUID = UUID.fromString("E0C3CAC1-A38E-5412-967D-B2E5913F8C4A")
    val BLE_UUID_SERVICE_REVISION_CHAR: UUID = UUID.fromString("E0C32A28-A38E-5412-967D-B2E5913F8C4A")

    // Legacy aliases for backward compatibility
    @Deprecated("Use BLE_UUID_FIDO_SERVICE instead", ReplaceWith("BLE_UUID_FIDO_SERVICE"))
    val FIDO_U2F_SERVICE_UUID: UUID = BLE_UUID_FIDO_SERVICE

    @Deprecated("Use BLE_UUID_CONTROL_POINT_CHAR instead", ReplaceWith("BLE_UUID_CONTROL_POINT_CHAR"))
    val U2F_CONTROL_POINT_UUID: UUID = BLE_UUID_CONTROL_POINT_CHAR

    @Deprecated("Use BLE_UUID_STATUS_CHAR instead", ReplaceWith("BLE_UUID_STATUS_CHAR"))
    val U2F_STATUS_UUID: UUID = BLE_UUID_STATUS_CHAR

    @Deprecated("Use BLE_UUID_CONTROL_POINT_LENGTH_CHAR instead", ReplaceWith("BLE_UUID_CONTROL_POINT_LENGTH_CHAR"))
    val U2F_CONTROL_POINT_LENGTH_UUID: UUID = BLE_UUID_CONTROL_POINT_LENGTH_CHAR

    @Deprecated("Use BLE_UUID_SERVICE_REVISION_CHAR instead", ReplaceWith("BLE_UUID_SERVICE_REVISION_CHAR"))
    val U2F_SERVICE_REVISION_UUID: UUID = BLE_UUID_SERVICE_REVISION_CHAR

    @Deprecated("Use BLE_UUID_SERVICE_REVISION_BITFIELD_CHAR instead", ReplaceWith("BLE_UUID_SERVICE_REVISION_BITFIELD_CHAR"))
    val U2F_SERVICE_REVISION_BITFIELD_UUID: UUID = BLE_UUID_SERVICE_REVISION_BITFIELD_CHAR

    @Deprecated("Use BLE_UUID_PROXIMITY_LOGIN_BITFIELD instead", ReplaceWith("BLE_UUID_PROXIMITY_LOGIN_BITFIELD"))
    val PROXIMITY_LOGIN_BITFIELD_UUID: UUID = BLE_UUID_PROXIMITY_LOGIN_BITFIELD
    
    // U2F Status values
    const val U2F_STATUS_IDLE: Byte = 0x00
    const val U2F_STATUS_PROCESSING: Byte = 0x01
    const val U2F_STATUS_NEED_PRESENCE: Byte = 0x02
    
    // Service revision information
    const val SERVICE_REVISION = "1.0"
    const val SERVICE_REVISION_BITFIELD: Byte = 0x80.toByte() // Bit 7: supports U2F 1.2
    
    // Proximity login bitfield
    const val PROXIMITY_LOGIN_BITFIELD_DESCRIPTION = "proximityLoginBitfield"
    const val PROXIMITY_LOGIN_BITFIELD_VALUE: Byte = 0x00 // Disabled by default
    
    // Control point length (maximum packet size)
    const val U2F_CONTROL_POINT_LENGTH: Short = 512
    
    /**
     * Get human-readable status description
     * 
     * @param status The status byte
     * @return Human-readable status description
     */
    fun getStatusDescription(status: Byte): String {
        return when (status) {
            U2F_STATUS_IDLE -> "IDLE - Ready for requests"
            U2F_STATUS_PROCESSING -> "PROCESSING - Request in progress"
            U2F_STATUS_NEED_PRESENCE -> "NEED_PRESENCE - User presence required"
            else -> "UNKNOWN_STATUS(0x%02x)".format(status)
        }
    }
    
    /**
     * Get characteristic name from UUID
     *
     * @param uuid The characteristic UUID
     * @return Human-readable characteristic name
     */
    fun getCharacteristicName(uuid: UUID): String {
        return when (uuid) {
            // New vendor-specific UUIDs
            BLE_UUID_CONTROL_POINT_CHAR -> "BLE_UUID_CONTROL_POINT_CHAR"
            BLE_UUID_STATUS_CHAR -> "BLE_UUID_STATUS_CHAR"
            BLE_UUID_CONTROL_POINT_LENGTH_CHAR -> "BLE_UUID_CONTROL_POINT_LENGTH_CHAR"
            BLE_UUID_SERVICE_REVISION_CHAR -> "BLE_UUID_SERVICE_REVISION_CHAR"
            BLE_UUID_SERVICE_REVISION_BITFIELD_CHAR -> "BLE_UUID_SERVICE_REVISION_BITFIELD_CHAR"
            BLE_UUID_PROXIMITY_LOGIN_BITFIELD -> "BLE_UUID_PROXIMITY_LOGIN_BITFIELD"

            // Legacy UUIDs (deprecated but still supported)
            U2F_CONTROL_POINT_UUID -> "U2F_CONTROL_POINT (legacy)"
            U2F_STATUS_UUID -> "U2F_STATUS (legacy)"
            U2F_CONTROL_POINT_LENGTH_UUID -> "U2F_CONTROL_POINT_LENGTH (legacy)"
            U2F_SERVICE_REVISION_UUID -> "U2F_SERVICE_REVISION (legacy)"
            U2F_SERVICE_REVISION_BITFIELD_UUID -> "U2F_SERVICE_REVISION_BITFIELD (legacy)"
            PROXIMITY_LOGIN_BITFIELD_UUID -> "PROXIMITY_LOGIN_BITFIELD (legacy)"

            else -> "UNKNOWN_CHARACTERISTIC"
        }
    }
}
