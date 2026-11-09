package app.bunmusic.core

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.os.Process
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The last line of defence.
 *
 * A music player that dies on a bad JSON response, a corrupt preference file or
 * a throw inside a coroutine is not a music player. Three independent things go
 * wrong in an app like this, and each needs a different answer:
 *
 *  1. **An uncaught exception on any thread.** The process is already dead — the
 *     thread cannot be resumed — but it does not have to stay dead. The crash is
 *     written down, the app is relaunched, and the listener is back where they
 *     were with a note saying what happened. A restart loop is worse than a
 *     crash, so two failures inside ninety seconds means: stop, and let the
 *     system show its own dialog.
 *  2. **A coroutine that throws.** These do not have to kill anything. A
 *     [CoroutineExceptionHandler] turns them into a log line and the UI carries
 *     on, which is exactly right for a prefetch that lost its socket.
 *  3. **A screen that throws while drawing.** [app.bunmusic.ui.ScreenBoundary]
 *     catches that one, and the rest of the app keeps working.
 *
 * Everything in here is wrapped in `try`, including the writer itself. A crash
 * handler that throws is a crash handler that turns one bug into two.
 */
object CrashGuard {

    private const val DIR = "crash"
    private const val LOG = "log.txt"
    private const val FLAG = "crash_prefs"

    /** Old entries are dropped from the front. Twenty-four is about a month of bad luck. */
    private const val MAX_ENTRIES = 24

    /** A stack trace is trimmed to this before it is written. */
    private const val MAX_STACK = 2_600

    /** Two crashes closer together than this are treated as a loop. */
    private const val LOOP_WINDOW_MS = 90_000L
    private const val LOOP_LIMIT = 2

    /** Shown in the app if it comes back within this long after a crash. */
    private const val NOTICE_MS = 30_000L

    /** Separates the fields of one entry. Never appears in a stack trace. */
    private const val SEP = '\u0001'

    /** Stands in for a line break inside a stack trace, which is one line on disk. */
    private const val BR = '\u0002'

    data class Entry(
        val at: Long,
        val thread: String,
        val where: String,
        val message: String,
        val stack: String,
    ) {
        /** One line for a list row: "NullPointerException · screen:Player". */
        val headline: String get() = "$message · $where"

        val clock: String
            get() = runCatching {
                SimpleDateFormat("d MMM, HH:mm:ss", Locale.getDefault()).format(Date(at))
            }.getOrDefault("")
    }

    private var context: Application? = null
    private var previous: Thread.UncaughtExceptionHandler? = null
    private var installed = false

    /**
     * What is on screen right now.
     *
     * Compose will not let a composable be wrapped in `try` — the compiler
     * rejects it — so a screen that throws during composition cannot be caught
     * and shown as a card. What can be done is knowing *which* screen it was: the
     * tag rides along with the crash, the app comes back on Home rather than into
     * the same page, and the stability screen names the culprit.
     */
    @Volatile
    private var screen: String? = null

    fun markScreen(tag: String) {
        screen = tag
    }

    fun currentScreen(): String? = screen

    // ------------------------------------------------------------------ install

    /**
     * Call this first, before any other initialiser, so a failure in one of them
     * is caught by the handler rather than by the launcher.
     */
    fun install(application: Application) {
        if (installed) return
        installed = true
        context = application
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handle(application, thread, throwable)
        }
    }

    /**
     * The last resort. Writes the crash, decides whether a restart is sane, and
     * only then lets the process go.
     */
    private fun handle(application: Application, thread: Thread, throwable: Throwable) {
        var restarted = false
        try {
            val where = "uncaught" + (screen?.let { " · screen:$it" } ?: "")
            val entry = summarise(throwable, thread.name, where, System.currentTimeMillis())
            append(application, entry)
            val recent = history(application).map { it.at }
            if (shouldRestart(recent, System.currentTimeMillis())) {
                markRestart(application)
                restarted = relaunch(application)
            }
        } catch (_: Throwable) {
            // Nothing left to try.
        }
        if (restarted) {
            try {
                Process.killProcess(Process.myPid())
                System.exit(10)
            } catch (_: Throwable) {
                // Already on the way out.
            }
        } else {
            // Honest failure: hand it back so the system can show its own dialog.
            try {
                previous?.uncaughtException(thread, throwable)
            } catch (_: Throwable) {
                Process.killProcess(Process.myPid())
            }
        }
    }

    /**
     * Relaunch through the alarm manager rather than `startActivity` directly.
     *
     * Since Android 10 a background process cannot simply open an activity, and a
     * dying process is about as background as it gets. An alarm targeting an
     * activity still gets through, which is why this is three lines longer than
     * the obvious version.
     */
    private fun relaunch(application: Application): Boolean {
        val intent = Intent(application, app.bunmusic.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(app.bunmusic.MainActivity.EXTRA_RECOVERED, true)
        return try {
            val pending = PendingIntent.getActivity(
                application,
                8_811,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val alarms = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarms.set(AlarmManager.RTC, System.currentTimeMillis() + 350L, pending)
            true
        } catch (_: Throwable) {
            try {
                application.startActivity(intent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    // -------------------------------------------------------------------- write

    /**
     * One daemon thread owns the log file. A coroutine that throws on Main used
     * to run the whole read-modify-write synchronously on the UI thread — an ANR
     * on a slow device — and two coroutines recording at once could interleave
     * their rewrites and lose a line.
     */
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bunmusic-crashlog").apply { isDaemon = true }
    }

    /** Record a failure that was caught and survived. Never throws. */
    fun record(throwable: Throwable, where: String) {
        val application = context ?: return
        val entry = runCatching {
            summarise(throwable, Thread.currentThread().name, where, System.currentTimeMillis())
        }.getOrElse { return }
        // On a background thread keep the synchronous path, so a startup failure
        // is on disk before the process can die. On Main, hand the disk work off.
        val onMain = runCatching { Looper.myLooper() === Looper.getMainLooper() }.getOrDefault(false)
        if (onMain) io.execute { write(application, entry) } else write(application, entry)
    }

    private fun write(application: Application, entry: Entry) {
        try {
            append(application, entry)
        } catch (_: Throwable) {
            // Recording must never be the reason something fails.
        }
    }

    /**
     * Run [block], and if it throws, log it and return null.
     *
     * Used at every startup initialiser: a phone with a corrupt preferences file
     * or no free storage should open a slightly empty app, not close.
     */
    inline fun <T> safe(where: String, block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        // CancellationException and Errors (OOM, StackOverflow) must propagate:
        // swallowing them would leave the app running with a half-initialised
        // state or a corrupt heap, which is worse than a visible crash.
        if (t is CancellationException || t is Error) throw t
        record(t, where)
        null
    }

    /** For every `launch` in a long-lived scope: a failure is a log line, not a death. */
    fun handler(tag: String): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, t -> record(t, "coroutine:$tag") }

    /**
     * Pure: the next contents of the log after adding one entry. Exposed so the
     * read-modify-write contract is testable without an Application.
     */
    internal fun appendLines(lines: List<String>, entry: Entry): List<String> =
        trim(lines + format(entry), MAX_ENTRIES)

    /**
     * The read-modify-write is synchronized so concurrent records cannot lose a
     * line, and the rewrite goes through a temp file + rename: a process killed
     * mid-write leaves the previous complete log, never a half line.
     */
    @Synchronized
    private fun append(application: Application, entry: Entry) {
        val file = logFile(application) ?: return
        val kept = appendLines(runCatching { file.readLines() }.getOrDefault(emptyList()), entry)
        val text = kept.joinToString("\n")
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                tmp.delete()
                file.writeText(text)
            }
        }
    }

    private fun logFile(application: Application): File? = runCatching {
        val dir = File(application.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        File(dir, LOG)
    }.getOrNull()

    fun history(): List<Entry> = context?.let { history(it) } ?: emptyList()

    private fun history(application: Application): List<Entry> {
        val file = logFile(application) ?: return emptyList()
        return runCatching {
            file.readLines().mapNotNull { parse(it) }
        }.getOrDefault(emptyList())
    }

    fun last(): Entry? = history().lastOrNull()

    fun count(): Int = history().size

    fun clear() {
        val application = context ?: return
        runCatching { logFile(application)?.delete() }
        runCatching { application.getSharedPreferences(FLAG, Context.MODE_PRIVATE).edit().clear().apply() }
    }

    // ------------------------------------------------------------------ restart

    private fun markRestart(application: Application) {
        runCatching {
            application.getSharedPreferences(FLAG, Context.MODE_PRIVATE).edit()
                .putLong("restartedAt", System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * "The app closed and came back" — read once, then forgotten, so the note is
     * not repeated on the next ordinary launch.
     */
    fun consumeNotice(): Boolean {
        val application = context ?: return false
        return runCatching {
            val sp = application.getSharedPreferences(FLAG, Context.MODE_PRIVATE)
            val at = sp.getLong("restartedAt", 0L)
            if (at == 0L) return false
            sp.edit().remove("restartedAt").apply()
            System.currentTimeMillis() - at < NOTICE_MS
        }.getOrDefault(false)
    }

    // -------------------------------------------------------------- pure parts

    /**
     * Whether a relaunch is the right answer.
     *
     * One crash is bad luck; two within ninety seconds is a bug that will keep
     * firing, and relaunching into it just makes the phone warm. In that case the
     * system dialog is the honest outcome.
     */
    fun shouldRestart(times: List<Long>, now: Long, windowMs: Long = LOOP_WINDOW_MS, limit: Int = LOOP_LIMIT): Boolean {
        val recent = times.count { it <= now && now - it <= windowMs }
        return recent < limit
    }

    fun summarise(
        throwable: Throwable,
        thread: String,
        where: String,
        at: Long,
        maxStack: Int = MAX_STACK,
    ): Entry {
        val stack = runCatching {
            throwable.stackTraceToString()
        }.getOrDefault("")
        val trimmed = if (stack.length > maxStack) stack.take(maxStack) + "\n…" else stack
        val message = runCatching {
            val name = throwable::class.java.simpleName.ifBlank { "Throwable" }
            val text = throwable.message?.trim().orEmpty()
            if (text.isEmpty()) name else "$name: ${text.take(160)}"
        }.getOrDefault("Throwable")
        return Entry(at, thread, where, message, trimmed)
    }

    fun format(entry: Entry): String = listOf(
        entry.at.toString(),
        cleanField(entry.thread),
        cleanField(entry.where),
        cleanField(entry.message),
        // A stack trace keeps its line breaks (as BR) but must not carry a raw
        // \r either: readLines() treats CR, LF and CRLF all as terminators, so
        // one stray \r would split this record into two unparseable lines.
        entry.stack.replace(SEP, ' ').replace('\r', ' ').replace('\n', BR),
    ).joinToString(SEP.toString())

    /** One field on one line: no field separator, no line terminator of any kind. */
    private fun cleanField(s: String): String =
        s.replace(SEP, ' ').replace('\r', ' ').replace('\n', ' ')

    fun parse(line: String): Entry? {
        if (line.isBlank()) return null
        val p = line.split(SEP)
        if (p.size < 5) return null
        val at = p[0].toLongOrNull() ?: return null
        return Entry(
            at = at,
            thread = p[1],
            where = p[2],
            message = p[3],
            stack = p[4].replace(BR, '\n'),
        )
    }

    /** Keep the newest [max], drop the rest from the front. */
    fun trim(lines: List<String>, max: Int): List<String> {
        if (lines.size <= max) return lines
        return lines.subList(lines.size - max, lines.size)
    }

    /** For the share sheet and the stability screen. */
    fun report(): String {
        val entries = history()
        if (entries.isEmpty()) return "No crashes recorded."
        val device = runCatching {
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        }.getOrDefault("unknown device")
        return buildString {
            appendLine("BunMusic ${AppVersion.LABEL}")
            appendLine(device)
            appendLine("${entries.size} recorded problem(s)")
            appendLine()
            entries.takeLast(6).reversed().forEach { e ->
                appendLine("${e.clock} · ${e.thread}")
                appendLine("  ${e.message}")
                e.stack.lineSequence().take(6).forEach { appendLine("    $it") }
                appendLine()
            }
        }
    }
}
