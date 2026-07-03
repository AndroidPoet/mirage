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
import io.androidpoet.mirage.core.setColorsUniform

private const val MAX_COLORS = 10
private const val MAX_NOISE_ITERATIONS = 8

private val SHADER = """
$SIZING_AGSL

uniform float u_time;

uniform vec4 u_colorBack;
uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;

uniform float u_thickness;
uniform float u_radius;
uniform float u_innerShape;
uniform float u_noiseScale;
uniform float u_noiseIterations;

${Agsl.PI}
${Agsl.RANDOM_R}
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
vec2 fbm(vec2 n0, vec2 n1) {
  vec2 total = vec2(0.0);
  float amplitude = .4;
  for (int i = 0; i < $MAX_NOISE_ITERATIONS; i++) {
    if (i < int(u_noiseIterations)) {
      total.x += valueNoise(n0) * amplitude;
      total.y += valueNoise(n1) * amplitude;
      n0 *= 1.99;
      n1 *= 1.99;
      amplitude *= 0.65;
    }
  }
  return total;
}

float getNoise(vec2 uv, vec2 pUv, float t) {
  vec2 pUvLeft = pUv + .03 * t;
  float period = max(abs(u_noiseScale * TWO_PI), 1e-6);
  vec2 pUvRight = vec2(fract(pUv.x / period) * period, pUv.y) + .03 * t;
  vec2 noise = fbm(pUvLeft, pUvRight);
  return mix(noise.y, noise.x, smoothstep(-.25, .25, uv.x));
}

float getRingShape(vec2 uv) {
  float radius = u_radius;
  float thickness = u_thickness;

  float dist = length(uv);
  float ringValue = 1. - smoothstep(radius, radius + thickness, dist);
  ringValue *= smoothstep(radius - pow(u_innerShape, 3.) * thickness, radius, dist);

  return ringValue;
}

half4 main(vec2 fragCoord) {
  vec2 shape_uv = getObjectUV(fragCoord);

  float t = u_time;

  float cycleDuration = 3.;
  float period2 = 2.0 * cycleDuration;
  float localTime1 = fract((0.1 * t + cycleDuration) / period2) * period2;
  float localTime2 = fract((0.1 * t) / period2) * period2;
  float timeBlend = .5 + .5 * sin(.1 * t * PI / cycleDuration - .5 * PI);

  float atg = atan(shape_uv.y, shape_uv.x) + .001;
  float l = length(shape_uv);
  float radialOffset = .5 * l - inversesqrt(max(1e-4, l));
  vec2 polar_uv1 = vec2(atg, localTime1 - radialOffset) * u_noiseScale;
  vec2 polar_uv2 = vec2(atg, localTime2 - radialOffset) * u_noiseScale;

  float noise1 = getNoise(shape_uv, polar_uv1, t);
  float noise2 = getNoise(shape_uv, polar_uv2, t);

  float noise = mix(noise1, noise2, timeBlend);

  shape_uv *= (.8 + 1.2 * noise);

  float ringShape = getRingShape(shape_uv);

  float mixer = ringShape * ringShape * (u_colorsCount - 1.);
  int idxLast = int(u_colorsCount) - 1;
  vec4 gradient = u_colors[0];
  for (int i = 0; i < $MAX_COLORS; i++) {
    if (i == idxLast) {
      gradient = u_colors[i];
    }
  }
  gradient.rgb *= gradient.a;
  for (int i = ${MAX_COLORS - 2}; i >= 0; i--) {
    float localT = clamp(mixer - float(idxLast - i - 1), 0., 1.);
    vec4 c = u_colors[i];
    c.rgb *= c.a;
    gradient = mix(gradient, c, localT);
  }

  vec3 color = gradient.rgb * ringShape;
  float opacity = gradient.a * ringShape;

  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  color = color + bgColor * (1. - opacity);
  opacity = opacity + u_colorBack.a * (1. - opacity);

  color += bandingNoise(fragCoord);

  return half4(color, opacity);
}
"""

public object SmokeRingDefaults {
  public val ColorBack: Color = Color(0xFF000000)
  public val Colors: List<Color> = listOf(
    Color(0xFFFFFFFF),
  )
}

/**
 * Radial multi-colored gradient shaped with layered noise for a natural,
 * smoky aesthetic.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun SmokeRing(
  modifier: Modifier = Modifier,
  colorBack: Color = SmokeRingDefaults.ColorBack,
  colors: List<Color> = SmokeRingDefaults.Colors,
  noiseScale: Float = 3f,
  noiseIterations: Int = 8,
  radius: Float = 0.25f,
  thickness: Float = 0.65f,
  innerShape: Float = 0.7f,
  speed: Float = 0.5f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Object.copy(scale = 0.8f),
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
    setColorsUniform("u_colors", "u_colorsCount", colors, MAX_COLORS)
    setFloatUniform("u_thickness", thickness)
    setFloatUniform("u_radius", radius)
    setFloatUniform("u_innerShape", innerShape)
    setFloatUniform("u_noiseScale", noiseScale)
    setFloatUniform("u_noiseIterations", noiseIterations.toFloat())
  }
}
