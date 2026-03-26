package com.taobao.meta.avatar.tts

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Pre-processor specialized for Piper TTS.
 *
 * Pipeline order:
 * 1. cleanSymbols()
 * 2. normalizeNumbers()
 * 3. applyMisaProducts()
 *
 * This should only be applied right before TTS synthesis.
 */
class PiperTextPreProcessor(context: Context) {

    companion object {
        private const val TAG = "PiperPreProcessor"
        private const val ASSET_FILE = "misa_product.json"

        private val DIGITS = arrayOf(
            "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
        )

        private val PHONE_PATTERN = Regex("""(?<!\d)0\d{9}(?!\d)""")
        private val DATE_FULL_PATTERN = Regex("""(?<!\d)(\d{1,2})/(\d{1,2})/(\d{4})(?!\d)""")
        private val DATE_SHORT_PATTERN = Regex("""(?<!\d)(\d{1,2})/(\d{1,2})(?!/\d)(?!\d)""")
        private val NUMBER_PATTERN = Regex("""\d+""")

        private val ELLIPSIS_PATTERN = Regex("""[…]|\.\.\.|[?!]+""")
        // Keep "/" intact so DD/MM and DD/MM/YYYY can still be normalized later.
        private val SEPARATOR_PATTERN = Regex("""[:;\\|]""")
        private val UNWANTED_PATTERN = Regex("""[^\p{L}\p{N}\s,./]""")
        private val MULTI_COMMA = Regex(""",+""")
        private val MULTI_DOT = Regex("""\.+""")
        private val MULTI_SPACE = Regex("""\s+""")
    }

    private val productReplacements: List<Pair<String, String>>

    init {
        val merged = mutableMapOf<String, String>()
        try {
            val json = JSONObject(
                context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            )

            val rawMap = mutableMapOf<String, String>()
            json.keys().forEach { category ->
                val obj = json.getJSONObject(category)
                obj.keys().forEach { key ->
                    val normalizedKey = key.lowercase().trim()
                    if (!rawMap.containsKey(normalizedKey)) {
                        rawMap[normalizedKey] = obj.getString(key).trim()
                    }
                }
            }

            rawMap.forEach { (normalizedKey, rawValue) ->
                val keyWords = normalizedKey.split(" ").filter { it.isNotEmpty() }
                val valueTokens = rawValue.split(" ").filter { it.isNotEmpty() }

                val hyphenated = if (keyWords.size <= 1) {
                    valueTokens.joinToString("-")
                } else {
                    val groups = mutableListOf<String>()
                    var index = 0
                    for ((wordIndex, word) in keyWords.withIndex()) {
                        val syllableCount = rawMap[word]
                            ?.split(" ")
                            ?.filter { it.isNotEmpty() }
                            ?.size
                            ?: run {
                                val remaining = valueTokens.size - index
                                val wordsLeft = keyWords.size - wordIndex
                                (remaining / wordsLeft).coerceAtLeast(1)
                            }

                        val end = (index + syllableCount).coerceAtMost(valueTokens.size)
                        groups.add(valueTokens.subList(index, end).joinToString("-"))
                        index = end
                    }

                    if (index < valueTokens.size && groups.isNotEmpty()) {
                        val lastIndex = groups.lastIndex
                        groups[lastIndex] = groups[lastIndex] + "-" +
                            valueTokens.subList(index, valueTokens.size).joinToString("-")
                    }

                    groups.joinToString(" ")
                }

                merged[normalizedKey] = hyphenated
            }

            Log.i(TAG, "Loaded ${merged.size} product replacements")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $ASSET_FILE", e)
        }

        productReplacements = merged.entries
            .sortedByDescending { it.key.length }
            .map { it.key to it.value }
    }

    fun process(text: String): String {
        var normalized = text
        normalized = cleanSymbols(normalized)
        normalized = normalizeNumbers(normalized)
        normalized = applyMisaProducts(normalized)
        return normalized
    }

    private fun cleanSymbols(text: String): String {
        var normalized = text
        normalized = ELLIPSIS_PATTERN.replace(normalized, ".")
        normalized = SEPARATOR_PATTERN.replace(normalized, ",")
        normalized = UNWANTED_PATTERN.replace(normalized, " ")
        normalized = MULTI_COMMA.replace(normalized, ",")
        normalized = MULTI_DOT.replace(normalized, ".")
        normalized = MULTI_SPACE.replace(normalized, " ").trim()
        return normalized
    }

    private fun normalizeNumbers(text: String): String {
        var normalized = text

        normalized = PHONE_PATTERN.replace(normalized) { match ->
            readPhoneNumber(match.value)
        }

        normalized = DATE_FULL_PATTERN.replace(normalized) { match ->
            val day = match.groupValues[1].toIntOrNull() ?: 0
            val month = match.groupValues[2].toIntOrNull() ?: 0
            val year = match.groupValues[3].toLongOrNull() ?: 0L
            "ngày ${readNumber(day.toLong())} tháng ${readNumber(month.toLong())} năm ${readNumber(year)}"
        }

        normalized = DATE_SHORT_PATTERN.replace(normalized) { match ->
            val day = match.groupValues[1].toIntOrNull() ?: 0
            val month = match.groupValues[2].toIntOrNull() ?: 0
            "ngày ${readNumber(day.toLong())} tháng ${readNumber(month.toLong())}"
        }

        normalized = NUMBER_PATTERN.replace(normalized) { match ->
            val value = match.value
            try {
                if (value.length > 9) {
                    value.map { DIGITS[it.toString().toInt()] }.joinToString(" ")
                } else {
                    readNumber(value.toLong())
                }
            } catch (_: Exception) {
                value
            }
        }

        return normalized
    }

    private fun readPhoneNumber(phone: String): String {
        return phone.chunked(2).joinToString(" ") { pair ->
            pair.map { DIGITS[it.toString().toInt()] }.joinToString(" ")
        }
    }

    private fun applyMisaProducts(text: String): String {
        var normalized = text.lowercase()
        for ((key, value) in productReplacements) {
            normalized = Regex(
                pattern = "\\b${Regex.escape(key)}\\b",
                option = RegexOption.IGNORE_CASE
            ).replace(normalized, value)
        }
        return normalized
    }

    private fun readNumber(number: Long): String {
        if (number == 0L) return DIGITS[0]

        var remaining = number
        val result = StringBuilder()
        listOf(
            1_000_000_000L to "tỷ",
            1_000_000L to "triệu",
            1_000L to "nghìn",
        ).forEach { (divisor, unit) ->
            val quotient = remaining / divisor
            if (quotient > 0) {
                result.append(readTriple(quotient.toInt())).append(" ").append(unit).append(" ")
                remaining %= divisor
            }
        }

        if (remaining > 0) {
            result.append(readTriple(remaining.toInt()))
        }

        return result.toString().trim()
    }

    private fun readTriple(number: Int): String {
        val hundreds = number / 100
        val tens = (number % 100) / 10
        val units = number % 10
        val result = StringBuilder()

        if (hundreds > 0) {
            result.append(DIGITS[hundreds]).append(" trăm ")
            if (tens == 0 && units > 0) {
                result.append("linh ")
            }
        }

        when {
            tens > 1 -> {
                result.append(DIGITS[tens]).append(" mươi ")
                when {
                    units == 1 -> result.append("mốt ")
                    units == 5 -> result.append("lăm ")
                    units > 0 -> result.append(DIGITS[units]).append(" ")
                }
            }

            tens == 1 -> {
                result.append("mười ")
                when {
                    units == 1 -> result.append("một ")
                    units == 5 -> result.append("lăm ")
                    units > 0 -> result.append(DIGITS[units]).append(" ")
                }
            }

            tens == 0 && units > 0 -> result.append(DIGITS[units]).append(" ")
        }

        return result.toString().trimEnd()
    }
}
