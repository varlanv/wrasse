package sample

annotation class DisplayName(val s: String)

@DisplayName(
    """
        Given something
        When something
        Then something
    """
)
class Foo

// expect-clean
