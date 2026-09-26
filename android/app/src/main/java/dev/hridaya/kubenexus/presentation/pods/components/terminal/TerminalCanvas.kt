package dev.hridaya.kubenexus.presentation.pods.components.terminal

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.ViewConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hridaya.kubenexus.core.terminal.TerminalSnapshot
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

@Composable
fun TerminalCanvas(
    snapshot: TerminalSnapshot?,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 13f,
    cursorColor: Color = Color.White.copy(alpha = 0.5f),
    selectionBackgroundColor: Color = Color(0xFF444444),
    selection: TerminalSelection? = null,
    onSelectionChange: (TerminalSelection?) -> Unit = {},
    terminalHandle: Long = 0,
    onResize: (cols: Int, rows: Int, cellWidth: Int, cellHeight: Int, widthPx: Int, heightPx: Int) -> Unit =
        { _, _, _, _, _, _ -> },
    onTap: () -> Unit = {},
    onScroll: (delta: Int, x: Float, y: Float) -> Unit = { _, _, _ -> },
    onSelectionChanged: (TerminalSelectionState?) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var lastResizedGrid by remember { mutableStateOf(Pair(0, 0)) }
    val androidViewConfiguration = remember(context) { ViewConfiguration.get(context) }
    val touchSlopPx =
        remember(androidViewConfiguration) { androidViewConfiguration.scaledTouchSlop.toFloat() }
    val longPressTimeoutMs =
        remember(androidViewConfiguration) { ViewConfiguration.getLongPressTimeout().toLong() }

    val primaryTypeface = remember { Typeface.MONOSPACE }
    val boldTypeface = remember { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    val italicTypeface = remember { Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC) }
    val boldItalicTypeface = remember { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC) }

    val textPaint = remember(primaryTypeface, fontSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = primaryTypeface
            textSize = fontSizePx
        }
    }

    val cellWidth = remember(textPaint) {
        max(1f, textPaint.measureText("W"))
    }
    val cellHeight = remember(textPaint) {
        val metrics = textPaint.fontMetrics
        max(1f, ceil(metrics.descent - metrics.ascent + metrics.leading))
    }
    val fontBaseline = remember(textPaint) {
        val metrics = textPaint.fontMetrics
        -metrics.ascent
    }

    LaunchedEffect(canvasSize, cellWidth, cellHeight) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && cellWidth > 0f && cellHeight > 0f) {
            val cols = max(1, floor(canvasSize.width / cellWidth).toInt())
            val rows = max(1, floor(canvasSize.height / cellHeight).toInt())
            if (lastResizedGrid != Pair(cols, rows)) {
                lastResizedGrid = Pair(cols, rows)
                onResize(
                    cols,
                    rows,
                    round(cellWidth).toInt(),
                    round(cellHeight).toInt(),
                    canvasSize.width,
                    canvasSize.height
                )
            }
        }
    }

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentSnapshot by rememberUpdatedState(snapshot)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .terminalGestures(
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                touchSlopPx = touchSlopPx,
                longPressTimeoutMs = longPressTimeoutMs,
                snapshot = { currentSnapshot },
                selection = { currentSelection },
                onSelectionChange = { currentOnSelectionChange(it) },
                onTap = { currentOnTap() },
                onScroll = { delta, x, y -> currentOnScroll(delta, x, y) },
            ),
    ) {
        // Fill canvas area with black scoped to this canvas only
        drawRect(Color.Black)

        val snap = currentSnapshot ?: return@Canvas
        val totalCells = snap.cols * snap.rows
        if (totalCells <= 0 || snap.codepoints.size < totalCells) return@Canvas

        // Cells in the default background are skipped below, so paint the grid with it once.
        if (snap.defaultBgArgb != 0 && snap.defaultBgArgb != 0xFF000000.toInt()) {
            drawRect(Color(snap.defaultBgArgb), size = Size(snap.cols * cellWidth, snap.rows * cellHeight))
        }

        val normalizedSelection = selection?.normalized(totalCells)
        val selBgArgb = selectionBackgroundColor.toArgb()
        val bgRect = RectF()

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            for (row in 0 until snap.rows) {
                val rowY = row * cellHeight
                val baselineY = rowY + fontBaseline
                val rowStart = row * snap.cols

                for (col in 0 until snap.cols) {
                    val idx = rowStart + col
                    val codepoint = snap.codepoints[idx]
                    val cellFlag = snap.flags[idx].toInt() and 0xFF

                    if ((cellFlag and TerminalSnapshot.CELL_FLAG_SPACER) != 0) continue

                    val cellX = col * cellWidth
                    val isSelected = normalizedSelection != null && idx in normalizedSelection
                    val colors = cellColors(
                        fg = snap.fgArgb[idx],
                        bg = snap.bgArgb[idx],
                        cellFlags = cellFlag,
                        defaultFg = snap.defaultFgArgb,
                    )
                    val effectiveBg = if (isSelected) selBgArgb else colors.background
                    val effectiveFg = if (isSelected) 0xFFFFFFFF.toInt() else colors.foreground

                    // The surface is already painted with the default background.
                    if (effectiveBg != snap.defaultBgArgb && effectiveBg != 0) {
                        bgRect.set(cellX, rowY, cellX + cellWidth, rowY + cellHeight)
                        textPaint.color = effectiveBg
                        native.drawRect(bgRect, textPaint)
                    }

                    // Draw cursor
                    if (snap.cursorVisible && col == snap.cursorX && row == snap.cursorY) {
                        bgRect.set(cellX, rowY, cellX + cellWidth, rowY + cellHeight)
                        textPaint.color = cursorColor.toArgb()
                        native.drawRect(bgRect, textPaint)
                    }

                    // Draw text character
                    if (codepoint != 0 && codepoint != 32) {
                        val isBold = (cellFlag and TerminalSnapshot.CELL_FLAG_BOLD) != 0
                        val isItalic = (cellFlag and TerminalSnapshot.CELL_FLAG_ITALIC) != 0

                        textPaint.typeface = when {
                            isBold && isItalic -> boldItalicTypeface
                            isBold -> boldTypeface
                            isItalic -> italicTypeface
                            else -> primaryTypeface
                        }

                        textPaint.color = effectiveFg
                        textPaint.isUnderlineText =
                            (cellFlag and TerminalSnapshot.CELL_FLAG_UNDERLINE) != 0

                        val glyph = snap.glyphAt(idx)
                        native.drawText(glyph, cellX, baselineY, textPaint)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TerminalCanvasPreview() {
    KubeNexusTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.Black),
        ) {
            TerminalCanvas(snapshot = null)
        }
    }
}
