package sample

enum class Foo {
    @Suppress("no-such-rule")
    enumEntry,
}

// expect-error 5:5 enum-entry-naming "Enum entry name should be PascalCase ('EnumEntry') or SCREAMING_SNAKE_CASE ('ENUM_ENTRY')"
