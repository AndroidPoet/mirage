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

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/**
 * Host for procedural (non-image) shaders: fills the box with the shader.
 *
 * [setUniforms] runs on every draw and receives the current animation time in
 * seconds; shaders that declare u_time must forward it themselves
 * (setFloatUniform("u_time", time)) — AGSL strips unused uniforms, and setting
 * a stripped uniform throws, so the host cannot set it unconditionally.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun MirageSurface(
  shaderSource: String,
  modifier: Modifier = Modifier,
  speed: Float = 0f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Object,
  setUniforms: RuntimeShader.(time: Float) -> Unit,
) {
  val shader = remember(shaderSource) { RuntimeShader(shaderSource) }
  val time = rememberShaderTime(speed, startFrame)
  Box(
    modifier.drawWithCache {
      val brush = ShaderBrush(shader)
      onDrawBehind {
        shader.setSizingUniforms(sizing, size.width, size.height, density)
        shader.setUniforms(time.value)
        drawRect(brush)
      }
    },
  )
}

/**
 * Host for image-filter shaders: the layer content is fed to the shader as
 * the child shader named "u_image". Sample it with u_image.eval(px) where px
 * is in layer pixels (multiply normalized UVs by u_resolution).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun Modifier.mirageEffect(
  shaderSource: String,
  speed: Float = 0f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams(fit = ShaderFit.Cover),
  setUniforms: RuntimeShader.(time: Float) -> Unit,
): Modifier {
  val shader = remember(shaderSource) { RuntimeShader(shaderSource) }
  val time = rememberShaderTime(speed, startFrame)
  val pixelRatio = LocalDensity.current.density
  return graphicsLayer {
    if (size.width > 0f && size.height > 0f) {
      shader.setSizingUniforms(
        sizing = sizing,
        width = size.width,
        height = size.height,
        pixelRatio = pixelRatio,
        imageAspectRatio = size.width / size.height,
      )
      shader.setUniforms(time.value)
      renderEffect = RenderEffect
        .createRuntimeShaderEffect(shader, "u_image")
        .asComposeRenderEffect()
    }
    clip = true
  }
}
