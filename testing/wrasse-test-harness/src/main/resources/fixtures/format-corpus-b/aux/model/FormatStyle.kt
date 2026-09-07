package com.varlanv.wrasse.model

class FormatStyle(
    val indentWidth: Int = 4,
    val maxLineLength: Int = 120,
    val trailingCommas: Boolean = true,
    val importLayout: ImportLayout = ImportLayout.ASCII,
    val multilineSignatureThreshold: Int? = 3,
    val wrapNestedCallArguments: Boolean = false,
)

enum class ImportLayout {
    ASCII,
}
