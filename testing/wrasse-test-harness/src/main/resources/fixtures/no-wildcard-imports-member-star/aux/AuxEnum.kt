package sample.auxenum

enum class Status {
    ACTIVE,
    INACTIVE,
    PENDING;

    fun describe(): String = name.lowercase()
}
