package io.github.aicybridge

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Stable
internal class GlassBackdrop(val layer: GraphicsLayer) {
    var coordinates: LayoutCoordinates? by mutableStateOf(null)
}

@Composable
internal fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

@Composable
internal fun Modifier.captureGlassBackdrop(backdrop: GlassBackdrop): Modifier {
    val background = MiuixTheme.colorScheme.surface
    return onGloballyPositioned { backdrop.coordinates = it }.drawWithContent {
        backdrop.layer.record {
            drawRect(background)
            this@drawWithContent.drawContent()
        }
        drawLayer(backdrop.layer)
    }
}

/** Compact proportions, translucent material and sliding selection inspired by Miuix's glass demo. */
@Composable
internal fun GlassCapsuleNavigation(backdrop: GlassBackdrop, selectedPage: Int, onSelect: (Int) -> Unit,
                                    homeIcon: ImageVector, logsIcon: ImageVector, height: Dp, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val indicatorX by animateDpAsState(96.dp * selectedPage,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 500f), label = "glass_selection")
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.width(192.dp).height(height).testTag("capsule_navigation")
            .shadow(6.dp, CircleShape, clip = false,
                ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.14f))
            .glassSurface(backdrop)) {
            Box(Modifier.offset { IntOffset(indicatorX.roundToPx(), 0) }.padding(4.dp).size(width = 88.dp, height = height - 8.dp)
                .background(colors.onSurface.copy(alpha = if (dark) 0.10f else 0.055f), CircleShape)
                .border(0.6.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.52f), CircleShape))
            Row(Modifier.fillMaxSize().selectableGroup()) {
                GlassTab(homeIcon, "主页", "nav_home", selectedPage == 0, { onSelect(0) }, Modifier.weight(1f))
                GlassTab(logsIcon, "日志", "nav_logs", selectedPage == 1, { onSelect(1) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GlassTab(icon: ImageVector, label: String, tag: String, selected: Boolean,
                     onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f), label = "glass_tab_press")
    val colors = MiuixTheme.colorScheme
    val color = if (selected) colors.primary else colors.onSurfaceVariantSummary
    Box(modifier.fillMaxHeight().testTag(tag).selectable(selected, onClick = onClick, role = Role.Tab,
        interactionSource = interaction, indication = null), contentAlignment = Alignment.Center) {
        Column(Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = color)
            Text(label, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@Composable
private fun Modifier.glassSurface(backdrop: GlassBackdrop): Modifier {
    val sample = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val dark = isSystemInDarkTheme()
    val surface = MiuixTheme.colorScheme.surfaceContainer
    return onGloballyPositioned { coordinates = it }.drawWithCache {
        val pad = 24.dp.toPx()
        val effect: RenderEffect? = if (Build.VERSION.SDK_INT >= 31) {
            val blur = AndroidGlassBlur.create(6.dp.toPx())
            val glass = if (Build.VERSION.SDK_INT >= 33) {
                AndroidGlassLens.create(blur, size, pad, 5.dp.toPx())
            } else blur
            glass.asComposeRenderEffect()
        } else null
        sample.renderEffect = effect
        val sampleSize = IntSize((size.width + pad * 2).roundToInt(), (size.height + pad * 2).roundToInt())
        val radius = size.height / 2f
        val shape = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius))) }
        val stroke = 0.8.dp.toPx()
        val highlight = Brush.linearGradient(listOf(
            Color.White.copy(alpha = if (dark) 0.38f else 0.9f),
            Color.White.copy(alpha = if (dark) 0.05f else 0.18f),
            Color.White.copy(alpha = if (dark) 0.20f else 0.58f),
        ))
        val shine = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.07f else 0.18f), Color.Transparent))
        onDrawBehind {
            val source = backdrop.coordinates
            val target = coordinates
            val canSample = effect != null && source?.isAttached == true && target?.isAttached == true
            clipPath(shape) {
                if (canSample) {
                    val position = source!!.localPositionOf(target!!, Offset.Zero)
                    // Re-record a small crop of the existing display list, never a CPU bitmap or the glass itself.
                    sample.record(size = sampleSize) {
                        translate(pad - position.x, pad - position.y) { drawLayer(backdrop.layer) }
                    }
                    translate(-pad, -pad) { drawLayer(sample) }
                }
                drawRect(surface.copy(alpha = if (canSample) { if (dark) 0.40f else 0.48f } else 0.97f))
                drawRect(shine)
            }
            drawRoundRect(highlight, topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(radius - stroke / 2), style = Stroke(stroke))
        }
    }
}

// Keep newer framework classes behind SDK guards so the module still installs on Android 8+.
@RequiresApi(31)
private object AndroidGlassBlur {
    fun create(radius: Float): android.graphics.RenderEffect =
        android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
}

@RequiresApi(33)
private object AndroidGlassLens {
    fun create(blur: android.graphics.RenderEffect, size: Size, padding: Float, bend: Float): android.graphics.RenderEffect {
        val shader = android.graphics.RuntimeShader(SOURCE)
        shader.setFloatUniform("extent", size.width, size.height)
        shader.setFloatUniform("padding", padding)
        shader.setFloatUniform("bend", bend)
        return android.graphics.RenderEffect.createChainEffect(
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"), blur)
    }

    // A convex capsule lens: bend samples toward its spine most strongly at the rim.
    private const val SOURCE = """
        uniform shader content;
        uniform float2 extent;
        uniform float padding;
        uniform float bend;
        half4 main(float2 position) {
            float2 center = extent * 0.5;
            float radius = max(center.y, 1.0);
            float2 local = position - float2(padding) - center;
            float2 spine = float2(clamp(local.x, -center.x + radius, center.x - radius), 0.0);
            float2 radial = local - spine;
            float distance = length(radial);
            float rim = smoothstep(0.45, 1.0, distance / radius);
            float2 normal = radial / max(distance, 0.001);
            half4 color = content.eval(position - normal * bend * rim * rim);
            half luminance = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
            color.rgb = mix(half3(luminance), color.rgb, 1.10);
            return color;
        }
    """
}
