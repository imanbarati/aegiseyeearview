package com.example.routing

import com.example.model.*
import java.util.PriorityQueue
import kotlin.math.*

object TacticalPathfinder {

    private const val EARTH_RADIUS_KM = 6371.0
    private const val WALKING_SPEED_KMH = 4.8 // Brisk crisis walking pace

    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Evaluates continuous threat-level heatmap intensity [0.0..1.5] at any coordinate
     * using Gaussian radial decay from all latitude/longitude heatmap data points.
     */
    fun evaluateHeatmapIntensity(
        lat: Double,
        lng: Double,
        heatmapPoints: List<ThreatHeatmapPoint>
    ): Float {
        var total = 0.0
        for (pt in heatmapPoints) {
            val distKm = calculateDistanceKm(lat, lng, pt.lat, pt.lng)
            val sigma = pt.radiusKm.coerceAtLeast(0.5)
            // Skip points far outside 3.5 * sigma for performance
            if (distKm <= sigma * 3.5) {
                val gaussian = exp(-(distKm * distKm) / (2.0 * sigma * sigma))
                total += pt.intensity * gaussian
            }
        }
        return total.toFloat().coerceIn(0f, 1.5f)
    }

    /**
     * Checks whether a given coordinate is inside a camera's active optical FOV cone,
     * and assesses nearby vulnerable public OSINT vs hardened state CCTV cameras.
     */
    fun evaluateCctvExposure(
        lat: Double,
        lng: Double,
        cameras: List<SurveillanceCamera>,
        scanRadiusMeters: Double = 3500.0
    ): UserSurveillanceExposure {
        val camerasInFov = mutableListOf<SurveillanceCamera>()
        var vulnerablePublicNearby = 0
        var stateEncryptedNearby = 0
        var offlineBlindSpotsNearby = 0
        var nearestCamera: SurveillanceCamera? = null
        var nearestDistM = Double.MAX_VALUE

        for (cam in cameras) {
            val distM = calculateDistanceKm(lat, lng, cam.lat, cam.lng) * 1000.0
            if (distM < nearestDistM) {
                nearestDistM = distM
                nearestCamera = cam
            }

            if (distM <= scanRadiusMeters) {
                when (cam.vulnerabilityStatus) {
                    CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT -> vulnerablePublicNearby++
                    CameraVulnerabilityStatus.HARDENED_STATE_ENCRYPTED -> stateEncryptedNearby++
                    CameraVulnerabilityStatus.OFFLINE_JAMMED -> offlineBlindSpotsNearby++
                }
            }

            if (cam.vulnerabilityStatus != CameraVulnerabilityStatus.OFFLINE_JAMMED &&
                distM <= cam.coverageRadiusMeters
            ) {
                if (cam.fovAngleDegrees >= 359f) {
                    camerasInFov.add(cam)
                } else {
                    val bearingFromCam = calculateBearingDegrees(cam.lat, cam.lng, lat, lng)
                    val angleDiff = abs(((bearingFromCam - cam.azimuthDegrees + 540.0) % 360.0) - 180.0)
                    if (angleDiff <= cam.fovAngleDegrees / 2.0) {
                        camerasInFov.add(cam)
                    }
                }
            }
        }

        val isExposed = camerasInFov.isNotEmpty()
        val statusBadge = if (isExposed) {
            "EXPOSED // ${camerasInFov.size} CAMERA(S) IN LINE-OF-SIGHT"
        } else {
            "INVULNERABLE // BLIND SPOT CONFIRMED"
        }

        val statusSummary = if (isExposed) {
            val hasVulnerableOsint = camerasInFov.any {
                it.vulnerabilityStatus == CameraVulnerabilityStatus.VULNERABLE_PUBLIC_OSINT
            }
            val hasState = camerasInFov.any {
                it.vulnerabilityStatus == CameraVulnerabilityStatus.HARDENED_STATE_ENCRYPTED
            }
            buildString {
                append("Active optical detection by ")
                append(camerasInFov.joinToString(", ") { it.name })
                append(". ")
                if (hasVulnerableOsint) append("PUBLIC OSINT stream is exposed to anyone online. ")
                if (hasState) append("STATE ENCRYPTED CCTV is logging movement.")
            }
        } else {
            if (nearestCamera != null && nearestDistM <= scanRadiusMeters) {
                "Outside active FOV cones. Nearest: ${nearestCamera.name} (${nearestDistM.toInt()}m away, ${nearestCamera.vulnerabilityStatus.label})."
            } else {
                "Clear of all cataloged public OSINT & state security CCTV optics within ${(scanRadiusMeters / 1000.0).toInt()}km."
            }
        }

        return UserSurveillanceExposure(
            isExposed = isExposed,
            statusBadge = statusBadge,
            statusSummary = statusSummary,
            camerasInFov = camerasInFov,
            vulnerablePublicNearbyCount = vulnerablePublicNearby,
            stateEncryptedNearbyCount = stateEncryptedNearby,
            offlineBlindSpotsNearbyCount = offlineBlindSpotsNearby,
            nearestCamera = nearestCamera,
            nearestCameraDistanceMeters = if (nearestDistM == Double.MAX_VALUE) -1.0 else nearestDistM
        )
    }

    /**
     * Selects the nearest designated Safe Zone (factoring in threat-level intensity along the approach)
     * and computes the safest walking route using an A* heatmap-weighted spatial grid.
     */
    fun findSafestWalkingRoute(
        startLat: Double,
        startLng: Double,
        safeHavens: List<SafeHaven>,
        heatmapPoints: List<ThreatHeatmapPoint>,
        cameras: List<SurveillanceCamera>,
        targetSafeHavenId: String? = null,
        avoidCctv: Boolean = true,
        threatWeight: Double = 22.0
    ): TacticalRoute {
        val targetHaven = if (targetSafeHavenId != null) {
            safeHavens.firstOrNull { it.id == targetSafeHavenId } ?: selectNearestSafeZone(startLat, startLng, safeHavens, heatmapPoints)
        } else {
            selectNearestSafeZone(startLat, startLng, safeHavens, heatmapPoints)
        }

        val gridRows = 26
        val gridCols = 26

        val minLatRaw = min(startLat, targetHaven.lat)
        val maxLatRaw = max(startLat, targetHaven.lat)
        val minLngRaw = min(startLng, targetHaven.lng)
        val maxLngRaw = max(startLng, targetHaven.lng)

        val latSpan = (maxLatRaw - minLatRaw).coerceAtLeast(0.025)
        val lngSpan = (maxLngRaw - minLngRaw).coerceAtLeast(0.025)

        val latPad = latSpan * 0.45
        val lngPad = lngSpan * 0.45

        val minLat = minLatRaw - latPad
        val maxLat = maxLatRaw + latPad
        val minLng = minLngRaw - lngPad
        val maxLng = maxLngRaw + lngPad

        val dLat = (maxLat - minLat) / (gridRows - 1)
        val dLng = (maxLng - minLng) / (gridCols - 1)

        fun cellLat(r: Int) = minLat + r * dLat
        fun cellLng(c: Int) = minLng + c * dLng

        fun closestCell(lat: Double, lng: Double): Pair<Int, Int> {
            val r = ((lat - minLat) / dLat).roundToInt().coerceIn(0, gridRows - 1)
            val c = ((lng - minLng) / dLng).roundToInt().coerceIn(0, gridCols - 1)
            return r to c
        }

        val startCell = closestCell(startLat, startLng)
        val goalCell = closestCell(targetHaven.lat, targetHaven.lng)

        // Precompute cell threat heatmap intensity & CCTV penalty
        val cellIntensity = Array(gridRows) { FloatArray(gridCols) }
        val cellCctvPenalty = Array(gridRows) { DoubleArray(gridCols) }

        for (r in 0 until gridRows) {
            val lat = cellLat(r)
            for (c in 0 until gridCols) {
                val lng = cellLng(c)
                cellIntensity[r][c] = evaluateHeatmapIntensity(lat, lng, heatmapPoints)
                if (avoidCctv) {
                    var camPen = 0.0
                    for (cam in cameras) {
                        if (cam.vulnerabilityStatus != CameraVulnerabilityStatus.OFFLINE_JAMMED) {
                            val distM = calculateDistanceKm(lat, lng, cam.lat, cam.lng) * 1000.0
                            if (distM <= cam.coverageRadiusMeters * 1.25) {
                                camPen += if (cam.operatorType == CameraOperatorType.PUBLIC_MUNICIPAL_OSINT) 4.0 else 7.5
                            }
                        }
                    }
                    cellCctvPenalty[r][c] = camPen
                }
            }
        }

        // A* Search
        data class Node(val r: Int, val c: Int, val g: Double, val f: Double) : Comparable<Node> {
            override fun compareTo(other: Node): Int = this.f.compareTo(other.f)
        }

        val gScore = Array(gridRows) { DoubleArray(gridCols) { Double.POSITIVE_INFINITY } }
        val parentR = Array(gridRows) { IntArray(gridCols) { -1 } }
        val parentC = Array(gridRows) { IntArray(gridCols) { -1 } }
        val closed = Array(gridRows) { BooleanArray(gridCols) }

        val pq = PriorityQueue<Node>()
        gScore[startCell.first][startCell.second] = 0.0
        val startH = calculateDistanceKm(
            cellLat(startCell.first), cellLng(startCell.second),
            cellLat(goalCell.first), cellLng(goalCell.second)
        )
        pq.add(Node(startCell.first, startCell.second, 0.0, startH))

        val dirs = arrayOf(
            -1 to 0, 1 to 0, 0 to -1, 0 to 1,
            -1 to -1, -1 to 1, 1 to -1, 1 to 1
        )

        while (pq.isNotEmpty()) {
            val curr = pq.poll() ?: break
            if (curr.r == goalCell.first && curr.c == goalCell.second) break
            if (closed[curr.r][curr.c]) continue
            closed[curr.r][curr.c] = true

            for ((dr, dc) in dirs) {
                val nr = curr.r + dr
                val nc = curr.c + dc
                if (nr !in 0 until gridRows || nc !in 0 until gridCols || closed[nr][nc]) continue

                val stepDistKm = calculateDistanceKm(cellLat(curr.r), cellLng(curr.c), cellLat(nr), cellLng(nc))
                val intensity = cellIntensity[nr][nc].toDouble()
                val hazardMultiplier = 1.0 + threatWeight * intensity.pow(2.0) + cellCctvPenalty[nr][nc]
                val tentativeG = gScore[curr.r][curr.c] + stepDistKm * hazardMultiplier

                if (tentativeG < gScore[nr][nc]) {
                    gScore[nr][nc] = tentativeG
                    parentR[nr][nc] = curr.r
                    parentC[nr][nc] = curr.c
                    val h = calculateDistanceKm(cellLat(nr), cellLng(nc), cellLat(goalCell.first), cellLng(goalCell.second))
                    pq.add(Node(nr, nc, tentativeG, tentativeG + h))
                }
            }
        }

        // Reconstruct grid path
        val rawPoints = mutableListOf<GeoPoint>()
        var cr = goalCell.first
        var cc = goalCell.second
        var safetySteps = 0
        while (cr != -1 && cc != -1 && safetySteps < gridRows * gridCols) {
            rawPoints.add(GeoPoint(cellLat(cr), cellLng(cc)))
            if (cr == startCell.first && cc == startCell.second) break
            val pr = parentR[cr][cc]
            val pc = parentC[cr][cc]
            cr = pr
            cc = pc
            safetySteps++
        }
        rawPoints.reverse()

        // Ensure exact start & exact Safe Zone destination
        val finalPath = mutableListOf<GeoPoint>()
        finalPath.add(GeoPoint(startLat, startLng, "Current Position", true))
        if (rawPoints.size > 2) {
            // Subsample intermediate grid waypoints while keeping turns
            for (i in 1 until rawPoints.size - 1) {
                val prev = finalPath.last()
                val pt = rawPoints[i]
                if (calculateDistanceKm(prev.lat, prev.lng, pt.lat, pt.lng) >= 0.35 || i % 2 == 0) {
                    finalPath.add(GeoPoint(pt.lat, pt.lng, "Stealth WP-${finalPath.size}", true))
                }
            }
        }
        finalPath.add(GeoPoint(targetHaven.lat, targetHaven.lng, targetHaven.name, true))

        // Compute route distance, heatmap exposure, and avoided hazards
        var totalDistKm = 0.0
        var peakIntensity = 0f
        var sumIntensity = 0f
        var sampleCount = 0

        for (i in 0 until finalPath.size - 1) {
            val p1 = finalPath[i]
            val p2 = finalPath[i + 1]
            totalDistKm += calculateDistanceKm(p1.lat, p1.lng, p2.lat, p2.lng)

            // Sample midpoint + endpoint intensity
            val midIntensity = evaluateHeatmapIntensity((p1.lat + p2.lat) / 2.0, (p1.lng + p2.lng) / 2.0, heatmapPoints)
            val endIntensity = evaluateHeatmapIntensity(p2.lat, p2.lng, heatmapPoints)
            peakIntensity = max(peakIntensity, max(midIntensity, endIntensity))
            sumIntensity += midIntensity + endIntensity
            sampleCount += 2
        }

        val avgIntensity = if (sampleCount > 0) sumIntensity / sampleCount else 0f
        val walkMinutes = ((totalDistKm / WALKING_SPEED_KMH) * 60.0).roundToInt().coerceAtLeast(1)

        // Identify hotspots near corridor that were successfully bypassed
        val bypassedHotspots = heatmapPoints
            .filter { it.intensity >= 0.45f }
            .filter { pt ->
                val distStart = calculateDistanceKm(startLat, startLng, pt.lat, pt.lng)
                val distEnd = calculateDistanceKm(targetHaven.lat, targetHaven.lng, pt.lat, pt.lng)
                val corridorRadius = totalDistKm + 30.0
                distStart <= corridorRadius && distEnd <= corridorRadius
            }
            .map { "${it.label} (${(it.intensity * 100).toInt()}% Threat)" }
            .distinct()
            .take(5)

        val camerasAvoided = if (avoidCctv) {
            cameras.count { cam ->
                cam.vulnerabilityStatus != CameraVulnerabilityStatus.OFFLINE_JAMMED &&
                calculateDistanceKm(startLat, startLng, cam.lat, cam.lng) <= totalDistKm + 5.0 &&
                finalPath.all { wp ->
                    calculateDistanceKm(wp.lat, wp.lng, cam.lat, cam.lng) * 1000.0 > cam.coverageRadiusMeters
                }
            }
        } else 0

        val riskScore = (avgIntensity * 65f + peakIntensity * 25f).roundToInt().coerceIn(4, 96)
        val classification = when {
            riskScore < 25 -> "OPTIMAL STEALTH WALKING CORRIDOR (LOW HEATMAP EXPOSURE)"
            riskScore < 50 -> "VIABLE FOOT EVACUATION (MODERATE THREAT DETOUR)"
            else -> "HIGH-ALERT CORRIDOR (PROCEED UNDER COVER & NIGHTFALL)"
        }

        val initialBearing = calculateBearingDegrees(startLat, startLng, finalPath[1].lat, finalPath[1].lng).roundToInt()
        val directives = buildList {
            add("A* PATHFINDER LOCKED: Nearest Safe Zone -> ${targetHaven.name} (${round(totalDistKm * 10) / 10} km, ~$walkMinutes min walk).")
            add("Initial foot heading $initialBearing°; maintaining detour around ${bypassedHotspots.size.coerceAtLeast(1)} peak threat heatmap cluster(s).")
            if (avoidCctv) {
                add("CCTV BLIND-SPOT ROUTING ACTIVE: Bypassed $camerasAvoided active public OSINT & state security camera cones.")
            }
            add("Keep below 5 km/h near checkpoints; radio watch on ${targetHaven.contactFreq}.")
        }

        return TacticalRoute(
            id = "walk_${targetHaven.id}",
            name = "SAFE WALK -> ${targetHaven.name.uppercase()}",
            origin = "Live Agent Pos",
            destination = targetHaven.name,
            path = finalPath,
            distanceKm = round(totalDistKm * 100.0) / 100.0,
            estDriveHours = round((totalDistKm / 65.0) * 10.0) / 10.0,
            riskScore = riskScore,
            riskClassification = classification,
            deadZonesAvoided = bypassedHotspots.ifEmpty { listOf("Active Strike Thermal Cores") },
            tacticalDirectives = directives,
            isWalkingRoute = true,
            estWalkMinutes = walkMinutes,
            peakHeatmapIntensity = peakIntensity.coerceAtMost(1f),
            averageHeatmapIntensity = avgIntensity.coerceAtMost(1f),
            cctvCamerasAvoided = camerasAvoided,
            targetSafeHavenId = targetHaven.id
        )
    }

    /**
     * Picks the nearest designated Safe Zone factoring in heatmap intensity at and around the haven.
     */
    fun selectNearestSafeZone(
        userLat: Double,
        userLng: Double,
        safeHavens: List<SafeHaven>,
        heatmapPoints: List<ThreatHeatmapPoint>
    ): SafeHaven {
        return safeHavens.minByOrNull { haven ->
            val distKm = calculateDistanceKm(userLat, userLng, haven.lat, haven.lng)
            val midLat = (userLat + haven.lat) / 2.0
            val midLng = (userLng + haven.lng) / 2.0
            val midHeat = evaluateHeatmapIntensity(midLat, midLng, heatmapPoints)
            val destHeat = evaluateHeatmapIntensity(haven.lat, haven.lng, heatmapPoints)
            // Penalize routes that must cross intense heat zones
            distKm * (1.0 + 1.5 * (midHeat + destHeat))
        } ?: safeHavens.first()
    }
}
