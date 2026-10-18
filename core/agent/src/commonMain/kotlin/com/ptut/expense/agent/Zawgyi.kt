/*
 * Ported to Kotlin from google/myanmar-tools, clients/java (ZawgyiUnicodeMarkovModel, BinaryMarkov,
 * TransliterateZ2U, Phase, Rule). Changed: Java to Kotlin, one file, no debug output.
 *
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ptut.expense.agent

import kotlin.math.exp

/**
 * #205: is this Burmese text Zawgyi or Unicode? Two Markov chains over Myanmar code points, read
 * from `files/zawgyi_unicode_model.dat`, the model myanmar-tools ships.
 *
 * Zawgyi reuses Unicode's Myanmar code points for other letters, so the same bytes read as two
 * different words. No font tells the app which one a person typed; this model does.
 */
class ZawgyiDetector(model: ByteArray) {
    private val ssv: Int
    private val differences: Array<FloatArray>

    init {
        val input = BigEndianReader(model)
        check(input.long() == MODEL_TAG) { "Not a Zawgyi model" }
        ssv = when (val version = input.int()) {
            1 -> SSV_STD_EXA_EXB_SPC
            2 -> input.int()
            else -> error("Zawgyi model version $version is not known")
        }
        check(ssv in 0 until SSV_COUNT) { "Zawgyi model SSV $ssv is not known" }
        check(input.long() == MARKOV_TAG) { "Not a Markov chain" }
        check(input.int() == MARKOV_VERSION) { "Markov chain version is not known" }
        val size = input.short()
        differences = Array(size) { FloatArray(size) }
        for (from in 0 until size) {
            var entries = input.short()
            val fallback = if (entries == 0) 0f else input.float()
            var next = -1
            for (to in 0 until size) {
                if (entries > 0 && next < to) {
                    next = input.short()
                    entries--
                }
                differences[from][to] = if (next == to) input.float() else fallback
            }
        }
    }

    /**
     * Between 0 (Unicode) and 1 (Zawgyi), or negative infinity for text with no Myanmar letters.
     * Every Myanmar code point is in the BMP, so walking chars gives the same states as code points.
     */
    fun zawgyiProbability(text: String): Double {
        var previous = 0
        var total = 0.0
        var seen = false
        for (index in 0..text.length) {
            val state = if (index == text.length) 0 else stateOf(text[index].code)
            // 0 to 0 carries no signal: neither end is Myanmar.
            if (previous != 0 || state != 0) {
                total += differences[previous][state]
                seen = true
            }
            previous = state
        }
        return if (seen) 1.0 / (1.0 + exp(total)) else Double.NEGATIVE_INFINITY
    }

    private fun stateOf(code: Int): Int = when (code) {
        in STD_CP0..STD_CP1 -> code - STD_CP0 + STD_OFFSET
        in AFT_CP0..AFT_CP1 -> code - AFT_CP0 + AFT_OFFSET
        in EXA_CP0..EXA_CP1 -> code - EXA_CP0 + EXA_OFFSET
        in EXB_CP0..EXB_CP1 -> code - EXB_CP0 + EXB_OFFSET
        in SPC_CP0..SPC_CP1 -> if (ssv == SSV_STD_EXA_EXB_SPC) code - SPC_CP0 + SPC_OFFSET else 0
        else -> 0
    }

    private companion object {
        /** "UZMODEL " and "BMARKOV " in ASCII. */
        const val MODEL_TAG = 0x555A4D4F44454C20L
        const val MARKOV_TAG = 0x424D41524B4F5620L
        const val MARKOV_VERSION = 0
        const val SSV_STD_EXA_EXB_SPC = 0
        const val SSV_COUNT = 2

        const val STD_CP0 = 0x1000
        const val STD_CP1 = 0x103F
        const val AFT_CP0 = 0x104A
        const val AFT_CP1 = 0x109F
        const val EXA_CP0 = 0xAA60
        const val EXA_CP1 = 0xAA7F
        const val EXB_CP0 = 0xA9E0
        const val EXB_CP1 = 0xA9FF
        const val SPC_CP0 = 0x2000
        const val SPC_CP1 = 0x200B

        const val STD_OFFSET = 1
        const val AFT_OFFSET = STD_OFFSET + STD_CP1 - STD_CP0 + 1
        const val EXA_OFFSET = AFT_OFFSET + AFT_CP1 - AFT_CP0 + 1
        const val EXB_OFFSET = EXA_OFFSET + EXA_CP1 - EXA_CP0 + 1
        const val SPC_OFFSET = EXB_OFFSET + EXB_CP1 - EXB_CP0 + 1
    }
}

/** Java's DataInputStream order: big-endian, a float as its IEEE bits. */
private class BigEndianReader(private val bytes: ByteArray) {
    private var at = 0

    private fun byte(): Int = bytes[at++].toInt() and BYTE_MASK

    fun short(): Int = ((byte() shl 8) or byte()).toShort().toInt()

    fun int(): Int = (byte() shl 24) or (byte() shl 16) or (byte() shl 8) or byte()

    fun long(): Long = (int().toLong() shl 32) or (int().toLong() and INT_MASK)

    fun float(): Float = Float.fromBits(int())

    private companion object {
        const val BYTE_MASK = 0xFF
        const val INT_MASK = 0xFFFFFFFFL
    }
}

/**
 * Zawgyi to Unicode: myanmar-tools' rules, compiled from ICU's `my-t-my-s0-zawgyi` transform.
 * Eight phases run in order; each walks the text once and tries every rule at each position.
 */
object ZawgyiConverter {

    fun toUnicode(text: String): String = phases.fold(text) { result, phase -> run(phase, result) }

    /** Phase.runPhase: a rule that matches moves past what it wrote, unless it asks to look again. */
    private fun run(rules: List<Rule>, input: String): String {
        val out = StringBuilder()
        var rest = input
        var atStart = true
        while (rest.isNotEmpty()) {
            var found = false
            for (rule in rules) {
                if (rule.matchOnStart && !atStart) continue
                val match = rule.regex.find(rest) ?: continue
                found = true
                val after = rest.length - match.value.length
                rest = rule.regex.replaceFirst(rest, rule.substitution)
                if (!rule.revisit) {
                    val written = rest.length - after
                    out.appendRange(rest, 0, written)
                    rest = rest.substring(written)
                }
            }
            if (!found) {
                out.append(rest[0])
                rest = rest.substring(1)
            }
            atStart = false
        }
        return out.toString()
    }

    private class Rule(
        pattern: String,
        val substitution: String,
        val matchOnStart: Boolean = false,
        val revisit: Boolean = false,
    ) {
        val regex = Regex("^$pattern")
    }

    // Phase 6's `\u000136u` is U+0001 then "36u", as in the source; U+1036 was probably meant.
    private val phases: List<List<Rule>> by lazy {
        listOf(
            // Phase 0
            listOf(
                Rule("([\u1000-\u1021])\u103A\u1064", "\u1004\u103A\u1039$1\u103B"),
                Rule("([\u1000-\u1021])\u1064", "\u1004\u103A\u1039$1"),
                Rule("\u1064", "\u1004\u103A\u1039"),
                Rule("([\u1000-\u1021])\u108B", "\u1004\u103A\u1039$1\u102D"),
                Rule("([\u1000-\u1021])\u108C", "\u1004\u103A\u1039$1\u102E"),
                Rule("([\u1000-\u1021])\u108D", "\u1004\u103A\u1039$1\u1036"),
                Rule("([\u1000-\u1021])\u103A\u1033\u108B", "\u1004\u103A\u1039$1\u103B\u102D\u102F"),
                Rule("([\u1000-\u1021])\u103A\u108B", "\u1004\u103A\u1039$1\u103B\u102D"),
                Rule("([\u1000-\u1021])\u103A\u108C", "\u1004\u103A\u1039$1\u103B\u102E"),
                Rule("([\u1000-\u1021])\u103A\u108D", "\u1004\u103A\u1039$1\u103B\u1036"),
                Rule("([\u1000-\u1021])\u103A\u108E", "$1\u103B\u102D\u1036"),
                Rule("\u108B", "\u1004\u103A\u1039\u102D"),
                Rule("\u108C", "\u1004\u103A\u1039\u102E"),
                Rule("\u108D", "\u1004\u103A\u1039\u1036"),
                Rule("\u106A", "\u1009"),
                Rule("\u106B", "\u100A"),
                Rule("\u108F", "\u1014"),
                Rule("\u1090", "\u101B"),
                Rule("\u1086", "\u103F"),
                Rule("[\u103A\u107D]", "\u103B"),
                Rule("([\u103B\u107E-\u1084])+", "\u103C"),
                Rule("\u103C*\u108A", "\u103D\u103E"),
                Rule("\u103C", "\u103D"),
                Rule("[\u103D\u1087]", "\u103E"),
                Rule("\u1088", "\u103E\u102F"),
                Rule("\u1089", "\u103E\u1030"),
                Rule("\u1033", "\u102F"),
                Rule("\u1034", "\u1030"),
                Rule("\u1039", "\u103A"),
                Rule("[\u1094\u1095]", "\u1037"),
                Rule("\u1025\u1039", "\u1009\u103A"),
                Rule("\u1025\u1061", "\u1009\u1039\u1001"),
                Rule("\u1025\u1062", "\u1009\u1039\u1002"),
                Rule("\u1025\u1065", "\u1009\u1039\u1005"),
                Rule("\u1025\u1068", "\u1009\u1039\u1007"),
                Rule("\u1025\u1076", "\u1009\u1039\u1013"),
                Rule("\u1025\u1078", "\u1009\u1039\u1015"),
                Rule("\u1025\u107A", "\u1009\u1039\u1017"),
                Rule("\u1025\u1079", "\u1009\u1039\u1016"),
                Rule("\u105A", "\u102B\u103A"),
                Rule("\u1060", "\u1039\u1000"),
                Rule("\u1061", "\u1039\u1001"),
                Rule("\u1062", "\u1039\u1002"),
                Rule("\u1063", "\u1039\u1003"),
                Rule("\u1065", "\u1039\u1005"),
                Rule("[\u1066\u1067]", "\u1039\u1006"),
                Rule("\u1068", "\u1039\u1007"),
                Rule("\u1069", "\u1039\u1008"),
                Rule("\u106C", "\u1039\u100B"),
                Rule("\u106D", "\u1039\u100C"),
                Rule("\u1070", "\u1039\u100F"),
                Rule("[\u1071\u1072]", "\u1039\u1010"),
                Rule("\u1096", "\u1039\u1010\u103D"),
                Rule("[\u1073\u1074]", "\u1039\u1011"),
                Rule("\u1075", "\u1039\u1012"),
                Rule("\u1076", "\u1039\u1013"),
                Rule("\u1077", "\u1039\u1014"),
                Rule("\u1078", "\u1039\u1015"),
                Rule("\u1079", "\u1039\u1016"),
                Rule("\u107A", "\u1039\u1017"),
                Rule("[\u107B\u1093]", "\u1039\u1018"),
                Rule("\u107C", "\u1039\u1019"),
                Rule("\u1085", "\u1039\u101C"),
                Rule("\u108E", "\u102D\u1036"),
                Rule("\u106E", "\u100D\u1039\u100D"),
                Rule("\u106F", "\u100D\u1039\u100E"),
                Rule("\u1091", "\u100F\u1039\u100D"),
                Rule("\u1092", "\u100B\u1039\u100C"),
                Rule("\u1097", "\u100B\u1039\u100B"),
                Rule("\u104E", "\u104E\u1004\u103A\u1038"),
            ),
            // Phase 1
            listOf(
                Rule("\u1040([^\u1040-\u1049])", "\u101D$1", matchOnStart = true),
                Rule("\u1044([^\u1040-\u1049])", "\u104E$1", matchOnStart = true, revisit = true),
                Rule("([^\u1040-\u1049])\u1040$", "$1\u101D"),
                Rule("([^\u1040-\u1049])\u1044$", "$1\u104E"),
                Rule("([\u102B-\u103F])\u1040([^\u1040-\u1049])", "$1\u101D$2"),
                Rule("([\u102B-\u103F])\u1044([^\u1040-\u1049])", "$1\u104E$2"),
            ),
            // Phase 2
            listOf(
                Rule("([ \u00A0\u1680\u2000-\u200D\u202F\u205F\u2060\u3000\uFEFF])\u1037", "\u1037$1"),
                Rule("([ \u00A0\u1680\u2000-\u200D\u202F\u205F\u2060\u3000\uFEFF]+)([\u102B-\u1030\u1032-\u103B\u103D\u103E])", "$2"),
                Rule("\u1037+", "\u1037"),
                Rule("\u1031+\u1004\u103A\u1039([\u1000-\u1021])", "\u1004\u103A\u1039$1\u1031"),
                Rule("\u1031+\u1037+([\u1000-\u1021])", "$1\u1031\u1037"),
                Rule("\u1031+\u103C([\u1000-\u1021])", "$1\u103C\u1031"),
                Rule("\u1031+([\u1000-\u1021])([\u103B\u103D\u103E]+)", "$1$2\u1031"),
                Rule("\u1031+([\u1000-\u102A])", "$1\u1031"),
            ),
            // Phase 3
            listOf(
                Rule("\u103B\u103A", "\u103A\u103B"),
                Rule("\u1025\u102E", "\u1026"),
                Rule("\u103A\u1037", "\u1037\u103A"),
                Rule("\u1036([\u103B-\u103E]*)([\u102B-\u1030\u1032]+)", "$1$2\u1036"),
                Rule("([\u102B\u102C\u102F\u1030])([\u102D\u102E\u1032])", "$2$1"),
                Rule("\u103C([\u1000-\u1021])", "$1\u103C"),
            ),
            // Phase 4
            listOf(
                Rule("([\u103B-\u103E])\u1039([\u1000-\u1021])", "\u1039$2$1"),
                Rule("\u103C\u103A\u1039([\u1000-\u1021])", "\u103A\u1039$1\u103C"),
                Rule("\u1036([\u103B-\u103E]+)", "$1\u1036"),
            ),
            // Phase 5
            listOf(
                Rule("([\u103C-\u103E]+)\u103B", "\u103B$1"),
                Rule("([\u103D\u103E]+)\u103C", "\u103C$1"),
                Rule("\u103E\u103D", "\u103D\u103E"),
                Rule("([\u1031]+)([\u102B-\u1030\u1032]*)\u1039([\u1000-\u1021])", "\u1039$3$1$2"),
                Rule("([\u102B-\u1030\u1032]+)\u1039([\u1000-\u1021])", "\u1039$2$1"),
                Rule("([\u103B-\u103E]*)([\u1031]+)([\u103B-\u103E]*)", "$1$3$2"),
                Rule("\u1037([\u102D-\u1030\u1032\u1036\u103B-\u103E]+)", "$1\u1037"),
                Rule("([\u102B-\u1030\u1032]+)([\u103B-\u103E]+)", "$2$1"),
                Rule("([\u1000-\u1021])([\u102B-\u1032\u1036\u103B-\u103E])\u103A([\u1000-\u1021])", "$1\u103A$2$3"),
            ),
            // Phase 6
            listOf(
                Rule("\u1005\u103B", "\u1008"),
                Rule("([\u102B-\u1032])([\u103B-\u103E])", "$2$1"),
                Rule("([\u103C-\u103E])\u103B", "\u103B$1"),
                Rule("([\u103D\u103E])\u103C", "\u103C$1"),
                Rule("\u103E\u103D", "\u103D\u103E"),
                Rule("\u1038([\u000136u\u102B-\u1030\u1032\u1037\u103A-\u103F])", "$1\u1038"),
                Rule("\u1036\u102F", "\u102F\u1036"),
            ),
            // Phase 7
            listOf(
                Rule("\u102D\u102D+", "\u102D"),
                Rule("\u102E\u102E+", "\u102E"),
                Rule("\u102F\u102F+", "\u102F"),
                Rule("\u1030\u1030+", "\u1030"),
                Rule("\u1032\u1032+", "\u1032"),
                Rule("\u1036\u1036+", "\u1036"),
                Rule("\u1037\u1037+", "\u1037"),
                Rule("\u1039\u1039+", "\u1039"),
                Rule("\u103A\u103A+", "\u103A"),
                Rule("\u103B\u103B+", "\u103B"),
                Rule("\u103C\u103C+", "\u103C"),
                Rule("\u103D\u103D+", "\u103D"),
                Rule("\u103E\u103E+", "\u103E"),
                Rule("\u102F[\u1030\u103A]", "\u102F"),
                Rule("\u102D\u102E", "\u102E"),
                Rule("([ \u00A0\u1680\u2000-\u200D\u202F\u205F\u2060\u3000\uFEFF])+([\u102B-\u1032\u1036-\u103E])", "$2"),
                Rule("\u200B+", "", matchOnStart = true),
                Rule("\u200B+$", ""),
                Rule("[ \u00A0\u1680\u2000-\u200D\u202F\u205F\u2060\u3000\uFEFF]*\u200B[ \u00A0\u1680\u2000-\u200D\u202F\u205F\u2060\u3000\uFEFF]*", "\u200B"),
            ),
        )
    }
}
