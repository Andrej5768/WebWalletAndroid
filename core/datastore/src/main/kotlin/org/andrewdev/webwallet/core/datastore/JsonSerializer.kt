package org.andrewdev.webwallet.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * A typed DataStore backed by kotlinx.serialization JSON instead of protobuf (ADR-0002): the same guarantees
 * (atomic writes, a single reader) without the protobuf plugin. Unknown keys are ignored, so an older app opens a
 * newer file; a file that cannot be read is replaced by the defaults.
 */
class JsonSerializer<T>(private val serializer: KSerializer<T>, override val defaultValue: T) : Serializer<T> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readFrom(input: InputStream): T = try {
        json.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot read the store", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }
}
