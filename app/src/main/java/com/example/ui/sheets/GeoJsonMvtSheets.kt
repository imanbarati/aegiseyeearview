package com.example.ui.sheets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.map.mvt.MvtFeatureSelection
import com.example.ui.map.mvt.TacticalGeoJsonOverlay
import com.example.ui.theme.*

/**
 * Bottom sheet for managing local GeoJSON tactical overlays compiled into Mapbox Vector Tiles (MVT)
 * and rendered inside the OsmDroid MapView, plus importing custom local .geojson files via SAF.
 */
@Composable
fun GeoJsonMvtLayersSheet(
    overlays: List<TacticalGeoJsonOverlay>,
    onToggleOverlay: (String) -> Unit,
    onImportGeoJson: (fileName: String, rawJson: String) -> Boolean,
    onDeleteCustomOverlay: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPasteDialog by remember { mutableStateOf(false) }
    var importFeedback by remember { mutableStateOf<String?>(null) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "custom_overlay.geojson"
                if (!raw.isNullOrBlank()) {
                    val ok = onImportGeoJson(fileName, raw)
                    importFeedback = if (ok) {
                        "MVT compiled & loaded: $fileName"
                    } else {
                        "Invalid GeoJSON FeatureCollection in $fileName"
                    }
                }
            } catch (_: Exception) {
                importFeedback = "Failed to read selected GeoJSON file"
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(1.dp, TacticalCyan, RoundedCornerShape(12.dp))
            .testTag("geojson_mvt_layers_sheet"),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MVT VECTOR & GEOJSON OVERLAYS",
                        style = MaterialTheme.typography.titleSmall,
                        color = TacticalCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Local GeoJSON -> Mapbox Vector Tile (.mvt) -> OsmDroid",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalTextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close MVT Layers", tint = TacticalTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        openDocumentLauncher.launch(arrayOf("application/geo+json", "application/json", "*/*"))
                    },
                    modifier = Modifier.weight(1f).testTag("import_geojson_file_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TacticalCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TacticalCyan)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("IMPORT .GEOJSON", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = { showPasteDialog = true },
                    modifier = Modifier.weight(1f).testTag("paste_geojson_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = DivisionOrange, contentColor = TacticalDarkBg)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PASTE GEOJSON", style = MaterialTheme.typography.labelSmall)
                }
            }

            importFeedback?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (msg.startsWith("MVT")) ThreatLowGreen else ThreatCriticalRed
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(overlays, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (item.isEnabled) TacticalCyan.copy(alpha = 0.6f) else TacticalBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggleOverlay(item.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TacticalTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${item.fileName} // ${item.featureCount} features // MVT Z${item.tileZ}/${item.tileX}/${item.tileY} (${item.mvtByteSize} B)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TacticalTextDim
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.isCustomImported) {
                                IconButton(onClick = { onDeleteCustomOverlay(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Custom Layer", tint = ThreatCriticalRed)
                                }
                            }
                            Switch(
                                checked = item.isEnabled,
                                onCheckedChange = { onToggleOverlay(item.id) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = TacticalCyan,
                                    checkedTrackColor = TacticalCyan.copy(alpha = 0.35f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPasteDialog) {
        PasteGeoJsonDialog(
            onDismiss = { showPasteDialog = false },
            onConfirm = { name, raw ->
                val ok = onImportGeoJson(name, raw)
                importFeedback = if (ok) "MVT compiled & loaded: $name" else "Invalid GeoJSON syntax"
                if (ok) showPasteDialog = false
            }
        )
    }
}

@Composable
private fun PasteGeoJsonDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, rawJson: String) -> Unit
) {
    var layerName by remember { mutableStateOf("field_recon_overlay.geojson") }
    var rawJson by remember {
        mutableStateOf(
            """
            {
              "type": "FeatureCollection",
              "name": "Field Recon Sector",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "name": "North Ridge Fallback",
                    "sector_code": "RECON-01",
                    "threat_level": "LOW",
                    "stroke": "#00E5FF"
                  },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[51.39, 35.72], [51.41, 35.76], [51.43, 35.80]]
                  }
                }
              ]
            }
            """.trimIndent()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TacticalSurface,
        title = {
            Text("COMPILE LOCAL GEOJSON -> MVT", style = MaterialTheme.typography.titleSmall, color = TacticalCyan)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = layerName,
                    onValueChange = { layerName = it },
                    label = { Text("Layer / File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rawJson,
                    onValueChange = { rawJson = it },
                    label = { Text("GeoJSON FeatureCollection") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(layerName.ifBlank { "custom.geojson" }, rawJson) },
                colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan, contentColor = TacticalDarkBg)
            ) {
                Text("PARSE & RENDER MVT")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TacticalTextSecondary)
            }
        }
    )
}

/**
 * Inspector card displayed when the user taps a vector polygon, corridor, or beacon
 * rendered by [MvtVectorTileOverlay] inside OsmDroid.
 */
@Composable
fun MvtFeatureDetailSheet(
    selection: MvtFeatureSelection,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TacticalCyan, RoundedCornerShape(12.dp))
            .testTag("mvt_feature_detail_sheet"),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MVT VECTOR // ${selection.feature.type.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan
                    )
                    Text(
                        text = selection.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = TacticalTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Feature Details", tint = TacticalTextSecondary)
                }
            }

            Text(
                text = "Source: ${selection.sourceFileName} | Tile Z${selection.tileZ}/${selection.tileX}/${selection.tileY}",
                style = MaterialTheme.typography.labelSmall,
                color = TacticalTextDim
            )

            val notes = selection.feature.properties["notes"] as? String
            if (!notes.isNullOrBlank()) {
                Text(text = notes, style = MaterialTheme.typography.bodySmall, color = TacticalTextSecondary)
            }

            val metaTags = selection.feature.properties.entries
                .filter { it.key !in setOf("name", "notes", "stroke", "fill") }
                .take(4)
            if (metaTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    metaTags.forEach { (k, v) ->
                        Box(
                            modifier = Modifier
                                .background(TacticalSurfaceElevated, RoundedCornerShape(4.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${k.uppercase()}: $v",
                                style = MaterialTheme.typography.labelSmall,
                                color = DivisionOrange
                            )
                        }
                    }
                }
            }
        }
    }
}
