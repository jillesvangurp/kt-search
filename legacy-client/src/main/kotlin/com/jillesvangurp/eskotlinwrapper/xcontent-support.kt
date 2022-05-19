package com.jillesvangurp.eskotlinwrapper

//fun MapBackedProperties.toXContent(builder: XContentBuilder): XContentBuilder {
//    builder.writeAny(this)
//    return builder
//}
//
//fun MapBackedProperties.stringify(pretty: Boolean = false): String {
//    val bos = ByteArrayOutputStream()
//    val builder = XContentFactory.jsonBuilder(bos)
//    if (pretty) {
//        builder.prettyPrint()
//    }
//    toXContent(builder)
//    builder.close()
//    bos.flush()
//    bos.close()
//    return bos.toByteArray().toString(StandardCharsets.UTF_8)
//}