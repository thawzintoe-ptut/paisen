package com.ptut.expense.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Every radius in the handoff, named for its value. Material's five slots cannot express
 * fifteen steps, and a semantic name would lie the moment two components at different radii
 * shared it. Roughly: 12–16 chips and tiles, 19–24 rows and keys, 26–28 cards, 30–34 hero,
 * sheet and FAB pill.
 */
object PtutRadii {
    val r11 = RoundedCornerShape(11.dp)
    val r12 = RoundedCornerShape(12.dp)
    val r14 = RoundedCornerShape(14.dp)
    val r15 = RoundedCornerShape(15.dp)
    val r16 = RoundedCornerShape(16.dp)
    val r18 = RoundedCornerShape(18.dp)
    val r17 = RoundedCornerShape(17.dp)
    val r19 = RoundedCornerShape(19.dp)
    val r20 = RoundedCornerShape(20.dp)
    val r22 = RoundedCornerShape(22.dp)
    val r23 = RoundedCornerShape(23.dp)
    val r24 = RoundedCornerShape(24.dp)
    val r26 = RoundedCornerShape(26.dp)
    val r28 = RoundedCornerShape(28.dp)
    val r30 = RoundedCornerShape(30.dp)
    val r31 = RoundedCornerShape(31.dp)
    val r32 = RoundedCornerShape(32.dp)
    val r34 = RoundedCornerShape(34.dp)
    val r38 = RoundedCornerShape(38.dp)
}

/** Only for the Material components that demand a [Shapes]. Screens read [PtutRadii]. */
val PtutShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = PtutRadii.r12,
    large = PtutRadii.r16,
    extraLarge = PtutRadii.r28,
)
