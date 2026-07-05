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
package io.androidpoet.mirage

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.androidpoet.mirage.core.Agsl
import io.androidpoet.mirage.core.MirageSurface
import io.androidpoet.mirage.core.SIZING_AGSL
import io.androidpoet.mirage.core.SizingParams
import io.androidpoet.mirage.core.setColorUniform4f

private val SHADER = """
$SIZING_AGSL

uniform vec4 u_colorFront;
uniform vec4 u_colorBack;
uniform float u_shape;
uniform float u_frequency;
uniform float u_amplitude;
uniform float u_spacing;
uniform float u_proportion;
uniform float u_softness;

${Agsl.PI}

float getWaveShape(vec2 shape_uv) {
  float wave = .5 * cos(shape_uv.x * u_frequency * TWO_PI);
  float zigzag = 2. * abs(fract(shape_uv.x * u_frequency) - .5);
  float irregular = sin(shape_uv.x * .25 * u_frequency * TWO_PI) * cos(shape_uv.x * u_frequency * TWO_PI);
  float irregular2 = .75 * (sin(shape_uv.x * u_frequency * TWO_PI) + .5 * cos(shape_uv.x * .5 * u_frequency * TWO_PI));

  float offset = mix(zigzag, wave, smoothstep(0., 1., u_shape));
  offset = mix(offset, irregular, smoothstep(1., 2., u_shape));
  offset = mix(offset, irregular2, smoothstep(2., 3., u_shape));
  offset *= 2. * u_amplitude;

  float spacing = (.001 + u_spacing);
  return .5 + .5 * sin((shape_uv.y + offset) * PI / spacing);
}

half4 main(vec2 fragCoord) {
  vec2 patternUV = getPatternUV(fragCoord);
  vec2 shape_uv = patternUV * 4.;

  float shape = getWaveShape(shape_uv);

  // AGSL has no derivative intrinsics: estimate the screen-space derivative
  // of the wave field with finite differences one pixel over.
  vec2 duvx = (getPatternUV(fragCoord + vec2(1., 0.)) - patternUV) * 4.;
  vec2 duvy = (getPatternUV(fragCoord + vec2(0., 1.)) - patternUV) * 4.;
  float shapeX = getWaveShape(shape_uv + duvx);
  float shapeY = getWaveShape(shape_uv + duvy);
  float fwShape = abs(shapeX - shape) + abs(shapeY - shape);

  float aa = .0001 + fwShape;
  float dc = 1. - clamp(u_proportion, 0., 1.);
  float e0 = dc - u_softness - aa;
  float e1 = dc + u_softness + aa;
  float res = smoothstep(min(e0, e1), max(e0, e1), shape);

  vec3 fgColor = u_colorFront.rgb * u_colorFront.a;
  float fgOpacity = u_colorFront.a;
  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  float bgOpacity = u_colorBack.a;

  vec3 color = fgColor * res;
  float opacity = fgOpacity * res;

  color += bgColor * (1. - opacity);
  opacity += bgOpacity * (1. - opacity);

  return half4(color, opacity);
}
"""

public object WavesDefaults {
  public val ColorFront: Color = Color(0xFFFFBB00)
  public val ColorBack: Color = Color(0xFF000000)
}

/**
 * A static line pattern configurable into textures ranging from sharp
 * zigzags to smooth flowing waves.
 */
@Composable
public fun Waves(
  modifier: Modifier = Modifier,
  colorFront: Color = WavesDefaults.ColorFront,
  colorBack: Color = WavesDefaults.ColorBack,
  shape: Float = 0f,
  frequency: Float = 0.5f,
  amplitude: Float = 0.5f,
  spacing: Float = 1.2f,
  proportion: Float = 0.1f,
  softness: Float = 0f,
  sizing: SizingParams = SizingParams.Pattern.copy(scale = 0.6f),
) {
  MirageSurface(
    shaderSource = SHADER,
    modifier = modifier,
    sizing = sizing,
  ) { _ ->
    setColorUniform4f("u_colorFront", colorFront)
    setColorUniform4f("u_colorBack", colorBack)
    setFloatUniform("u_shape", shape)
    setFloatUniform("u_frequency", frequency)
    setFloatUniform("u_amplitude", amplitude)
    setFloatUniform("u_spacing", spacing)
    setFloatUniform("u_proportion", proportion)
    setFloatUniform("u_softness", softness)
  }
}
