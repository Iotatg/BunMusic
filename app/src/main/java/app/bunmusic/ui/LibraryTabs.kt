package app.bunmusic.ui

/**
 * Which list the Library's selection bar is allowed to operate on.
 *
 * The Saved tab (3) renders [downloads], but `shown` — the list the select-all,
 * bulk-like, bulk-queue and bulk-download actions all read from — fell through
 * to `emptyList()`. A long press therefore opened a selection bar whose count
 * was always zero and whose "all" button selected nothing. Kept as a pure
 * function so the four-way mapping can be asserted on the JVM.
 */
object LibraryTabs {
    fun <T> shownFor(
        tab: Int,
        liked: List<T>,
        recents: List<T>,
        downloads: List<T>,
    ): List<T> = when (tab) {
        1 -> liked
        2 -> recents
        3 -> downloads
        else -> emptyList()
    }
}
