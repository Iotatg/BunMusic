package app.bunmusic

import app.bunmusic.data.Auth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure account helpers — no Android framework involved. */
class AccountTest {

    @Test
    fun phoneNumbersNormaliseToCountryCode() {
        assertEquals("919876543210", Auth.normalizePhone("9876543210"))
        assertEquals("919876543210", Auth.normalizePhone("09876543210"))
        assertEquals("919876543210", Auth.normalizePhone("+91 98765 43210"))
        assertEquals("919876543210", Auth.normalizePhone("91-98765-43210"))
        assertEquals("442071234567", Auth.normalizePhone("442071234567"))
    }

    @Test
    fun phoneIsPrettyPrinted() {
        assertEquals("+91 98765 43210", Auth.prettyPhone("919876543210"))
        assertEquals("", Auth.prettyPhone(null))
    }

    @Test
    fun shortNumbersAreRejectedByLength() {
        assertTrue(Auth.normalizePhone("12345").length < 10)
    }
}
