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

private const val MAX_COLORS = 8
private const val MAX_BALLS = 20

private val SHADER = """
$SIZING_AGSL

uniform float u_time;

uniform vec4 u_colorBack;
uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;
uniform float u_size;
uniform float u_sizeRange;
uniform float u_count;

${Agsl.PI}
${Agsl.RANDOM_R}
${Agsl.BANDING_FIX}

float noise(float x) {
  float i = floor(x);
  float f = fract(x);
  float u = f * f * (3.0 - 2.0 * f);
  vec2 p0 = vec2(i, 0.0);
  vec2 p1 = vec2(i + 1.0, 0.0);
  return mix(randomR(p0), randomR(p1), u);
}

float getBallShape(vec2 uv, vec2 c, float p) {
  float s = .5 * length(uv - c);
  s = 1. - clamp(s, 0., 1.);
  s = pow(s, p);
  return s;
}

float getTotalShape(vec2 uv, float t) {
  float totalShape = 0.;
  for (int i = 0; i < $MAX_BALLS; i++) {
    if (i < int(ceil(u_count))) {
      float idxFract = float(i) / float($MAX_BALLS);
      float angle = TWO_PI * idxFract;

      float speed = 1. - .2 * idxFract;
      float noiseX = noise(angle * 10. + float(i) + t * speed);
      float noiseY = noise(angle * 20. + float(i) - t * speed);

      vec2 pos = vec2(.5) + 1e-4 + .9 * (vec2(noiseX, noiseY) - .5);

      float sizeFrac = 1.;
      if (float(i) > floor(u_count - 1.)) {
        sizeFrac *= fract(u_count);
      }

      float shape = getBallShape(uv, pos, 45. - 30. * u_size * sizeFrac);
      shape *= pow(u_size, .2);
      shape = smoothstep(0., 1., shape);

      totalShape += shape;
    }
  }
  return totalShape;
}

half4 main(vec2 fragCoord) {
  vec2 shape_uv = getObjectUV(fragCoord);

  shape_uv += .5;

  const float firstFrameOffset = 2503.4;
  float t = .2 * (u_time + firstFrameOffset);

  vec3 totalColor = vec3(0.);
  float totalShape = 0.;
  float totalOpacity = 0.;

  for (int i = 0; i < $MAX_BALLS; i++) {
    if (i < int(ceil(u_count))) {
      float idxFract = float(i) / float($MAX_BALLS);
      float angle = TWO_PI * idxFract;

      float speed = 1. - .2 * idxFract;
      float noiseX = noise(angle * 10. + float(i) + t * speed);
      float noiseY = noise(angle * 20. + float(i) - t * speed);

      vec2 pos = vec2(.5) + 1e-4 + .9 * (vec2(noiseX, noiseY) - .5);

      int safeIndex = int(mod(float(i), floor(u_colorsCount + .5)));
      vec4 ballColor = u_colors[0];
      for (int j = 0; j < $MAX_COLORS; j++) {
        if (j == safeIndex) {
          ballColor = u_colors[j];
        }
      }
      ballColor.rgb *= ballColor.a;

      float sizeFrac = 1.;
      if (float(i) > floor(u_count - 1.)) {
        sizeFrac *= fract(u_count);
      }

      float shape = getBallShape(shape_uv, pos, 45. - 30. * u_size * sizeFrac);
      shape *= pow(u_size, .2);
      shape = smoothstep(0., 1., shape);

      totalColor += ballColor.rgb * shape;
      totalShape += shape;
      totalOpacity += ballColor.a * shape;
    }
  }

  totalColor /= max(totalShape, 1e-4);
  totalOpacity /= max(totalShape, 1e-4);

  // AGSL has no derivative functions: estimate the screen-space derivative of
  // the metaball field with finite differences one pixel over.
  float shapeX = getTotalShape(getObjectUV(fragCoord + vec2(1., 0.)) + .5, t);
  float shapeY = getTotalShape(getObjectUV(fragCoord + vec2(0., 1.)) + .5, t);
  float edge_width = abs(shapeX - totalShape) + abs(shapeY - totalShape);
  float finalShape = smoothstep(.4, .4 + edge_width, totalShape);

  vec3 color = totalColor * finalShape;
  float opacity = totalOpacity * finalShape;

  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  color = color + bgColor * (1. - opacity);
  opacity = opacity + u_colorBack.a * (1. - opacity);

  color += bandingNoise(fragCoord);

  return half4(color, opacity);
}
"""

public object MetaballsDefaults {
  public val ColorBack: Color = Color(0xFF000000)
  public val Colors: List<Color> = listOf(
    Color(0xFF6E33CC),
    Color(0xFFFF5500),
    Color(0xFFFFC105),
    Color(0xFFFFC800),
    Color(0xFFF585FF),
  )
}

/**
 * Up to 20 colored gooey balls moving around the center and merging into
 * smooth organic shapes.
 */
@Composable
public fun Metaballs(
  modifier: Modifier = Modifier,
  colorBack: Color = MetaballsDefaults.ColorBack,
  colors: List<Color> = MetaballsDefaults.Colors,
  count: Float = 10f,
  size: Float = 0.83f,
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
    setColorUniform4f("u_colorBack", colorBack)
    setColorsUniform("u_colors", "u_colorsCount", colors, MAX_COLORS)
    setFloatUniform("u_size", size)
    setFloatUniform("u_count", count)
  }
}
