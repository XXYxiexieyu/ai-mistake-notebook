package com.hiro.mistakebook.math

import java.util.Locale

/**
 * Converts the lightweight LaTeX emitted by AI models and common exponent notation
 * into Unicode that can be rendered consistently with the bundled symbol database.
 *
 * The original text is never rewritten in storage; this class is intentionally a
 * display/save normalization layer so old records remain compatible.
 */
class MathTextNormalizer(
    symbolMap: Map<String, String> = emptyMap()
) {
    private val symbolMap = symbolMap.toSortedMap(
        compareByDescending<String> { it.length }.thenBy { it }
    )

    private val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '−' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'f' to 'ᶠ',
        'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ', 'k' to 'ᵏ', 'l' to 'ˡ',
        'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ',
        't' to 'ᵗ', 'u' to 'ᵘ', 'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ',
        'z' to 'ᶻ'
    )

    private val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '−' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
        'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ',
        's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ'
    )

    fun normalize(input: String): String {
        if (input.isBlank()) return input

        var text = input
            .replace("\r\n", "\n")
            .replace("\\left.", "")
            .replace("\\right.", "")
            .replace("\\left", "")
            .replace("\\right", "")
            .replace("\\,", "")
            .replace("\\!", "")
            .replace("\\;", " ")
            .replace("\\:", " ")
            .replace("\\quad", " ")
            .replace("\\qquad", "  ")
            .replace("\\not=", "≠")
            .replace("$", "")
            .replace("\\(", "")
            .replace("\\)", "")
            .replace("\\[", "")
            .replace("\\]", "")
            .replace("\\degree", "°")
            .replace("^\\circ", "°")
            .replace("^{\\circ}", "°")

        text = replaceFractions(text)
        text = replaceRoots(text)
        text = replaceDecoratedGroups(text)
        text = replaceModifiers(text, superscripts, powerPattern, "幂")
        text = replaceModifiers(text, subscripts, subscriptPattern, "下标")

        for ((alias, value) in symbolMap) {
            text = text.replace(alias, value)
        }

        text = text
            .replace("\\cdot", "·")
            .replace("\\times", "×")
            .replace("\\div", "÷")
            .replace("\\sqrt", "√")
            .replace("\\frac", "⁄")
            .replace("\\ ", " ")
            .replace("\\{", "{")
            .replace("\\}", "}")
            .replace("\\%", "%")
            .replace("\\_", "_")
            .replace("\\&", "&")

        // Unknown formatting commands should not leak into the reading view.
        text = UNKNOWN_COMMAND.replace(text, "")
        return text
            .replace(Regex("[ \\t]+\\n"), "\n")
            .trim()
    }

    private fun replaceFractions(input: String): String {
        var text = input
        repeat(4) {
            val next = FRACTION.replace(text) { match ->
                val numerator = match.groupValues[1].trim()
                val denominator = match.groupValues[2].trim()
                "($numerator)⁄($denominator)"
            }
            if (next == text) return text
            text = next
        }
        return text
    }

    private fun replaceRoots(input: String): String {
        var text = input
        repeat(4) {
            val next = ROOT_WITH_DEGREE.replace(text) { match ->
                val degree = superscript(match.groupValues[1])
                "$degree√(${match.groupValues[2].trim()})"
            }.let { afterDegree ->
                ROOT.replace(afterDegree) { match ->
                    "√(${match.groupValues[1].trim()})"
                }
            }
            if (next == text) return text
            text = next
        }
        return text
    }

    private fun replaceDecoratedGroups(input: String): String {
        var text = input
        text = CASES.replace(text) { match ->
            val rows = match.groupValues[1]
                .replace("&", " ")
                .replace("\\\\", "；")
                .replace(Regex("\\s+"), " ")
                .trim()
            "{$rows}"
        }
        text = ALIGNED.replace(text) { match ->
            match.groupValues[1]
                .replace("&", "")
                .replace("\\\\", "\n")
                .trim()
        }
        text = VECTOR.replace(text) { match ->
            "${match.groupValues[1]}⃗"
        }
        text = OVERLINE.replace(text) { match ->
            "${match.groupValues[1]}̄"
        }
        text = HAT.replace(text) { match ->
            "${match.groupValues[1]}̂"
        }
        text = TILDE.replace(text) { match ->
            "${match.groupValues[1]}̃"
        }
        text = BOXED.replace(text) { match -> match.groupValues[1] }
        text = TEXT_GROUP.replace(text) { match -> match.groupValues[1] }
        return text
    }

    private fun replaceModifiers(
        input: String,
        mapping: Map<Char, Char>,
        pattern: Regex,
        label: String
    ): String {
        return pattern.replace(input) { match ->
            val content = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()
                .replace(" ", "")
                .replace("−", "-")
            buildString {
                content.forEach { character ->
                    val converted = mapping[character] ?: return@replace "$label($content)"
                    append(converted)
                }
            }
        }
    }

    private fun superscript(value: String): String =
        value.map { superscripts[it] ?: return value }.joinToString("")

    private fun subscript(value: String): String =
        value.map { subscripts[it] ?: return value }.joinToString("")

    companion object {
        private val FRACTION = Regex("""\\[dt]?frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}""")
        private val ROOT_WITH_DEGREE = Regex("""\\sqrt\s*\[([^{}]*)\]\s*\{([^{}]*)\}""")
        private val ROOT = Regex("""\\sqrt\s*\{([^{}]*)\}""")
        private val CASES = Regex("""\\begin\{cases\}(.*?)\\end\{cases\}""", RegexOption.DOT_MATCHES_ALL)
        private val ALIGNED = Regex("""\\begin\{(?:aligned|align\*?)\}(.*?)\\end\{(?:aligned|align\*?)\}""", RegexOption.DOT_MATCHES_ALL)
        private val VECTOR = Regex("""\\(?:vec|overrightarrow)\s*\{([^{}]*)\}""")
        private val OVERLINE = Regex("""\\(?:overline|bar)\s*\{([^{}]*)\}""")
        private val HAT = Regex("""\\hat\s*\{([^{}]*)\}""")
        private val TILDE = Regex("""\\tilde\s*\{([^{}]*)\}""")
        private val BOXED = Regex("""\\boxed\s*\{([^{}]*)\}""")
        private val TEXT_GROUP = Regex("""\\(?:text|mathrm|operatorname|mathbf|boldsymbol|mathit|mathsf|mathtt)\s*\{([^{}]*)\}""")
        private val powerPattern = Regex("""\^(?:\{([^{}]+)\}|([+\-−]?[0-9]+|[A-Za-z]|[()]))""")
        private val subscriptPattern = Regex("""_(?:\{([^{}]+)\}|([+\-−]?[0-9]+|[A-Za-z]|[()]))""")
        private val UNKNOWN_COMMAND = Regex("""\\[A-Za-z]+""")
    }
}

fun MathSymbolDocument.createNormalizer(): MathTextNormalizer =
    MathTextNormalizer(latexSymbolMap)
