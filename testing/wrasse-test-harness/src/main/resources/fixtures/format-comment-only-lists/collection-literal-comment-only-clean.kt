package sample

annotation class Marker(val values: Array<String>)

@Marker([/* nothing here */])
class Target

// expect-clean
