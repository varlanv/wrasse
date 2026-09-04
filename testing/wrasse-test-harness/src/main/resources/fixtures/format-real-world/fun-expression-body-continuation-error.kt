package sample

class Config(
    val header: String,
    val limit: Int,
    val shared: Boolean,
)

fun buildDefaultConfig(headerName: String) =
    Config(
        headerName,
        1600,
        false,
    )

// expect-error 1:1 format "File is not wrasse-formatted"
