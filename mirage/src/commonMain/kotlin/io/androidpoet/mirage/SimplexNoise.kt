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
uniform float u_stepsPerColor;
uniform float u_softness;

${Agsl.SIMPLEX_NOISE}
${Agsl.BANDING_FIX}

float getNoise(vec2 uv, float t) {
  float noise = .5 * snoise(uv - vec2(0., .3 * t));
  noise += .5 * snoise(2. * uv + vec2(0., .32 * t));
  return noise;
}

float steppedSmooth(float m, float steps, float softness, float fw) {
  float stepT = floor(m * steps) / steps;
  float f = m * steps - floor(m * steps);
  float smoothed = smoothstep(.5 - softness, min(1., .5 + softness + steps * fw), f);
  return stepT + smoothed / steps;
}

half4 main(vec2 fragCoord) {
  vec2 patternUV = getPatternUV(fragCoord);
  vec2 shape_uv = patternUV * .1;

  float t = .2 * u_time;

  float shape = .5 + .5 * getNoise(shape_uv, t);

  // fwidth() is unavailable in AGSL: estimate the screen-space derivative of
  // the noise field with finite differences one pixel over.
  vec2 duvx = (getPatternUV(fragCoord + vec2(1., 0.)) - patternUV) * .1;
  vec2 duvy = (getPatternUV(fragCoord + vec2(0., 1.)) - patternUV) * .1;
  float shapeX = .5 + .5 * getNoise(shape_uv + duvx, t);
  float shapeY = .5 + .5 * getNoise(shape_uv + duvy, t);
  float fwMixer = (abs(shapeX - shape) + abs(shapeY - shape)) * u_colorsCount;

  float mixer = (shape - .5 / u_colorsCount) * u_colorsCount;

  float steps = max(1., u_stepsPerColor);

  vec4 gradient = u_colors[0];
  gradient.rgb *= gradient.a;
  for (int i = 1; i < $MAX_COLORS; i++) {
    if (i < int(u_colorsCount)) {
      float localM = clamp(mixer - float(i - 1), 0., 1.);
      localM = steppedSmooth(localM, steps, .5 * u_softness, fwMixer);

      vec4 c = u_colors[i];
      c.rgb *= c.a;
      gradient = mix(gradient, c, localM);
    }
  }

  if ((mixer < 0.) || (mixer > (u_colorsCount - 1.))) {
    float localM = mixer + 1.;
    if (mixer > (u_colorsCount - 1.)) {
      localM = mixer - (u_colorsCount - 1.);
    }
    localM = steppedSmooth(localM, steps, .5 * u_softness, fwMixer);
    vec4 cFst = u_colors[0];
    cFst.rgb *= cFst.a;
    vec4 cLast = u_colors[0];
    for (int i = 0; i < $MAX_COLORS; i++) {
      if (i == int(u_colorsCount - 1.)) {
        cLast = u_colors[i];
      }
    }
    cLast.rgb *= cLast.a;
    gradient = mix(cLast, cFst, localM);
  }

  vec3 color = gradient.rgb;
  float opacity = gradient.a;

  color += bandingNoise(fragCoord);

  return half4(color, opacity);
}
"""

public object SimplexNoiseDefaults {
  public val Colors: List<Color> = listOf(
    Color(0xFF4449CF),
    Color(0xFFFFD1E0),
    Color(0xFFF94446),
    Color(0xFFFFD36B),
    Color(0xFFFFFFFF),
  )
}

/**
 * A multi-color gradient mapped into smooth, animated curves built from two
 * layered simplex noises.
 */
@Composable
public fun SimplexNoise(
  modifier: Modifier = Modifier,
  colors: List<Color> = SimplexNoiseDefaults.Colors,
  stepsPerColor: Int = 2,
  softness: Float = 0f,
  speed: Float = 0.5f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Pattern.copy(scale = 0.6f),
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
    setFloatUniform("u_stepsPerColor", stepsPerColor.toFloat())
    setFloatUniform("u_softness", softness)
  }
}
