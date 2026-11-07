package com.stylo.shop.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stylo.shop.R
import com.stylo.shop.core.ui.theme.statusColors

/**
 * One chip rule for the whole app (docs/ui.md `FilterChip`): unselected uses the sand fill plus
 * an `outline` border, selected flips to ink and always carries a check mark, so the state is
 * never conveyed by colour alone.
 */
@Composable
fun StyloFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        trailingIcon = trailingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

/**
 * Colour swatch: a 26dp dot inside a 48dp target, exposed as a radio button so TalkBack reads the
 * colour name and the selected state instead of an unnamed clickable box.
 */
@Composable
fun StyloSwatch(
    colorName: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = colorName },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SwatchDot(colorName)
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = swatchCheckTint(colorName),
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            SwatchDot(colorName)
        }
    }
}

@Composable
private fun SwatchDot(colorName: String) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(swatchColor(colorName))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/**
 * Size chip: 48dp target; unavailable sizes keep their label, fade to 40% and are announced with
 * their state (docs/ui.md `SizeChips`).
 */
@Composable
fun StyloSizeChip(
    size: String,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unavailable = stringResource(R.string.size_unavailable)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val container = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .alpha(if (available) 1f else 0.4f)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .then(if (selected) Modifier.background(container) else Modifier)
            .border(if (selected) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.small)
            .selectable(selected = selected, enabled = available, role = Role.RadioButton, onClick = onClick)
            .semantics { if (!available) stateDescription = unavailable }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = size,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

/** Quantity stepper: real remove/add icons with names, inside 48dp targets. */
@Composable
fun StyloQtyStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    decreaseTestTag: String? = null,
    increaseTestTag: String? = null,
) {
    val quantityLabel = stringResource(R.string.qty_value)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StepperButton(
            symbol = "\u2212",
            description = stringResource(R.string.qty_decrease),
            enabled = enabled && canDecrease,
            onClick = onDecrease,
            testTag = decreaseTestTag,
        )
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .defaultMinSize(minWidth = 28.dp)
                .semantics { contentDescription = "$quantityLabel: $quantity" },
            textAlign = TextAlign.Center,
        )
        StepperButton(
            symbol = "+",
            description = stringResource(R.string.qty_increase),
            enabled = enabled && canIncrease,
            onClick = onIncrease,
            testTag = increaseTestTag,
        )
    }
}

/** 48dp stepper key: the glyph is decoration, the surface carries the accessible name. */
@Composable
private fun StepperButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String?,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .size(48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                // The glyph is decoration; the surface above carries the spoken name.
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * Primary button that keeps its label while a request is in flight: the spinner is additive, so
 * the control never loses its accessible name or its width.
 */
@Composable
fun StyloBusyButton(
    text: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = CircleShape,
        modifier = modifier.then(
            if (testTag != null) Modifier.testTag(testTag) else Modifier,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tone roles for status pills; mirrors the table in docs/ui.md. */
enum class StyloTone { Success, Warning, Info, Indigo, Neutral }

/** Status pill: text + paired container, never colour alone. */
@Composable
fun StyloStatusBadge(
    text: String,
    tone: StyloTone,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.statusColors
    val pair = when (tone) {
        StyloTone.Success -> colors.success to colors.successContainer
        StyloTone.Warning -> colors.warning to colors.warningContainer
        StyloTone.Info -> colors.info to colors.infoContainer
        StyloTone.Indigo -> colors.indigo to colors.indigoContainer
        StyloTone.Neutral -> colors.neutral to colors.neutralContainer
    }
    Surface(
        modifier = modifier,
        color = pair.second,
        contentColor = pair.first,
        shape = CircleShape,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
