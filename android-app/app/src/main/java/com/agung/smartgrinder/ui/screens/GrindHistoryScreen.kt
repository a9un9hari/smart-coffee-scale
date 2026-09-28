package com.agung.smartgrinder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agung.smartgrinder.GrindRecord
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.components.NeutralOutlinedButton
import com.agung.smartgrinder.ui.components.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Grind sessions synced from the firmware's on-flash GrindLog, newest
 * first. Each row can take a reference weight from another scale (net
 * dose) plus a note, so the Timemore-measured dose can be checked against
 * an independent scale later - the diff is shown right on the card.
 */
@Composable
fun GrindHistoryScreen(
    records: List<GrindRecord>,
    syncing: Boolean,
    connected: Boolean,
    onSync: () -> Unit,
    onSaveReference: (boot: Long, uptimeS: Long, refWeightG: Float?, note: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        syncing -> "Syncing from grinder…"
                        !connected -> "${records.size} grinds · not connected"
                        else -> "${records.size} grinds"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                NeutralOutlinedButton("Sync", onClick = onSync, enabled = connected && !syncing)
            }
        }
        if (records.isEmpty()) {
            item {
                Text(
                    "No grinds yet. They're recorded on the grinder itself and pulled in here whenever the app connects.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(records, key = { "${it.boot}-${it.uptimeS}" }) { record ->
            GrindRecordCard(record, onSaveReference)
        }
    }
}

@Composable
private fun GrindRecordCard(
    record: GrindRecord,
    onSaveReference: (boot: Long, uptimeS: Long, refWeightG: Float?, note: String) -> Unit
) {
    var refText by remember(record.refWeightG) {
        mutableStateOf(record.refWeightG?.let { String.format(Locale.US, "%.2f", it) } ?: "")
    }
    var note by remember(record.note) { mutableStateOf(record.note) }

    val received = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(record.receivedAtMs))
    SectionCard(label = "Cup ${record.profile} · boot ${record.boot} +${record.uptimeS}s · synced $received") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format(Locale.US, "%.2fg", record.finalG),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            val diff = record.finalG - record.targetG
            Text(
                String.format(Locale.US, "target %.2fg (%+.2f)", record.targetG, diff),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            buildString {
                append("Result: ${record.result}")
                record.mainG?.let { append(String.format(Locale.US, " · main %.2fg", it)) }
                if (record.pulses > 0) {
                    append(" · ${record.pulses} top-up (short ")
                    append(record.pulseShortG.joinToString(" → ") { String.format(Locale.US, "%.2f", it) })
                    append("g)")
                }
            },
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            buildString {
                append(String.format(Locale.US, "Learned offset %.2f", record.learnedBeforeG))
                record.learnedAfterG?.let { append(String.format(Locale.US, " → %.2fg", it)) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        record.refWeightG?.let { ref ->
            Text(
                String.format(Locale.US, "Other scale %.2fg · grinder read %+.2fg vs it", ref, record.finalG - ref),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = refText,
                onValueChange = { refText = it.replace(',', '.') },
                label = { Text("Other scale (net g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        AppButton(
            "Save",
            onClick = { onSaveReference(record.boot, record.uptimeS, refText.toFloatOrNull(), note.trim()) },
            modifier = Modifier.align(Alignment.End)
        )
    }
}
