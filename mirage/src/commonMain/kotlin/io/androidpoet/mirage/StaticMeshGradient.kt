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

uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;

uniform float u_positions;
uniform float u_waveX;
uniform float u_waveXShift;
uniform float u_waveY;
uniform float u_waveYShift;
uniform float u_mixing;
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
  float b = .6 + mod(float(i), 3.) * .3;
  float c = .8 + mod(float(i + 1), 4.) * 0.25;

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

  float radius = smoothstep(0., 1., length(uv - .5));
  float center = 1. - radius;
  for (float i = 1.; i <= 2.; i++) {
    uv.x += u_waveX * center / i * cos(TWO_PI * u_waveXShift + i * 2. * smoothstep(.0, 1., uv.y));
    uv.y += u_waveY * center / i * cos(TWO_PI * u_waveYShift + i * 2. * smoothstep(.0, 1., uv.x));
  }

  vec3 color = vec3(0.);
  float opacity = 0.;
  float totalWeight = 0.;
  float positionSeed = 25. + .33 * u_positions;

  for (int i = 0; i < $MAX_COLORS; i++) {
    if (i < int(u_colorsCount)) {
      vec2 pos = getPosition(i, positionSeed) + mixerGrain;
      float dist = length(uv - pos);
      dist = length(uv - pos);

      vec3 colorFraction = u_colors[i].rgb * u_colors[i].a;
      float opacityFraction = u_colors[i].a;

      float mixing = pow(u_mixing, .7);
      float power = mix(2., 1., mixing);
      dist = pow(dist, power);

      float w = 1. / (dist + 1e-3);
      float baseSharpness = mix(.0, 8., clamp(w, 0., 1.));
      float sharpness = mix(baseSharpness, 1., mixing);
      w = pow(w, sharpness);
      color += colorFraction * w;
      opacity += opacityFraction * w;
      totalWeight += w;
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

public object StaticMeshGradientDefaults {
  public val Colors: List<Color> = listOf(
    Color(0xFFFFAD0A),
    Color(0xFF6200FF),
    Color(0xFFE2A3FF),
    Color(0xFFFF99FD),
  )
}

/**
 * Multi-point mesh gradient with up to 10 color spots, enhanced by
 * two-direction warping, adjustable blend sharpness, and grain controls.
 */
@Composable
public fun StaticMeshGradient(
  modifier: Modifier = Modifier,
  colors: List<Color> = StaticMeshGradientDefaults.Colors,
  positions: Float = 2f,
  waveX: Float = 1f,
  waveXShift: Float = 0.6f,
  waveY: Float = 1f,
  waveYShift: Float = 0.21f,
  mixing: Float = 0.93f,
  grainMixer: Float = 0f,
  grainOverlay: Float = 0f,
  sizing: SizingParams = SizingParams.Object.copy(rotation = 270f),
) {
  MirageSurface(
    shaderSource = SHADER,
    modifier = modifier,
    sizing = sizing,
  ) { _ ->
    setColorsUniform("u_colors", "u_colorsCount", colors, MAX_COLORS)
    setFloatUniform("u_positions", positions)
    setFloatUniform("u_waveX", waveX)
    setFloatUniform("u_waveXShift", waveXShift)
    setFloatUniform("u_waveY", waveY)
    setFloatUniform("u_waveYShift", waveYShift)
    setFloatUniform("u_mixing", mixing)
    setFloatUniform("u_grainMixer", grainMixer)
    setFloatUniform("u_grainOverlay", grainOverlay)
  }
}
