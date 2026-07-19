package com.varlanv.wrasse.lang

/**
 * Lower-case hex encoding shared by every SHA-256 digest → string conversion in wrasse
 * (patch hashing on both the compile side and the apply side).
 */
object HexEncoding {

    private val DIGITS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )

    fun lowerCase(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = DIGITS[v ushr 4]
            chars[i * 2 + 1] = DIGITS[v and 0x0F]
        }
        return String(chars)
    }
}
