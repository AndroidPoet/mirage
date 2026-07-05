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

uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;

uniform float u_stepsPerColor;
uniform vec4 u_colorGlow;
uniform vec4 u_colorGap;
uniform float u_distortion;
uniform float u_gap;
uniform float u_glow;

${Agsl.PI}
${Agsl.RANDOM_GB}

vec4 voronoi(vec2 x, float t) {
  vec2 ip = floor(x);
  vec2 fp = fract(x);

  vec2 mg = vec2(0.);
  vec2 mr = vec2(0.);
  float md = 8.;
  float rand = 0.;

  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      vec2 g = vec2(float(i), float(j));
      vec2 o = randomGB(ip + g);
      float raw_hash = o.x;
      o = .5 + u_distortion * sin(t + TWO_PI * o);
      vec2 r = g + o - fp;
      float d = dot(r, r);

      if (d < md) {
        md = d;
        mr = r;
        mg = g;
        rand = raw_hash;
      }
    }
  }

  md = 8.;
  for (int j = -2; j <= 2; j++) {
    for (int i = -2; i <= 2; i++) {
      vec2 g = mg + vec2(float(i), float(j));
      vec2 o = randomGB(ip + g);
      o = .5 + u_distortion * sin(t + TWO_PI * o);
      vec2 r = g + o - fp;
      if (dot(mr - r, mr - r) > .00001) {
        md = min(md, dot(.5 * (mr + r), normalize(r - mr)));
      }
    }
  }

  return vec4(md, mr, rand);
}

half4 main(vec2 fragCoord) {
  vec2 shape_uv = getPatternUV(fragCoord);
  shape_uv *= 1.25;

  float t = u_time;

  vec4 voronoiRes = voronoi(shape_uv, t);

  float shape = clamp(voronoiRes.w, 0., 1.);
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

  vec3 cellColor = gradient.rgb;
  float cellOpacity = gradient.a;

  float glows = length(voronoiRes.yz * u_glow);
  glows = pow(glows, 1.5);

  vec3 color = mix(cellColor, u_colorGlow.rgb * u_colorGlow.a, u_colorGlow.a * glows);
  float opacity = cellOpacity + u_colorGlow.a * glows;

  float edge = voronoiRes.x;
  float smoothEdge = .02 / (2. * u_scale) * (1. + .5 * u_gap);
  edge = smoothstep(u_gap - smoothEdge, u_gap + smoothEdge, edge);

  color = mix(u_colorGap.rgb * u_colorGap.a, color, edge);
  opacity = mix(u_colorGap.a, opacity, edge);

  return half4(color, opacity);
}
"""

public object VoronoiDefaults {
  public val Colors: List<Color> = listOf(
    Color(0xFFFF8247),
    Color(0xFFFFE53D),
  )
  public val ColorGlow: Color = Color(0xFFFFFFFF)
  public val ColorGap: Color = Color(0xFF2E0000)
}

/**
 * Anti-aliased animated Voronoi pattern with smooth and customizable edges.
 */
@Composable
public fun Voronoi(
  modifier: Modifier = Modifier,
  colors: List<Color> = VoronoiDefaults.Colors,
  stepsPerColor: Int = 3,
  colorGlow: Color = VoronoiDefaults.ColorGlow,
  colorGap: Color = VoronoiDefaults.ColorGap,
  distortion: Float = 0.4f,
  gap: Float = 0.04f,
  glow: Float = 0f,
  speed: Float = 0.5f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Pattern.copy(scale = 0.5f),
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
    setColorUniform4f("u_colorGlow", colorGlow)
    setColorUniform4f("u_colorGap", colorGap)
    setFloatUniform("u_distortion", distortion)
    setFloatUniform("u_gap", gap)
    setFloatUniform("u_glow", glow)
  }
}
