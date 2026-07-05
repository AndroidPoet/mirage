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
uniform float u_bandCount;
uniform float u_twist;
uniform float u_center;
uniform float u_proportion;
uniform float u_softness;
uniform float u_noise;
uniform float u_noiseFrequency;

${Agsl.PI}
${Agsl.SIMPLEX_NOISE}
${Agsl.ROTATION2}
${Agsl.BANDING_FIX}

float getShape(vec2 shape_uv, float t) {
  float l = length(shape_uv);
  l = max(1e-4, l);

  float angle = ceil(u_bandCount) * atan(shape_uv.y, shape_uv.x) + t;
  float angle_norm = angle / TWO_PI;

  float twist = 3. * clamp(u_twist, 0., 1.);
  float offset = pow(l, -twist) + angle_norm;

  float shape = fract(offset);
  shape = 1. - abs(2. * shape - 1.);
  shape += u_noise * snoise(15. * pow(u_noiseFrequency, 2.) * shape_uv);

  float mid = smoothstep(.2, .2 + .8 * u_center, pow(l, twist));
  shape = mix(0., shape, mid);

  float proportion = clamp(u_proportion, 0., 1.);
  float exponent = mix(.25, 1., proportion * 2.);
  exponent = mix(exponent, 10., max(0., proportion * 2. - 1.));
  shape = pow(shape, exponent);

  return shape;
}

half4 main(vec2 fragCoord) {
  vec2 shape_uv = getObjectUV(fragCoord);

  float l = max(1e-4, length(shape_uv));
  float twist = 3. * clamp(u_twist, 0., 1.);

  float t = u_time;

  float shape = getShape(shape_uv, t);

  // AGSL has no derivatives: estimate the screen-space derivative of the
  // shape field with finite differences one pixel over.
  vec2 uvX = getObjectUV(fragCoord + vec2(1., 0.));
  vec2 uvY = getObjectUV(fragCoord + vec2(0., 1.));
  float shapeX = getShape(uvX, t);
  float shapeY = getShape(uvY, t);
  float fwMixer = (abs(shapeX - shape) + abs(shapeY - shape)) * u_colorsCount;

  float mixer = shape * u_colorsCount;
  vec4 gradient = u_colors[0];
  gradient.rgb *= gradient.a;

  float outerShape = 0.;
  for (int i = 1; i < ${MAX_COLORS + 1}; i++) {
    if (i <= int(u_colorsCount)) {
      float m = clamp(mixer - float(i - 1), 0., 1.);
      float aa = fwMixer;
      m = smoothstep(.5 - .5 * u_softness - aa, .5 + .5 * u_softness + aa, m);

      if (i == 1) {
        outerShape = m;
      }

      vec4 c = u_colors[i - 1];
      c.rgb *= c.a;
      gradient = mix(gradient, c, m);
    }
  }

  float lInvTwist = pow(l, -twist);
  float lInvTwistX = pow(max(1e-4, length(uvX)), -twist);
  float lInvTwistY = pow(max(1e-4, length(uvY)), -twist);
  float midAA = .1 * (abs(lInvTwistX - lInvTwist) + abs(lInvTwistY - lInvTwist));
  float outerMid = smoothstep(.2, .2 + midAA, pow(l, twist));
  outerShape = mix(0., outerShape, outerMid);

  vec3 color = gradient.rgb * outerShape;
  float opacity = gradient.a * outerShape;

  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  color = color + bgColor * (1.0 - opacity);
  opacity = opacity + u_colorBack.a * (1.0 - opacity);

  color += bandingNoise(fragCoord);

  return half4(color, opacity);
}
"""

public object SwirlDefaults {
  public val ColorBack: Color = Color(0xFF330000)
  public val Colors: List<Color> = listOf(
    Color(0xFFFFD1D1),
    Color(0xFFFF8A8A),
    Color(0xFF660000),
  )
}

/**
 * Animated bands of color twisting and bending, producing spirals, arcs,
 * and flowing circular patterns.
 */
@Composable
public fun Swirl(
  modifier: Modifier = Modifier,
  colorBack: Color = SwirlDefaults.ColorBack,
  colors: List<Color> = SwirlDefaults.Colors,
  bandCount: Float = 4f,
  twist: Float = 0.1f,
  center: Float = 0.2f,
  proportion: Float = 0.5f,
  softness: Float = 0f,
  noiseFrequency: Float = 0.4f,
  noise: Float = 0.2f,
  speed: Float = 0.32f,
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
    setFloatUniform("u_bandCount", bandCount)
    setFloatUniform("u_twist", twist)
    setFloatUniform("u_center", center)
    setFloatUniform("u_proportion", proportion)
    setFloatUniform("u_softness", softness)
    setFloatUniform("u_noise", noise)
    setFloatUniform("u_noiseFrequency", noiseFrequency)
  }
}
