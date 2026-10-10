/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.bili.biliplus

object BvAvConverter {

    private const val TABLE = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"
    private val TR = HashMap<Char, Long>()
    private val S = intArrayOf(0, 1, 2, 9, 7, 5, 6, 4, 8, 3, 10, 11)
    private const val BASE = 58L
    private const val BV_LEN = 12
    private const val MAX = 1L shl 51
    private const val XOR = 23442827791579L
    private const val MASK = (1L shl 51) - 1

    init {
        for (i in TABLE.indices) {
            TR[TABLE[i]] = i.toLong()
        }
    }

    fun bv2av(bv: String): Long {
        val clean = bv.trim()
        var r = 0L
        for (i in 3 until BV_LEN) {
            r = r * BASE + (TR[clean[S[i]]] ?: 0L)
        }
        return (r and MASK) xor XOR
    }

    fun av2bv(av: Long): String {
        val r = CharArray(BV_LEN) { ' ' }
        r[0] = 'B'
        r[1] = 'V'
        r[2] = '1'
        var it = BV_LEN - 1
        var tmp = (av or MAX) xor XOR
        while (tmp != 0L) {
            r[S[it]] = TABLE[(tmp % BASE).toInt()]
            tmp /= BASE
            it--
        }
        return String(r)
    }

    fun extractAvId(url: String): Long? {
        val avPattern = Regex("""av(\d+)""", RegexOption.IGNORE_CASE)
        val avMatch = avPattern.find(url)
        if (avMatch != null) {
            return avMatch.groupValues[1].toLongOrNull()
        }

        val bvPattern = Regex("""(BV[\w]{10})""", RegexOption.IGNORE_CASE)
        val bvMatch = bvPattern.find(url)
        if (bvMatch != null) {
            return try {
                bv2av(bvMatch.groupValues[1])
            } catch (_: Exception) {
                null
            }
        }

        return null
    }
}
