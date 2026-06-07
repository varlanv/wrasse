package com.varlanv.wrasse.lang

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.NoSuchFileException

class FileWalkUpSpec :
    BaseSpec({
        context("find") {
            should("find file in start directory") {
                useTempDir { root ->
                    val file = Files.createFile(root.resolve("wrasse.json"))
                    FileWalkUp.find(root, "wrasse.json").getOrThrow() shouldBe file.toRealPath()
                }
            }

            should("find file in parent directory") {
                useTempDir { root ->
                    val file = Files.createFile(root.resolve("wrasse.json"))
                    val child = Files.createDirectory(root.resolve("sub"))
                    FileWalkUp.find(child, "wrasse.json").getOrThrow() shouldBe file.toRealPath()
                }
            }

            should("find file in grandparent directory") {
                useTempDir { root ->
                    val file = Files.createFile(root.resolve("wrasse.json"))
                    val child = Files.createDirectories(root.resolve("a/b"))
                    FileWalkUp.find(child, "wrasse.json").getOrThrow() shouldBe file.toRealPath()
                }
            }

            should("return nearest match when file exists at multiple levels") {
                useTempDir { root ->
                    Files.createFile(root.resolve("wrasse.json"))
                    val child = Files.createDirectory(root.resolve("sub"))
                    val nearer = Files.createFile(child.resolve("wrasse.json"))
                    FileWalkUp.find(child, "wrasse.json").getOrThrow() shouldBe nearer.toRealPath()
                }
            }

            should("return null when file does not exist anywhere") {
                useTempDir { root ->
                    val child = Files.createDirectories(root.resolve("a/b/c"))
                    FileWalkUp.find(child, "wrasse.json").getOrThrow().shouldBeNull()
                }
            }

            should("not match directories with the target name") {
                useTempDir { root ->
                    Files.createDirectory(root.resolve("wrasse.json"))
                    FileWalkUp.find(root, "wrasse.json").getOrThrow().shouldBeNull()
                }
            }

            should("return failure for nonexistent start directory") {
                useTempDir { root ->
                    val noSuchDir = root.resolve("does-not-exist")
                    val result = FileWalkUp.find(noSuchDir, "wrasse.json")
                    val ex = result.exceptionOrNull()
                    ex.shouldBeInstanceOf<NoSuchFileException>()
                    ex.message shouldBe noSuchDir.toString()
                }
            }

            should("follow symlinks in start directory") {
                useTempDir { root ->
                    val real = Files.createDirectories(root.resolve("real"))
                    Files.createFile(real.resolve("wrasse.json"))
                    val link = Files.createSymbolicLink(root.resolve("link"), real)
                    FileWalkUp.find(link, "wrasse.json").getOrThrow() shouldBe real.resolve("wrasse.json").toRealPath()
                }
            }
        }
    })
