package com.varlanv.wrasse.lang

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ConfigValueParserSpec :
    BaseSpec({
        context("string values") {
            should("parse a simple string") {
                val result = ConfigValueParser.parse("\"hello\"")
                result shouldBe ConfigValue.Str("hello")
            }

            should("parse an empty string") {
                val result = ConfigValueParser.parse("\"\"")
                result shouldBe ConfigValue.Str("")
            }

            should("parse a string with basic escape sequences") {
                val result = ConfigValueParser.parse("\"a\\\"b\\\\c\\/d\"")
                result shouldBe ConfigValue.Str("a\"b\\c/d")
            }

            should("parse a string with whitespace escape sequences") {
                val result = ConfigValueParser.parse("\"\\t\\n\\r\"")
                result shouldBe ConfigValue.Str("\t\n\r")
            }

            should("parse a string with backspace and form feed escapes") {
                val result = ConfigValueParser.parse("\"\\b\\f\"")
                result shouldBe ConfigValue.Str("\b\u000C")
            }

            should("parse a string with a unicode escape") {
                val result = ConfigValueParser.parse("\"\\u0041\"")
                result shouldBe ConfigValue.Str("A")
            }

            should("parse a string with a non-ASCII unicode escape") {
                val result = ConfigValueParser.parse("\"\\u00E9\"")
                result shouldBe ConfigValue.Str("é")
            }

            should("parse a string containing spaces and punctuation") {
                val result = ConfigValueParser.parse("\"hello world! @#\$%\"")
                result shouldBe ConfigValue.Str("hello world! @#\$%")
            }

            should("reject an unterminated string") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("\"hello")
                }
            }

            should("reject an invalid escape sequence") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("\"\\x\"")
                }
            }

            should("reject an unescaped control character") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("\"hello" + "\u0001" + "world\"")
                }
            }

            should("reject an incomplete unicode escape") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("\"\\u00\"")
                }
            }

            should("reject an invalid unicode escape") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("\"\\uZZZZ\"")
                }
            }
        }

        context("number values") {
            should("parse zero") {
                ConfigValueParser.parse("0") shouldBe ConfigValue.Num(0)
            }

            should("parse a positive integer") {
                ConfigValueParser.parse("42") shouldBe ConfigValue.Num(42)
            }

            should("parse a negative integer") {
                ConfigValueParser.parse("-7") shouldBe ConfigValue.Num(-7)
            }

            should("parse a large integer") {
                ConfigValueParser.parse("9999999999") shouldBe ConfigValue.Num(9999999999L)
            }

            should("parse a decimal number as Dbl") {
                ConfigValueParser.parse("3.14") shouldBe ConfigValue.Dbl(3.14)
            }

            should("parse zero with decimal as Dbl") {
                ConfigValueParser.parse("0.5") shouldBe ConfigValue.Dbl(0.5)
            }

            should("parse a negative decimal as Dbl") {
                ConfigValueParser.parse("-0.001") shouldBe ConfigValue.Dbl(-0.001)
            }

            should("parse a number with exponent as Dbl") {
                ConfigValueParser.parse("1e10") shouldBe ConfigValue.Dbl(1e10)
            }

            should("parse a number with uppercase exponent as Dbl") {
                ConfigValueParser.parse("1E10") shouldBe ConfigValue.Dbl(1e10)
            }

            should("parse a number with positive exponent sign") {
                ConfigValueParser.parse("1e+2") shouldBe ConfigValue.Dbl(1e2)
            }

            should("parse a number with negative exponent sign") {
                ConfigValueParser.parse("1e-2") shouldBe ConfigValue.Dbl(1e-2)
            }

            should("parse a decimal with exponent") {
                ConfigValueParser.parse("1.5e3") shouldBe ConfigValue.Dbl(1.5e3)
            }

            should("reject leading zeros") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("01")
                }
            }

            should("reject a bare minus sign") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("-")
                }
            }

            should("reject a decimal point without following digit") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("1.")
                }
            }

            should("reject an exponent without following digit") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("1e")
                }
            }
        }

        context("boolean values") {
            should("parse true") {
                ConfigValueParser.parse("true") shouldBe ConfigValue.Bool(true)
            }

            should("parse false") {
                ConfigValueParser.parse("false") shouldBe ConfigValue.Bool(false)
            }
        }

        context("null value") {
            should("parse null") {
                ConfigValueParser.parse("null") shouldBe ConfigValue.Null
            }
        }

        context("objects") {
            should("parse an empty object") {
                val result = ConfigValueParser.parse("{}")
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                result.value.map shouldBe emptyMap()
            }

            should("parse an object with a single string value") {
                val result = ConfigValueParser.parse("""{"key": "value"}""")
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                result.value.get<ConfigValue.Str>("key") shouldBe Property.Val(ConfigValue.Str("value"))
            }

            should("parse an object with multiple value types") {
                val result = ConfigValueParser.parse(
                    """{"s": "hello", "n": 42, "b": true, "d": 1.5, "nil": null}"""
                )
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                val props = result.value
                props.get<ConfigValue.Str>("s") shouldBe Property.Val(ConfigValue.Str("hello"))
                props.get<ConfigValue.Num>("n") shouldBe Property.Val(ConfigValue.Num(42))
                props.get<ConfigValue.Bool>("b") shouldBe Property.Val(ConfigValue.Bool(true))
                props.get<ConfigValue.Dbl>("d") shouldBe Property.Val(ConfigValue.Dbl(1.5))
                props.get<ConfigValue.Null>("nil") shouldBe Property.Val(ConfigValue.Null)
            }

            should("parse nested objects") {
                val result = ConfigValueParser.parse("""{"outer": {"inner": "deep"}}""")
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                val outer = result.value.get<ConfigValue.Obj>("outer")
                outer.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()
                outer.value.value.get<ConfigValue.Str>("inner") shouldBe
                    Property.Val(ConfigValue.Str("deep"))
            }

            should("overwrite duplicate keys keeping the last value") {
                val result = ConfigValueParser.parse("""{"a": 1, "a": 2}""")
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                result.value.get<ConfigValue.Num>("a") shouldBe Property.Val(ConfigValue.Num(2))
            }

            should("return Missing for absent keys") {
                val result = ConfigValueParser.parse("""{"a": 1}""") as ConfigValue.Obj
                result.value.get<ConfigValue.Str>("b") shouldBe Property.Missing("b")
            }

            should("return TypeMismatch for wrong value type") {
                val result = ConfigValueParser.parse("""{"a": 1}""") as ConfigValue.Obj
                result.value.get<ConfigValue.Str>("a") shouldBe
                    Property.TypeMismatch("a", ConfigValue.Num(1))
            }

            should("reject an unterminated object") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("""{"a": 1""")
                }
            }

            should("reject a non-string key") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("""{42: "value"}""")
                }
            }

            should("reject a missing colon") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("""{"a" 1}""")
                }
            }
        }

        context("arrays") {
            should("parse an empty array as StrArr with empty list") {
                val result = ConfigValueParser.parse("[]")
                result shouldBe ConfigValue.StrArr(emptyList())
            }

            should("parse a string array as StrArr") {
                val result = ConfigValueParser.parse("""["a", "b", "c"]""")
                result shouldBe ConfigValue.StrArr(listOf("a", "b", "c"))
            }

            should("parse an integer array as NumArr") {
                val result = ConfigValueParser.parse("[1, 2, 3]")
                result shouldBe ConfigValue.NumArr(listOf(1L, 2L, 3L))
            }

            should("parse an object array as ObjArr") {
                val result = ConfigValueParser.parse("""[{"x": 1}, {"x": 2}]""")
                result.shouldBeInstanceOf<ConfigValue.ObjArr>()
                val arr = result.value
                arr shouldHaveSize 2
                arr[0].get<ConfigValue.Num>("x") shouldBe Property.Val(ConfigValue.Num(1))
                arr[1].get<ConfigValue.Num>("x") shouldBe Property.Val(ConfigValue.Num(2))
            }

            should("parse a boolean array as Arr") {
                val result = ConfigValueParser.parse("[true, false, true]")
                result.shouldBeInstanceOf<ConfigValue.Arr>()
                result.value shouldBe listOf(
                    ConfigValue.Bool(true),
                    ConfigValue.Bool(false),
                    ConfigValue.Bool(true),
                )
            }

            should("parse a double array as Arr") {
                val result = ConfigValueParser.parse("[1.5, 2.5]")
                result.shouldBeInstanceOf<ConfigValue.Arr>()
                result.value shouldBe listOf(ConfigValue.Dbl(1.5), ConfigValue.Dbl(2.5))
            }

            should("parse a mixed-type array as Arr") {
                val result = ConfigValueParser.parse("""[1, "two", true, null]""")
                result.shouldBeInstanceOf<ConfigValue.Arr>()
                result.value shouldBe listOf(
                    ConfigValue.Num(1),
                    ConfigValue.Str("two"),
                    ConfigValue.Bool(true),
                    ConfigValue.Null,
                )
            }

            should("parse a single-element string array as StrArr") {
                val result = ConfigValueParser.parse("""["only"]""")
                result shouldBe ConfigValue.StrArr(listOf("only"))
            }

            should("parse nested arrays as Arr") {
                val result = ConfigValueParser.parse("[[1, 2], [3, 4]]")
                result.shouldBeInstanceOf<ConfigValue.Arr>()
                result.value shouldHaveSize 2
                result.value[0] shouldBe ConfigValue.NumArr(listOf(1L, 2L))
                result.value[1] shouldBe ConfigValue.NumArr(listOf(3L, 4L))
            }

            should("reject an unterminated array") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("[1, 2")
                }
            }
        }

        context("JSONC features") {
            should("skip single-line comments before a value") {
                val result = ConfigValueParser.parse(
                    """
                    // this is a comment
                    "hello"
                    """.trimIndent()
                )
                result shouldBe ConfigValue.Str("hello")
            }

            should("skip single-line comments after a value in objects") {
                val result = ConfigValueParser.parse(
                    """
                    {
                      // comment before key
                      "a": 1, // inline comment
                      "b": 2
                    }
                    """.trimIndent()
                )
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                result.value.get<ConfigValue.Num>("a") shouldBe Property.Val(ConfigValue.Num(1))
                result.value.get<ConfigValue.Num>("b") shouldBe Property.Val(ConfigValue.Num(2))
            }

            should("skip block comments") {
                val result = ConfigValueParser.parse(
                    """
                    /* block comment */
                    {"key": /* inline */ "value"}
                    """.trimIndent()
                )
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                result.value.get<ConfigValue.Str>("key") shouldBe Property.Val(ConfigValue.Str("value"))
            }

            should("skip multi-line block comments") {
                val result = ConfigValueParser.parse(
                    """
                    {
                      /*
                       * multi-line
                       * block comment
                       */
                      "a": 1
                    }
                    """.trimIndent()
                )
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                result.value.get<ConfigValue.Num>("a") shouldBe Property.Val(ConfigValue.Num(1))
            }

            should("allow trailing comma in objects") {
                val result = ConfigValueParser.parse("""{"a": 1, "b": 2,}""")
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                result.value.get<ConfigValue.Num>("a") shouldBe Property.Val(ConfigValue.Num(1))
                result.value.get<ConfigValue.Num>("b") shouldBe Property.Val(ConfigValue.Num(2))
            }

            should("allow trailing comma in arrays") {
                val result = ConfigValueParser.parse("""[1, 2, 3,]""")
                result shouldBe ConfigValue.NumArr(listOf(1L, 2L, 3L))
            }

            should("reject unterminated block comments") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("/* never closed")
                }
            }

            should("not treat comment syntax inside strings as comments") {
                val result = ConfigValueParser.parse("""{"url": "http://example.com"}""")
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                result.value.get<ConfigValue.Str>("url") shouldBe
                    Property.Val(ConfigValue.Str("http://example.com"))
            }

            should("handle a single-line comment at end of input without trailing newline") {
                val result = ConfigValueParser.parse("42 // trailing")
                result shouldBe ConfigValue.Num(42)
            }
        }

        context("whitespace handling") {
            should("parse a value surrounded by whitespace") {
                val result = ConfigValueParser.parse("  \t\n\r  42  \n  ")
                result shouldBe ConfigValue.Num(42)
            }

            should("parse an object with varied whitespace") {
                val result = ConfigValueParser.parse(
                    "  { \n \"a\" \t : \r\n 1 \n } \n"
                )
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                result.value.get<ConfigValue.Num>("a") shouldBe Property.Val(ConfigValue.Num(1))
            }
        }

        context("nesting depth") {
            should("parse structures nested exactly at the maximum depth") {
                val json = "{\"a\":".repeat(ConfigValueParser.MAX_DEPTH) +
                    "1" +
                    "}".repeat(ConfigValueParser.MAX_DEPTH)
                val result = ConfigValueParser.parse(json)
                result.shouldBeInstanceOf<ConfigValue.Obj>()
            }

            should("reject objects nested beyond the maximum depth") {
                val json = "{\"a\":".repeat(ConfigValueParser.MAX_DEPTH + 1) +
                    "1" +
                    "}".repeat(ConfigValueParser.MAX_DEPTH + 1)
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse(json)
                }.message shouldBe "Nesting exceeds ${ConfigValueParser.MAX_DEPTH} levels (at position ${(ConfigValueParser.MAX_DEPTH) * 5})"
            }

            should("reject arrays nested beyond the maximum depth") {
                val json = "[".repeat(ConfigValueParser.MAX_DEPTH + 1) +
                    "1" +
                    "]".repeat(ConfigValueParser.MAX_DEPTH + 1)
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse(json)
                }.message shouldBe "Nesting exceeds ${ConfigValueParser.MAX_DEPTH} levels (at position ${ConfigValueParser.MAX_DEPTH})"
            }

            should("track depth independently for parallel branches") {
                val inner = "{\"x\":1}"
                val json = """{"a": $inner, "b": $inner}"""
                val result = ConfigValueParser.parse(json)
                result.shouldBeInstanceOf<ConfigValue.Obj>()
            }
        }

        context("realistic config parsing") {
            should("parse a wrasse.json-like configuration") {
                val json = """
                {
                  // Schema for IDE autocompletion
                  "${'$'}schema": "https://example.com/wrasse.schema.json",
                  "exclude": ["**/build/**", "**/generated/**"],
                  "rules": {
                    "no-semicolons": {
                      "enabled": true,
                      "severity": "error",
                      "exclude": ["**/test/**"]
                    },
                    "max-line-length": {
                      "enabled": true,
                      "severity": "warning",
                      "config": {
                        "maxLength": 120
                      }
                    }
                  },
                  "format": {
                    "outputDir": ".wrasse-format"
                  }
                }
                """.trimIndent()
                val result = ConfigValueParser.parse(json)
                result.shouldBeInstanceOf<ConfigValue.Obj>()
                val root = result.value

                root.get<ConfigValue.Str>("\$schema") shouldBe
                    Property.Val(ConfigValue.Str("https://example.com/wrasse.schema.json"))

                val exclude = root.get<ConfigValue.StrArr>("exclude")
                exclude.shouldBeInstanceOf<Property.Val<ConfigValue.StrArr>>()
                exclude.value.value shouldBe listOf("**/build/**", "**/generated/**")

                val rules = root.get<ConfigValue.Obj>("rules")
                rules.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()

                val noSemicolons = rules.value.value.get<ConfigValue.Obj>("no-semicolons")
                noSemicolons.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()
                noSemicolons.value.value.get<ConfigValue.Bool>("enabled") shouldBe
                    Property.Val(ConfigValue.Bool(true))
                noSemicolons.value.value.get<ConfigValue.Str>("severity") shouldBe
                    Property.Val(ConfigValue.Str("error"))

                val maxLineLength = rules.value.value.get<ConfigValue.Obj>("max-line-length")
                maxLineLength.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()
                val config = maxLineLength.value.value.get<ConfigValue.Obj>("config")
                config.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()
                config.value.value.get<ConfigValue.Num>("maxLength") shouldBe
                    Property.Val(ConfigValue.Num(120))

                val format = root.get<ConfigValue.Obj>("format")
                format.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()
                format.value.value.get<ConfigValue.Str>("outputDir") shouldBe
                    Property.Val(ConfigValue.Str(".wrasse-format"))
            }
        }

        context("error cases") {
            should("reject empty input") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("")
                }
            }

            should("reject whitespace-only input") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("   \n\t  ")
                }
            }

            should("reject trailing content after a valid value") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("42 24")
                }
            }

            should("reject an unexpected character at root level") {
                shouldThrow<IllegalArgumentException> {
                    ConfigValueParser.parse("@")
                }
            }
        }
    })
