package com.hiro.mistakebook.chemistry

import com.hiro.mistakebook.math.MathSymbolDocument

/**
 * Normalizes chemistry notation before the math normalizer runs.
 * Formula aliases are applied longest-first so CO2 is converted before CO.
 */
class ChemistryTextNormalizer(
    symbolMap: Map<String, String> = emptyMap()
) {
    private val replacements = symbolMap
        .filterKeys { it.isNotBlank() }
        .filterValues { it.isNotBlank() }
        .toSortedMap(compareByDescending<String> { it.length }.thenBy { it })
        .map { (alias, value) -> Regex(chemicalAliasPattern(alias)) to value }

    fun normalize(input: String): String {
        if (input.isBlank()) return input
        var text = stripCeWrappers(input)
        text = text
            .replace("\\rightleftharpoons", "⇌")
            .replace("\\leftrightharpoons", "⇌")
            .replace("\\longrightarrow", "→")
            .replace("\\rightarrow", "→")
            .replace("\\to", "→")
            .replace("\\uparrow", "↑")
            .replace("\\downarrow", "↓")
            .replace("\\Delta", "Δ")
            .replace("\\triangle", "Δ")

        text = X_ARROW.replace(text) { match ->
            val condition = match.groupValues[1].trim()
            if (condition.isBlank()) "→" else "→($condition)"
        }
        text = REVERSIBLE_X_ARROW.replace(text) { match ->
            val condition = match.groupValues[1].trim()
            if (condition.isBlank()) "⇌" else "⇌($condition)"
        }

        for ((pattern, value) in replacements) {
            text = pattern.replace(text) { value }
        }
        return text
    }

    private fun stripCeWrappers(input: String): String {
        var text = input
        while (true) {
            val marker = text.indexOf("\\ce")
            if (marker < 0) return text
            val open = text.indexOf('{', marker)
            if (open < 0) return text
            var depth = 0
            var close = -1
            for (index in open until text.length) {
                when (text[index]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            close = index
                            break
                        }
                    }
                }
            }
            if (close < 0) return text
            text = text.substring(0, marker) +
                text.substring(open + 1, close) +
                text.substring(close + 1)
        }
    }

    companion object {
        private val X_ARROW = Regex("""\\xrightarrow\s*\{([^{}]*)\}""")
        private val REVERSIBLE_X_ARROW = Regex("""\\xrightleftharpoons\s*\{([^{}]*)\}""")
    }
}

fun MathSymbolDocument.createChemistryNormalizer(): ChemistryTextNormalizer =
    ChemistryTextNormalizer(
        entries
            .flatMap { entry -> entry.latexAliases.map { alias -> alias to entry.insertionText } }
            .toMap()
    )

private fun chemicalAliasPattern(alias: String): String = buildString {
    if (alias.first().isLetterOrDigit()) append("(?<![A-Za-z])")
    append(Regex.escape(alias))
    if (alias.last().isLetterOrDigit()) append("(?![A-Za-z0-9])")
}
