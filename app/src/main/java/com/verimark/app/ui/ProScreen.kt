package com.verimark.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.verimark.app.billing.BillingEvent
import com.verimark.app.billing.BillingProducts
import com.verimark.app.billing.BillingViewModel
import com.verimark.app.billing.EntitlementState
import com.verimark.app.billing.ProTier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onBack: () -> Unit,
    viewModel: BillingViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val entitlement by viewModel.entitlement.collectAsState()
    val products by viewModel.products.collectAsState()
    val purchasing by viewModel.purchasing.collectAsState()

    var selectedTier by remember { mutableStateOf(ProTier.YEARLY) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is BillingEvent.Message) {
                Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val priceFor = { tier: ProTier ->
        products.firstOrNull { it.productId == BillingProducts.productId(tier) }?.price.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VeriMark Pro") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                "Review more. Report better.",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Upgrade for unlimited projects and markers, plus clean, professional PDF reports.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (entitlement.isPro) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "You're on VeriMark Pro",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            FeatureRow("Unlimited projects")
            FeatureRow("Unlimited markers")
            FeatureRow("Professional PDF reports")
            FeatureRow("No VeriMark watermark")
            FeatureRow("Future Pro features")

            Spacer(Modifier.height(20.dp))

            if (entitlement is EntitlementState.BillingUnavailable) {
                Text(
                    "Google Play isn't available right now. Please check your connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
            }

            if (entitlement is EntitlementState.Free && products.isEmpty()) {
                Text(
                    "Pricing isn't available yet. Google Play products may still be being configured. Please check back soon.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }

            if (!entitlement.isPro) {
                TierCard(
                    title = "Monthly",
                    price = priceFor(ProTier.MONTHLY),
                    badge = null,
                    selected = selectedTier == ProTier.MONTHLY,
                    onClick = { selectedTier = ProTier.MONTHLY }
                )
                Spacer(Modifier.height(10.dp))
                TierCard(
                    title = "Yearly",
                    price = priceFor(ProTier.YEARLY),
                    badge = "BEST VALUE",
                    selected = selectedTier == ProTier.YEARLY,
                    onClick = { selectedTier = ProTier.YEARLY }
                )
                Spacer(Modifier.height(10.dp))
                TierCard(
                    title = "Lifetime",
                    price = priceFor(ProTier.LIFETIME),
                    badge = "ONE-TIME",
                    selected = selectedTier == ProTier.LIFETIME,
                    onClick = { selectedTier = ProTier.LIFETIME }
                )

                Spacer(Modifier.height(16.dp))

                val selectedPrice = priceFor(selectedTier)
                Button(
                    onClick = {
                        activity?.let { viewModel.purchase(it, BillingProducts.productId(selectedTier)) }
                    },
                    enabled = !purchasing && selectedPrice.isNotBlank() && activity != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (purchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Continue")
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { viewModel.restorePurchases() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore Purchases")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TierCard(
    title: String,
    price: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = border
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    price.ifBlank { "Price unavailable" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (price.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
