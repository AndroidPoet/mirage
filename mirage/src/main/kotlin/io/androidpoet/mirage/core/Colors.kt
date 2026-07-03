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
package io.androidpoet.mirage.core

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color

/**
 * Sets a `uniform vec4 <name>[maxCount]` color array plus its `<countName>`
 * float. Colors are passed as straight (non-premultiplied) sRGB floats, the
 * same representation the upstream WebGL mount uses; shaders premultiply
 * internally where needed.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public fun RuntimeShader.setColorsUniform(name: String, countName: String, colors: List<Color>, maxCount: Int) {
  val arr = FloatArray(maxCount * 4)
  val count = minOf(colors.size, maxCount)
  for (i in 0 until count) {
    val c = colors[i]
    arr[i * 4] = c.red
    arr[i * 4 + 1] = c.green
    arr[i * 4 + 2] = c.blue
    arr[i * 4 + 3] = c.alpha
  }
  setFloatUniform(name, arr)
  setFloatUniform(countName, count.toFloat())
}

/** Sets a single `uniform vec4` color as straight (non-premultiplied) sRGB floats. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public fun RuntimeShader.setColorUniform4f(name: String, color: Color) {
  setFloatUniform(name, color.red, color.green, color.blue, color.alpha)
}
