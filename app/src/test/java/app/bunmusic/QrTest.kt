package app.bunmusic

import app.bunmusic.core.Qr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The QR encoder against a reference implementation.
 *
 * Every matrix below was produced by python-qrcode 8.2 with the version, error
 * level and mask pinned, then pasted here verbatim. If a single module moves —
 * a table entry, the interleaving order, one bit of the format information —
 * these comparisons fail. That is the point: a QR code that is subtly wrong is
 * still a picture of a QR code, and nothing on a screen would tell you.
 */
class QrTest {

    private class QrCase(
        val text: String,
        val ecc: Qr.Ecc,
        val mask: Int,
        val version: Int,
        val rows: List<String>,
    )

    private val reference = listOf(
        QrCase(
            "BUNMUSIC", Qr.Ecc.M, 0, 1,
            listOf(
                "111111100110001111111",
                "100000101100001000001",
                "101110100001101011101",
                "101110100110001011101",
                "101110101111101011101",
                "100000100101001000001",
                "111111101010101111111",
                "000000000000000000000",
                "101010100110100010010",
                "110011001101010100000",
                "010111111111010111111",
                "100001001101110000011",
                "001100101001001000101",
                "000000001110001100110",
                "111111100110100110011",
                "100000100000001010010",
                "101110101100101010100",
                "101110100011010000110",
                "101110101001011111101",
                "100000100001110000010",
                "111111101111011111111",
            ),
        ),
        QrCase(
            "https://github.com/Iotatg/BunMusic", Qr.Ecc.Q, 5, 4,
            listOf(
                "111111101111000010000010001111111",
                "100000101111100101001110001000001",
                "101110100100100001111000001011101",
                "101110100100011000010000001011101",
                "101110100110100100100100101011101",
                "100000100001110100001100001000001",
                "111111101010101010101010101111111",
                "000000000001101010100000100000000",
                "010000111101101001000000010000011",
                "101001011000111100110011000011100",
                "101001100110001100101100110010100",
                "101000000110111110000010110111111",
                "001011100110111001101110101000001",
                "001010011101101010001110011101011",
                "110000100011111110110011111001010",
                "000001011111001011110011010101101",
                "100101100110110011110011110110101",
                "011000010000001000001101001001101",
                "111101101101100000010001111101101",
                "001101000000000001011100011011100",
                "111000100111110111111110110101010",
                "110100000110110000001010100110100",
                "101111101111100000001000010110010",
                "101101001101000101100011111111101",
                "111000111001111101111001111110011",
                "000000001100111000010001100010111",
                "111111101001101100001101101010110",
                "100000100011000111100001100011111",
                "101110100111010000010100111111000",
                "101110100011000001101111110111101",
                "101110100111110001001001010110111",
                "100000101101101111011010001111100",
                "111111100000010000110011110100010",
            ),
        ),
        QrCase(
            "क्यू साझा", Qr.Ecc.L, 3, 2,
            listOf(
                "1111111010101000001111111",
                "1000001001111001001000001",
                "1011101011100000101011101",
                "1011101011101101001011101",
                "1011101010011101101011101",
                "1000001000110011101000001",
                "1111111010101010101111111",
                "0000000000101110000000000",
                "1111001010111011010011101",
                "1111110010101000000110011",
                "1101111011111011010101011",
                "0111110011100000000011000",
                "0101111011101101000011111",
                "0101110101011011011000011",
                "0100001100110111110100101",
                "1010010010110101011101101",
                "0010001001100010111110110",
                "0000000010000010100011101",
                "1111111000001010101010010",
                "1000001000101101100010100",
                "1011101000111011111110101",
                "1011101011111001000010111",
                "1011101011000010010001010",
                "1000001010001000000101000",
                "1111111010101101000001111",
            ),
        ),
        QrCase(
            "The quick brown fox jumps over the lazy dog. 0123456789", Qr.Ecc.H, 7, 8,
            listOf(
                "1111111010011111111111000001111011101000101111111",
                "1000001010101000001001010100010100010111101000001",
                "1011101001100101100100101011100100010001101011101",
                "1011101010010110011001110011011111100101001011101",
                "1011101010001111111010111111100111001000001011101",
                "1000001010011101001111100010110000100110001000001",
                "1111111010101010101010101010101010101010101111111",
                "0000000001001000000100100011110000110000100000000",
                "0001001001101100000111111110110000000010000111011",
                "1110110011000011100110011101010001011100010100111",
                "0100011100001100100011100101110001110000010110111",
                "0001100110010000000010101001100000110001000000111",
                "1101001110101101101000100110100000001000011110110",
                "0010100110011100010001011000011010000110110001111",
                "1101011110011111100010101010110000111001101001101",
                "0101110001111000001010100011101011001100001111011",
                "1110011000101100000010110111001101001010000011011",
                "1110000011001010010010001110010001010110111100001",
                "1000001101011101111000001011110101111110001110111",
                "1010110000011000100000100101001001101010010010101",
                "0011001000011011010111011110001000001101111000001",
                "0111100011011010011011000111111100000001000000100",
                "0000111111100001100100111110101001101101111110111",
                "0100100011010111000001100010011111011101100011011",
                "1110101011101000111011101010011101011000101010011",
                "0010100010101011111000100010111011100101100010110",
                "1010111110111011111001111111010110000111111111100",
                "0100100111100110111000010100101001111100001000011",
                "0011001011111001110100100101001111001110110011110",
                "0110000010110010110000101001100011011011011110000",
                "1011111001011111011110101001010010000000111111001",
                "0110010011101011100111100110001100100000001001100",
                "0101111101101101000101010010111001011110101000010",
                "0010000111011000011111110101101001000101010011011",
                "0101101110110000110010101001011111101001011101011",
                "0101000011011010011011110110010111000110001001000",
                "0101111011111010100001101111111101110110101001111",
                "1101100011000111110000011100100011111001110000001",
                "0100011101111100000011011110010111100101011010001",
                "0111000100001110000110000110001010101010101010101",
                "1110001110000110010010111110100110101100111110010",
                "0000000010111101111110100011111110100000100011111",
                "1111111000010101001001101011110000111010101010111",
                "1000001001111100101001100010001011111001100010101",
                "1011101001001101001011111111000100001110111110000",
                "1011101011001110111101101011000100010000001111001",
                "1011101001101100000100010011010110110100000110001",
                "1000001000100110110110000010001101001010011100000",
                "1111111000000110011011001001100110111110101010011",
            ),
        ),
    )

    @Test
    fun everyReferenceMatrixIsReproducedExactly() {
        for (case in reference) {
            val code = Qr.encode(case.text, case.ecc, mask = case.mask, forcedVersion = case.version)
            assertEquals("size for ${case.text}", case.rows.size, code.size)
            assertEquals("modules for ${case.text}", case.rows, code.rows())
        }
    }

    @Test
    fun theThreeFindersAreWhereTheStandardSays() {
        val code = Qr.encode("BUNMUSIC")
        for ((x, y) in listOf(0 to 0, code.size - 7 to 0, 0 to code.size - 7)) {
            // Outer ring dark, then light, then a 3x3 dark core.
            assertTrue("corner $x,$y", code.at(x, y))
            assertTrue("ring $x,$y", code.at(x + 1, y + 1).not())
            assertTrue("core $x,$y", code.at(x + 3, y + 3))
        }
    }

    @Test
    fun sizeFollowsTheVersion() {
        for (version in listOf(1, 2, 7, 10, 25, 40)) {
            val code = Qr.encode("A", forcedVersion = version)
            assertEquals(version * 4 + 17, code.size)
        }
    }

    @Test
    fun theSmallestVersionThatFitsIsChosen() {
        // Version 1 at M holds 14 bytes; the fifteenth byte pushes it to 2.
        assertEquals(21, Qr.encode("x".repeat(14), Qr.Ecc.M).size)
        assertEquals(25, Qr.encode("x".repeat(15), Qr.Ecc.M).size)
    }

    @Test
    fun capacitiesMatchThePublishedTable() {
        assertEquals(17, Qr.capacityBytes(1, Qr.Ecc.L))
        assertEquals(14, Qr.capacityBytes(1, Qr.Ecc.M))
        assertEquals(11, Qr.capacityBytes(1, Qr.Ecc.Q))
        assertEquals(7, Qr.capacityBytes(1, Qr.Ecc.H))
        assertEquals(213, Qr.capacityBytes(10, Qr.Ecc.M))
        assertEquals(2953, Qr.capacityBytes(40, Qr.Ecc.L))
    }

    @Test
    fun multibyteTextIsCountedInBytesNotCharacters() {
        // "क्यू" is four code points and twelve UTF-8 bytes; at H that is version 2.
        val code = Qr.encode("क्यू", Qr.Ecc.H)
        assertEquals(25, code.size)
    }

    @Test
    fun textThatCannotFitIsRefusedRatherThanTruncated() {
        try {
            Qr.encode("x".repeat(2954), Qr.Ecc.L)
            fail("expected TooLongException")
        } catch (e: Qr.TooLongException) {
            assertEquals(2954, e.bytes)
        }
    }

    @Test
    fun aForcedVersionTooSmallIsARefusalNotASilentOverflow() {
        try {
            Qr.encode("x".repeat(100), Qr.Ecc.M, forcedVersion = 1)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("too small"))
        }
    }

    @Test
    fun autoMaskAlwaysProducesAReadableLookingSymbol() {
        // Not a decode — a sanity pass that the chooser stays in range and the
        // timing row is intact, which is the part a bad mask can break.
        for (n in listOf(5, 40, 120, 400, 1200)) {
            val code = Qr.encode("q".repeat(n))
            // Between the finders only: inside them the finder pattern wins.
            for (i in 8 until code.size - 8) {
                assertEquals("timing at $i", i % 2 == 0, code.at(6, i))
            }
            assertNotNull(code.rows())
        }
    }

    @Test
    fun aVersionOneCodeHasTheRightNumberOfDarkModules() {
        // Format information carries the mask, so a different mask is a
        // different picture of the same data — not a different code.
        val a = Qr.encode("BUNMUSIC", Qr.Ecc.M, mask = 0)
        val b = Qr.encode("BUNMUSIC", Qr.Ecc.M, mask = 3)
        assertEquals(a.size, b.size)
        assertTrue(a.rows() != b.rows())
        assertEquals(a.size * a.size - a.rows().sumOf { r -> r.count { it == '0' } }, a.darkCount())
    }

    @Test
    fun aBlankStringStillProducesASymbol() {
        val code = Qr.encode("")
        assertEquals(21, code.size)
        assertNull(Qr.smallestVersion(9999, Qr.Ecc.H))
    }
}
