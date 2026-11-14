package com.example.ui.sheets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ThreatLevel
import com.example.service.GeoJsonThreatPolygon
import com.example.ui.map.BackupExportProgress
import com.example.ui.map.LocalBackupFileInfo
import com.example.ui.map.OfflineMapBackupExporter
import com.example.ui.map.TileCacheStats
import com.example.ui.map.TilePrefetchProgress
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Disk Cache Manager UI sheet for OsmDroid offline tile storage, connectivity failover status,
 * pre-caching the current map sector, and purging cached tiles.
 */
@Composable
fun OfflineTileCacheSheet(
    isNetworkOnline: Boolean,
    cacheStats: TileCacheStats?,
    prefetchProgress: TilePrefetchProgress,
    canPrefetchOnlineTiles: Boolean,
    backupExportProgress: BackupExportProgress,
    savedLocalBackups: List<LocalBackupFileInfo>,
    overlayInventorySummary: String,
    onPrefetchCurrentSector: () -> Unit,
    onRefreshStats: () -> Unit,
    onClearCache: () -> Unit,
    onExportBackupToLocalFile: () -> Unit,
    onExportBackupToUri: (Uri, String) -> Unit,
    onDeleteLocalBackup: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val netColor = if (isNetworkOnline) ThreatLowGreen else DivisionAmber
    var pendingArchiveName by remember { mutableStateOf(OfflineMapBackupExporter.defaultBackupFileName()) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd HH:mm", Locale.US) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            onExportBackupToUri(uri, pendingArchiveName)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .heightIn(max = 580.dp)
            .border(1.5.dp, TacticalCyan.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .testTag("offline_tile_cache_sheet"),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OSMDROID DISK CACHE MANAGER",
                        style = MaterialTheme.typography.titleSmall,
                        color = TacticalCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "SQLite Tile Store // Zero-Connectivity Resilience",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalTextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Disk Cache Manager", tint = TacticalTextSecondary)
                }
            }

            // Network & Failover Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(netColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .border(1.dp, netColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(netColor, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isNetworkOnline) {
                            "UPLINK ONLINE // AUTO-CACHE ACTIVE"
                        } else {
                            "OFFLINE FAILOVER // SERVING DISK CACHE"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = netColor
                    )
                }
                IconButton(onClick = onRefreshStats, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Cache Stats", tint = TacticalCyan, modifier = Modifier.size(16.dp))
                }
            }

            // Cache Telemetry Metrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("DISK USAGE", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        text = if (cacheStats != null) {
                            "${String.format("%.2f", cacheStats.usedMb)} / ${cacheStats.maxMb.toInt()} MB"
                        } else {
                            "SCANNING..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TacticalTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text("CACHED TILES", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        text = "${cacheStats?.estimatedTileCount ?: 0} TILES",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TacticalCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text("RETENTION", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        text = "${cacheStats?.retentionDays ?: 30} DAYS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ThreatLowGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Prefetch Progress
            if (prefetchProgress.isRunning || prefetchProgress.isComplete) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = prefetchProgress.statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (prefetchProgress.isComplete) ThreatLowGreen else TacticalCyan
                    )
                    LinearProgressIndicator(
                        progress = { prefetchProgress.progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = TacticalCyan,
                        trackColor = TacticalBorder
                    )
                }
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPrefetchCurrentSector,
                    enabled = !prefetchProgress.isRunning && isNetworkOnline && canPrefetchOnlineTiles,
                    modifier = Modifier.weight(1.4f).testTag("cache_sector_offline_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TacticalCyan,
                        contentColor = TacticalDarkBg
                    )
                ) {
                    Icon(Icons.Default.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (canPrefetchOnlineTiles) "CACHE SECTOR OFFLINE" else "SELECT ONLINE LAYER",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                OutlinedButton(
                    onClick = onClearCache,
                    enabled = !prefetchProgress.isRunning,
                    modifier = Modifier.weight(0.8f).testTag("purge_tile_cache_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThreatCriticalRed),
                    border = BorderStroke(1.dp, ThreatCriticalRed.copy(alpha = 0.7f))
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PURGE", style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider(color = TacticalBorder)

            // Emergency Offline Backup Export Section (Cached Map Area + Overlays -> .ZIP)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, DivisionOrange.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = DivisionOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "EMERGENCY OFFLINE BACKUP (.ZIP)",
                            style = MaterialTheme.typography.labelMedium,
                            color = DivisionOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Exports cached OsmDroid map area tiles plus active overlays ($overlayInventorySummary) into a portable offline ZIP bundle.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalTextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val name = OfflineMapBackupExporter.defaultBackupFileName()
                            pendingArchiveName = name
                            createDocumentLauncher.launch(name)
                        },
                        enabled = !backupExportProgress.isExporting,
                        modifier = Modifier.weight(1.25f).testTag("export_offline_backup_uri_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DivisionOrange,
                            contentColor = TacticalDarkBg
                        )
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("SAVE TO DEVICE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onExportBackupToLocalFile,
                        enabled = !backupExportProgress.isExporting,
                        modifier = Modifier.weight(1.0f).testTag("export_offline_backup_local_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TacticalCyan),
                        border = BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("QUICK BACKUP", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (backupExportProgress.isExporting) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = DivisionOrange,
                        trackColor = TacticalBorder
                    )
                }

                if (backupExportProgress.lastSummary != null || backupExportProgress.errorMessage != null) {
                    val isError = backupExportProgress.errorMessage != null
                    val summary = backupExportProgress.lastSummary
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isError) ThreatCriticalRed.copy(alpha = 0.14f) else ThreatLowGreen.copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (isError) ThreatCriticalRed.copy(alpha = 0.5f) else ThreatLowGreen.copy(alpha = 0.45f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(8.dp)
                            .testTag("backup_export_status_card"),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isError) {
                                "EXPORT ERROR: ${backupExportProgress.errorMessage}"
                            } else {
                                backupExportProgress.statusMessage
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isError) ThreatCriticalRed else ThreatLowGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isError && summary != null) {
                            val sizeLabel = if (summary.archiveSizeMb >= 1.0) {
                                "${String.format("%.2f", summary.archiveSizeMb)} MB"
                            } else {
                                "${String.format("%.1f", summary.archiveSizeKb)} KB"
                            }
                            Text(
                                text = "Archive Size: $sizeLabel • Trail: ${summary.breadcrumbsCount} • SOS: ${summary.sosBroadcastsCount} • Route: ${if (summary.routeIncluded) "YES" else "NO"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TacticalTextSecondary
                            )
                        }
                    }
                }

                if (savedLocalBackups.isNotEmpty()) {
                    Text(
                        text = "SAVED LOCAL BACKUPS (${savedLocalBackups.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan
                    )
                    savedLocalBackups.take(4).forEach { backup ->
                        val sizeText = if (backup.sizeMb >= 1.0) {
                            "${String.format("%.2f", backup.sizeMb)} MB"
                        } else {
                            "${String.format("%.1f", backup.sizeKb)} KB"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TacticalSurface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = backup.fileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TacticalTextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$sizeText • ${dateFormatter.format(Date(backup.lastModifiedMs))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TacticalTextDim
                                )
                            }
                            IconButton(
                                onClick = { onDeleteLocalBackup(backup.filePath) },
                                modifier = Modifier.size(30.dp).testTag("delete_local_backup_${backup.fileName}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Local Backup",
                                    tint = ThreatCriticalRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Detail sheet displayed when clicking any custom GeoJSON Threat Zone polygon overlaid on OsmDroid.
 */
@Composable
fun GeoJsonThreatPolygonDetailSheet(
    polygon: GeoJsonThreatPolygon,
    onDismiss: () -> Unit,
    onPlanBypass: (GeoJsonThreatPolygon) -> Unit,
    modifier: Modifier = Modifier
) {
    val levelColor = when (polygon.threatLevel) {
        ThreatLevel.CRITICAL_ALPHA -> ThreatCriticalRed
        ThreatLevel.HIGH_BRAVO -> ThreatHighOrange
        ThreatLevel.MODERATE_CHARLIE -> ThreatModerateYellow
        ThreatLevel.CAUTION_DELTA -> TacticalCyan
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, levelColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .testTag("geojson_threat_polygon_sheet"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TacticalSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(levelColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = polygon.threatLevel.code,
                            style = MaterialTheme.typography.labelMedium,
                            color = TacticalDarkBg
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${polygon.sectorCode} // GEOJSON POLYGON",
                        style = MaterialTheme.typography.labelMedium,
                        color = levelColor
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close GeoJSON Threat Sheet", tint = TacticalTextSecondary)
                }
            }

            Column {
                Text(
                    text = polygon.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TacticalTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SOURCE: ${polygon.sourceFileName} | CATEGORY: ${polygon.category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("CENTROID", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        text = "${String.format("%.3f", polygon.centerLat)}°N, ${String.format("%.3f", polygon.centerLng)}°E",
                        style = MaterialTheme.typography.bodySmall,
                        color = TacticalTextPrimary
                    )
                }
                Column {
                    Text("AREA", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        text = "~${String.format("%.1f", polygon.approxAreaKm2)} KM²",
                        style = MaterialTheme.typography.bodySmall,
                        color = levelColor
                    )
                }
                Column {
                    Text("VERTICES", style = MaterialTheme.typography.labelSmall, color = TacticalTextDim)
                    Text(
                        text = "${polygon.outerRing.size} PTS",
                        style = MaterialTheme.typography.bodySmall,
                        color = DivisionOrange
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(levelColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .border(1.dp, levelColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = polygon.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = TacticalTextPrimary
                    )
                    Text(
                        text = "CASUALTY RISK: ${polygon.casualtyRisk} | BYPASS BUFFER: +${polygon.bypassBufferKm.toInt()} KM",
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor
                    )
                }
            }

            Button(
                onClick = { onPlanBypass(polygon) },
                modifier = Modifier.fillMaxWidth().testTag("geojson_polygon_bypass_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DivisionOrange,
                    contentColor = TacticalDarkBg
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CALCULATE SAFE EVACUATION BYPASS", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
