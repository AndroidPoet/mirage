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

/**
 * The uniform-setter surface a shader body writes to. Mirrors the subset of
 * `android.graphics.RuntimeShader`'s setters the shaders actually use, so the
 * shader wrappers read identically on every platform. On Android these forward
 * to RuntimeShader; on iOS / Desktop / Web they forward to Skia's
 * RuntimeShaderBuilder.
 */
public interface MirageUniforms {
  public fun setFloatUniform(name: String, value: Float)
  public fun setFloatUniform(name: String, value1: Float, value2: Float)
  public fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float)
  public fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)
  public fun setFloatUniform(name: String, values: FloatArray)
}

/**
 * A compiled shader plus the platform hooks the hosts need: apply uniforms via
 * [MirageUniforms], then turn it into a [Shader] (fill path) or a [RenderEffect]
 * (image-filter path).
 */
internal interface MirageProgram : MirageUniforms {
  fun composeShader(): Shader
  fun composeRenderEffect(childShaderName: String): RenderEffect
}

/** Compiles [source] into a platform program. Heavy — always cache in `remember`. */
internal expect fun createMirageProgram(source: String): MirageProgram

/**
 * Whether runtime shaders are usable on this platform. Skia platforms return
 * true; Android returns true only on API 33 (Tiramisu) and above, where
 * `RuntimeShader` exists.
 */
public expect fun mirageShadersSupported(): Boolean
