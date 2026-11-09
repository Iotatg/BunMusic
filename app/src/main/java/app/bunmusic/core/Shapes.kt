package app.bunmusic.core

import kotlin.math.abs
import kotlin.math.floor

/**
 * Shape, as a setting rather than a constant.
 *
 * Material 3 Expressive's most visible change is that a corner radius is no
 * longer one number: a screen mixes round, arched and leaf-shaped containers, and
 * the difference is what tells the eye which things are the same kind of thing.
 * BunMusic drew every card with `RoundedCornerShape(14.dp)`, which is tidy and
 * completely flat — an album, a playlist and a shortcut all had the same
 * silhouette.
 *
 * Everything here is arithmetic on four fractions of the container's short side,
 * so it can be tested without a display, and so the same shape renders at 48 dp
 * in a row and 320 dp on the player.
 */
enum class BunShape(val label: String) {
    ROUND("Round"),
    SQUIRCLE("Squircle"),
    ARCH("Arch"),
    LEAF("Leaf"),
    GEM("Gem"),
    PILL("Pill"),
    CLAM("Clam"),
    BLOCK("Block");

    companion object {
        /** Unknown ids fall back rather than throw: a stored preference is user data. */
        fun byId(id: String?): BunShape =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: ROUND
    }
}

/**
 * Four corner radii as a fraction of the container's short side.
 *
 * 0 is a square corner, 0.5 is a half-circle. Values above 0.5 are meaningless —
 * Compose clamps them anyway — so [Shapes.radii] never produces one.
 */
data class CornerRadii(
    val topStart: Float,
    val topEnd: Float,
    val bottomEnd: Float,
    val bottomStart: Float,
) {
    /** The same silhouette at a real size, in pixels. */
    fun scaled(shortSide: Float): CornerRadii {
        // coerceAtLeast returns NaN unchanged; a NaN size would poison all four radii.
        val s = shortSide.finiteOr().coerceAtLeast(0f)
        return CornerRadii(topStart * s, topEnd * s, bottomEnd * s, bottomStart * s)
    }

    /** The largest of the four, which is what a preview badge wants to show. */
    val largest: Float get() = maxOf(topStart, topEnd, bottomEnd, bottomStart)

    /** Symmetric shapes are the common case; this keeps call sites honest. */
    val uniform: Boolean
        get() = topStart == topEnd && topEnd == bottomEnd && bottomEnd == bottomStart
}

object Shapes {

    /** Every shape as fractions. Read once; the table is the whole design. */
    private val TABLE: Map<BunShape, CornerRadii> = mapOf(
        // The old default, kept so an existing install looks unchanged.
        BunShape.ROUND to CornerRadii(0.18f, 0.18f, 0.18f, 0.18f),
        // A squircle: rounder than a rounded rectangle, flatter than a circle.
        BunShape.SQUIRCLE to CornerRadii(0.30f, 0.30f, 0.30f, 0.30f),
        // Arched top, nearly square feet — reads as a doorway, good for covers.
        BunShape.ARCH to CornerRadii(0.50f, 0.50f, 0.06f, 0.06f),
        // Two opposite corners: the leaf that Material's shape library added.
        BunShape.LEAF to CornerRadii(0.46f, 0.06f, 0.46f, 0.06f),
        // A cut gem: pointed top and bottom, tight sides.
        BunShape.GEM to CornerRadii(0.50f, 0.10f, 0.50f, 0.10f),
        // A capsule. On a square container this is a circle, which is the point.
        BunShape.PILL to CornerRadii(0.50f, 0.50f, 0.50f, 0.50f),
        // The leaf's mirror: square shoulders, round base.
        BunShape.CLAM to CornerRadii(0.06f, 0.06f, 0.50f, 0.50f),
        // Almost sharp. Used for the "data" surfaces that should not look soft.
        BunShape.BLOCK to CornerRadii(0.02f, 0.02f, 0.02f, 0.02f),
    )

    fun radii(shape: BunShape): CornerRadii = TABLE[shape] ?: TABLE.getValue(BunShape.ROUND)

    fun radii(shape: BunShape, shortSide: Float): CornerRadii = radii(shape).scaled(shortSide)

    /**
     * Interpolate two silhouettes.
     *
     * This is the whole of "shape morph": at t = 0 the first, at t = 1 the second,
     * and the corners travel independently so a leaf opening into a squircle looks
     * like the container relaxing rather than like two cross-fades.
     */
    fun morph(from: CornerRadii, to: CornerRadii, t: Float): CornerRadii {
        // coerceIn returns NaN unchanged; guard so a stray fraction cannot yield NaN corners.
        val k = t.finiteOr().coerceIn(0f, 1f)
        fun mix(a: Float, b: Float) = a + (b - a) * k
        return CornerRadii(
            topStart = mix(from.topStart, to.topStart),
            topEnd = mix(from.topEnd, to.topEnd),
            bottomEnd = mix(from.bottomEnd, to.bottomEnd),
            bottomStart = mix(from.bottomStart, to.bottomStart),
        )
    }

    /**
     * A shape chosen by a stable number — a track id, a row index, a collection
     * name's hash. Same input, same shape, forever; no allocation, no state.
     *
     * The pool deliberately excludes [BunShape.PILL] and [BunShape.BLOCK]: used at
     * random on covers they read as mistakes rather than variety.
     */
    private val POOL = listOf(
        BunShape.ROUND,
        BunShape.SQUIRCLE,
        BunShape.ARCH,
        BunShape.LEAF,
        BunShape.GEM,
        BunShape.CLAM,
    )

    fun pick(seed: Int): BunShape = pick(seed, POOL)

    fun pick(seed: Int, pool: List<BunShape>): BunShape {
        if (pool.isEmpty()) return BunShape.ROUND
        val h = mix(seed)
        return pool[(h and 0x7FFF_FFFF) % pool.size]
    }

    /** A cover's shape from its id: the same album is never two silhouettes. */
    fun pick(seed: String?): BunShape {
        if (seed.isNullOrEmpty()) return BunShape.ROUND
        var h = 0
        for (c in seed) h = h * 31 + c.code
        return pick(h)
    }

    /**
     * Consecutive seeds should not give consecutive shapes, or a list of covers
     * becomes stripes of the same silhouette.
     */
    fun mix(seed: Int): Int {
        var x = seed * -0x61c88647
        x = x xor (x ushr 16)
        x *= -0x7a143595
        x = x xor (x ushr 13)
        return x
    }

    /** Where a shape sits between the four canonical corners, for a slider. */
    fun sharpness(shape: BunShape): Float = (radii(shape).largest * 2f).coerceIn(0f, 1f)

    /**
     * The nearest shape to an arbitrary corner value. Used when a slider is
     * dragged: the user picks a softness and the app picks the name for it.
     */
    fun nearest(shortSideFraction: Float): BunShape {
        val target = abs(shortSideFraction).coerceIn(0f, 0.5f)
        return TABLE.entries.minByOrNull { abs(it.value.largest - target) }?.key ?: BunShape.ROUND
    }

    /** For tests and the appearance screen: every shape, in table order. */
    fun all(): List<BunShape> = BunShape.entries.toList()

    /** Rounds a fraction to the nearest hundredth — used only by the preview label. */
    fun percent(shape: BunShape): Int = floor(radii(shape).largest * 200f + 0.5f).toInt()
}
