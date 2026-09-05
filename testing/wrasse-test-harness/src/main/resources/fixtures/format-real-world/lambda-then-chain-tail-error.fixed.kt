package sample

class Primitive(val contentOrNull: String?)

class JsonElement(val jsonPrimitive: Primitive)

private fun List<Map<String, JsonElement>>.decimalOf(
    filterType: String,
    field: String,
): Double? = find { filter -> filter["filterType"]?.jsonPrimitive?.contentOrNull == filterType }
    ?.get(field)
    ?.jsonPrimitive
    ?.contentOrNull
    ?.toDoubleOrNull()