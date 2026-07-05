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
import io.androidpoet.mirage.core.setColorsUniform

private const val MAX_COLORS = 5

private val SHADER = """
$SIZING_AGSL

uniform float u_time;

uniform vec4 u_colorBack;
uniform vec4 u_colorBloom;
uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;

uniform float u_density;
uniform float u_spotty;
uniform float u_midSize;
uniform float u_midIntensity;
uniform float u_intensity;
uniform float u_bloom;

${Agsl.PI}
${Agsl.ROTATION2}
${Agsl.RANDOM_R}
${Agsl.HASH11}
${Agsl.BANDING_FIX}

float valueNoise(vec2 st) {
  vec2 i = floor(st);
  vec2 f = fract(st);
  float a = randomR(i);
  float b = randomR(i + vec2(1.0, 0.0));
  float c = randomR(i + vec2(0.0, 1.0));
  float d = randomR(i + vec2(1.0, 1.0));
  vec2 u = f * f * (3.0 - 2.0 * f);
  float x1 = mix(a, b, u.x);
  float x2 = mix(c, d, u.x);
  return mix(x1, x2, u.y);
}

float raysShape(vec2 uv, float r, float freq, float intensity, float radius) {
  float a = atan(uv.y, uv.x);
  vec2 left = vec2(a * freq, r);
  vec2 right = vec2(fract(a / TWO_PI) * TWO_PI * freq, r);
  float n_left = pow(valueNoise(left), intensity);
  float n_right = pow(valueNoise(right), intensity);
  float shape = mix(n_right, n_left, smoothstep(-.15, .15, uv.x));
  return shape;
}

half4 main(vec2 fragCoord) {
  vec2 shape_uv = getObjectUV(fragCoord);

  float t = .2 * u_time;

  float radius = length(shape_uv);
  float spots = 6.5 * abs(u_spotty);

  float intensity = 4. - 3. * clamp(u_intensity, 0., 1.);

  float delta = 1. - smoothstep(0., 1., radius);

  float midSize = 10. * abs(u_midSize);
  float ms_lo = 0.02 * midSize;
  float ms_hi = max(midSize, 1e-6);
  float middleShape = pow(u_midIntensity, 0.3) * (1. - smoothstep(ms_lo, ms_hi, 3.0 * radius));
  middleShape = pow(middleShape, 5.0);

  vec3 accumColor = vec3(0.0);
  float accumAlpha = 0.0;

  for (int i = 0; i < $MAX_COLORS; i++) {
    if (i < int(u_colorsCount)) {
      vec2 rotatedUV = rotate(shape_uv, float(i) + 1.0);

      float r1 = radius * (1.0 + 0.4 * float(i)) - 3.0 * t;
      float r2 = 0.5 * radius * (1.0 + spots) - 2.0 * t;
      float density = 6. * u_density + step(.5, u_density) * pow(4.5 * (u_density - .5), 4.);
      float f = mix(1.0, 3.0 + 0.5 * float(i), hash11(float(i) * 15.)) * density;

      float ray = raysShape(rotatedUV, r1, 5.0 * f, intensity, radius);
      ray *= raysShape(rotatedUV, r2, 4.0 * f, intensity, radius);
      ray += (1. + 4. * ray) * middleShape;
      ray = clamp(ray, 0.0, 1.0);

      float srcAlpha = u_colors[i].a * ray;
      vec3 srcColor = u_colors[i].rgb * srcAlpha;

      vec3 alphaBlendColor = accumColor + (1.0 - accumAlpha) * srcColor;
      float alphaBlendAlpha = accumAlpha + (1.0 - accumAlpha) * srcAlpha;

      vec3 addBlendColor = accumColor + srcColor;
      float addBlendAlpha = accumAlpha + srcAlpha;

      accumColor = mix(alphaBlendColor, addBlendColor, u_bloom);
      accumAlpha = mix(alphaBlendAlpha, addBlendAlpha, u_bloom);
    }
  }

  float overlayAlpha = u_colorBloom.a;
  vec3 overlayColor = u_colorBloom.rgb * overlayAlpha;

  vec3 colorWithOverlay = accumColor + accumAlpha * overlayColor;
  accumColor = mix(accumColor, colorWithOverlay, u_bloom);

  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;

  vec3 color = accumColor + (1. - accumAlpha) * bgColor;
  float opacity = accumAlpha + (1. - accumAlpha) * u_colorBack.a;
  color = clamp(color, 0., 1.);
  opacity = clamp(opacity, 0., 1.);

  color += bandingNoise(fragCoord);

  return half4(color, opacity);
}
"""

public object GodRaysDefaults {
  public val ColorBack: Color = Color(0xFF000000)
  public val ColorBloom: Color = Color(0xFF0000FF)
  public val Colors: List<Color> = listOf(
    Color(0x6EA600FF),
    Color(0xF06200FF),
    Color(0xFFFFFFFF),
    Color(0xFF33FFF5),
  )
}

/**
 * Animated rays of light radiating from the center, blended with up to 5
 * colors.
 */
@Composable
public fun GodRays(
  modifier: Modifier = Modifier,
  colorBack: Color = GodRaysDefaults.ColorBack,
  colorBloom: Color = GodRaysDefaults.ColorBloom,
  colors: List<Color> = GodRaysDefaults.Colors,
  density: Float = 0.3f,
  spotty: Float = 0.3f,
  midSize: Float = 0.2f,
  midIntensity: Float = 0.4f,
  intensity: Float = 0.8f,
  bloom: Float = 0.4f,
  speed: Float = 0.75f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Object.copy(offsetY = -0.55f),
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
    setColorUniform4f("u_colorBloom", colorBloom)
    setColorsUniform("u_colors", "u_colorsCount", colors, MAX_COLORS)
    setFloatUniform("u_density", density)
    setFloatUniform("u_spotty", spotty)
    setFloatUniform("u_midSize", midSize)
    setFloatUniform("u_midIntensity", midIntensity)
    setFloatUniform("u_intensity", intensity)
    setFloatUniform("u_bloom", bloom)
  }
}
