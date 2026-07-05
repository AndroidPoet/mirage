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

private const val MAX_COLORS = 10

private val SHADER = """
$SIZING_AGSL

uniform float u_time;

uniform vec4 u_colorBack;
uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;
uniform float u_stepsPerColor;
uniform float u_size;
uniform float u_sizeRange;
uniform float u_spreading;

${Agsl.PI}
${Agsl.ROTATION2}
${Agsl.RANDOM_R}
${Agsl.RANDOM_GB}

vec3 voronoiShape(vec2 uv, float time) {
  vec2 i_uv = floor(uv);
  vec2 f_uv = fract(uv);

  float spreading = .25 * clamp(u_spreading, 0., 1.);

  float minDist = 1.;
  vec2 randomizer = vec2(0.);
  for (int y = -1; y <= 1; y++) {
    for (int x = -1; x <= 1; x++) {
      vec2 tileOffset = vec2(float(x), float(y));
      vec2 rand = randomGB(i_uv + tileOffset);
      vec2 cellCenter = vec2(.5 + 1e-4);
      cellCenter += spreading * cos(time + TWO_PI * rand);
      cellCenter -= .5;
      cellCenter = rotate(cellCenter, randomR(vec2(rand.x, rand.y)) + .1 * time);
      cellCenter += .5;
      float dist = length(tileOffset + cellCenter - f_uv);
      if (dist < minDist) {
        minDist = dist;
        randomizer = rand;
      }
    }
  }

  return vec3(minDist, randomizer);
}

half4 main(vec2 fragCoord) {
  vec2 patternUV = getPatternUV(fragCoord);
  vec2 shape_uv = patternUV;
  shape_uv *= 1.5;

  const float firstFrameOffset = -10.;
  float t = u_time + firstFrameOffset;

  vec3 voronoi = voronoiShape(shape_uv, t) + 1e-4;

  float radius = .25 * clamp(u_size, 0., 1.) - .5 * clamp(u_sizeRange, 0., 1.) * voronoi[2];
  float dist = voronoi[0];
  // AGSL has no derivative functions: dist is a unit-gradient distance field,
  // so its screen-space derivative is the per-pixel step of the pattern UV.
  float edgeWidth = 1.5 * length(getPatternUV(fragCoord + vec2(1., 0.)) - patternUV);
  float dots = 1. - smoothstep(radius - edgeWidth, radius + edgeWidth, dist);

  float shape = voronoi[1];

  float mixer = shape * (u_colorsCount - 1.);
  mixer = (shape - .5 / u_colorsCount) * u_colorsCount;
  float steps = max(1., u_stepsPerColor);

  vec4 gradient = u_colors[0];
  gradient.rgb *= gradient.a;
  for (int i = 1; i < $MAX_COLORS; i++) {
    if (i < int(u_colorsCount)) {
      float localT = clamp(mixer - float(i - 1), 0.0, 1.0);
      localT = floor(localT * steps + .5) / steps;
      vec4 c = u_colors[i];
      c.rgb *= c.a;
      gradient = mix(gradient, c, localT);
    }
  }

  if ((mixer < 0.) || (mixer > (u_colorsCount - 1.))) {
    float localT = mixer + 1.;
    if (mixer > (u_colorsCount - 1.)) {
      localT = mixer - (u_colorsCount - 1.);
    }
    localT = floor(localT * steps + .5) / steps;
    vec4 cFst = u_colors[0];
    cFst.rgb *= cFst.a;
    vec4 cLast = u_colors[0];
    for (int i = 0; i < $MAX_COLORS; i++) {
      if (i == int(u_colorsCount - 1.)) {
        cLast = u_colors[i];
      }
    }
    cLast.rgb *= cLast.a;
    gradient = mix(cLast, cFst, localT);
  }

  vec3 color = gradient.rgb * dots;
  float opacity = gradient.a * dots;

  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  color = color + bgColor * (1. - opacity);
  opacity = opacity + u_colorBack.a * (1. - opacity);

  return half4(color, opacity);
}
"""

public object DotOrbitDefaults {
  public val ColorBack: Color = Color(0xFF000000)
  public val Colors: List<Color> = listOf(
    Color(0xFFFFC96B),
    Color(0xFFFF6200),
    Color(0xFFFF2F00),
    Color(0xFF421100),
    Color(0xFF1A0000),
  )
}

/**
 * Animated multi-color dots pattern with each dot orbiting around its cell
 * center.
 */
@Composable
public fun DotOrbit(
  modifier: Modifier = Modifier,
  colorBack: Color = DotOrbitDefaults.ColorBack,
  colors: List<Color> = DotOrbitDefaults.Colors,
  size: Float = 1f,
  sizeRange: Float = 0f,
  spreading: Float = 1f,
  stepsPerColor: Int = 4,
  speed: Float = 1.5f,
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
    setColorsUniform("u_colors", "u_colorsCount", colors, MAX_COLORS)
    setFloatUniform("u_size", size)
    setFloatUniform("u_sizeRange", sizeRange)
    setFloatUniform("u_spreading", spreading)
    setFloatUniform("u_stepsPerColor", stepsPerColor.toFloat())
  }
}
