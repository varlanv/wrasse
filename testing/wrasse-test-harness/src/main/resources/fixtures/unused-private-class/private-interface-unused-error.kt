package sample

private interface Unused

class Bar

// expect-error 3:1 unused-private-class "Private class 'Unused' is unused"
