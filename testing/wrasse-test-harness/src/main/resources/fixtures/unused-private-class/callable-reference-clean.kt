package sample

private class Used(val param: String)

fun make(): ((String) -> Any) = ::Used

// expect-clean
