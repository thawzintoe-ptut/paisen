package com.ptut.expense.design

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.ptut.expense.domain.Category
import com.ptut.expense.ui.MetaGlyph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PtutIconTest {

    /**
     * The whole reason this type replaced a map. The old one ended `else -> MoreHoriz`, so a name
     * nobody had drawn rendered as the Other glyph — no build error, no failing test, just the
     * wrong picture.
     */
    @Test
    fun anUndrawnNameThrowsRatherThanDrawingSomethingElse() {
        val thrown = assertFailsWith<IllegalStateException> { PtutIcon.named("no_such_glyph") }

        assertTrue("no_such_glyph" in thrown.message.orEmpty(), "the error does not name the glyph")
    }

    @Test
    fun everyIconAnswersToItsOwnDesignName() {
        PtutIcon.entries.forEach { icon ->
            assertEquals(icon, PtutIcon.named(icon.designName), "${icon.designName} resolves elsewhere")
        }
    }

    /**
     * A category carries its icon as a string, so this is the one place a seed can go wrong. It
     * would have thrown at the first frame that drew the chip row, not here, without this.
     */
    @Test
    fun everySeededCategoryNamesAGlyphThatExists() {
        Category.seed().forEach { category ->
            assertNotNull(PtutIcon.named(category.icon), "${category.name} names no glyph")
        }
    }

    /**
     * FR-12 lets a category be re-iconed, and the picker offers a subset rather than the whole
     * table. Anything already worn has to be in that subset: an icon the grid does not offer
     * opens it with nothing selected, which reads as "no icon yet" rather than "this one".
     */
    @Test
    fun everyIconACategoryCanAlreadyWearIsOnOffer() {
        val offered = PtutIcon.categoryChoices.map { it.icon }.toSet()
        // The seeds, plus the one a category made on this screen starts with.
        val worn = Category.seed().map { PtutIcon.named(it.icon) } + PtutIcon.Label

        worn.forEach { assertTrue(it in offered, "${it.designName} is worn but never offered") }
    }

    /**
     * A picker of fourteen made the reader pick the least wrong one. The point of the set is that
     * a category can look like what it is, which needs enough glyphs to cover what people buy.
     */
    @Test
    fun thePickerOffersEnoughGlyphsToNameWhatPeopleActuallyBuy() {
        assertEquals(40, PtutIcon.categoryChoices.size)
    }

    /** Two tiles reading the same aloud is the same bug as two drawing the same picture. */
    @Test
    fun noTwoChoicesInThePickerAreSpokenTheSameWay() {
        val spoken = PtutIcon.categoryChoices.map { it.spoken }

        assertEquals(spoken.size, spoken.distinct().size, "two choices are read out identically")
    }

    /**
     * Several names share a Heroicon — `label` and `sell` are both the tag, `delete` and
     * `auto_delete` both the bin. Two identical pictures side by side in a picker read as a bug,
     * and choosing between them is a choice with no difference.
     */
    @Test
    fun noTwoChoicesInThePickerDrawTheSamePicture() {
        val drawn = PtutIcon.categoryChoices.map { it.icon.image }

        assertEquals(drawn.size, drawn.distinct().size, "the picker offers the same glyph twice")
    }

    /**
     * A meta row carries its glyph as a string too, so the record and edit screens can name one
     * nothing draws. Without this it would throw on the frame that opened the screen, not here.
     */
    @Test
    fun everyMetaRowGlyphNamesAGlyphThatExists() {
        listOf(
            MetaGlyph.Merchant,
            MetaGlyph.Time,
            MetaGlyph.Tag,
            MetaGlyph.Source,
        ).forEach { name ->
            assertNotNull(PtutIcon.named(name), "$name names no glyph")
        }
    }

    /** The screens waiting on this slice: #10, #14, #15, #16 and the v2 split flow. */
    @Test
    fun theNamesTheOpenScreensNeedAreAllDrawn() {
        listOf(
            "payments",
            "edit",
            "wifi_off",
            "chevron_right",
            "toggle_on",
            "auto_awesome",
            "group_add",
        ).forEach { name ->
            assertNotNull(PtutIcon.named(name), "$name is still missing")
        }
    }

    /**
     * The spec's rule for the set: 24px outline, stroke 1.6, round caps and joins, and no colour
     * of its own. Checked on the artwork rather than by eye, because a glyph pasted at Heroicons'
     * own 1.5 looks right until it sits beside one at 1.6.
     */
    @Test
    fun everyGlyphIsAnOutlineAtTheSpecsWeight() {
        PtutIcon.entries.forEach { icon ->
            val paths = icon.image.root.paths()
            assertTrue(paths.isNotEmpty(), "${icon.designName} draws nothing")

            assertEquals(24f, icon.image.viewportWidth, "${icon.designName} is not a 24 viewport")
            assertEquals(24f, icon.image.viewportHeight, "${icon.designName} is not a 24 viewport")

            paths.forEach { path ->
                assertEquals(1.6f, path.strokeLineWidth, "${icon.designName} is not stroke 1.6")
                assertEquals(StrokeCap.Round, path.strokeLineCap, "${icon.designName} has square caps")
                assertEquals(StrokeJoin.Round, path.strokeLineJoin, "${icon.designName} has hard joins")
                assertEquals(null, path.fill, "${icon.designName} is filled, not an outline")
                assertNotNull(path.stroke, "${icon.designName} has no stroke to tint")
            }
        }
    }

    private fun VectorGroup.paths(): List<VectorPath> = flatMap { node: VectorNode ->
        when (node) {
            is VectorPath -> listOf(node)
            is VectorGroup -> node.paths()
        }
    }
}
