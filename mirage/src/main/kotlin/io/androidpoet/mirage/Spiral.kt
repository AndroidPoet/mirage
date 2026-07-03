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

import android.os.Build
import androidx.annotation.RequiresApi
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

uniform float u_time;

uniform vec4 u_colorBack;
uniform vec4 u_colorFront;
uniform float u_density;
uniform float u_distortion;
uniform float u_strokeWidth;
uniform float u_strokeCap;
uniform float u_strokeTaper;
uniform float u_noise;
uniform float u_noiseFrequency;
uniform float u_softness;

${Agsl.PI}
${Agsl.SIMPLEX_NOISE}
${Agsl.BANDING_FIX}

float getSpiralOffset(vec2 uv, float t) {
  float l = length(uv);
  float density = clamp(u_density, 0., 1.);
  l = pow(max(l, 1e-6), density);
  float angle = atan(uv.y, uv.x) - t;
  float angleNormalised = angle / TWO_PI;

  angleNormalised += .125 * u_noise * snoise(16. * pow(u_noiseFrequency, 3.) * uv);

  float offset = l + angleNormalised;
  offset -= u_distortion * (sin(4. * l - .5 * t) * cos(PI + l + .5 * t));
  return offset;
}

half4 main(vec2 fragCoord) {
  vec2 patternUV = getPatternUV(fragCoord);
  vec2 uv = 2. * patternUV;

  float t = u_time;
  float l = length(uv);
  float density = clamp(u_density, 0., 1.);
  l = pow(max(l, 1e-6), density);

  float offset = getSpiralOffset(uv, t);
  float stripe = fract(offset);

  float shape = 2. * abs(stripe - .5);
  float width = 1. - clamp(u_strokeWidth, .005 * u_strokeTaper, 1.);

  float wCap = mix(width, (1. - stripe) * (1. - step(.5, stripe)), (1. - clamp(l, 0., 1.)));
  width = mix(width, wCap, u_strokeCap);
  width *= (1. - clamp(u_strokeTaper, 0., 1.) * l);

  // AGSL has no derivative intrinsics: estimate the screen-space derivatives
  // of the spiral offset and stripe shape with finite differences one pixel over.
  vec2 duvx = 2. * (getPatternUV(fragCoord + vec2(1., 0.)) - patternUV);
  vec2 duvy = 2. * (getPatternUV(fragCoord + vec2(0., 1.)) - patternUV);
  float offsetX = getSpiralOffset(uv + duvx, t);
  float offsetY = getSpiralOffset(uv + duvy, t);
  float fw = abs(offsetX - offset) + abs(offsetY - offset);
  float shapeX = 2. * abs(fract(offsetX) - .5);
  float shapeY = 2. * abs(fract(offsetY) - .5);
  float fwShape = abs(shapeX - shape) + abs(shapeY - shape);

  float fwMult = 4. - 3. * (smoothstep(.05, .4, 2. * u_strokeWidth) * smoothstep(.05, .4, 2. * (1. - u_strokeWidth)));
  float pixelSize = mix(fwMult * fw, fwShape, clamp(fw, 0., 1.));
  pixelSize = mix(pixelSize, .002, u_strokeCap * (1. - clamp(l, 0., 1.)));

  float res = smoothstep(width - pixelSize - u_softness, width + pixelSize + u_softness, shape);

  vec3 fgColor = u_colorFront.rgb * u_colorFront.a;
  float fgOpacity = u_colorFront.a;
  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  float bgOpacity = u_colorBack.a;

  vec3 color = fgColor * res;
  float opacity = fgOpacity * res;

  color += bgColor * (1. - opacity);
  opacity += bgOpacity * (1. - opacity);

  color += bandingNoise(fragCoord);

  return half4(color, opacity);
}
"""

public object SpiralDefaults {
  public val ColorBack: Color = Color(0xFF001429)
  public val ColorFront: Color = Color(0xFF79D1FF)
}

/**
 * A single-colored animated spiral that morphs across a wide range of shapes,
 * from crisp, thin-lined geometry to flowing whirlpool forms and wavy,
 * abstract rings.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun Spiral(
  modifier: Modifier = Modifier,
  colorBack: Color = SpiralDefaults.ColorBack,
  colorFront: Color = SpiralDefaults.ColorFront,
  density: Float = 1f,
  distortion: Float = 0f,
  strokeWidth: Float = 0.5f,
  strokeTaper: Float = 0f,
  strokeCap: Float = 0f,
  noise: Float = 0f,
  noiseFrequency: Float = 0f,
  softness: Float = 0f,
  speed: Float = 1f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Pattern,
) {
  MirageSurface(
    shaderSource = SHADER,
    modifier = modifier,
    speed = speed,
    startFrame = startFrame,
    sizing = sizing,
  ) { time ->
    setFloatUniform("u_time", time)
    setColorUniform4f("u_colorBack", colorBack)
    setColorUniform4f("u_colorFront", colorFront)
    setFloatUniform("u_density", density)
    setFloatUniform("u_distortion", distortion)
    setFloatUniform("u_strokeWidth", strokeWidth)
    setFloatUniform("u_strokeCap", strokeCap)
    setFloatUniform("u_strokeTaper", strokeTaper)
    setFloatUniform("u_noise", noise)
    setFloatUniform("u_noiseFrequency", noiseFrequency)
    setFloatUniform("u_softness", softness)
  }
}
