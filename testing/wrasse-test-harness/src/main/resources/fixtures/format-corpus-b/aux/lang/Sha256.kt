package com.varlanv.wrasse.lang

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/**
 * Lower-case hex SHA-256 of a file's content, computed the same way from either side of the
 * patch hand-off: [ofText] streams the compiler's text through a UTF-8 encoder in chunks (no
 * whole-file byte copy), [ofBytes] digests bytes already read from disk. The two agree for any
 * valid UTF-8 file, which is what lets the applier hash the raw bytes it read.
 */
object Sha256 {
    private const val CHUNK_BYTES = 8_192

    fun ofText(text: CharSequence): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val encoder = Charsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val input = CharBuffer.wrap(text)
        val output = ByteBuffer.allocate(CHUNK_BYTES)
        while (true) {
            val result = encoder.encode(input, output, true)
            output.flip()
            digest.update(output)
            output.clear()
            if (result.isUnderflow) break
        }
        encoder.flush(output)
        output.flip()
        digest.update(output)
        return HexEncoding.lowerCase(digest.digest())
    }

    fun ofBytes(bytes: ByteArray): String = HexEncoding.lowerCase(MessageDigest.getInstance("SHA-256").digest(bytes))
}
