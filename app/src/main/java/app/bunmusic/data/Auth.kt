package app.bunmusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Accounts for BunMusic.
 *
 * Three ways in, all of them real:
 *  - **Email** — password is stretched with PBKDF2-HMAC-SHA256 (120k rounds, per-account
 *    salt) and only the derived key is written to disk. The plain password never is.
 *  - **Phone** — a six digit one-time code with a two minute lifetime, five attempts and
 *    a 30 second resend cooldown. Codes are delivered through [OtpSender]; with no SMS
 *    gateway configured the code is surfaced in the UI so the flow stays testable.
 *  - **Google** — an ID token from Credential Manager, decoded here into a profile.
 *
 * Every account owns its own library slice: [Store] is re-scoped on sign-in, so likes,
 * playlists and history never leak between accounts on a shared device.
 */
object Auth {

    // --------------------------------------------------------------- types

    enum class Provider(val label: String) {
        EMAIL("Email"),
        PHONE("Phone"),
        GOOGLE("Google"),
    }

    data class Account(
        val id: String,
        val name: String,
        val email: String?,
        val phone: String?,
        val provider: Provider,
        val avatarUrl: String?,
        val createdAt: Long,
        val lastLoginAt: Long,
        val emailVerified: Boolean,
        val phoneVerified: Boolean,
    ) {
        val handle: String
            get() = email ?: phone?.let { maskPhone(it) } ?: "BunMusic listener"

        val initials: String
            get() {
                val parts = name.trim().split(' ').filter { it.isNotBlank() }
                return when {
                    parts.isEmpty() -> "B"
                    parts.size == 1 -> parts[0].take(1).uppercase()
                    else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
                }
            }

        val isVerified: Boolean
            get() = when (provider) {
                Provider.EMAIL -> emailVerified
                Provider.PHONE -> phoneVerified
                Provider.GOOGLE -> true
            }
    }

    /** A failure that already carries a message fit for the UI. */
    class AuthError(message: String, val kind: Kind) : Exception(message) {
        enum class Kind { INVALID, EXISTS, NOT_FOUND, WRONG_PASSWORD, RATE_LIMITED, EXPIRED, NOT_CONFIGURED, NETWORK, UNKNOWN }
    }

    data class OtpChallenge(
        val phone: String,
        val code: String,
        val expiresAt: Long,
        val resendAt: Long,
        val demo: Boolean,
    )

    /** Pluggable delivery so a real gateway can be dropped in without touching the UI. */
    interface OtpSender {
        /** Returns true when the code actually left the device. */
        fun send(phone: String, code: String): Boolean
    }

    private object NullSender : OtpSender {
        override fun send(phone: String, code: String) = false
    }

    var sender: OtpSender = NullSender

    // ------------------------------------------------------------ storage

    private const val PREFS = "bunmusic_auth"
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEY_SESSION = "session"
    private const val KEY_LAST = "last_account"
    private const val KEY_OTP = "otp"

    private const val F = '\u0001'
    private const val R = '\n'

    private const val ITERATIONS = 120_000
    private const val OTP_TTL_MS = 2 * 60_000L
    private const val OTP_COOLDOWN_MS = 30_000L
    private const val OTP_MAX_ATTEMPTS = 5

    private lateinit var sp: SharedPreferences

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _pending = MutableStateFlow<OtpChallenge?>(null)
    val pending: StateFlow<OtpChallenge?> = _pending.asStateFlow()

    private val random = SecureRandom()

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _accounts.value = readAccounts()
        val id = sp.getString(KEY_SESSION, null)
        val acc = id?.let { a -> _accounts.value.firstOrNull { it.id == a } }
        _account.value = acc
        Store.attach(acc?.id)
    }

    // ------------------------------------------------------------ session

    /** True when the person chose to browse without an account. */
    var guest: Boolean
        get() = sp.getBoolean("guest", false)
        private set(value) = sp.edit().putBoolean("guest", value).apply()

    fun continueAsGuest() {
        guest = true
        Store.attach(null)
        _account.value = null
    }

    fun signOut() {
        val current = _account.value
        if (current != null) sp.edit().putString(KEY_LAST, current.id).apply()
        sp.edit().remove(KEY_SESSION).apply()
        _account.value = null
        guest = false
        Store.attach(null)
    }

    /** Forget the current account entirely, including its library slice. */
    fun deleteAccount(): Result<Unit> = runCatching {
        val current = _account.value ?: error("Not signed in")
        val rest = readAccounts().filterNot { it.id == current.id }
        writeAccounts(rest)
        Store.dropScope(current.id)
        sp.edit().remove(KEY_SESSION).remove(KEY_LAST).remove("hash_" + current.id).apply()
        _account.value = null
        guest = false
        Store.attach(null)
    }

    fun switchTo(accountId: String): Result<Account> = runCatching {
        val acc = readAccounts().firstOrNull { it.id == accountId }
            ?: throw AuthError("That account is no longer on this device", AuthError.Kind.NOT_FOUND)
        complete(acc)
    }

    // -------------------------------------------------------------- email

    fun signUp(name: String, email: String, password: String): Result<Account> = runCatching {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        when {
            cleanName.length < 2 -> fail("Enter your name", AuthError.Kind.INVALID)
            !isEmail(cleanEmail) -> fail("That email address looks incomplete", AuthError.Kind.INVALID)
            password.length < 8 -> fail("Use at least 8 characters", AuthError.Kind.INVALID)
        }
        if (readAccounts().any { it.email?.equals(cleanEmail, true) == true }) {
            fail("An account already uses this email", AuthError.Kind.EXISTS)
        }
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val acc = Account(
            id = newId(),
            name = cleanName,
            email = cleanEmail,
            phone = null,
            provider = Provider.EMAIL,
            avatarUrl = null,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis(),
            emailVerified = true,
            phoneVerified = false,
        )
        writeAccounts(readAccounts() + acc, hash = acc.id to encodeHash(salt, hash(password, salt)))
        complete(acc)
    }

    fun signIn(email: String, password: String): Result<Account> = runCatching {
        val cleanEmail = email.trim().lowercase()
        val acc = readAccounts().firstOrNull { it.email?.equals(cleanEmail, true) == true }
            ?: fail("No account found for this email", AuthError.Kind.NOT_FOUND)
        if (acc.provider != Provider.EMAIL) {
            fail("This account uses ${acc.provider.label} sign-in", AuthError.Kind.INVALID)
        }
        val stored = hashOf(acc.id) ?: fail("This account needs a new password", AuthError.Kind.INVALID)
        if (!verify(password, stored)) fail("Wrong password — try again", AuthError.Kind.WRONG_PASSWORD)
        complete(acc)
    }

    fun changePassword(old: String, new: String): Result<Unit> = runCatching {
        val acc = _account.value ?: fail("Sign in first", AuthError.Kind.INVALID)
        val stored = hashOf(acc.id) ?: fail("No password on file", AuthError.Kind.INVALID)
        if (!verify(old, stored)) fail("Current password is wrong", AuthError.Kind.WRONG_PASSWORD)
        if (new.length < 8) fail("Use at least 8 characters", AuthError.Kind.INVALID)
        val salt = ByteArray(16).also { random.nextBytes(it) }
        writeHash(acc.id, encodeHash(salt, hash(new, salt)))
    }

    // -------------------------------------------------------------- phone

    fun requestOtp(rawPhone: String): Result<OtpChallenge> = runCatching {
        val phone = normalizePhone(rawPhone)
        if (phone.length < 10) fail("Enter a 10 digit mobile number", AuthError.Kind.INVALID)

        val now = System.currentTimeMillis()
        val last = sp.getString(KEY_OTP, null)?.split(F)?.getOrNull(4)?.toLongOrNull() ?: 0L
        if (now - last < OTP_COOLDOWN_MS && last > 0) {
            val wait = ((OTP_COOLDOWN_MS - (now - last)) / 1000) + 1
            fail("Wait ${wait}s before asking for another code", AuthError.Kind.RATE_LIMITED)
        }

        val code = (100_000 + random.nextInt(900_000)).toString()
        val challenge = OtpChallenge(
            phone = phone,
            code = code,
            expiresAt = now + OTP_TTL_MS,
            resendAt = now + OTP_COOLDOWN_MS,
            demo = !sender.send(phone, code),
        )
        sp.edit().putString(KEY_OTP, listOf(phone, code, challenge.expiresAt.toString(), "0", now.toString()).joinToString(F.toString())).apply()
        _pending.value = challenge
        challenge
    }

    fun verifyOtp(phone: String, code: String): Result<Account> = runCatching {
        val clean = normalizePhone(phone)
        val row = sp.getString(KEY_OTP, null)?.split(F) ?: fail("Request a code first", AuthError.Kind.INVALID)
        if (row.size < 5 || row[0] != clean) fail("Request a code for this number first", AuthError.Kind.INVALID)
        if (row[1] != code.trim()) {
            val attempts = (row[3].toIntOrNull() ?: 0) + 1
            sp.edit().putString(KEY_OTP, listOf(row[0], row[1], row[2], attempts.toString(), row[4]).joinToString(F.toString())).apply()
            if (attempts >= OTP_MAX_ATTEMPTS) {
                sp.edit().remove(KEY_OTP).apply()
                fail("Too many tries — request a fresh code", AuthError.Kind.RATE_LIMITED)
            }
            fail("That code is not right", AuthError.Kind.INVALID)
        }
        if (System.currentTimeMillis() > (row[2].toLongOrNull() ?: 0L)) {
            sp.edit().remove(KEY_OTP).apply()
            fail("Code expired — request a new one", AuthError.Kind.EXPIRED)
        }
        sp.edit().remove(KEY_OTP).apply()
        _pending.value = null

        val existing = readAccounts().firstOrNull { it.phone == clean }
        if (existing != null) {
            complete(existing.copy(phoneVerified = true, provider = Provider.PHONE, lastLoginAt = System.currentTimeMillis()))
        } else {
            val acc = Account(
                id = newId(),
                name = "Listener ${clean.takeLast(4)}",
                email = null,
                phone = clean,
                provider = Provider.PHONE,
                avatarUrl = null,
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis(),
                emailVerified = false,
                phoneVerified = true,
            )
            writeAccounts(readAccounts() + acc)
            complete(acc)
        }
    }

    fun cancelOtp() {
        _pending.value = null
    }

    // ------------------------------------------------------------- google

    /**
     * Called with a verified Google ID token. The payload is only used for display
     * fields — the token itself is what proves the identity.
     */
    fun signInWithGoogle(idToken: String): Result<Account> = runCatching {
        val payload = decodeJwtPayload(idToken) ?: fail("Google returned an unreadable token", AuthError.Kind.NETWORK)
        val sub = payload["sub"] ?: fail("Google did not return an account id", AuthError.Kind.NETWORK)
        val email = payload["email"]
        val name = payload["name"] ?: email?.substringBefore('@') ?: "Google user"
        val picture = payload["picture"]
        val verified = payload["email_verified"] == "true"

        val existing = readAccounts().firstOrNull { it.id == googleId(sub) }
        if (existing != null) {
            complete(existing.copy(name = name, avatarUrl = picture, lastLoginAt = System.currentTimeMillis()))
        } else {
            if (email != null && readAccounts().any { it.email?.equals(email, true) == true }) {
                // same person, different door — link the Google identity to that account
                val linked = readAccounts().first { it.email.equals(email, true) }
                complete(linked.copy(provider = Provider.GOOGLE, avatarUrl = picture, emailVerified = verified || linked.emailVerified))
            } else {
                val acc = Account(
                    id = googleId(sub),
                    name = name,
                    email = email,
                    phone = null,
                    provider = Provider.GOOGLE,
                    avatarUrl = picture,
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis(),
                    emailVerified = verified,
                    phoneVerified = false,
                )
                writeAccounts(readAccounts() + acc)
                complete(acc)
            }
        }
    }

    // ------------------------------------------------------------ profile

    fun rename(name: String): Result<Account> = runCatching {
        val acc = _account.value ?: fail("Sign in first", AuthError.Kind.INVALID)
        val clean = name.trim()
        if (clean.length < 2) fail("Enter your name", AuthError.Kind.INVALID)
        val updated = acc.copy(name = clean)
        writeAccounts(readAccounts().map { if (it.id == updated.id) updated else it })
        _account.value = updated
        updated
    }

    fun linkEmail(email: String, password: String): Result<Account> = runCatching {
        val acc = _account.value ?: fail("Sign in first", AuthError.Kind.INVALID)
        if (acc.email != null) fail("An email is already linked", AuthError.Kind.EXISTS)
        val clean = email.trim().lowercase()
        if (!isEmail(clean)) fail("That email address looks incomplete", AuthError.Kind.INVALID)
        if (password.length < 8) fail("Use at least 8 characters", AuthError.Kind.INVALID)
        if (readAccounts().any { it.email?.equals(clean, true) == true }) {
            fail("An account already uses this email", AuthError.Kind.EXISTS)
        }
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val updated = acc.copy(email = clean, emailVerified = true)
        writeAccounts(readAccounts().map { if (it.id == updated.id) updated else it }, hash = updated.id to encodeHash(salt, hash(password, salt)))
        _account.value = updated
        updated
    }

    // ------------------------------------------------------------ helpers

    private fun complete(acc: Account): Account {
        val fresh = acc.copy(lastLoginAt = System.currentTimeMillis())
        writeAccounts(readAccounts().map { if (it.id == fresh.id) fresh else it }.let { list ->
            if (list.any { it.id == fresh.id }) list else list + fresh
        })
        sp.edit().putString(KEY_SESSION, fresh.id).putString(KEY_LAST, fresh.id).remove("guest").apply()
        _account.value = fresh
        Store.attach(fresh.id)
        return fresh
    }

    private fun fail(message: String, kind: AuthError.Kind): Nothing = throw AuthError(message, kind)

    private fun isEmail(value: String): Boolean =
        value.length in 5..160 && Regex("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]{2,}$").matches(value)

    private fun newId(): String {
        val bytes = ByteArray(9).also { random.nextBytes(it) }
        return "u" + bytes.joinToString("") { "%02x".format(it) }
    }

    private fun googleId(sub: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(sub.toByteArray())
        return "g" + digest.take(9).joinToString("") { "%02x".format(it) }
    }

    fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
            digits.length == 12 && digits.startsWith("91") -> digits
            digits.length in 11..15 -> digits
            else -> digits
        }
    }

    fun prettyPhone(phone: String?): String {
        if (phone == null) return ""
        return if (phone.length == 12 && phone.startsWith("91")) "+91 " + phone.drop(2).chunked(5).joinToString(" ") else "+$phone"
    }

    private fun maskPhone(phone: String) = "•••• " + phone.takeLast(4)

    // ------------------------------------------------------------- crypto

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun encodeHash(salt: ByteArray, key: ByteArray): String {
        val enc = Base64.getEncoder()
        return "pbkdf2$F$ITERATIONS$F${enc.encodeToString(salt)}$F${enc.encodeToString(key)}"
    }

    private fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(F)
        if (parts.size < 4) return false
        val salt = Base64.getDecoder().decode(parts[2])
        val expected = Base64.getDecoder().decode(parts[3])
        val actual = hash(password, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun decodeJwtPayload(token: String): Map<String, String>? {
        val segment = token.split('.').getOrNull(1) ?: return null
        val json = runCatching {
            String(Base64.getUrlDecoder().decode(segment.padEnd((segment.length + 3) / 4 * 4, '=')))
        }.getOrNull() ?: return null
        val out = LinkedHashMap<String, String>()
        Regex("\"([a-zA-Z_]+)\"\\s*:\\s*(\"([^\"]*)\"|(true|false))").findAll(json).forEach { m ->
            val key = m.groupValues[1]
            val value = if (m.groupValues[3].isNotEmpty()) m.groupValues[3] else m.groupValues[4]
            out[key] = value
        }
        return out
    }

    // -------------------------------------------------------------- codec

    private fun readAccounts(): List<Account> {
        val out = ArrayList<Account>()
        for (line in (sp.getString(KEY_ACCOUNTS, null) ?: "").split(R)) {
            if (line.isBlank()) continue
            val p = line.split(F)
            if (p.size < 10) continue
            out.add(
                Account(
                    id = p[0],
                    name = p[1],
                    email = p[2].ifBlank { null },
                    phone = p[3].ifBlank { null },
                    provider = runCatching { Provider.valueOf(p[4]) }.getOrDefault(Provider.EMAIL),
                    avatarUrl = p[5].ifBlank { null },
                    createdAt = p[6].toLongOrNull() ?: 0L,
                    lastLoginAt = p[7].toLongOrNull() ?: 0L,
                    emailVerified = p[8] == "1",
                    phoneVerified = p[9] == "1",
                )
            )
        }
        return out
    }

    private fun writeAccounts(list: List<Account>, hash: Pair<String, String>? = null) {
        val body = list.joinToString(R.toString()) { a ->
            listOf(
                a.id, a.name, a.email ?: "", a.phone ?: "", a.provider.name, a.avatarUrl ?: "",
                a.createdAt.toString(), a.lastLoginAt.toString(),
                if (a.emailVerified) "1" else "0", if (a.phoneVerified) "1" else "0",
            ).joinToString(F.toString())
        }
        val editor = sp.edit().putString(KEY_ACCOUNTS, body)
        if (hash != null) editor.putString("hash_" + hash.first, hash.second)
        editor.apply()
        _accounts.value = list.sortedByDescending { it.lastLoginAt }
    }

    private fun hashOf(id: String): String? = sp.getString("hash_$id", null)

    private fun writeHash(id: String, value: String) {
        sp.edit().putString("hash_$id", value).apply()
    }
}
