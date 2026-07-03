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

private const val MAX_COLORS = 5
private const val MAX_SPOTS = 4

private val SHADER = """
$SIZING_AGSL

uniform float u_time;

uniform vec4 u_colorBack;
uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;
uniform float u_roundness;
uniform float u_thickness;
uniform float u_marginLeft;
uniform float u_marginRight;
uniform float u_marginTop;
uniform float u_marginBottom;
uniform float u_aspectRatio;
uniform float u_softness;
uniform float u_intensity;
uniform float u_bloom;
uniform float u_spotSize;
uniform float u_spots;
uniform float u_pulse;
uniform float u_smoke;
uniform float u_smokeSize;

${Agsl.PI}
${Agsl.BANDING_FIX}

float beat(float time) {
  float first = pow(abs(sin(time * TWO_PI)), 10.);
  float second = pow(abs(sin((time - .15) * TWO_PI)), 10.);

  return clamp(first + 0.6 * second, 0.0, 1.0);
}

float sst(float edge0, float edge1, float x) {
  return smoothstep(edge0, edge1, x);
}

float roundedBox(vec2 uv, vec2 halfSize, float dist, float cornerDist, float thickness, float softness, float pxSize) {
  float borderDistance = abs(dist);
  // AGSL has no derivative intrinsics: pxSize is the pixel footprint of the
  // distance field in borderUV units, used as the AA width.
  float aa = 2. * pxSize;
  float border = 1. - sst(min(mix(thickness, -thickness, softness), thickness + aa), max(mix(thickness, -thickness, softness), thickness + aa), borderDistance);
  float cornerFadeCircles = 0.;
  cornerFadeCircles = mix(1., cornerFadeCircles, sst(0., 1., length((uv + halfSize) / thickness)));
  cornerFadeCircles = mix(1., cornerFadeCircles, sst(0., 1., length((uv - vec2(-halfSize.x, halfSize.y)) / thickness)));
  cornerFadeCircles = mix(1., cornerFadeCircles, sst(0., 1., length((uv - vec2(halfSize.x, -halfSize.y)) / thickness)));
  cornerFadeCircles = mix(1., cornerFadeCircles, sst(0., 1., length((uv - halfSize) / thickness)));
  aa = pxSize;
  float cornerFade = sst(0., mix(aa, thickness, softness), cornerDist);
  cornerFade *= cornerFadeCircles;
  border += cornerFade;
  return border;
}

${Agsl.RANDOM_GB}

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

half4 main(vec2 fragCoord) {
  const float firstFrameOffset = 109.;
  float t = 1.2 * (u_time + firstFrameOffset);

  vec2 borderUV = getResponsiveUV(fragCoord);
  float pulse = u_pulse * beat(.18 * u_time);

  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.)) * u_pixelRatio;
  vec2 responsiveBoxGivenSize = vec2(
  (u_worldWidth == 0.) ? u_resolution.x : givenBoxSize.x,
  (u_worldHeight == 0.) ? u_resolution.y : givenBoxSize.y);
  float responsiveRatio = responsiveBoxGivenSize.x / responsiveBoxGivenSize.y;
  vec2 responsiveBoxSize = getBoxSize(responsiveRatio, responsiveBoxGivenSize).xy;
  float pxSize = 1. / (min(responsiveBoxSize.x, responsiveBoxSize.y) * u_scale);

  float canvasRatio = responsiveBoxGivenSize.x / responsiveBoxGivenSize.y;
  vec2 halfSize = vec2(.5);
  borderUV.x *= max(canvasRatio, 1.);
  borderUV.y /= min(canvasRatio, 1.);
  halfSize.x *= max(canvasRatio, 1.);
  halfSize.y /= min(canvasRatio, 1.);

  float mL = u_marginLeft;
  float mR = u_marginRight;
  float mT = u_marginTop;
  float mB = u_marginBottom;
  float mX = mL + mR;
  float mY = mT + mB;

  if (u_aspectRatio > 0.) {
    float shapeRatio = canvasRatio * (1. - mX) / max(1. - mY, 1e-6);
    float freeX = shapeRatio > 1. ? (1. - mX) * (1. - 1. / max(abs(shapeRatio), 1e-6)) : 0.;
    float freeY = shapeRatio < 1. ? (1. - mY) * (1. - shapeRatio) : 0.;
    mL += freeX * 0.5;
    mR += freeX * 0.5;
    mT += freeY * 0.5;
    mB += freeY * 0.5;
    mX = mL + mR;
    mY = mT + mB;
  }

  float thickness = .5 * u_thickness * min(halfSize.x, halfSize.y);

  halfSize.x *= (1. - mX);
  halfSize.y *= (1. - mY);

  vec2 centerShift = vec2(
  (mL - mR) * max(canvasRatio, 1.) * 0.5,
  (mB - mT) / min(canvasRatio, 1.) * 0.5
  );

  borderUV -= centerShift;
  halfSize -= mix(thickness, 0., u_softness);

  float radius = mix(0., min(halfSize.x, halfSize.y), u_roundness);
  vec2 d = abs(borderUV) - halfSize + radius;
  float outsideDistance = length(max(d, .0001)) - radius;
  float insideDistance = min(max(d.x, d.y), .0001);
  float cornerDist = abs(min(max(d.x, d.y) - .45 * radius, .0));
  float dist = outsideDistance + insideDistance;

  float borderThickness = mix(thickness, 3. * thickness, u_softness);
  float border = roundedBox(borderUV, halfSize, dist, cornerDist, borderThickness, u_softness, pxSize);
  border = pow(border, 1. + u_softness);

  vec2 smokeUV = .3 * u_smokeSize * getPatternUV(fragCoord);
  float smoke = clamp(3. * valueNoise(2.7 * smokeUV + .5 * t), 0., 1.);
  smoke -= valueNoise(3.4 * smokeUV - .5 * t);
  float smokeThickness = thickness + .2;
  smokeThickness = min(.4, max(smokeThickness, .1));
  smoke *= roundedBox(borderUV, halfSize, dist, cornerDist, smokeThickness, 1., pxSize);
  smoke = 30. * smoke * smoke;
  smoke *= mix(0., .5, pow(u_smoke, 2.));
  smoke *= mix(1., pulse, u_pulse);
  smoke = clamp(smoke, 0., 1.);
  border += smoke;

  border = clamp(border, 0., 1.);

  vec3 blendColor = vec3(0.);
  float blendAlpha = 0.;
  vec3 addColor = vec3(0.);
  float addAlpha = 0.;

  float bloom = 4. * u_bloom;
  float intensity = 1. + (1. + 4. * u_softness) * u_intensity;

  float angle = atan(borderUV.y, borderUV.x) / TWO_PI;

  for (int colorIdx = 0; colorIdx < $MAX_COLORS; colorIdx++) {
    if (colorIdx < int(u_colorsCount)) {
      float colorIdxF = float(colorIdx);

      vec3 c = u_colors[colorIdx].rgb * u_colors[colorIdx].a;
      float a = u_colors[colorIdx].a;

      for (int spotIdx = 0; spotIdx < $MAX_SPOTS; spotIdx++) {
        if (spotIdx < int(u_spots)) {
          float spotIdxF = float(spotIdx);

          vec2 randVal = randomGB(vec2(spotIdxF * 10. + 2., 40. + colorIdxF));

          float time = (.1 + .15 * abs(sin(spotIdxF * (2. + colorIdxF)) * cos(spotIdxF * (2. + 2.5 * colorIdxF)))) * t + randVal.x * 3.;
          time *= mix(1., -1., step(.5, randVal.y));

          float mask = .5 + .5 * mix(
          sin(t + spotIdxF * (5. - 1.5 * colorIdxF)),
          cos(t + spotIdxF * (3. + 1.3 * colorIdxF)),
          step(mod(colorIdxF, 2.), .5)
          );

          float p = clamp(2. * u_pulse - randVal.x, 0., 1.);
          mask = mix(mask, pulse, p);

          float atg1 = fract(angle + time);
          float spotSize = .05 + .6 * pow(u_spotSize, 2.) + .05 * randVal.x;
          spotSize = mix(spotSize, .1, p);
          float sector = sst(.5 - spotSize, .5, atg1) * (1. - sst(.5, .5 + spotSize, atg1));

          sector *= mask;
          sector *= border;
          sector *= intensity;
          sector = clamp(sector, 0., 1.);

          vec3 srcColor = c * sector;
          float srcAlpha = a * sector;

          blendColor += ((1. - blendAlpha) * srcColor);
          blendAlpha = blendAlpha + (1. - blendAlpha) * srcAlpha;
          addColor += srcColor;
          addAlpha += srcAlpha;
        }
      }
    }
  }

  vec3 accumColor = mix(blendColor, addColor, bloom);
  float accumAlpha = mix(blendAlpha, addAlpha, bloom);
  accumAlpha = clamp(accumAlpha, 0., 1.);

  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  vec3 color = accumColor + (1. - accumAlpha) * bgColor;
  float opacity = accumAlpha + (1. - accumAlpha) * u_colorBack.a;

  color += bandingNoise(fragCoord);

  return half4(color, opacity);
}
"""

public enum class PulsingBorderAspectRatio(public val value: Float) {
  Auto(0f),
  Square(1f),
}

public object PulsingBorderDefaults {
  public val ColorBack: Color = Color(0xFF000000)
  public val Colors: List<Color> = listOf(
    Color(0xFF0DC1FD),
    Color(0xFFD915EF),
    Color(0xCCFF3F2E),
  )
}

/**
 * Luminous trails of color merging into a glowing gradient contour.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun PulsingBorder(
  modifier: Modifier = Modifier,
  colorBack: Color = PulsingBorderDefaults.ColorBack,
  colors: List<Color> = PulsingBorderDefaults.Colors,
  roundness: Float = 0.25f,
  thickness: Float = 0.1f,
  marginLeft: Float = 0f,
  marginRight: Float = 0f,
  marginTop: Float = 0f,
  marginBottom: Float = 0f,
  aspectRatio: PulsingBorderAspectRatio = PulsingBorderAspectRatio.Auto,
  softness: Float = 0.75f,
  intensity: Float = 0.2f,
  bloom: Float = 0.25f,
  spots: Int = 5,
  spotSize: Float = 0.5f,
  pulse: Float = 0.25f,
  smoke: Float = 0.3f,
  smokeSize: Float = 0.6f,
  speed: Float = 1f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Object.copy(scale = 0.6f),
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
    setFloatUniform("u_roundness", roundness)
    setFloatUniform("u_thickness", thickness)
    setFloatUniform("u_marginLeft", marginLeft)
    setFloatUniform("u_marginRight", marginRight)
    setFloatUniform("u_marginTop", marginTop)
    setFloatUniform("u_marginBottom", marginBottom)
    setFloatUniform("u_aspectRatio", aspectRatio.value)
    setFloatUniform("u_softness", softness)
    setFloatUniform("u_intensity", intensity)
    setFloatUniform("u_bloom", bloom)
    setFloatUniform("u_spots", spots.toFloat())
    setFloatUniform("u_spotSize", spotSize)
    setFloatUniform("u_pulse", pulse)
    setFloatUniform("u_smoke", smoke)
    setFloatUniform("u_smokeSize", smokeSize)
  }
}
