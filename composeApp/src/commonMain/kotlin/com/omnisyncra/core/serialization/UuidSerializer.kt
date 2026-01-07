package com.omnisyncra.core.serialization

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuidFrom
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Kotlinx Serialization serializer for UUID
 */
object UuidSerializer : KSerializer<Uuid> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Uuid) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): Uuid {
        return uuidFrom(decoder.decodeString())
    }
}