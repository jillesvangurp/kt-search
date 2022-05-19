package com.jillesvangurp.eskotlinwrapper

import org.elasticsearch.search.SearchHit
import kotlin.reflect.KClass

/**
 * Implement this for custom serialization/deserialization of objects in your index. Use this in combination with a [IndexRepository].
 */
interface ModelReaderAndWriter<T : Any> {
    val clazz: KClass<T>
    fun deserializer(): (ByteArray?) -> T
    fun serializer(): (T) -> ByteArray

    /**
     * @return a byte array with the json bytes for `obj`
     */
    fun serialize(obj: T): ByteArray {
        return serializer().invoke(obj)
    }

    /**
     * @return deserialize `obj`
     */
    fun deserialize(bytes: ByteArray): T {
        return deserializer().invoke(bytes)
    }

    /**
     * deserialize a searchHit. The default implementation accesses the `sourceRef` inside the searchHit.
     */
    fun deserialize(searchHit: SearchHit): T {
        // will throw npe if there is no source; source can be null on the java side
        val bytes = searchHit.sourceRef?.toBytesRef()?.bytes ?: "{}".toByteArray()

        return deserializer().invoke(bytes)
    }
}
