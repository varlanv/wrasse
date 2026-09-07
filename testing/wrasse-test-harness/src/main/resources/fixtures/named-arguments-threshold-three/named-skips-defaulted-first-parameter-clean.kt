package sample

class Backend(val threads: Int = 4, val parentJob: String)

fun demo(): Backend = Backend(parentJob = "root")

// expect-clean
