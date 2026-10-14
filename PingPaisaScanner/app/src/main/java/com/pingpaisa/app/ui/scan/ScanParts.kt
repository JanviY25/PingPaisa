package com.pingpaisa.app.ui.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingpaisa.app.data.ImageStore
import com.pingpaisa.app.data.ItemState
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.Assessment
import com.pingpaisa.app.finance.Decision
import com.pingpaisa.app.ui.theme.PP
import com.pingpaisa.app.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 48dp touch target around a 38dp translucent circle, as in the reference top bar. */
@Composable
fun CircleButton(icon: ImageVector?, description: String, modifier: Modifier = Modifier, size: Dp = 38.dp, label: String? = null, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            else if (label != null) Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Thin white corner brackets + a soft green scan line sweeping between them. */
@Composable
fun ScanFrameOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val fraction by transition.animateFloat(
        initialValue = 0.08f, targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "line"
    )
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val len = 28.dp.toPx()
        val r = 16.dp.toPx()
        val corner = Path().apply {
            moveTo(0f, len); lineTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            lineTo(len, 0f)
        }
        val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pivot = Offset(size.width / 2f, size.height / 2f)
        listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f).forEach { (sx, sy) ->
            withTransform({ scale(sx, sy, pivot) }) { drawPath(corner, Color.White, style = style) }
        }
        val inset = 14.dp.toPx()
        val y = size.height * fraction
        val fade = Brush.horizontalGradient(
            listOf(Color.Transparent, PP.ScanLine.copy(alpha = 0.4f), PP.ScanLine.copy(alpha = 0.4f), Color.Transparent),
            startX = inset, endX = size.width - inset
        )
        drawLine(fade, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
        val core = Brush.horizontalGradient(
            listOf(Color.Transparent, PP.ScanLine, PP.ScanLine, Color.Transparent), startX = inset, endX = size.width - inset
        )
        drawLine(core, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
fun PriceChip(label: String, value: String, dim: Boolean, busy: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val base = modifier.clip(RoundedCornerShape(20.dp)).background(PP.ChipBg.copy(alpha = 0.92f))
    Column(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .semantics { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color(0xFFAEBBB3), fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = Color.White.copy(alpha = if (dim) 0.55f else 1f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (busy) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(Modifier.size(16.dp), color = PP.Accent, strokeWidth = 2.dp)
            }
        }
    }
}

private fun gradientFor(kind: VerdictKind): Pair<Color, Color> = when (kind) {
    VerdictKind.GREEN -> Color(0xFFE4F1E0) to Color(0xFFB3D4AC)
    VerdictKind.AMBER -> Color(0xFFFBEFD2) to Color(0xFFEBCD92)
    VerdictKind.RED -> Color(0xFFF9E0E0) to Color(0xFFEBB3B3)
    VerdictKind.NEUTRAL -> Color(0xFFE6EBE7) to Color(0xFFC3CEC7)
}

@Composable
fun VerdictCard(ui: VerdictUi, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val (c1, c2) = gradientFor(ui.kind)
    val icon = when (ui.icon) {
        VerdictIcon.CHECK -> Icons.Rounded.Check
        VerdictIcon.WARN -> Icons.Outlined.ErrorOutline
        VerdictIcon.LOCK -> Icons.Outlined.Lock
        VerdictIcon.SCAN -> Icons.Outlined.CropFree
    }
    val shape = RoundedCornerShape(28.dp)
    val base = modifier.clip(shape).background(Brush.linearGradient(listOf(c1, c2), Offset.Zero, Offset(900f, 700f)))
    Row(
        (if (onClick != null) base.clickable(role = Role.Button, onClickLabel = "Why this verdict", onClick = onClick) else base)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = PP.Ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR VERDICT", color = PP.VerdictLabel, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                if (ui.action == VerdictAction.DETAILS) Text("Details ›", color = PP.VerdictLabel, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(2.dp))
            Text(ui.title, color = PP.Ink, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(ui.body, color = PP.InkSoft, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (ui.bullets.isNotEmpty()) Spacer(Modifier.height(8.dp))
            ui.bullets.forEach { b ->
                Row(Modifier.padding(top = 2.dp)) {
                    Text(b.mark.glyph, color = PP.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                    Text(b.text, color = PP.InkSoft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun ProductThumb(path: String?, size: Dp, modifier: Modifier = Modifier) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = path?.let { p -> withContext(Dispatchers.IO) { ImageStore.loadThumbnail(p, 160)?.asImageBitmap() } }
    }
    Box(modifier.size(size).clip(RoundedCornerShape(10.dp)).background(PP.Field), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Outlined.ShoppingBag, null, tint = PP.TextLo, modifier = Modifier.size(size / 2))
    }
}

@Composable
fun ProductTray(
    products: List<Product>, assessment: Assessment?, selectedId: String?,
    onSelect: (Product) -> Unit, onRemove: (Product) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier
) {
    val total = products.sumOf { it.price ?: 0.0 }
    val noPrice = products.count { it.price == null && it.state != ItemState.PROCESSING }
    Column(modifier) {
        Text(
            "${products.size} item${if (products.size == 1) "" else "s"} · Total ${Money.format(total)}" +
                if (noPrice > 0) " · $noPrice without price" else "",
            color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
        )
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { p ->
                ProductCard(p, assessment?.line(p.id)?.decision, p.id == selectedId, { onSelect(p) }, { onRemove(p) })
            }
            item(key = "add") {
                Box(
                    Modifier.width(64.dp).height(104.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.1f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = "Add more photos" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Add, null, tint = Color.White) }
            }
        }
    }
}

@Composable
private fun ProductCard(p: Product, decision: Decision?, selected: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(134.dp).height(104.dp).clip(shape).background(PP.ChipBg.copy(alpha = 0.93f))
            .border(BorderStroke(if (selected) 1.5.dp else 0.dp, if (selected) PP.Accent else Color.Transparent), shape)
            .clickable(role = Role.Button, onClickLabel = "Edit ${p.name}", onClick = onClick).padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            ProductThumb(p.imagePath, 36.dp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove)
                    .semantics { contentDescription = "Remove ${p.name}" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Close, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(p.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            p.state == ItemState.PROCESSING -> CircularProgressIndicator(Modifier.padding(top = 3.dp).size(14.dp), color = PP.Accent, strokeWidth = 2.dp)
            p.price == null -> Text("Add price", color = PP.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Money.format(p.price), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                val tag = when (decision) {
                    Decision.BUY_NOW -> "✓ Buy"
                    Decision.BUY_WITH_CAUTION -> "! Buy"
                    Decision.WAIT -> "… Wait"
                    Decision.SKIP -> "✕ Skip"
                    else -> null
                }
                if (tag != null) Text("  $tag", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun AppBottomNav(onOther: (String) -> Unit, onYou: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(PP.NavBg).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(54.dp)) {
            NavItem(Icons.Outlined.Home, "Home", false, Modifier.weight(1f)) { onOther("Home") }
            NavItem(Icons.Outlined.BarChart, "Stats", false, Modifier.weight(1f)) { onOther("Stats") }
            NavItem(Icons.Outlined.CropFree, "Scan", true, Modifier.weight(1f)) {}
            NavItem(Icons.Outlined.ChatBubbleOutline, "AI", false, Modifier.weight(1f)) { onOther("AI") }
            NavItem(Icons.Outlined.PersonOutline, "You", false, Modifier.weight(1f), onYou)
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (active) PP.NavActive else PP.NavIdle
    Column(
        modifier.fillMaxSize().clickable(role = Role.Tab, onClick = onClick).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 10.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}
