package sample

enum class Foo {
    enumEntry,
}

// expect-error 4:5 enum-entry-naming "Enum entry name should be PascalCase ('EnumEntry') or SCREAMING_SNAKE_CASE ('ENUM_ENTRY')"
