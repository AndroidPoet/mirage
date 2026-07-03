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

private val SHADER = """
$SIZING_AGSL

uniform vec4 u_colorBack;
uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;

uniform float u_radius;
uniform float u_focalDistance;
uniform float u_focalAngle;
uniform float u_falloff;
uniform float u_mixing;
uniform float u_distortion;
uniform float u_distortionShift;
uniform float u_distortionFreq;
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

float getMixer(vec2 fragCoord) {
  vec2 uv = 2. * getObjectUV(fragCoord);
  vec2 grainUV = uv * 1000.;

  vec2 center = vec2(0.);
  float angleRad = -radians(u_focalAngle + 90.);
  vec2 focalPoint = vec2(cos(angleRad), sin(angleRad)) * u_focalDistance;
  float radius = u_radius;

  vec2 c_to_uv = uv - center;
  vec2 f_to_uv = uv - focalPoint;
  vec2 f_to_c = center - focalPoint;
  float r = length(c_to_uv);

  float fragAngle = atan(c_to_uv.y, c_to_uv.x);
  float angleDiff = fract((fragAngle - angleRad + PI) / TWO_PI) * TWO_PI - PI;

  float halfAngle = acos(clamp(radius / max(u_focalDistance, 1e-4), 0.0, 1.0));
  float e0 = 0.6 * PI;
  float e1 = halfAngle;
  float lo = min(e0, e1);
  float hi = max(e0, e1);
  float s = smoothstep(lo, hi, abs(angleDiff));
  float isInSector = (e1 >= e0) ? (1.0 - s) : s;

  float a = dot(f_to_uv, f_to_uv);
  float b = -2.0 * dot(f_to_uv, f_to_c);
  float c = dot(f_to_c, f_to_c) - radius * radius;

  float discriminant = b * b - 4.0 * a * c;
  float t = 1.0;

  if (discriminant >= 0.0) {
    float sqrtD = sqrt(discriminant);
    float div = max(1e-4, 2.0 * a);
    float t0 = (-b - sqrtD) / div;
    float t1 = (-b + sqrtD) / div;
    t = max(t0, t1);
    if (t < 0.0) t = 0.0;
  }

  float dist = length(f_to_uv);
  float normalized = dist / max(1e-4, length(f_to_uv * t));
  float shape = clamp(normalized, 0.0, 1.0);

  float falloffMapped = mix(.2 + .8 * max(0., u_falloff + 1.), mix(1., 15., u_falloff * u_falloff), step(.0, u_falloff));

  float falloffExp = mix(falloffMapped, 1., shape);
  shape = pow(shape, falloffExp);
  shape = 1. - clamp(shape, 0., 1.);

  float outerMask = .002;
  float outer = 1.0 - smoothstep(radius - outerMask, radius + outerMask, r);
  outer = mix(outer, 1., isInSector);

  shape = mix(0., shape, outer);
  shape *= 1. - smoothstep(radius - .01, radius, r);

  float angle = atan(f_to_uv.y, f_to_uv.x);
  shape -= pow(u_distortion, 2.) * shape * pow(abs(sin(PI * clamp(length(f_to_uv) - 0.2 + u_distortionShift, 0.0, 1.0))), 4.0) * (sin(u_distortionFreq * angle) + cos(floor(0.65 * u_distortionFreq) * angle));

  float grain = noise(grainUV, vec2(0.));
  float mixerGrain = .4 * u_grainMixer * (grain - .5);

  return shape * u_colorsCount + mixerGrain;
}

half4 main(vec2 fragCoord) {
  vec2 uv = 2. * getObjectUV(fragCoord);
  vec2 grainUV = uv * 1000.;

  // fwidth() is unavailable in AGSL: estimate the screen-space derivative of
  // the color mixer with finite differences one pixel over.
  float mixer = getMixer(fragCoord);
  float mixerX = getMixer(fragCoord + vec2(1., 0.));
  float mixerY = getMixer(fragCoord + vec2(0., 1.));

  vec4 gradient = u_colors[0];
  gradient.rgb *= gradient.a;

  float outerShape = 0.;
  for (int i = 1; i < ${MAX_COLORS + 1}; i++) {
    if (i <= int(u_colorsCount)) {
      float mLinear = clamp(mixer - float(i - 1), 0.0, 1.0);
      float mLinearX = clamp(mixerX - float(i - 1), 0.0, 1.0);
      float mLinearY = clamp(mixerY - float(i - 1), 0.0, 1.0);

      float aa = abs(mLinearX - mLinear) + abs(mLinearY - mLinear);
      float width = min(u_mixing, 0.5);
      float t = clamp((mLinear - (0.5 - width - aa)) / (2. * width + 2. * aa), 0., 1.);
      float p = mix(2., 1., clamp((u_mixing - 0.5) * 2., 0., 1.));
      float m = t < 0.5
        ? 0.5 * pow(2. * t, p)
        : 1. - 0.5 * pow(2. * (1. - t), p);

      float quadBlend = clamp((u_mixing - 0.5) * 2., 0., 1.);
      m = mix(m, m * m, 0.5 * quadBlend);

      if (i == 1) {
        outerShape = m;
      }

      vec4 c = u_colors[i - 1];
      c.rgb *= c.a;
      gradient = mix(gradient, c, m);
    }
  }

  vec3 color = gradient.rgb * outerShape;
  float opacity = gradient.a * outerShape;

  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  color = color + bgColor * (1.0 - opacity);
  opacity = opacity + u_colorBack.a * (1.0 - opacity);

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

public object StaticRadialGradientDefaults {
  public val ColorBack: Color = Color(0xFF000000)
  public val Colors: List<Color> = listOf(
    Color(0xFF00BBFF),
    Color(0xFF00FFE1),
    Color(0xFFFFFFFF),
  )
}

/**
 * Radial gradient with up to 10 blended colors, featuring advanced mixing
 * modes, focal point controls, shape distortion, and grain effects.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun StaticRadialGradient(
  modifier: Modifier = Modifier,
  colorBack: Color = StaticRadialGradientDefaults.ColorBack,
  colors: List<Color> = StaticRadialGradientDefaults.Colors,
  radius: Float = 0.8f,
  focalDistance: Float = 0.99f,
  focalAngle: Float = 0f,
  falloff: Float = 0.24f,
  mixing: Float = 0.5f,
  distortion: Float = 0f,
  distortionShift: Float = 0f,
  distortionFreq: Float = 12f,
  grainMixer: Float = 0f,
  grainOverlay: Float = 0f,
  sizing: SizingParams = SizingParams.Object,
) {
  MirageSurface(
    shaderSource = SHADER,
    modifier = modifier,
    sizing = sizing,
  ) { _ ->
    setColorUniform4f("u_colorBack", colorBack)
    setColorsUniform("u_colors", "u_colorsCount", colors, MAX_COLORS)
    setFloatUniform("u_radius", radius)
    setFloatUniform("u_focalDistance", focalDistance)
    setFloatUniform("u_focalAngle", focalAngle)
    setFloatUniform("u_falloff", falloff)
    setFloatUniform("u_mixing", mixing)
    setFloatUniform("u_distortion", distortion)
    setFloatUniform("u_distortionShift", distortionShift)
    setFloatUniform("u_distortionFreq", distortionFreq)
    setFloatUniform("u_grainMixer", grainMixer)
    setFloatUniform("u_grainOverlay", grainOverlay)
  }
}
