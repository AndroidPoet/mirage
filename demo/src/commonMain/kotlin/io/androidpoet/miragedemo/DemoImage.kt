/*
 * Designed and developed by 2026 androidpoet (Ranbir Singh)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.androidpoet.miragedemo

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale

/** Colorful generated test image used to demo the image-filter shaders. */
@Composable
fun DemoImage(modifier: Modifier = Modifier) {
  val bitmap = remember { demoBitmap() }
  Image(
    bitmap = bitmap,
    contentDescription = null,
    modifier = modifier,
    contentScale = ContentScale.Crop,
  )
}

private fun demoBitmap(): ImageBitmap {
  val w = 800
  val h = 1000
  val bitmap = ImageBitmap(w, h)
  val canvas = Canvas(bitmap)
  val paint = Paint()

  paint.shader = LinearGradientShader(
    from = Offset.Zero,
    to = Offset(0f, h.toFloat()),
    colors = listOf(Color(0xFF1A2980), Color(0xFF26D0CE), Color(0xFFFFF3B0)),
  )
  canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
  paint.shader = null

  fun circle(cx: Float, cy: Float, r: Float, color: Color) {
    paint.color = color
    canvas.drawCircle(Offset(cx, cy), r, paint)
  }

  circle(200f, 250f, 160f, Color(0xFFFF5E5B))
  circle(560f, 420f, 220f, Color(0xFFFFC145))
  circle(330f, 700f, 190f, Color(0xFF6C4AB6))
  circle(650f, 820f, 120f, Color(0xFF2ECC71))
  circle(120f, 880f, 90f, Color.White)

  paint.color = Color(0xFF111111)
  canvas.drawRect(60f, 60f, 740f, 130f, paint)
  paint.color = Color.White
  canvas.drawRect(80f, 78f, 400f, 112f, paint)

  return bitmap
}

private fun Canvas.drawRect(l: Float, t: Float, r: Float, b: Float, paint: Paint) {
  drawRect(androidx.compose.ui.geometry.Rect(l, t, r, b), paint)
}
