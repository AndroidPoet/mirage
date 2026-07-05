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

uniform float u_distortion;
uniform float u_swirl;
uniform float u_grainMixer;
uniform float u_grainOverlay;

${Agsl.PI}
${Agsl.ROTATION2}
${Agsl.HASH21}

float valueNoise(vec2 st) {
  vec2 i = floor(st);
  vec2 f = fract(st);
  float a = hash21(i);
  float b = hash21(i + vec2(1.0, 0.0));
  float c = hash21(i + vec2(0.0, 1.0));
  float d = hash21(i + vec2(1.0, 1.0));
  vec2 u = f * f * (3.0 - 2.0 * f);
  float x1 = mix(a, b, u.x);
  float x2 = mix(c, d, u.x);
  return mix(x1, x2, u.y);
}

float noise(vec2 n, vec2 seedOffset) {
  return valueNoise(n + seedOffset);
}

vec2 getPosition(int i, float t) {
  float a = float(i) * .37;
  float b = .6 + fract(float(i) / 3.) * .9;
  float c = .8 + fract(float(i + 1) / 4.);

  float x = sin(t * b + a);
  float y = cos(t * c + a * 1.5);

  return .5 + .5 * vec2(x, y);
}

half4 main(vec2 fragCoord) {
  vec2 uv = getObjectUV(fragCoord);
  uv += .5;
  vec2 grainUV = uv * 1000.;

  float grain = noise(grainUV, vec2(0.));
  float mixerGrain = .4 * u_grainMixer * (grain - .5);

  const float firstFrameOffset = 41.5;
  float t = .5 * (u_time + firstFrameOffset);

  float radius = smoothstep(0., 1., length(uv - .5));
  float center = 1. - radius;
  for (float i = 1.; i <= 2.; i++) {
    uv.x += u_distortion * center / i * sin(t + i * .4 * smoothstep(.0, 1., uv.y)) * cos(.2 * t + i * 2.4 * smoothstep(.0, 1., uv.y));
    uv.y += u_distortion * center / i * cos(t + i * 2. * smoothstep(.0, 1., uv.x));
  }

  vec2 uvRotated = uv;
  uvRotated -= vec2(.5);
  float angle = 3. * u_swirl * radius;
  uvRotated = rotate(uvRotated, -angle);
  uvRotated += vec2(.5);

  vec3 color = vec3(0.);
  float opacity = 0.;
  float totalWeight = 0.;

  for (int i = 0; i < $MAX_COLORS; i++) {
    if (i < int(u_colorsCount)) {
      vec2 pos = getPosition(i, t) + mixerGrain;
      vec3 colorFraction = u_colors[i].rgb * u_colors[i].a;
      float opacityFraction = u_colors[i].a;

      float dist = length(uvRotated - pos);

      dist = pow(dist, 3.5);
      float weight = 1. / (dist + 1e-3);
      color += colorFraction * weight;
      opacity += opacityFraction * weight;
      totalWeight += weight;
    }
  }

  color /= max(1e-4, totalWeight);
  opacity /= max(1e-4, totalWeight);

  float grainOverlay = valueNoise(rotate(grainUV, 1.) + vec2(3.));
  grainOverlay = mix(grainOverlay, valueNoise(rotate(grainUV, 2.) + vec2(-1.)), .5);
  grainOverlay = pow(grainOverlay, 1.3);

  float grainOverlayV = grainOverlay * 2. - 1.;
  vec3 grainOverlayColor = vec3(step(0., grainOverlayV));
  float grainOverlayStrength = u_grainOverlay * abs(grainOverlayV);
  grainOverlayStrength = pow(grainOverlayStrength, .8);
  color = mix(color, grainOverlayColor, .35 * grainOverlayStrength);

  opacity += .5 * grainOverlayStrength;
  opacity = clamp(opacity, 0., 1.);

  return half4(color, opacity);
}
"""

public object MeshGradientDefaults {
  public val Colors: List<Color> = listOf(
    Color(0xFFE0EAFF),
    Color(0xFF241D9A),
    Color(0xFFF75092),
    Color(0xFF9F50D3),
  )
}

/**
 * A flowing composition of color spots, moving along distinct trajectories
 * and transformed by organic distortion.
 */
@Composable
public fun MeshGradient(
  modifier: Modifier = Modifier,
  colors: List<Color> = MeshGradientDefaults.Colors,
  distortion: Float = 0.8f,
  swirl: Float = 0.1f,
  grainMixer: Float = 0f,
  grainOverlay: Float = 0f,
  speed: Float = 1f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Object,
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
    setFloatUniform("u_distortion", distortion)
    setFloatUniform("u_swirl", swirl)
    setFloatUniform("u_grainMixer", grainMixer)
    setFloatUniform("u_grainOverlay", grainOverlay)
  }
}
