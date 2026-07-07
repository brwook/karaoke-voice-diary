package com.konodiary.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.konodiary.app.ui.theme.BestBadgeBgDark
import com.konodiary.app.ui.theme.BestBadgeBgLight
import com.konodiary.app.ui.theme.BestBadgeFgDark
import com.konodiary.app.ui.theme.BestBadgeFgLight
import com.konodiary.app.ui.theme.StarGold

/**
 * Tone of a [StatusChip] — DESIGN.md §4.
 */
enum class ChipTone { NEUTRAL, ACTIVE, SUCCESS, ERROR, GOLD }

/**
 * Status badge/chip. Fully rounded, labelSmall text, tone-based container color.
 * DESIGN.md §4.
 */
@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val (container: Color, content: Color) = when (tone) {
        ChipTone.NEUTRAL -> scheme.surfaceVariant to scheme.onSurfaceVariant
        ChipTone.ACTIVE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ChipTone.SUCCESS -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ChipTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
        ChipTone.GOLD ->
            if (dark) BestBadgeBgDark to BestBadgeFgDark
            else BestBadgeBgLight to BestBadgeFgLight
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Star rating. When [onRatingChange] is null the row is display-only. Tapping the
 * currently-selected star resets to 0. DESIGN.md §4.
 */
@Composable
fun RatingStars(
    rating: Int,
    onRatingChange: ((Int) -> Unit)? = null,
    starSize: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    Row(modifier = modifier) {
        for (star in 1..5) {
            val filled = star <= rating
            val starModifier = if (onRatingChange != null) {
                Modifier.clickable(role = Role.Button) {
                    // Re-tapping the current value toggles it off.
                    onRatingChange(if (star == rating) 0 else star)
                }
            } else {
                Modifier
            }
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (filled) StarGold else emptyColor,
                modifier = starModifier
                    .padding(1.dp)
                    .size(starSize),
            )
        }
    }
}
