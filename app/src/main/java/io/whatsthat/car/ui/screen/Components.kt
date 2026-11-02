package io.whatsthat.car.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.whatsthat.car.domain.Side
import io.whatsthat.car.platform.SdkState
import io.whatsthat.car.platform.SdkStatus
import io.whatsthat.car.platform.VoiceInput
import io.whatsthat.car.ui.theme.WT

/** "저거뭐야?" 음성 버튼. 듣는 중에는 입력 크기에 맞춰 바깥 링만 은은하게 변한다(과한 애니메이션 지양). */
@Composable
fun AskOrb(phase: VoiceInput.Phase, level: Float, speaking: Boolean, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    val listening = phase != VoiceInput.Phase.IDLE
    val ring by animateFloatAsState(if (listening) 0.35f + level * 0.65f else 0f, tween(180), label = "ring")
    Box(
        Modifier
            .size(size)
            .semantics { contentDescription = "저거뭐야 음성 질문" }
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(WT.HyundaiBlue, WT.Night), radius = r), r)
            drawCircle(WT.Line, r - 2.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (listening) drawCircle(WT.ActiveBlue.copy(alpha = 0.35f + ring * 0.5f), r - 6.dp.toPx() - (1 - ring) * 18.dp.toPx(), style = Stroke(6.dp.toPx()))
            drawCircle(if (listening) WT.ActiveBlue else Color(0xFF0B3A6E), r * 0.62f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (speaking) Icons.Rounded.GraphicEq else Icons.Rounded.Mic, null,
                tint = if (listening) WT.Night else WT.TextHigh, modifier = Modifier.size(size * 0.16f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !enabled -> "음성 준비 중"
                    phase == VoiceInput.Phase.IDLE -> "저거뭐야?"
                    else -> "듣고 있어요"
                },
                color = if (listening) WT.Night else WT.TextHigh,
                fontSize = (size.value * 0.075f).sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 큰 터치 타깃의 질문 칩. 음성 대신 한 번에 누를 수 있는 프리셋. */
@Composable
fun QuickAsk(label: String, icon: ImageVector?, iconFirst: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(WT.SurfaceHigh)
            .border(1.dp, WT.Line, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null && iconFirst) { Icon(icon, null, tint = WT.ActiveBlue, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(12.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = WT.TextHigh, maxLines = 1)
        if (icon != null && !iconFirst) { Spacer(Modifier.width(12.dp)); Icon(icon, null, tint = WT.ActiveBlue, modifier = Modifier.size(34.dp)) }
    }
}

@Composable
fun SideBadge(side: Side, size: Dp = 72.dp) {
    val icon = when (side) {
        Side.LEFT -> Icons.AutoMirrored.Rounded.ArrowBack
        Side.RIGHT -> Icons.AutoMirrored.Rounded.ArrowForward
        Side.AHEAD -> Icons.Rounded.ArrowUpward
        Side.ANY -> Icons.Rounded.Place
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(20.dp)).background(WT.ActiveBlue.copy(alpha = 0.14f))
            .border(1.dp, WT.ActiveBlue.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, side.spoken, tint = WT.ActiveBlue, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
fun Pill(text: String, active: Boolean = false, color: Color = WT.ActiveBlue, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (active) color.copy(alpha = 0.18f) else WT.Surface)
            .border(BorderStroke(1.dp, if (active) color.copy(alpha = 0.7f) else WT.Line), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = if (active) color else WT.TextMid, maxLines = 1) }
}

@Composable
fun SdkRow(s: SdkStatus) {
    val c = when (s.state) {
        SdkState.PLEOS -> WT.Ok
        SdkState.FALLBACK, SdkState.DEMO -> WT.Warn
        SdkState.CONNECTING -> WT.TextLow
        SdkState.UNAVAILABLE -> WT.Bad
    }
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(14.dp))
        Text(s.name, style = MaterialTheme.typography.titleMedium, color = WT.TextHigh, modifier = Modifier.width(200.dp))
        Column {
            Text(s.state.label, style = MaterialTheme.typography.labelMedium, color = c)
            if (s.note.isNotBlank()) Text(s.note, fontSize = 15.sp, color = WT.TextLow, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(28.dp)).background(WT.Surface).border(1.dp, WT.Line, RoundedCornerShape(28.dp)).padding(28.dp),
    ) { content() }
}
