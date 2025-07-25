package me.henneke.wearauthn.ble.models

/**
 * Constants for CTAP BLE communication protocol.
 * 
 * This includes both standard FIDO CTAP BLE commands and custom vendor commands
 * used by the Crayonic WearAuthn implementation.
 */
object CtapBleConstants {
    
    // Base type for CTAP BLE commands
    const val TYPE_INIT: Byte = 0x80.toByte()
    
    // Standard FIDO CTAP BLE Commands
    const val CTAPBLE_PING: Byte = (TYPE_INIT.toInt() or 0x01).toByte()        // 0x81
    const val CTAPBLE_KEEPALIVE: Byte = (TYPE_INIT.toInt() or 0x02).toByte()   // 0x82
    const val CTAPBLE_MSG: Byte = (TYPE_INIT.toInt() or 0x03).toByte()         // 0x83
    const val CTAPBLE_CANCEL: Byte = (TYPE_INIT.toInt() or 0x3e).toByte()      // 0xBE
    const val CTAPBLE_ERROR: Byte = (TYPE_INIT.toInt() or 0x3f).toByte()       // 0xBF
    
    // Custom Vendor Commands (outside FIDO spec)
    const val CTAPBLE_CRAYONIC_BRIDGE: Byte = (TYPE_INIT.toInt() or 0x5B).toByte() // 0xDB
    const val CTAPBLE_SMARTCARD: Byte = (TYPE_INIT.toInt() or 0x5C).toByte()       // 0xDC
    const val CTAPBLE_SMARTCARD_AUX: Byte = (TYPE_INIT.toInt() or 0x5D).toByte()   // 0xDD
    const val CTAPBLE_SMARTCARD_ERROR: Byte = (TYPE_INIT.toInt() or 0x5E).toByte() // 0xDE
    
    // Bridge Command Types (ble_ctap_bridge_cmd_t)
    const val BRIDGE_CMD_STATUS: Byte = 0x00
    const val BRIDGE_CMD_KICKOFF: Byte = 0x01
    const val BRIDGE_CARDS_REMOVE: Byte = 0x02
    const val BRIDGE_CARDS_INSERT: Byte = 0x03
    const val BRIDGE_ECHO: Byte = 0x04
    
    // Maximum device name length for bridge commands
    const val CRAYONIC_BRIDGE_MAX_DEVICE_NAME_LENGTH = 32
    
    /**
     * Get human-readable name for CTAP BLE command
     * 
     * @param cmd The command byte
     * @return Human-readable command name
     */
    fun getCtapCommandName(cmd: Byte): String {
        return when (cmd) {
            CTAPBLE_PING -> "CTAPBLE_PING"
            CTAPBLE_KEEPALIVE -> "CTAPBLE_KEEPALIVE"
            CTAPBLE_MSG -> "CTAPBLE_MSG"
            CTAPBLE_CANCEL -> "CTAPBLE_CANCEL"
            CTAPBLE_ERROR -> "CTAPBLE_ERROR"
            CTAPBLE_CRAYONIC_BRIDGE -> "CTAPBLE_CRAYONIC_BRIDGE"
            CTAPBLE_SMARTCARD -> "CTAPBLE_SMARTCARD"
            CTAPBLE_SMARTCARD_AUX -> "CTAPBLE_SMARTCARD_AUX"
            CTAPBLE_SMARTCARD_ERROR -> "CTAPBLE_SMARTCARD_ERROR"
            else -> "UNKNOWN_CMD(0x%02x)".format(cmd)
        }
    }
    
    /**
     * Get human-readable name for bridge command
     * 
     * @param bridgeCmd The bridge command byte
     * @return Human-readable bridge command name
     */
    fun getBridgeCommandName(bridgeCmd: Byte): String {
        return when (bridgeCmd) {
            BRIDGE_CMD_STATUS -> "BRIDGE_CMD_STATUS"
            BRIDGE_CMD_KICKOFF -> "BRIDGE_CMD_KICKOFF"
            BRIDGE_CARDS_REMOVE -> "BRIDGE_CARDS_REMOVE"
            BRIDGE_CARDS_INSERT -> "BRIDGE_CARDS_INSERT"
            BRIDGE_ECHO -> "BRIDGE_ECHO"
            else -> "UNKNOWN_BRIDGE_CMD(0x%02x)".format(bridgeCmd)
        }
    }
    
    /**
     * Check if a command is a standard FIDO CTAP command
     * 
     * @param cmd The command byte to check
     * @return true if it's a standard FIDO command, false if it's a vendor command
     */
    fun isStandardFidoCommand(cmd: Byte): Boolean {
        return when (cmd) {
            CTAPBLE_PING, CTAPBLE_KEEPALIVE, CTAPBLE_MSG, CTAPBLE_CANCEL, CTAPBLE_ERROR -> true
            else -> false
        }
    }
    
    /**
     * Check if a command is a Crayonic vendor command
     * 
     * @param cmd The command byte to check
     * @return true if it's a Crayonic vendor command, false otherwise
     */
    fun isCrayonicVendorCommand(cmd: Byte): Boolean {
        return when (cmd) {
            CTAPBLE_CRAYONIC_BRIDGE, CTAPBLE_SMARTCARD, CTAPBLE_SMARTCARD_AUX, CTAPBLE_SMARTCARD_ERROR -> true
            else -> false
        }
    }
}
