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

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private class SkiaMirageProgram(private val builder: RuntimeShaderBuilder) : MirageProgram {
  override fun setFloatUniform(name: String, value: Float): Unit = builder.uniform(name, value)

  override fun setFloatUniform(name: String, value1: Float, value2: Float): Unit = builder.uniform(name, value1, value2)

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float): Unit =
    builder.uniform(name, value1, value2, value3)

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float): Unit =
    builder.uniform(name, value1, value2, value3, value4)

  override fun setFloatUniform(name: String, values: FloatArray): Unit = builder.uniform(name, values)

  // Skia snapshots uniforms into a fresh Shader on each draw.
  override fun composeShader(): Shader = builder.makeShader().asComposeShader()

  override fun composeRenderEffect(childShaderName: String): RenderEffect =
    ImageFilter.makeRuntimeShader(builder, childShaderName, null).asComposeRenderEffect()
}

internal actual fun createMirageProgram(source: String): MirageProgram =
  SkiaMirageProgram(RuntimeShaderBuilder(RuntimeEffect.makeForShader(source)))

public actual fun mirageShadersSupported(): Boolean = true
