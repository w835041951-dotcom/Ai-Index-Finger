package com.aiindexfinger

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingStringLiteralTest {
    @Test
    fun `production Kotlin has no hardcoded text in user facing sinks`() {
        val sourceRoot = File("src/main/kotlin")
        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    val fixedText = line
                        .replace(Regex("""\$\{[^}]+}"""), "")
                        .replace(Regex("""\$[A-Za-z_]\w*"""), "")
                    if (USER_FACING_LITERAL_PATTERNS.any { it.containsMatchIn(fixedText) }) {
                        "${file.relativeTo(sourceRoot).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(
            "Hardcoded user-facing text must use string resources:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `scanner recognizes user facing literals without flagging resource calls`() {
        val violations = listOf(
            "Text(\"Visible\")",
            "Text(\"用户可见\")",
            "contentDescription = \"Open settings\"",
            ".setContentTitle(\"Running\")",
            "setText(\"Stop\")",
        )
        val allowed = listOf(
            "Text(stringResource(R.string.title))",
            "setText(R.string.stop)",
            ".setContentTitle(getString(R.string.running))",
            "contentDescription = stringResource(R.string.open_settings)",
            "Log.d(\"Tag\", \"debug only\")",
        )

        assertTrue(violations.all { line -> USER_FACING_LITERAL_PATTERNS.any { it.containsMatchIn(line) } })
        assertTrue(allowed.none { line -> USER_FACING_LITERAL_PATTERNS.any { it.containsMatchIn(line) } })
    }

    private companion object {
        val USER_FACING_LITERAL_PATTERNS = listOf(
            Regex("""\bText\(\s*"[^"]*[A-Za-z\p{IsHan}][^"]*"""),
            Regex("""\bsetText\(\s*"[^"]*[A-Za-z\p{IsHan}][^"]*"""),
            Regex("""\bcontentDescription\s*=\s*"[^"]*[A-Za-z\p{IsHan}][^"]*"""),
            Regex("""\.setContent(?:Title|Text)\(\s*"[^"]*[A-Za-z\p{IsHan}][^"]*"""),
        )
    }
}
