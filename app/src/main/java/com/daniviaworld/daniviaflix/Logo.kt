package com.daniviaworld.daniviaflix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Logo A "Play-D": una D con el triángulo de reproducción recortado. Coordenadas en un lienzo de 200x200.
private fun dOuter(): Path = Path().apply {
    moveTo(40f, 30f)
    lineTo(100f, 30f)
    arcTo(Rect(30f, 30f, 170f, 170f), -90f, 180f, false)
    lineTo(40f, 170f)
    close()
}

private fun dTri(): Path = Path().apply {
    moveTo(78f, 68f)
    lineTo(78f, 132f)
    lineTo(130f, 100f)
    close()
}

private fun dFilled(): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    addPath(dOuter())
    addPath(dTri())
}

private fun markBrush() = Brush.linearGradient(
    colors = listOf(Amber, Coral, Violet),
    start = Offset(0f, 0f),
    end = Offset(200f, 200f),
)

@Composable
fun DMark(dp: Dp, modifier: Modifier = Modifier) {
    val path = remember { dFilled() }
    val brush = remember { markBrush() }
    Canvas(modifier.size(dp)) {
        val k = this.size.minDimension / 200f
        scale(k, k, pivot = Offset.Zero) { drawPath(path, brush) }
    }
}

@Composable
fun Wordmark(fontSize: TextUnit) {
    Row {
        Text("DANIVIA", fontSize = fontSize, fontWeight = FontWeight.Bold, letterSpacing = 0.15.em, color = Color.White, maxLines = 1, softWrap = false)
        Text("FLIX", fontSize = fontSize, fontWeight = FontWeight.Light, letterSpacing = 0.15.em, color = Amber, maxLines = 1, softWrap = false)
    }
}

/** Animación de inicio: se dibuja el contorno, se rellena, destello de luz y aparece el nombre. */
@Composable
fun Splash(onDone: () -> Unit) {
    val outer = remember { dOuter() }
    val tri = remember { dTri() }
    val filled = remember { dFilled() }
    val brush = remember { markBrush() }
    val draw = remember { Animatable(0f) }
    val fill = remember { Animatable(0f) }
    val streak = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        draw.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        coroutineScope {
            launch { fill.animateTo(1f, tween(500)) }
            launch { streak.animateTo(1f, tween(800)) }
            launch {
                delay(300)
                word.animateTo(1f, tween(700))
            }
        }
        delay(500)
        onDone()
    }

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(360.dp)
                        .height(2.dp)
                        .graphicsLayer {
                            scaleX = streak.value
                            alpha = 1f - streak.value * 0.9f
                        }
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFD9A0), Color.Transparent)))
                )
                Canvas(Modifier.size(180.dp)) {
                    val k = this.size.minDimension / 200f
                    scale(k, k, pivot = Offset.Zero) {
                        val a = 1f - fill.value
                        if (a > 0f) {
                            val pm = PathMeasure()
                            for ((p, c) in listOf(outer to Amber, tri to Coral)) {
                                pm.setPath(p, false)
                                val seg = Path()
                                pm.getSegment(0f, pm.length * draw.value, seg, true)
                                drawPath(
                                    seg,
                                    c.copy(alpha = a),
                                    style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                        if (fill.value > 0f) drawPath(filled, brush, alpha = fill.value)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.alpha(word.value)) {
                Text(
                    "DANIVIA", fontSize = 36.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (0.7f - 0.5f * word.value).em, color = Color.White,
                )
                Text(
                    "FLIX", fontSize = 36.sp, fontWeight = FontWeight.Light,
                    letterSpacing = (0.7f - 0.5f * word.value).em, color = Amber,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "TU CINE, A TU MANERA", fontSize = 13.sp, letterSpacing = 0.3.em, color = TextMuted,
                modifier = Modifier.alpha(word.value),
            )
        }
    }
}
