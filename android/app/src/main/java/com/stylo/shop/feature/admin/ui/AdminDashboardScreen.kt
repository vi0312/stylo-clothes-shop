package com.stylo.shop.feature.admin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stylo.shop.R
import com.stylo.shop.core.model.User

/**
 * Placeholder shell for the dashboard; the KPI/report surface lands in ticket 35 once the
 * reports API exists. It still carries the admin chrome so navigation is usable today.
 */
@Composable
fun AdminDashboardScreen(
    user: User,
    onSelectTab: (AdminTab) -> Unit,
    onOpenTaxonomy: () -> Unit,
    onLogout: () -> Unit,
) {
    AdminScaffold(
        selectedTab = AdminTab.DASHBOARD,
        title = stringResource(R.string.admin_tab_dashboard),
        onSelectTab = onSelectTab,
        onOpenTaxonomy = onOpenTaxonomy,
        onLogout = onLogout,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.signed_in_greeting, user.fullName),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.admin_dashboard_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
