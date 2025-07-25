package me.henneke.wearauthn.ble.models

import timber.log.Timber

/**
 * Data class representing the BLE frame status structure used in CTAP BLE communication.
 * 
 * This structure follows the packed C struct format:
 * ```c
 * typedef struct attribute((packed)) {
 *   uint8_t cmd;
 *   uint8_t len_h;
 *   uint8_t len_l;
 *   ble_ctap_bridge_cmd_t bridge_cmd;
 *   uint8_t version;
 *   uint8_t active;
 *   uint8_t data[CRAYONIC_BRIDGE_MAX_DEVICE_NAME_LENGTH];
 * } ble_frame_status_t;
 * ```
 */
data class BleFrameStatus(
    val cmd: Byte,
    val lenH: Byte,
    val lenL: Byte,
    val bridgeCmd: Byte,
    val version: Byte,
    val active: Byte,
    val data: ByteArray
) {
    /**
     * Calculate the length from the high and low bytes
     */
    val length: Int get() = ((lenH.toInt() and 0xFF) shl 8) or (lenL.toInt() and 0xFF)
    
    companion object {
        const val CRAYONIC_BRIDGE_MAX_DEVICE_NAME_LENGTH = 32
        
        /**
         * Parse a ByteArray into a BleFrameStatus object
         * 
         * @param bytes The byte array to parse
         * @return BleFrameStatus object if parsing succeeds, null otherwise
         */
        fun fromByteArray(bytes: ByteArray): BleFrameStatus? {
            if (bytes.size < 6) {
                Timber.w("BleFrameStatus: Insufficient data - need at least 6 bytes, got ${bytes.size}")
                return null
            }
            
            val cmd = bytes[0]
            val lenH = bytes[1]
            val lenL = bytes[2]
            val bridgeCmd = bytes[3]
            val version = bytes[4]
            val active = bytes[5]
            
            val dataLength = minOf(bytes.size - 6, CRAYONIC_BRIDGE_MAX_DEVICE_NAME_LENGTH)
            val data = if (dataLength > 0) {
                bytes.copyOfRange(6, 6 + dataLength)
            } else {
                byteArrayOf()
            }
            
            return BleFrameStatus(cmd, lenH, lenL, bridgeCmd, version, active, data)
        }
    }
    
    /**
     * Convert the BleFrameStatus back to a ByteArray
     * 
     * @return ByteArray representation of this frame
     */
    fun toByteArray(): ByteArray {
        val result = ByteArray(6 + data.size)
        result[0] = cmd
        result[1] = lenH
        result[2] = lenL
        result[3] = bridgeCmd
        result[4] = version
        result[5] = active
        data.copyInto(result, 6)
        return result
    }
    
    /**
     * Get a human-readable string representation of the frame
     */
    override fun toString(): String {
        return "BleFrameStatus(cmd=0x%02x, length=%d, bridgeCmd=0x%02x, version=%d, active=%d, dataSize=%d)".format(
            cmd, length, bridgeCmd, version, active, data.size
        )
    }
    
    /**
     * Custom equals method to handle ByteArray comparison
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BleFrameStatus

        if (cmd != other.cmd) return false
        if (lenH != other.lenH) return false
        if (lenL != other.lenL) return false
        if (bridgeCmd != other.bridgeCmd) return false
        if (version != other.version) return false
        if (active != other.active) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }
    
    /**
     * Custom hashCode method to handle ByteArray
     */
    override fun hashCode(): Int {
        var result = cmd.toInt()
        result = 31 * result + lenH
        result = 31 * result + lenL
        result = 31 * result + bridgeCmd
        result = 31 * result + version
        result = 31 * result + active
        result = 31 * result + data.contentHashCode()
        return result
    }
}
