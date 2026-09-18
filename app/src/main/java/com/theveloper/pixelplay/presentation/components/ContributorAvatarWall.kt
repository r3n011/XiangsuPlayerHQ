@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import coil.compose.AsyncImage
import com.theveloper.pixelplay.presentation.screens.Contributor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Material 表现力基本图形（与 Rhythm 一致：androidx.graphics MaterialShapes / RoundedPolygon）
 * 基础几何 / Cookie / 有机 / 趣味 四类。
 */
internal enum class AvatarShapeKind {
    CIRCLE, SQUARE, OVAL, PILL, DIAMOND, TRIANGLE, PENTAGON,
    COOKIE_4, COOKIE_6, COOKIE_7, COOKIE_12,
    PUFFY, GEM, BOOM, CLOVER, FLOWER, HEART, BURST, SUNNY
}

/** 根据名字 hash 稳定分配一个形状 */
internal fun shapeFor(name: String): AvatarShapeKind {
    val values = AvatarShapeKind.entries
    return values[abs(name.hashCode()) % values.size]
}

/** 在给定尺寸内生成形状路径（与 Rhythm 相同的 MaterialShapes RoundedPolygon，缩放居中到 size） */
internal fun avatarShapePath(kind: AvatarShapeKind, size: Float): Path {
    val polygon = when (kind) {
        AvatarShapeKind.CIRCLE -> MaterialShapes.Circle
        AvatarShapeKind.SQUARE -> MaterialShapes.Square
        AvatarShapeKind.OVAL -> MaterialShapes.Oval
        AvatarShapeKind.PILL -> MaterialShapes.Pill
        AvatarShapeKind.DIAMOND -> MaterialShapes.Diamond
        AvatarShapeKind.TRIANGLE -> MaterialShapes.Triangle
        AvatarShapeKind.PENTAGON -> MaterialShapes.Pentagon
        AvatarShapeKind.COOKIE_4 -> MaterialShapes.Cookie4Sided
        AvatarShapeKind.COOKIE_6 -> MaterialShapes.Cookie6Sided
        AvatarShapeKind.COOKIE_7 -> MaterialShapes.Cookie7Sided
        AvatarShapeKind.COOKIE_12 -> MaterialShapes.Cookie12Sided
        AvatarShapeKind.PUFFY -> MaterialShapes.Puffy
        AvatarShapeKind.GEM -> MaterialShapes.Gem
        AvatarShapeKind.BOOM -> MaterialShapes.Boom
        AvatarShapeKind.CLOVER -> MaterialShapes.Clover4Leaf
        AvatarShapeKind.FLOWER -> MaterialShapes.Flower
        AvatarShapeKind.HEART -> MaterialShapes.Heart
        AvatarShapeKind.BURST -> MaterialShapes.Burst
        AvatarShapeKind.SUNNY -> MaterialShapes.Sunny
    }
    // RoundedPolygon → android.graphics.Path → Compose Path
    val raw = polygon.toPath().asComposePath()
    val bounds = raw.getBounds()
    if (bounds.width <= 0f || bounds.height <= 0f) return raw
    // 等比缩放并居中到目标尺寸
    val scale = min(size / bounds.width, size / bounds.height)
    val matrix = Matrix().apply {
        translate(
            (size - bounds.width * scale) / 2f - bounds.left * scale,
            (size - bounds.height * scale) / 2f - bounds.top * scale,
        )
        scale(scale, scale)
    }
    return raw.apply { transform(matrix) }
}

/** 将 MaterialShapes 形状转为 Compose Shape（用于 clip 头像） */
internal fun avatarClipShape(kind: AvatarShapeKind): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(avatarShapePath(kind, size.width))
    }
}

/** 头像墙配色：Material 容器色调色板，按名字稳定选取 */
internal fun avatarPaletteColor(index: Int, colorScheme: androidx.compose.material3.ColorScheme): Color {
    return when (index % 8) {
        0 -> colorScheme.primaryContainer
        1 -> colorScheme.secondaryContainer
        2 -> colorScheme.tertiaryContainer
        3 -> colorScheme.errorContainer
        4 -> colorScheme.surfaceVariant
        5 -> colorScheme.primary.copy(alpha = 0.7f)
        6 -> colorScheme.secondary.copy(alpha = 0.7f)
        else -> colorScheme.tertiary.copy(alpha = 0.7f)
    }
}

/**
 * 社区贡献者头像墙（静态横向堆叠）：
 * - 所有开发者头像以 Material 基本图形轮廓 + 真实头像自然堆叠排列（不重叠）
 * - 横向滑动，头像逐个消失/出现，以展示全部开发者
 * - 每 3 秒自动轮播滚动到下一个；点击头像触发 onAvatarClick
 */
@Composable
internal fun ContributorAvatarWall(
    contributors: List<Contributor>,
    onAvatarClick: (Contributor) -> Unit,
    modifier: Modifier = Modifier,
    wallHeight: Dp = 140.dp,
) {
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme

    // 自动轮播：头像消失一个出现一个，循环展示所有开发者（用户拖动时暂停）
    LaunchedEffect(contributors.size) {
        while (isActive) {
            delay(3000)
            if (contributors.size > 1 && !listState.isScrollInProgress) {
                val next = (listState.firstVisibleItemIndex + 1) % contributors.size
                listState.animateScrollToItem(next)
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.height(wallHeight),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(contributors, key = { it.id }) { contributor ->
            ContributorAvatarItem(
                contributor = contributor,
                onClick = { onAvatarClick(contributor) },
                shape = avatarClipShape(shapeFor(contributor.displayName)),
                fillColor = avatarPaletteColor(abs(contributor.displayName.hashCode()), colorScheme),
            )
        }
    }
}

@Composable
private fun ContributorAvatarItem(
    contributor: Contributor,
    onClick: () -> Unit,
    shape: Shape,
    fillColor: Color,
) {
    val colorScheme = MaterialTheme.colorScheme
    val avatarSize = 56.dp

    Column(
        modifier = Modifier
            .widthIn(min = 64.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(fillColor.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 头像：Material 基本图形轮廓 + 真实头像（加载前显示首字母占位）
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(shape)
                .background(fillColor),
            contentAlignment = Alignment.Center,
        ) {
            val avatarUrl = contributor.avatarUrl
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = contributor.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Text(
                    text = contributor.displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.size(4.dp))

        // 名字
        Text(
            text = contributor.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 76.dp),
        )
    }
}
