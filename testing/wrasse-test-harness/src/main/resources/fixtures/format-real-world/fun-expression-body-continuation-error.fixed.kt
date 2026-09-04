package sample

class Config(
    val header: String,
    val limit: Int,
    val shared: Boolean,
)

fun buildDefaultConfig(headerName: String) = Config(headerName, 1600, false)