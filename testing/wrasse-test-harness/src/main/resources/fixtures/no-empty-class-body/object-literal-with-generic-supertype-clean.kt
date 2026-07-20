package sample

abstract class TypeReference<T>

val ref = object : TypeReference<HashMap<String, String>>() {}

// expect-clean
