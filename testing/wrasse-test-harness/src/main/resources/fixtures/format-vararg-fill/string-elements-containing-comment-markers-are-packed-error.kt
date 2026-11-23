package sample

val urls = listOf("http://a", "https://b", "// not", "/* nor */", "x://y", "z")

// expect-error 1:1 format "File is not wrasse-formatted"
