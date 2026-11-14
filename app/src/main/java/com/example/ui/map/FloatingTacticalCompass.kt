package com.example.ui.map

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensor.CompassMath
import com.example.sensor.CompassReading
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Persistent floating tactical compass HUD rendered over the OsmDroid map view.
 * Rotates smoothly based on device orientation sensors (`TYPE_ROTATION_VECTOR` / accel+mag)
 * and optionally indicates the relative azimuth to the active safe-zone waypoint.
 */
@Composable
fun FloatingTacticalCompass(
    compassReading: CompassReading,
    isHeadingLockEnabled: Boolean,
    targetBearingDegrees: Float?,
    onToggleHeadingLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Unwrap heading continuously so crossing 359° -> 0° animates the shortest arc
    var unwrappedTargetAngle by remember { mutableFloatStateOf(-compassReading.headingDegrees) }
    LaunchedEffect(compassReading.headingDegrees) {
        val desiredDialAngle = -compassReading.headingDegrees
        val delta = CompassMath.shortestAngleDelta(
            CompassMath.normalizeDegrees(unwrappedTargetAngle),
            CompassMath.normalizeDegrees(desiredDialAngle)
        )
        unwrappedTargetAngle += delta
    }

    val animatedDialAngle by animateFloatAsState(
        targetValue = unwrappedTargetAngle,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "TacticalCompassDial"
    )

    val headingInt = CompassMath.normalizeDegrees(compassReading.headingDegrees).roundToInt() % 360
    val accentColor = if (isHeadingLockEnabled) DivisionOrange else TacticalCyan

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TacticalSurface.copy(alpha = 0.92f))
            .border(1.2.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggleHeadingLock()
            }
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .defaultMinSize(minWidth = 76.dp, minHeight = 88.dp)
            .semantics {
                contentDescription = "Tactical Compass Heading $headingInt degrees ${compassReading.cardinal}. Tap to toggle map heading lock."
            }
            .testTag("floating_tactical_compass"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(66.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.minDimension / 2f) - 4f

                // Outer bezel & inner crosshair ring
                drawCircle(
                    color = Color(0xFF070A0F),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.45f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.2f)
                )
                drawCircle(
                    color = TacticalBorder.copy(alpha = 0.55f),
                    radius = radius * 0.62f,
                    center = center,
                    style = Stroke(width = 1f)
                )

                // Rotate degree ticks + North needle opposite to heading so N stays anchored to True North
                rotate(degrees = animatedDialAngle, pivot = center) {
                    for (deg in 0 until 360 step 15) {
                        val rad = Math.toRadians((deg - 90).toDouble())
                        val isCardinal = deg % 90 == 0
                        val isMajor = deg % 45 == 0
                        val tickLen = when {
                            isCardinal -> 8.5f
                            isMajor -> 5.5f
                            else -> 3f
                        }
                        val outerPt = Offset(
                            center.x + radius * cos(rad).toFloat(),
                            center.y + radius * sin(rad).toFloat()
                        )
                        val innerPt = Offset(
                            center.x + (radius - tickLen) * cos(rad).toFloat(),
                            center.y + (radius - tickLen) * sin(rad).toFloat()
                        )
                        drawLine(
                            color = when {
                                deg == 0 -> ThreatCriticalRed
                                isCardinal -> TacticalTextPrimary
                                else -> TacticalTextDim.copy(alpha = 0.7f)
                            },
                            start = innerPt,
                            end = outerPt,
                            strokeWidth = if (isCardinal) 2.4f else 1.2f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Active Evacuation / Safe Haven bearing diamond if present
                    if (targetBearingDegrees != null) {
                        val targetRad = Math.toRadians((targetBearingDegrees - 90).toDouble())
                        val tipR = radius - 10f
                        val bx = center.x + tipR * cos(targetRad).toFloat()
                        val by = center.y + tipR * sin(targetRad).toFloat()
                        drawCircle(
                            color = ThreatLowGreen,
                            radius = 4.2f,
                            center = Offset(bx, by)
                        )
                    }

                    // North red triangle needle + South muted needle
                    val northPath = Path().apply {
                        moveTo(center.x, center.y - radius * 0.56f)
                        lineTo(center.x - 6f, center.y)
                        lineTo(center.x + 6f, center.y)
                        close()
                    }
                    drawPath(northPath, color = ThreatCriticalRed)

                    val southPath = Path().apply {
                        moveTo(center.x, center.y + radius * 0.52f)
                        lineTo(center.x - 5f, center.y)
                        lineTo(center.x + 5f, center.y)
                        close()
                    }
                    drawPath(southPath, color = TacticalTextDim.copy(alpha = 0.65f))
                }

                // Fixed lubber tick at top center indicating device forward heading
                drawLine(
                    color = accentColor,
                    start = Offset(center.x, 1f),
                    end = Offset(center.x, 10f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )

                // Center pivot pin
                drawCircle(color = accentColor, radius = 3.5f, center = center)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (compassReading.hasHardwareSensor) ThreatLowGreen else DivisionAmber,
                        CircleShape
                    )
            )
            Text(
                text = "${headingInt.toString().padStart(3, '0')}° ${compassReading.cardinal}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor
            )
        }

        Text(
            text = if (isHeadingLockEnabled) "HDG-UP" else "NORTH-UP",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = TacticalTextSecondary
        )
    }
}
