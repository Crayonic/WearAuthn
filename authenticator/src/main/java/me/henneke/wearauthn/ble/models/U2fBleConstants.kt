package me.henneke.wearauthn.ble.models

import java.util.*

/**
 * Constants for U2F BLE service and characteristics.
 * 
 * This includes UUIDs, status values, and other constants used in the
 * FIDO U2F over BLE implementation.
 */
object U2fBleConstants {
    
    // FIDO U2F Service UUID (full 128-bit for GATT service)
    val FIDO_U2F_SERVICE_UUID: UUID = UUID.fromString("fdfb9654-1ffa-4e3f-82f5-ecde9f6b5f42")

    // FIDO U2F Advertising UUID (16-bit for advertising to save space)
    // Using FIDO Alliance assigned 16-bit UUID: 0xFFFD
    val FIDO_U2F_ADVERTISING_UUID: UUID = UUID.fromString("0000FFFD-0000-1000-8000-00805F9B34FB")
    
    // U2F Characteristic UUIDs
    val U2F_CONTROL_POINT_UUID: UUID = UUID.fromString("C4D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB")
    val U2F_STATUS_UUID: UUID = UUID.fromString("C4D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB")
    val U2F_CONTROL_POINT_LENGTH_UUID: UUID = UUID.fromString("C4D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB")
    val U2F_SERVICE_REVISION_UUID: UUID = UUID.fromString("C4D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB")
    val U2F_SERVICE_REVISION_BITFIELD_UUID: UUID = UUID.fromString("C4D0FFF5-DEAA-ECEE-B42F-C9BA7ED623BB")
    val PROXIMITY_LOGIN_BITFIELD_UUID: UUID = UUID.fromString("0000CAC1-0000-1000-8000-00805F9B34FB")
    
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
            U2F_CONTROL_POINT_UUID -> "U2F_CONTROL_POINT"
            U2F_STATUS_UUID -> "U2F_STATUS"
            U2F_CONTROL_POINT_LENGTH_UUID -> "U2F_CONTROL_POINT_LENGTH"
            U2F_SERVICE_REVISION_UUID -> "U2F_SERVICE_REVISION"
            U2F_SERVICE_REVISION_BITFIELD_UUID -> "U2F_SERVICE_REVISION_BITFIELD"
            PROXIMITY_LOGIN_BITFIELD_UUID -> "PROXIMITY_LOGIN_BITFIELD"
            else -> "UNKNOWN_CHARACTERISTIC"
        }
    }
}
