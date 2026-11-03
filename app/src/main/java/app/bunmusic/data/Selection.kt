package app.bunmusic.data

/**
 * A multi-selection, and the four gestures people expect from one.
 *
 * The library screen had a `Set<String>` and a toggle. That is enough to pick
 * three songs one at a time and nothing else: no select-all, no invert, and no
 * way to pick a run of twenty without twenty taps. The anchor is the missing
 * piece — the last row you touched — and every range is "anchor to here".
 *
 * Kept as data rather than screen state so the behaviour can be tested: an
 * off-by-one in a range select is invisible until a user deletes the wrong song.
 */
data class Selection(
    val ids: Set<String> = emptySet(),
    /** The row a range starts from: the last one added. */
    val anchor: String? = null,
) {

    val count: Int get() = ids.size

    val active: Boolean get() = ids.isNotEmpty()

    fun has(id: String): Boolean = ids.contains(id)

    /** Plain toggle, as a checkbox behaves. */
    fun toggle(id: String): Selection = if (ids.contains(id)) {
        Selection(ids - id, if (anchor == id) ids.firstOrNull { it != id } else anchor)
    } else {
        Selection(ids + id, id)
    }

    /** Add without removing — used by drag-select and by "select all in group". */
    fun add(id: String): Selection = Selection(ids + id, id)

    fun remove(id: String): Selection =
        Selection(ids - id, if (anchor == id) null else anchor)

    /** Every id in [order], in order. The anchor is untouched: a range can follow. */
    fun all(order: List<String>): Selection =
        Selection(ids + order, anchor ?: order.firstOrNull())

    fun none(): Selection = Selection(emptySet(), null)

    /** Everything that was not selected becomes selected, and the other way round. */
    fun invert(order: List<String>): Selection {
        val next = order.filterNot { ids.contains(it) }.toSet()
        return Selection(next, next.firstOrNull() ?: anchor)
    }

    /**
     * Select the run from the anchor to [id], inclusive, keeping what was already
     * selected. With no anchor this is a plain toggle, which is what a first tap
     * should be.
     */
    fun toggleRange(id: String, order: List<String>): Selection {
        val start = anchor?.let { order.indexOf(it) } ?: -1
        val end = order.indexOf(id)
        if (start < 0 || end < 0) return toggle(id)
        val lo = minOf(start, end)
        val hi = maxOf(start, end)
        val run = order.subList(lo, hi + 1).toSet()
        // A second range that lands inside the current one is a deselect: otherwise
        // there is no gesture that shrinks a selection.
        val shrinking = run.all { ids.contains(it) } && run.size > 1
        return Selection(
            ids = if (shrinking) ids - run else ids + run,
            anchor = if (shrinking) id else anchor,
        )
    }

    /** The selected ids in list order, so "queue these" keeps the order on screen. */
    fun ordered(order: List<String>): List<String> = order.filter { ids.contains(it) }

    /** Selected ids that are no longer in the list — dropped after a filter change. */
    fun prune(order: List<String>): Selection {
        val live = order.toSet()
        val kept = ids.filter { live.contains(it) }.toSet()
        if (kept.size == ids.size) return this
        return Selection(kept, anchor?.takeIf { live.contains(it) })
    }

    companion object {
        fun of(id: String): Selection = Selection(setOf(id), id)
    }
}
