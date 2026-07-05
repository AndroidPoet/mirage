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
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect as AndroidRenderEffect

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidMirageProgram(private val shader: RuntimeShader) : MirageProgram {
  override fun setFloatUniform(name: String, value: Float): Unit = shader.setFloatUniform(name, value)

  override fun setFloatUniform(name: String, value1: Float, value2: Float): Unit =
    shader.setFloatUniform(name, value1, value2)

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float): Unit =
    shader.setFloatUniform(name, value1, value2, value3)

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float): Unit =
    shader.setFloatUniform(name, value1, value2, value3, value4)

  override fun setFloatUniform(name: String, values: FloatArray): Unit = shader.setFloatUniform(name, values)

  // RuntimeShader already carries the freshly-set uniforms, so it doubles as the brush shader.
  override fun composeShader(): Shader = shader

  override fun composeRenderEffect(childShaderName: String): RenderEffect =
    AndroidRenderEffect.createRuntimeShaderEffect(shader, childShaderName).asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createMirageProgram(source: String): MirageProgram = AndroidMirageProgram(RuntimeShader(source))

public actual fun mirageShadersSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
