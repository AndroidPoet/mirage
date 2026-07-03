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

private const val MAX_COLORS = 7

private val SHADER = """
$SIZING_AGSL

uniform float u_time;

uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;
uniform vec4 u_colorBack;
uniform float u_density;
uniform float u_angle1;
uniform float u_angle2;
uniform float u_length;
uniform float u_edges;
uniform float u_blur;
uniform float u_fadeIn;
uniform float u_fadeOut;
uniform float u_gradient;

${Agsl.PI}
${Agsl.BANDING_FIX}

const float zLimit = .5;

vec2 getPanel(float angle, vec2 uv, float invLength, float aa) {
  float sinA = sin(angle);
  float cosA = cos(angle);

  float denom = sinA - uv.y * cosA;
  if (abs(denom) < .01) return vec2(0.);

  float z = uv.y / denom;

  if (z <= 0. || z > zLimit) return vec2(0.);

  float zRatio = z / zLimit;
  float panelMap = 1. - zRatio;
  float x = uv.x * (cosA * z + 1.) * invLength;

  float zOffset = zRatio - .5;
  float left = -.5 + zOffset * u_angle1;
  float right = .5 - zOffset * u_angle2;
  float blurX = aa + 2. * panelMap * u_blur;

  float leftEdge1 = left - blurX;
  float leftEdge2 = left + .25 * blurX;
  float rightEdge1 = right - .25 * blurX;
  float rightEdge2 = right + blurX;

  float panel = smoothstep(leftEdge1, leftEdge2, x) * (1.0 - smoothstep(rightEdge1, rightEdge2, x));
  panel *= mix(0., panel, smoothstep(0., .01 / max(u_scale, 1e-6), panelMap));

  float midScreen = abs(sinA);
  if (u_edges > .5) {
    panelMap = mix(.99, panelMap, panel * clamp(panelMap / (.15 * (1. - pow(midScreen, .1))), 0.0, 1.0));
  } else if (midScreen < .07) {
    panel *= (midScreen * 15.);
  }

  return vec2(panel, panelMap);
}

vec4 blendColor(vec4 colorA, float panelMask, float panelMap) {
  float fade = 1. - smoothstep(.97 - .97 * u_fadeIn, 1., panelMap);

  fade *= smoothstep(-.2 * (1. - u_fadeOut), u_fadeOut, panelMap);

  vec3 blendedRGB = mix(vec3(0.), colorA.rgb, fade);
  float blendedAlpha = mix(0., colorA.a, fade);

  return vec4(blendedRGB, blendedAlpha) * panelMask;
}

vec4 getPremultipliedColor(int idx) {
  vec4 c = vec4(0.);
  for (int i = 0; i < $MAX_COLORS; i++) {
    if (i == idx) {
      c = u_colors[i];
    }
  }
  c.rgb *= c.a;
  return c;
}

int imod(int a, int b) {
  return a - (a / b) * b;
}

half4 main(vec2 fragCoord) {
  vec2 uv = getObjectUV(fragCoord);
  uv *= 1.25;

  float t = .02 * u_time;
  t = fract(t);
  bool reverseTime = (t < 0.5);

  vec3 color = vec3(0.);
  float opacity = 0.;

  float aa = .005 / u_scale;
  int colorsCount = int(u_colorsCount);

  float invLength = 1.5 / max(u_length, .001);

  int panelsNumber = 12;

  float densityNormalizer = 1.;
  if (colorsCount == 4) {
    panelsNumber = 16;
    densityNormalizer = 1.34;
  } else if (colorsCount == 5) {
    panelsNumber = 20;
    densityNormalizer = 1.67;
  } else if (colorsCount == 7) {
    panelsNumber = 14;
    densityNormalizer = 1.17;
  }

  float fPanelsNumber = float(panelsNumber);

  float panelGrad = 1. - clamp(u_gradient, 0., 1.);

  for (int set = 0; set < 2; set++) {
    bool isForward = (set == 0 && !reverseTime) || (set == 1 && reverseTime);
    if (isForward) {

      for (int i = 0; i <= 20; i++) {
        if (i < panelsNumber) {
          int idx = panelsNumber - 1 - i;

          float offset = float(idx) / fPanelsNumber;
          if (set == 1) {
            offset += .5;
          }

          float densityFract = densityNormalizer * fract(t + offset);
          float angleNorm = densityFract / u_density;
          if (densityFract < .5 && angleNorm < .3) {
            float smoothDensity = clamp((.5 - densityFract) / .1, 0., 1.) * clamp(densityFract / .01, 0., 1.);
            float smoothAngle = clamp((.3 - angleNorm) / .05, 0., 1.);
            if (smoothDensity * smoothAngle >= .001) {
              if (angleNorm > .5) {
                angleNorm = 0.5;
              }
              vec2 panel = getPanel(angleNorm * TWO_PI + PI, uv, invLength, aa);
              if (panel.x > .001) {
                float panelMask = panel.x * smoothDensity * smoothAngle;
                float panelMap = panel.y;

                int colorIdx = imod(idx, colorsCount);
                int nextColorIdx = imod(idx + 1, colorsCount);

                vec4 colorA = getPremultipliedColor(colorIdx);
                vec4 colorB = getPremultipliedColor(nextColorIdx);

                colorA = mix(colorA, colorB, max(0., smoothstep(.0, .45, panelMap) - panelGrad));
                vec4 blended = blendColor(colorA, panelMask, panelMap);
                color = blended.rgb + color * (1. - blended.a);
                opacity = blended.a + opacity * (1. - blended.a);
              }
            }
          }
        }
      }

      for (int i = 0; i <= 20; i++) {
        if (i < panelsNumber) {
          int idx = panelsNumber - 1 - i;

          float offset = float(idx) / fPanelsNumber;
          if (set == 0) {
            offset += .5;
          }

          float densityFract = densityNormalizer * fract(-t + offset);
          float angleNorm = -densityFract / u_density;
          if (densityFract < .5 && angleNorm >= -.3) {
            float smoothDensity = clamp((.5 - densityFract) / .1, 0., 1.) * clamp(densityFract / .01, 0., 1.);
            float smoothAngle = clamp((angleNorm + .3) / .05, 0., 1.);
            if (smoothDensity * smoothAngle >= .001) {
              vec2 panel = getPanel(angleNorm * TWO_PI + PI, uv, invLength, aa);
              float panelMask = panel.x * smoothDensity * smoothAngle;
              if (panelMask > .001) {
                float panelMap = panel.y;

                int colorIdx = imod(colorsCount - imod(idx, colorsCount), colorsCount);
                if (colorIdx < 0) {
                  colorIdx += colorsCount;
                }
                int nextColorIdx = imod(colorIdx + 1, colorsCount);

                vec4 colorA = getPremultipliedColor(colorIdx);
                vec4 colorB = getPremultipliedColor(nextColorIdx);

                colorA = mix(colorA, colorB, max(0., smoothstep(.0, .45, panelMap) - panelGrad));
                vec4 blended = blendColor(colorA, panelMask, panelMap);
                color = blended.rgb + color * (1. - blended.a);
                opacity = blended.a + opacity * (1. - blended.a);
              }
            }
          }
        }
      }
    }
  }

  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  color = color + bgColor * (1.0 - opacity);
  opacity = opacity + u_colorBack.a * (1.0 - opacity);

  color += bandingNoise(fragCoord);

  return half4(color, opacity);
}
"""

public object ColorPanelsDefaults {
  public val Colors: List<Color> = listOf(
    Color(0xFFFF9D00),
    Color(0xFFFD4F30),
    Color(0xFF809BFF),
    Color(0xFF6D2EFF),
    Color(0xFF333AFF),
    Color(0xFFF15CFF),
    Color(0xFFFFD557),
  )
  public val ColorBack: Color = Color(0xFF000000)
}

/**
 * Pseudo-3D semi-transparent panels rotating around a central axis.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun ColorPanels(
  modifier: Modifier = Modifier,
  colors: List<Color> = ColorPanelsDefaults.Colors,
  colorBack: Color = ColorPanelsDefaults.ColorBack,
  angle1: Float = 0f,
  angle2: Float = 0f,
  length: Float = 1.1f,
  edges: Boolean = false,
  blur: Float = 0f,
  fadeIn: Float = 1f,
  fadeOut: Float = 0.3f,
  gradient: Float = 0f,
  density: Float = 3f,
  speed: Float = 0.5f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Object.copy(scale = 0.8f),
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
    setColorUniform4f("u_colorBack", colorBack)
    setFloatUniform("u_density", density)
    setFloatUniform("u_angle1", angle1)
    setFloatUniform("u_angle2", angle2)
    setFloatUniform("u_length", length)
    setFloatUniform("u_edges", if (edges) 1f else 0f)
    setFloatUniform("u_blur", blur)
    setFloatUniform("u_fadeIn", fadeIn)
    setFloatUniform("u_fadeOut", fadeOut)
    setFloatUniform("u_gradient", gradient)
  }
}
