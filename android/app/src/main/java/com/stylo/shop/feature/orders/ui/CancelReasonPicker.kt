package com.stylo.shop.feature.orders.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stylo.shop.R
import com.stylo.shop.core.ui.UiTags

/**
 * The label is sent as the cancellation reason, so the admin reads the same wording the customer
 * picked. The server contract stays free text: admin cancels and older clients keep working.
 */
internal enum class CancelReasonOption(@param:StringRes val labelRes: Int) {
    WRONG_ITEM(R.string.order_cancel_reason_wrong_item),
    WRONG_VARIANT(R.string.order_cancel_reason_wrong_variant),
    WANT_OTHER_PRODUCT(R.string.order_cancel_reason_want_other),
    CHANGE_ADDRESS(R.string.order_cancel_reason_change_address),
    NO_LONGER_NEEDED(R.string.order_cancel_reason_no_longer_needed),
    OTHER(R.string.order_cancel_reason_other),
}

@Composable
internal fun CancelReasonPicker(
    selected: CancelReasonOption?,
    onSelect: (CancelReasonOption) -> Unit,
    customReason: String,
    onCustomReasonChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.order_cancel_reason_prompt),
            style = MaterialTheme.typography.titleSmall,
        )
        CancelReasonOption.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == option,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == option, onClick = null, enabled = enabled)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(option.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (selected == CancelReasonOption.OTHER) {
            OutlinedTextField(
                value = customReason,
                onValueChange = { if (it.length <= 255) onCustomReasonChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTags.OrderCancelReason),
                label = { Text(stringResource(R.string.order_cancel_reason_label)) },
                placeholder = { Text(stringResource(R.string.order_cancel_other_hint)) },
                supportingText = { Text("${customReason.length}/255") },
                minLines = 2,
                maxLines = 4,
                enabled = enabled,
            )
        }
    }
}
