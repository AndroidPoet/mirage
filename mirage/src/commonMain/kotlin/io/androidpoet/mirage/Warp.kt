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
import io.androidpoet.mirage.core.setColorsUniform

private const val MAX_COLORS = 10

private val SHADER = """
$SIZING_AGSL

uniform float u_time;

uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;
uniform float u_proportion;
uniform float u_softness;
uniform float u_shape;
uniform float u_shapeScale;
uniform float u_distortion;
uniform float u_swirl;
uniform float u_swirlIterations;

${Agsl.PI}
${Agsl.ROTATION2}
${Agsl.BANDING_FIX}

float randomG(vec2 p) {
  vec2 q = fract(floor(p) * vec2(0.3183099, 0.3678794)) + 0.1;
  q += dot(q, q + 19.19);
  return fract(q.x * q.y);
}
float valueNoise(vec2 st) {
  vec2 i = floor(st);
  vec2 f = fract(st);
  float a = randomG(i);
  float b = randomG(i + vec2(1.0, 0.0));
  float c = randomG(i + vec2(0.0, 1.0));
  float d = randomG(i + vec2(1.0, 1.0));
  vec2 u = f * f * (3.0 - 2.0 * f);
  float x1 = mix(a, b, u.x);
  float x2 = mix(c, d, u.x);
  return mix(x1, x2, u.y);
}

float getShape(vec2 uv, float t) {
  float n1 = valueNoise(uv * 1. + t);
  float n2 = valueNoise(uv * 2. - t);
  float angle = n1 * TWO_PI;
  uv.x += 4. * u_distortion * n2 * cos(angle);
  uv.y += 4. * u_distortion * n2 * sin(angle);

  float swirl = u_swirl;
  for (int i = 1; i <= 20; i++) {
    if (i < int(u_swirlIterations)) {
      float iFloat = float(i);
      uv.x += swirl / iFloat * cos(t + iFloat * 1.5 * uv.y);
      uv.y += swirl / iFloat * cos(t + iFloat * 1. * uv.x);
    }
  }

  float proportion = clamp(u_proportion, 0., 1.);

  float shape = 0.;
  if (u_shape < .5) {
    vec2 checksShape_uv = uv * (.5 + 3.5 * u_shapeScale);
    shape = .5 + .5 * sin(checksShape_uv.x) * cos(checksShape_uv.y);
    shape += .48 * sign(proportion - .5) * pow(abs(proportion - .5), .5);
  } else if (u_shape < 1.5) {
    vec2 stripesShape_uv = uv * (2. * u_shapeScale);
    float f = fract(stripesShape_uv.y);
    shape = smoothstep(.0, .55, f) * (1.0 - smoothstep(.45, 1., f));
    shape += .48 * sign(proportion - .5) * pow(abs(proportion - .5), .5);
  } else {
    float shapeScaling = 5. * (1. - u_shapeScale);
    float e0 = 0.45 - shapeScaling;
    float e1 = 0.55 + shapeScaling;
    shape = smoothstep(min(e0, e1), max(e0, e1), 1.0 - uv.y + 0.3 * (proportion - 0.5));
  }
  return shape;
}

half4 main(vec2 fragCoord) {
  const float firstFrameOffset = 118.;
  float t = 0.0625 * (u_time + firstFrameOffset);

  vec2 uv = getPatternUV(fragCoord);
  uv *= .5;

  float shape = getShape(uv, t);

  // AGSL has no derivative intrinsics: estimate the screen-space derivative
  // of the shape field with finite differences one pixel over.
  vec2 uvX = getPatternUV(fragCoord + vec2(1., 0.)) * .5;
  vec2 uvY = getPatternUV(fragCoord + vec2(0., 1.)) * .5;
  float shapeX = getShape(uvX, t);
  float shapeY = getShape(uvY, t);

  float mixer = shape * (u_colorsCount - 1.);
  float mixerX = shapeX * (u_colorsCount - 1.);
  float mixerY = shapeY * (u_colorsCount - 1.);
  vec4 gradient = u_colors[0];
  gradient.rgb *= gradient.a;
  float aa = abs(shapeX - shape) + abs(shapeY - shape);
  for (int i = 1; i < $MAX_COLORS; i++) {
    if (i < int(u_colorsCount)) {
      float m = clamp(mixer - float(i - 1), 0.0, 1.0);
      float mX = clamp(mixerX - float(i - 1), 0.0, 1.0);
      float mY = clamp(mixerY - float(i - 1), 0.0, 1.0);
      float fwM = abs(mX - m) + abs(mY - m);

      float localMixerStart = floor(m);
      float softness = .5 * u_softness + fwM;
      float smoothed = smoothstep(max(0., .5 - softness - aa), min(1., .5 + softness + aa), m - localMixerStart);
      float stepped = localMixerStart + smoothed;

      m = mix(stepped, m, u_softness);

      vec4 c = u_colors[i];
      c.rgb *= c.a;
      gradient = mix(gradient, c, m);
    }
  }

  vec3 color = gradient.rgb;
  float opacity = gradient.a;

  color += bandingNoise(fragCoord);

  return half4(color, opacity);
}
"""

public enum class WarpPattern(public val value: Float) {
  Checks(0f),
  Stripes(1f),
  Edge(2f),
}

public object WarpDefaults {
  public val Colors: List<Color> = listOf(
    Color(0xFF121212),
    Color(0xFF9470FF),
    Color(0xFF121212),
    Color(0xFF8838FF),
  )
}

/**
 * Animated color fields warped by noise and swirls, applied over checks,
 * stripes, or split-edge base patterns.
 */
@Composable
public fun Warp(
  modifier: Modifier = Modifier,
  colors: List<Color> = WarpDefaults.Colors,
  proportion: Float = 0.45f,
  softness: Float = 1f,
  shape: WarpPattern = WarpPattern.Checks,
  shapeScale: Float = 0.1f,
  distortion: Float = 0.25f,
  swirl: Float = 0.8f,
  swirlIterations: Int = 10,
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
    setColorsUniform("u_colors", "u_colorsCount", colors, MAX_COLORS)
    setFloatUniform("u_proportion", proportion)
    setFloatUniform("u_softness", softness)
    setFloatUniform("u_shape", shape.value)
    setFloatUniform("u_shapeScale", shapeScale)
    setFloatUniform("u_distortion", distortion)
    setFloatUniform("u_swirl", swirl)
    setFloatUniform("u_swirlIterations", swirlIterations.toFloat())
  }
}
