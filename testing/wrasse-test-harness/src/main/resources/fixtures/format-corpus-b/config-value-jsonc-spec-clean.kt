package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ConfigValueJsoncSpec : BaseSpec({
    context("string values") {
        should("parse a simple string") {
            val result = ConfigValueJsonc.parse("\"hello\"").getOrThrow()
            result shouldBe ConfigValue.Str("hello")
        }

        should("parse an empty string") {
            val result = ConfigValueJsonc.parse("\"\"").getOrThrow()
            result shouldBe ConfigValue.Str("")
        }

        should("parse a string with basic escape sequences") {
            val result = ConfigValueJsonc.parse("\"a\\\"b\\\\c\\/d\"").getOrThrow()
            result shouldBe ConfigValue.Str("a\"b\\c/d")
        }

        should("parse a string with whitespace escape sequences") {
            val result = ConfigValueJsonc.parse("\"\\t\\n\\r\"").getOrThrow()
            result shouldBe ConfigValue.Str("\t\n\r")
        }

        should("parse a string with backspace and form feed escapes") {
            val result = ConfigValueJsonc.parse("\"\\b\\f\"").getOrThrow()
            result shouldBe ConfigValue.Str("\b\u000C")
        }

        should("parse a string with a unicode escape") {
            val result = ConfigValueJsonc.parse("\"\\u0041\"").getOrThrow()
            result shouldBe ConfigValue.Str("A")
        }

        should("parse a string with a non-ASCII unicode escape") {
            val result = ConfigValueJsonc.parse("\"\\u00E9\"").getOrThrow()
            result shouldBe ConfigValue.Str("é")
        }

        should("parse a string containing spaces and punctuation") {
            val result = ConfigValueJsonc.parse("\"hello world! @#\$%\"").getOrThrow()
            result shouldBe ConfigValue.Str("hello world! @#\$%")
        }

        should("reject an unterminated string") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("\"hello").getOrThrow()
            }.message shouldBe "Unterminated string (line 1, column 7)"
        }

        should("reject an invalid escape sequence") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("\"\\x\"").getOrThrow()
            }.message shouldBe "Invalid escape: \\x (line 1, column 3)"
        }

        should("reject an unescaped control character") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("\"hello" + "\u0001" + "world\"").getOrThrow()
            }.message shouldBe "Unescaped control character in string (line 1, column 7)"
        }

        should("reject an incomplete unicode escape") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("\"\\u00\"").getOrThrow()
            }.message shouldBe "Incomplete unicode escape (line 1, column 3)"
        }

        should("reject an invalid unicode escape") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("\"\\uZZZZ\"").getOrThrow()
            }.message shouldBe "Invalid unicode escape: \\uZZZZ (line 1, column 3)"
        }
    }

    context("number values") {
        should("parse zero") {
            ConfigValueJsonc.parse("0").getOrThrow() shouldBe ConfigValue.Num(0)
        }

        should("parse a positive integer") {
            ConfigValueJsonc.parse("42").getOrThrow() shouldBe ConfigValue.Num(42)
        }

        should("parse a negative integer") {
            ConfigValueJsonc.parse("-7").getOrThrow() shouldBe ConfigValue.Num(-7)
        }

        should("parse a large integer") {
            ConfigValueJsonc.parse("9999999999").getOrThrow() shouldBe ConfigValue.Num(9_999_999_999L)
        }

        should("parse a decimal number as Dbl") {
            ConfigValueJsonc.parse("3.14").getOrThrow() shouldBe ConfigValue.Dbl(3.14)
        }

        should("parse zero with decimal as Dbl") {
            ConfigValueJsonc.parse("0.5").getOrThrow() shouldBe ConfigValue.Dbl(0.5)
        }

        should("parse a negative decimal as Dbl") {
            ConfigValueJsonc.parse("-0.001").getOrThrow() shouldBe ConfigValue.Dbl(-0.001)
        }

        should("parse a number with exponent as Dbl") {
            ConfigValueJsonc.parse("1e10").getOrThrow() shouldBe ConfigValue.Dbl(1e10)
        }

        should("parse a number with uppercase exponent as Dbl") {
            ConfigValueJsonc.parse("1E10").getOrThrow() shouldBe ConfigValue.Dbl(1e10)
        }

        should("parse a number with positive exponent sign") {
            ConfigValueJsonc.parse("1e+2").getOrThrow() shouldBe ConfigValue.Dbl(1e2)
        }

        should("parse a number with negative exponent sign") {
            ConfigValueJsonc.parse("1e-2").getOrThrow() shouldBe ConfigValue.Dbl(1e-2)
        }

        should("parse a decimal with exponent") {
            ConfigValueJsonc.parse("1.5e3").getOrThrow() shouldBe ConfigValue.Dbl(1.5e3)
        }

        should("reject leading zeros") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("01").getOrThrow()
            }.message shouldBe "Leading zeros not allowed (line 1, column 2)"
        }

        should("reject a bare minus sign") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("-").getOrThrow()
            }.message shouldBe "Unexpected end in number (line 1, column 2)"
        }

        should("reject a decimal point without following digit") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("1.").getOrThrow()
            }.message shouldBe "Expected digit after '.' (line 1, column 3)"
        }

        should("reject an exponent without following digit") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("1e").getOrThrow()
            }.message shouldBe "Expected digit in exponent (line 1, column 3)"
        }
    }

    context("boolean values") {
        should("parse true") {
            ConfigValueJsonc.parse("true").getOrThrow() shouldBe ConfigValue.Bool(true)
        }

        should("parse false") {
            ConfigValueJsonc.parse("false").getOrThrow() shouldBe ConfigValue.Bool(false)
        }
    }

    context("null value") {
        should("parse null") {
            ConfigValueJsonc.parse("null").getOrThrow() shouldBe ConfigValue.Null
        }
    }

    context("objects") {
        should("parse an empty object") {
            val result = ConfigValueJsonc.parse("{}").getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            result.value.get("any", ConfigValue.Str::class.java) shouldBe Property.Missing
        }

        should("parse an object with a single string value") {
            val result = ConfigValueJsonc.parse("""{"key": "value"}""").getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            result.value.get("key", ConfigValue.Str::class.java) shouldBe Property.Val(ConfigValue.Str("value"))
        }

        should("parse an object with multiple value types") {
            val result = ConfigValueJsonc
                .parse("""{"s": "hello", "n": 42, "b": true, "d": 1.5, "nil": null}""")
                .getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            val props = result.value
            props.get("s", ConfigValue.Str::class.java) shouldBe Property.Val(ConfigValue.Str("hello"))
            props.get("n", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(42))
            props.get("b", ConfigValue.Bool::class.java) shouldBe Property.Val(ConfigValue.Bool(true))
            props.get("d", ConfigValue.Dbl::class.java) shouldBe Property.Val(ConfigValue.Dbl(1.5))
            props.get("nil", ConfigValue.Null::class.java) shouldBe Property.Val(ConfigValue.Null)
        }

        should("parse nested objects") {
            val result = ConfigValueJsonc.parse("""{"outer": {"inner": "deep"}}""").getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            val outer = result.value.get("outer", ConfigValue.Obj::class.java)
            outer.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()
            outer.value.value.get("inner", ConfigValue.Str::class.java) shouldBe Property.Val(ConfigValue.Str("deep"))
        }

        should("overwrite duplicate keys keeping the last value") {
            val result = ConfigValueJsonc.parse("""{"a": 1, "a": 2}""").getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            result.value.get("a", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(2))
        }

        should("return Missing for absent keys") {
            val result = ConfigValueJsonc.parse("""{"a": 1}""").getOrThrow() as ConfigValue.Obj
            result.value.get("b", ConfigValue.Str::class.java) shouldBe Property.Missing
        }

        should("return TypeMismatch for wrong value type") {
            val result = ConfigValueJsonc.parse("""{"a": 1}""").getOrThrow() as ConfigValue.Obj
            result.value.get("a", ConfigValue.Str::class.java) shouldBe Property.TypeMismatch(ConfigValue.Num(1))
        }

        should("reject an unterminated object") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("""{"a": 1""").getOrThrow()
            }.message shouldBe "Unterminated object (line 1, column 8)"
        }

        should("reject a non-string key") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("""{42: "value"}""").getOrThrow()
            }.message shouldBe "Expected string key, got '4' (line 1, column 2)"
        }

        should("reject a missing colon") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("""{"a" 1}""").getOrThrow()
            }.message shouldBe "Expected ':' (line 1, column 6)"
        }
    }

    context("arrays") {
        should("parse an empty array as StrArr with empty list") {
            val result = ConfigValueJsonc.parse("[]").getOrThrow()
            result shouldBe ConfigValue.StrArr(emptyList())
        }

        should("parse a string array as StrArr") {
            val result = ConfigValueJsonc.parse("""["a", "b", "c"]""").getOrThrow()
            result shouldBe ConfigValue.StrArr(listOf("a", "b", "c"))
        }

        should("parse an integer array as NumArr") {
            val result = ConfigValueJsonc.parse("[1, 2, 3]").getOrThrow()
            result shouldBe ConfigValue.NumArr(listOf(1L, 2L, 3L))
        }

        should("parse an object array as ObjArr") {
            val result = ConfigValueJsonc.parse("""[{"x": 1}, {"x": 2}]""").getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.ObjArr>()
            val arr = result.value
            arr shouldHaveSize 2
            arr[0].get("x", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(1))
            arr[1].get("x", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(2))
        }

        should("parse a boolean array as BoolArr") {
            val result = ConfigValueJsonc.parse("[true, false, true]").getOrThrow()
            result shouldBe ConfigValue.BoolArr(listOf(true, false, true))
        }

        should("parse a double array as DblArr") {
            val result = ConfigValueJsonc.parse("[1.5, 2.5]").getOrThrow()
            result shouldBe ConfigValue.DblArr(listOf(1.5, 2.5))
        }

        should("parse a null array as NullArr") {
            val result = ConfigValueJsonc.parse("[null, null]").getOrThrow()
            result shouldBe ConfigValue.NullArr(listOf(ConfigValue.Null, ConfigValue.Null))
        }

        should("reject a mixed-type array") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("""[1, "two", true, null]""").getOrThrow()
            }.message shouldBe "Mixed array element types (line 1, column 23)"
        }

        should("parse a single-element string array as StrArr") {
            val result = ConfigValueJsonc.parse("""["only"]""").getOrThrow()
            result shouldBe ConfigValue.StrArr(listOf("only"))
        }

        should("reject nested arrays") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("[[1, 2], [3, 4]]").getOrThrow()
            }.message shouldBe "Mixed array element types (line 1, column 17)"
        }

        should("reject an unterminated array") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("[1, 2").getOrThrow()
            }.message shouldBe "Unterminated array (line 1, column 6)"
        }
    }

    context("JSONC features") {
        should("skip single-line comments before a value") {
            val result = ConfigValueJsonc
                .parse(
                    """
                    // this is a comment
                    "hello"
                    """
                        .trimIndent(),
                )
                .getOrThrow()
            result shouldBe ConfigValue.Str("hello")
        }

        should("skip single-line comments after a value in objects") {
            val result = ConfigValueJsonc
                .parse(
                    """
                    {
                      // comment before key
                      "a": 1, // inline comment
                      "b": 2
                    }
                    """
                        .trimIndent(),
                )
                .getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            result.value.get("a", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(1))
            result.value.get("b", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(2))
        }

        should("skip block comments") {
            val result = ConfigValueJsonc
                .parse(
                    """
                    /* block comment */
                    {"key": /* inline */ "value"}
                    """
                        .trimIndent(),
                )
                .getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            result.value.get("key", ConfigValue.Str::class.java) shouldBe Property.Val(ConfigValue.Str("value"))
        }

        should("skip multi-line block comments") {
            val result = ConfigValueJsonc
                .parse(
                    """
                    {
                      /*
                       * multi-line
                       * block comment
                       */
                      "a": 1
                    }
                    """
                        .trimIndent(),
                )
                .getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            result.value.get("a", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(1))
        }

        should("allow trailing comma in objects") {
            val result = ConfigValueJsonc.parse("""{"a": 1, "b": 2,}""").getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            result.value.get("a", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(1))
            result.value.get("b", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(2))
        }

        should("allow trailing comma in arrays") {
            val result = ConfigValueJsonc.parse("""[1, 2, 3,]""").getOrThrow()
            result shouldBe ConfigValue.NumArr(listOf(1L, 2L, 3L))
        }

        should("reject unterminated block comments") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("/* never closed").getOrThrow()
            }.message shouldBe "Unterminated block comment (line 1, column 15)"
        }

        should("not treat comment syntax inside strings as comments") {
            val result = ConfigValueJsonc.parse("""{"url": "http://example.com"}""").getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            result.value.get("url", ConfigValue.Str::class.java) shouldBe
                Property.Val(ConfigValue.Str("http://example.com"))
        }

        should("handle a single-line comment at end of input without trailing newline") {
            val result = ConfigValueJsonc.parse("42 // trailing").getOrThrow()
            result shouldBe ConfigValue.Num(42)
        }
    }

    context("whitespace handling") {
        should("parse a value surrounded by whitespace") {
            val result = ConfigValueJsonc.parse("  \t\n\r  42  \n  ").getOrThrow()
            result shouldBe ConfigValue.Num(42)
        }

        should("parse an object with varied whitespace") {
            val result = ConfigValueJsonc.parse("  { \n \"a\" \t : \r\n 1 \n } \n").getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            result.value.get("a", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(1))
        }
    }

    context("nesting depth") {
        should("parse structures nested exactly at the maximum depth") {
            val json = "{\"a\":".repeat(ConfigValueJsonc.MAX_DEPTH) + "1" + "}".repeat(ConfigValueJsonc.MAX_DEPTH)
            val result = ConfigValueJsonc.parse(json).getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
        }

        should("reject objects nested beyond the maximum depth") {
            val json = "{\"a\":".repeat(ConfigValueJsonc.MAX_DEPTH + 1) +
                "1" +
                "}".repeat(ConfigValueJsonc.MAX_DEPTH + 1)
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse(json).getOrThrow()
            }.message shouldBe
                "Nesting exceeds ${ConfigValueJsonc.MAX_DEPTH} levels (line 1, column ${ConfigValueJsonc.MAX_DEPTH * 5 +
                    1})"
        }

        should("reject arrays nested beyond the maximum depth") {
            val json = "[".repeat(ConfigValueJsonc.MAX_DEPTH + 1) + "1" + "]".repeat(ConfigValueJsonc.MAX_DEPTH + 1)
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse(json).getOrThrow()
            }.message shouldBe
                "Nesting exceeds ${ConfigValueJsonc.MAX_DEPTH} levels (line 1, column ${ConfigValueJsonc.MAX_DEPTH +
                    1})"
        }

        should("track depth independently for parallel branches") {
            val inner = "{\"x\":1}"
            val json = """{"a": $inner, "b": $inner}"""
            val result = ConfigValueJsonc.parse(json).getOrThrow()
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
                """
                .trimIndent()
            val result = ConfigValueJsonc.parse(json).getOrThrow()
            result.shouldBeInstanceOf<ConfigValue.Obj>()
            val root = result.value

            root.get("\$schema", ConfigValue.Str::class.java) shouldBe
                Property.Val(ConfigValue.Str("https://example.com/wrasse.schema.json"))

            val exclude = root.get("exclude", ConfigValue.StrArr::class.java)
            exclude.shouldBeInstanceOf<Property.Val<ConfigValue.StrArr>>()
            exclude.value.value shouldBe listOf("**/build/**", "**/generated/**")

            val rules = root.get("rules", ConfigValue.Obj::class.java)
            rules.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()

            val noSemicolons = rules.value.value.get("no-semicolons", ConfigValue.Obj::class.java)
            noSemicolons.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()
            noSemicolons.value.value.get("enabled", ConfigValue.Bool::class.java) shouldBe
                Property.Val(ConfigValue.Bool(true))
            noSemicolons.value.value.get("severity", ConfigValue.Str::class.java) shouldBe
                Property.Val(ConfigValue.Str("error"))

            val maxLineLength = rules.value.value.get("max-line-length", ConfigValue.Obj::class.java)
            maxLineLength.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()
            val config = maxLineLength.value.value.get("config", ConfigValue.Obj::class.java)
            config.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()
            config.value.value.get("maxLength", ConfigValue.Num::class.java) shouldBe Property.Val(ConfigValue.Num(120))

            val format = root.get("format", ConfigValue.Obj::class.java)
            format.shouldBeInstanceOf<Property.Val<ConfigValue.Obj>>()
            format.value.value.get("outputDir", ConfigValue.Str::class.java) shouldBe
                Property.Val(ConfigValue.Str(".wrasse-format"))
        }
    }

    context("error cases") {
        should("reject empty input") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("").getOrThrow()
            }.message shouldBe "Empty input (line 1, column 1)"
        }

        should("reject whitespace-only input") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("   \n\t  ").getOrThrow()
            }.message shouldBe "Empty input (line 2, column 4)"
        }

        should("reject trailing content after a valid value") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("42 24").getOrThrow()
            }.message shouldBe "Unexpected trailing content (line 1, column 4)"
        }

        should("reject an unexpected character at root level") {
            shouldThrow<IllegalArgumentException> {
                ConfigValueJsonc.parse("@").getOrThrow()
            }.message shouldBe "Unexpected character '@' (line 1, column 1)"
        }
    }
})

// fixture-option: trailing-newline
// fixture-aux-file: aux/Stubs.kt
// fixture-aux-file: aux/Kotest.kt
// fixture-aux-file: aux/KotestCollections.kt
// fixture-aux-file: aux/KotestNulls.kt
// fixture-aux-file: aux/KotestTypes.kt
// fixture-aux-file: aux/KotestAssertions.kt
// fixture-aux-file: aux/KotestThrowables.kt
// fixture-aux-file: aux/lang/ConfigValueJsonc.kt
// fixture-aux-file: aux/lang/FileEdits.kt
// fixture-aux-file: aux/lang/FileWalkUp.kt
// fixture-aux-file: aux/lang/FormatRequest.kt
// fixture-aux-file: aux/lang/HexEncoding.kt
// fixture-aux-file: aux/lang/PerfRecorder.kt
// fixture-aux-file: aux/lang/SafeProperties.kt
// fixture-aux-file: aux/lang/Sha256.kt
// fixture-aux-file: aux/lang/StringSlice.kt
// fixture-aux-file: aux/lang/WEdit.kt
// fixture-aux-file: aux/lang/WPatchApplier.kt
// fixture-aux-file: aux/lang/WPatchReader.kt
// fixture-aux-file: aux/lang/WPatchStore.kt
// fixture-aux-file: aux/lang/WPatchWriter.kt
// fixture-aux-file: aux/lang/WPerf.kt
// fixture-aux-file: aux/lang/WReport.kt
// fixture-aux-file: aux/lang/WReportReplay.kt
// fixture-aux-file: aux/lang/WReportStore.kt
// expect-clean
