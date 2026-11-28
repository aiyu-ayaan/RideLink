package com.aiyu.ridelink.core.protocol

/**
 * One-byte XOR obfuscation some displays expect on every frame (PROTOCOL.md, "Frame obfuscation"): bytes
 * `2 .. size-2` are XORed with the model's key, so the start byte, the DID and the trailing `FF` stay plain and a
 * checksum in byte 18 is XORed too. Stock: BleEngine.writeCharacteristicToCentral with BluetoothUtil.encryptData
 * (U716, 0xEB) and encryptDataU577Premium / decryptDataForNtorqU577 (U577 premium, 0xEA). XOR is its own inverse,
 * so the same call encodes a frame to send and decodes a received one.
 */
object FrameCrypto {
    /** A copy of [frame] with its payload XORed with [key]; [frame] itself when [key] is null or the frame too short. */
    fun xor(frame: ByteArray, key: Int?): ByteArray {
        if (key == null || frame.size < 4) return frame
        val out = frame.copyOf()
        for (i in 2..frame.size - 2) out[i] = (out[i].toInt() xor key).toByte()
        return out
    }
}
