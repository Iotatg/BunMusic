package app.bunmusic.core

/**
 * A QR code, as a square grid of modules.
 *
 * Deliberately not a bitmap: the grid is what both the Compose canvas and a
 * test can read, and neither has to know how a QR is built.
 */
class QrCode(val size: Int, private val dark: BooleanArray) {

    /** True when the module at (x, y) is dark. Out of range reads as light. */
    fun at(x: Int, y: Int): Boolean =
        x in 0 until size && y in 0 until size && dark[y * size + x]

    fun darkCount(): Int = dark.count { it }

    /** Row-major string of "1"/"0", for tests and for eyeballing a code by hand. */
    fun rows(): List<String> = (0 until size).map { y ->
        buildString(size) { for (x in 0 until size) append(if (at(x, y)) '1' else '0') }
    }
}

/**
 * A QR encoder, byte mode only, versions 1–40.
 *
 * Written out rather than pulled in as a dependency for one reason: the app
 * already ships 16.8 MB and a QR library would be another half megabyte for a
 * feature that draws one square. Everything here is arithmetic, so the whole
 * thing is testable off-device — and it is, against a reference implementation.
 *
 * What it does not do: kanji mode, structured append, or micro QR. A queue code
 * is UTF-8 text, and UTF-8 goes in byte mode.
 */
object Qr {

    /** Error-correction level. Higher survives more damage and holds less. */
    enum class Ecc(internal val table: Int, internal val formatBits: Int) {
        L(0, 1),
        M(1, 0),
        Q(2, 3),
        H(3, 2),
    }

    /** Thrown when the text does not fit any version at the requested level. */
    class TooLongException(val bytes: Int, val limit: Int) :
        IllegalArgumentException("$bytes bytes do not fit in a QR code at this level (limit $limit)")

    /**
     * Encodes [text] as UTF-8.
     *
     * [mask] forces a mask pattern (0–7) instead of choosing the one with the
     * lowest penalty. Only tests pass this; the format info carries the number,
     * so a forced mask is still a valid, readable code.
     *
     * [version] forces a symbol size (1–40); 0 picks the smallest that fits.
     */
    fun encode(text: String, ecc: Ecc = Ecc.M, mask: Int = -1, forcedVersion: Int = 0): QrCode {
        val data = text.toByteArray(Charsets.UTF_8)
        val version = if (forcedVersion in 1..40) {
            require(neededBits(forcedVersion, data.size) <= dataCodewords(forcedVersion, ecc) * 8) {
                "version $forcedVersion is too small for ${data.size} bytes"
            }
            forcedVersion
        } else {
            smallestVersion(data.size, ecc)
                ?: throw TooLongException(data.size, capacityBytes(40, ecc))
        }

        val bits = BitBuffer()
        bits.append(0b0100, 4)
        bits.append(data.size, charCountBits(version))
        for (b in data) bits.append(b.toInt() and 0xFF, 8)

        val dataWords = dataCodewords(version, ecc)
        val capacityBits = dataWords * 8
        bits.append(0, minOf(4, capacityBits - bits.size))
        bits.append(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < capacityBits) {
            bits.append(pad, 8)
            pad = if (pad == 0xEC) 0x11 else 0xEC
        }

        val codewords = interleave(bits.toBytes(), version, ecc)
        val matrix = Matrix(version)
        matrix.drawFunctionPatterns()
        matrix.drawCodewords(codewords)

        val chosen = if (mask in 0..7) mask else bestMask(matrix)
        matrix.applyMask(chosen)
        matrix.drawFormat(ecc, chosen)

        return QrCode(matrix.size, matrix.modules)
    }

    /** Largest UTF-8 payload a version holds at [ecc], in bytes. */
    fun capacityBytes(version: Int, ecc: Ecc): Int =
        dataCodewords(version, ecc) - 2 - if (version >= 10) 1 else 0

    /** Smallest version that holds [bytes] bytes, or null if none does. */
    fun smallestVersion(bytes: Int, ecc: Ecc): Int? =
        (1..40).firstOrNull { neededBits(it, bytes) <= dataCodewords(it, ecc) * 8 }

    private fun neededBits(version: Int, bytes: Int) = 4 + charCountBits(version) + bytes * 8

    private fun charCountBits(version: Int) = if (version <= 9) 8 else 16

    private fun dataCodewords(version: Int, ecc: Ecc) =
        RAW_CODEWORDS[version] - ECC_PER_BLOCK[ecc.table][version] * BLOCKS[ecc.table][version]

    // ---------------------------------------------------------------- blocks

    private fun interleave(data: ByteArray, version: Int, ecc: Ecc): ByteArray {
        val table = ecc.table
        val blocks = BLOCKS[table][version]
        val eccLen = ECC_PER_BLOCK[table][version]
        val total = RAW_CODEWORDS[version]
        val shortBlocks = blocks - total % blocks
        val shortLen = total / blocks - eccLen

        val dataLens = IntArray(blocks) { if (it < shortBlocks) shortLen else shortLen + 1 }
        val payloads = ArrayList<ByteArray>(blocks)
        val eccs = ArrayList<ByteArray>(blocks)
        var at = 0
        for (b in 0 until blocks) {
            val chunk = data.copyOfRange(at, at + dataLens[b])
            at += dataLens[b]
            payloads.add(chunk)
            eccs.add(reedSolomon(chunk, eccLen))
        }

        val out = ByteArray(total)
        var o = 0
        for (i in 0..shortLen) {
            for (b in 0 until blocks) if (i < dataLens[b]) out[o++] = payloads[b][i]
        }
        for (i in 0 until eccLen) {
            for (b in 0 until blocks) out[o++] = eccs[b][i]
        }
        return out
    }

    // ------------------------------------------------------- reed–solomon

    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) EXP[i] = EXP[i - 255]
    }

    private fun mul(a: Int, b: Int): Int =
        if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]

    /** Remainder of data · x^degree divided by the generator polynomial. */
    internal fun reedSolomon(data: ByteArray, degree: Int): ByteArray {
        val gen = generator(degree)
        val rem = IntArray(degree)
        for (byte in data) {
            val factor = (byte.toInt() and 0xFF) xor rem[0]
            System.arraycopy(rem, 1, rem, 0, degree - 1)
            rem[degree - 1] = 0
            for (i in 0 until degree) rem[i] = rem[i] xor mul(gen[i], factor)
        }
        return ByteArray(degree) { rem[it].toByte() }
    }

    private fun generator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor mul(poly[j], EXP[i])
            }
            poly = next
        }
        return poly.copyOfRange(1, poly.size)
    }

    // ----------------------------------------------------------- bit buffer

    private class BitBuffer {
        private var bytes = ByteArray(64)
        var size = 0
            private set

        fun append(value: Int, len: Int) {
            for (i in len - 1 downTo 0) {
                val index = size ushr 3
                if (index >= bytes.size) bytes = bytes.copyOf(bytes.size * 2)
                if ((value ushr i) and 1 != 0) {
                    bytes[index] = (bytes[index].toInt() or (0x80 ushr (size and 7))).toByte()
                }
                size++
            }
        }

        fun toBytes(): ByteArray = bytes.copyOf((size + 7) ushr 3)
    }

    // --------------------------------------------------------------- matrix

    private class Matrix(val version: Int) {
        val size = version * 4 + 17
        val modules = BooleanArray(size * size)
        private val reserved = BooleanArray(size * size)

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y * size + x] = dark
            reserved[y * size + x] = true
        }

        private fun reservedAt(x: Int, y: Int) = reserved[y * size + x]

        fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)

            val align = ALIGN[version - 1]
            for (y in align) {
                for (x in align) {
                    if ((x == 6 && y == 6) ||
                        (x == 6 && y == align.last()) ||
                        (x == align.last() && y == 6)
                    ) continue
                    alignment(x, y)
                }
            }

            // Reserve the format strips so data placement skips them. The real
            // bits are written after the mask is known. Index 6 is skipped: that
            // is the timing line, and reserving it would erase it.
            for (i in 0 until 9) {
                if (i == 6) continue
                set(8, i, false)
                set(i, 8, false)
            }
            for (i in 0 until 8) {
                set(8, size - 1 - i, false)
                set(size - 1 - i, 8, false)
            }
            set(8, size - 8, true)

            if (version >= 7) {
                for (i in 0 until 18) {
                    val a = size - 11 + i % 3
                    val b = i / 3
                    set(a, b, false)
                    set(b, a, false)
                }
            }
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x !in 0 until size || y !in 0 until size) continue
                    val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    set(x, y, dist != 2 && dist != 4)
                }
            }
        }

        private fun alignment(cx: Int, cy: Int) {
            for (dy in -2..2) {
                for (dx in -2..2) {
                    set(cx + dx, cy + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
                }
            }
        }

        fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!reservedAt(x, y) && i < data.size * 8) {
                            val bit = (data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1
                            modules[y * size + x] = bit != 0
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (reservedAt(x, y)) continue
                    if (maskBit(mask, x, y)) {
                        val p = y * size + x
                        modules[p] = !modules[p]
                    }
                }
            }
        }

        fun drawFormat(ecc: Ecc, mask: Int) {
            val data = (ecc.formatBits shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412

            fun bit(i: Int) = ((bits ushr i) and 1) != 0

            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9..14) set(14 - i, 8, bit(i))

            for (i in 0..7) set(size - 1 - i, 8, bit(i))
            for (i in 8..14) set(8, size - 15 + i, bit(i))

            if (version >= 7) {
                var v = version
                repeat(12) { v = (v shl 1) xor ((v ushr 11) * 0x1F25) }
                val versionBits = (version shl 12) or v
                for (i in 0 until 18) {
                    val dark = ((versionBits ushr i) and 1) != 0
                    val a = size - 11 + i % 3
                    val b = i / 3
                    set(a, b, dark)
                    set(b, a, dark)
                }
            }
        }

        fun penalty(): Int {
            var score = 0

            // Rule 1 — runs of five or more.
            for (pass in 0..1) {
                for (a in 0 until size) {
                    var run = 1
                    var prev = at(a, 0, pass)
                    for (b in 1 until size) {
                        val cur = at(a, b, pass)
                        if (cur == prev) {
                            run++
                        } else {
                            if (run >= 5) score += 3 + (run - 5)
                            run = 1
                            prev = cur
                        }
                    }
                    if (run >= 5) score += 3 + (run - 5)
                }
            }

            // Rule 2 — 2×2 blocks of one colour.
            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val c = at(x, y, 0)
                    if (c == at(x + 1, y, 0) && c == at(x, y + 1, 0) && c == at(x + 1, y + 1, 0)) score += 3
                }
            }

            // Rule 3 — a 1:1:3:1:1 run with four light modules on either side.
            val finderish = booleanArrayOf(true, false, true, true, true, false, true)
            for (pass in 0..1) {
                for (a in 0 until size) {
                    for (b in 0..size - 7) {
                        var hit = true
                        for (k in 0..6) if (at(a, b + k, pass) != finderish[k]) { hit = false; break }
                        if (!hit) continue
                        if (b >= 4 && (b - 4 until b).all { !at(a, it, pass) }) score += 40
                        if (b + 11 <= size && (b + 7 until b + 11).all { !at(a, it, pass) }) score += 40
                    }
                }
            }

            // Rule 4 — distance of the dark proportion from 50%.
            val dark = modules.count { it }
            val total = size * size
            val k = (kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1
            score += k * 10
            return score
        }

        private fun at(a: Int, b: Int, transposed: Int): Boolean =
            if (transposed == 0) modules[b * size + a] else modules[a * size + b]
    }

    private fun bestMask(m: Matrix): Int {
        var best = 0
        var bestScore = Int.MAX_VALUE
        for (mask in 0..7) {
            m.applyMask(mask)
            val score = m.penalty()
            m.applyMask(mask)
            if (score < bestScore) {
                bestScore = score
                best = mask
            }
        }
        return best
    }

    private fun maskBit(mask: Int, x: Int, y: Int): Boolean = when (mask) {
        0 -> (x + y) % 2 == 0
        1 -> y % 2 == 0
        2 -> x % 3 == 0
        3 -> (x + y) % 3 == 0
        4 -> (y / 2 + x / 3) % 2 == 0
        5 -> (x * y) % 2 + (x * y) % 3 == 0
        6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
        7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
        else -> false
    }

    // ---------------------------------------------------------------- tables

    private val ECC_PER_BLOCK = arrayOf(
        intArrayOf(0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
        intArrayOf(0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
        intArrayOf(0, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
        intArrayOf(0, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
    )

    private val BLOCKS = arrayOf(
        intArrayOf(0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
        intArrayOf(0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
        intArrayOf(0, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
        intArrayOf(0, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81),
    )

    private val RAW_CODEWORDS = intArrayOf(
        0, 26, 44, 70, 100, 134, 172, 196, 242, 292, 346, 404, 466, 532, 581, 655, 733, 815,
        901, 991, 1085, 1156, 1258, 1364, 1474, 1588, 1706, 1828, 1921, 2051, 2185, 2323,
        2465, 2611, 2761, 2876, 3034, 3196, 3362, 3532, 3706,
    )

    private val ALIGN = arrayOf(
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46),
        intArrayOf(6, 28, 50),
        intArrayOf(6, 30, 54),
        intArrayOf(6, 32, 58),
        intArrayOf(6, 34, 62),
        intArrayOf(6, 26, 46, 66),
        intArrayOf(6, 26, 48, 70),
        intArrayOf(6, 26, 50, 74),
        intArrayOf(6, 30, 54, 78),
        intArrayOf(6, 30, 56, 82),
        intArrayOf(6, 30, 58, 86),
        intArrayOf(6, 34, 62, 90),
        intArrayOf(6, 28, 50, 72, 94),
        intArrayOf(6, 26, 50, 74, 98),
        intArrayOf(6, 30, 54, 78, 102),
        intArrayOf(6, 28, 54, 80, 106),
        intArrayOf(6, 32, 58, 84, 110),
        intArrayOf(6, 30, 58, 86, 114),
        intArrayOf(6, 34, 62, 90, 118),
        intArrayOf(6, 26, 50, 74, 98, 122),
        intArrayOf(6, 30, 54, 78, 102, 126),
        intArrayOf(6, 26, 52, 78, 104, 130),
        intArrayOf(6, 30, 56, 82, 108, 134),
        intArrayOf(6, 34, 60, 86, 112, 138),
        intArrayOf(6, 30, 58, 86, 114, 142),
        intArrayOf(6, 34, 62, 90, 118, 146),
        intArrayOf(6, 30, 54, 78, 102, 126, 150),
        intArrayOf(6, 24, 50, 76, 102, 128, 154),
        intArrayOf(6, 28, 54, 80, 106, 132, 158),
        intArrayOf(6, 32, 58, 84, 110, 136, 162),
        intArrayOf(6, 26, 54, 82, 110, 138, 166),
        intArrayOf(6, 30, 58, 86, 114, 142, 170),
    )
}
