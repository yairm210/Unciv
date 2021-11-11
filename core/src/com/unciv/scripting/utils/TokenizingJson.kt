package com.unciv.scripting.utils

import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.modules.SerializersModule



object TokenizingJson {

    // Json serialization that accepts `Any?`, and converts non-primitive values to string keys from `InstanceTokenizer`.

    object TokenizingSerializer: KSerializer<Any?> {
    
        // Adapted from https://stackoverflow.com/a/66158603/12260302
    
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any?")

        @Suppress("UNCHECKED_CAST")
        private val dataTypeSerializers: Map<String, KSerializer<Any?>> =
            //Tried replacing this with a generic `fun <T> serialize` and `serializer<T>`. The change in signature prevents `serialize` from being overridden correctly, I guess.
            mapOf(
                "Boolean" to serializer<Boolean>(),
                "Byte" to serializer<Byte>(),
                "Char" to serializer<Char>(),
                "Double" to serializer<Double>(),
                "Float" to serializer<Float>(),
                "Int" to serializer<Int>(),
                "Long" to serializer<Long>(),
                "Short" to serializer<Short>(),
                "String" to serializer<String>()
                // Only these types will be serialized. Everything else will be replaced with a string key from InstanceTokenizer.
            ).mapValues { (_, v) -> v as KSerializer<Any?> }
        
//        private fun <T> getDataTypeSerializer(value: T): KSerializer<Any?> {
//            return serializer<T>() as KSerializer<Any?>
//            //Right. T needs to be known at compile time.
//        }

        override fun serialize(encoder: Encoder, value: Any?) {
            if (value == null) {
                encoder.encodeNull()
            } else {
                val classname = value!!::class.simpleName!!
                if (classname in dataTypeSerializers) {
                    encoder.encodeSerializableValue(dataTypeSerializers[value!!::class.simpleName!!]!!, value!!)
                } else {
                    encoder.encodeString(InstanceTokenizer.getToken(value!!))
                }
            }
        }

        override fun deserialize(decoder: Decoder): Any? {
            if (decoder is JsonDecoder) {
                val jsonLiteral = (decoder as JsonDecoder).decodeJsonElement()
                val rawval: Any? = getJsonReal(jsonLiteral)
                return InstanceTokenizer.getReal(rawval)
            } else {
                throw UnsupportedOperationException("Decoding is not supported by TokenizingSerializer for ${decoder::class.simpleName}.")
            }
        }
    }
    
//    val serializersModule = SerializersModule {
//        contextual(TokenizingSerializer);
//        contextual(DynamicLookupSerializer())
//    }

    val json = Json {
        explicitNulls = true; //Disable these if it becomes a problem.
        encodeDefaults = true;
//        serializersModule = serializersModule
    }
    
    
    fun <T> getJsonElement(value: T?): JsonElement {
        if (value is JsonElement) {
            return value
        }
        if (value is Map<*, *>) { //TODO: Decide what to do with the keys.
            return JsonObject( (value as Map<String, Any?>).mapValues{ getJsonElement(it.value) } )
        }
        if (value is Iterable<*>) {
            // Apparently ::class.java.isArray can be used to check for primitive arrays, but it breaks 
            return JsonArray(value.map{ getJsonElement(it) })
        }
        if (value is Sequence<*>) {
            var v = (value as Sequence<Any?>).toList()
            println(v)
            println(v[0]!!::class.qualifiedName)
            return getJsonElement(v)
        }
        if (value is String) { 
            return JsonPrimitive(value as String)
        }
        if (value is Int || value is Long || value is Float || value is Double) { 
            return JsonPrimitive(value as Number)
        }
        if (value is Boolean) { //TODO: Arrays?
            return JsonPrimitive(value as Boolean)
        }
        if (value == null) {
            return JsonNull
        }
        return JsonPrimitive(InstanceTokenizer.getToken(value))
    }
    
    fun getJsonReal(value: JsonElement): Any? {
        if (value is JsonNull || value == JsonNull) {
            return null
        }
        if (value is JsonArray) {
            return (value as List<JsonElement>).map{ getJsonReal(it) }// as List<Any?>
        }
        if (value is JsonObject) {
            return (value as Map<String, JsonElement>).mapValues{ getJsonReal(it.value) }// as Map<String, Any?>
        }
        if (value is JsonPrimitive) {
            val v = value as JsonPrimitive
            if (v.isString) {
                return InstanceTokenizer.getReal(v.content)
            } else {
                return v.content.toIntOrNull()
                    ?: v.content.toDoubleOrNull()
                    ?: v.content.toBooleanStrictOrNull()
                    ?: v.content
            }
        }
        throw IllegalArgumentException("Unrecognized type of JsonElement: ${value::class}/${value}")
    }
}
