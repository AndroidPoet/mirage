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
import io.androidpoet.mirage.core.SIZING_AGSL
import io.androidpoet.mirage.core.ShaderFit
import io.androidpoet.mirage.core.SizingParams
import io.androidpoet.mirage.core.mirageEffect
import io.androidpoet.mirage.core.setColorUniform4f

private val SHADER = """
$SIZING_AGSL

uniform shader u_image;

uniform vec4 u_colorFront;
uniform vec4 u_colorBack;
uniform float u_radius;
uniform float u_contrast;

uniform float u_size;
uniform float u_grainMixer;
uniform float u_grainOverlay;
uniform float u_grainSize;
uniform float u_grid;
uniform float u_originalColors;
uniform float u_inverted;
uniform float u_type;

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

float lst(float edge0, float edge1, float x) {
  return clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
}

float sst(float edge0, float edge1, float x) {
  return smoothstep(edge0, edge1, x);
}

float getCircle(vec2 uv, float r, float baseR, float aa) {
  r = mix(.25 * baseR, 0., r);
  float d = length(uv - .5);
  return 1. - smoothstep(r - aa, r + aa, d);
}

float getCell(vec2 uv) {
  float insideX = step(0.0, uv.x) * (1.0 - step(1.0, uv.x));
  float insideY = step(0.0, uv.y) * (1.0 - step(1.0, uv.y));
  return insideX * insideY;
}

float getCircleWithHole(vec2 uv, float r, float baseR, float aa) {
  float cell = getCell(uv);

  r = mix(.75 * baseR, 0., r);
  float rMod = mod(r, .5);

  float d = length(uv - .5);
  float circle = 1. - smoothstep(rMod - aa, rMod + aa, d);
  if (r < .5) {
    return circle;
  } else {
    return cell - circle;
  }
}

float getGooeyBall(vec2 uv, float r, float baseR) {
  float d = length(uv - .5);
  float sizeRadius = .3;
  if (u_grid == 1.) {
    sizeRadius = .42;
  }
  sizeRadius = mix(sizeRadius * baseR, 0., r);
  sizeRadius = max(sizeRadius, 1e-4);
  d = 1. - sst(0., sizeRadius, d);

  d = pow(d, 2. + baseR);
  return d;
}

float getSoftBall(vec2 uv, float r, float baseR) {
  float d = length(uv - .5);
  float sizeRadius = clamp(baseR, 0., 1.);
  sizeRadius = mix(.5 * sizeRadius, 0., r);
  sizeRadius = max(sizeRadius, 1e-4);
  d = 1. - lst(0., sizeRadius, d);
  float powRadius = 1. - lst(0., 2., baseR);
  d = pow(d, 4. + 3. * powRadius);
  return d;
}

float getUvFrame(vec2 uv, vec2 pad) {
  float aa = 0.0001;

  float left   = smoothstep(-pad.x, -pad.x + aa, uv.x);
  float right  = smoothstep(1.0 + pad.x, 1.0 + pad.x - aa, uv.x);
  float bottom = smoothstep(-pad.y, -pad.y + aa, uv.y);
  float top    = smoothstep(1.0 + pad.y, 1.0 + pad.y - aa, uv.y);

  return left * right * bottom * top;
}

float sigmoid(float x, float k) {
  return 1.0 / (1.0 + exp(-k * (x - 0.5)));
}

float getLumAtPx(vec2 uv, float contrast) {
  vec4 tex = u_image.eval(uv * u_resolution);
  vec3 color = vec3(
  sigmoid(tex.r, contrast),
  sigmoid(tex.g, contrast),
  sigmoid(tex.b, contrast)
  );
  float lum = dot(vec3(0.2126, 0.7152, 0.0722), color);
  lum = mix(1., lum, tex.a);
  lum = (u_inverted > .5) ? (1. - lum) : lum;
  return lum;
}

vec4 getLumBall(vec2 p, vec2 pad, vec2 inCellOffset, float contrast, float baseR, float stepSize, float aa) {
  p += inCellOffset;
  vec2 uv_i = floor(p);
  vec2 uv_f = fract(p);
  vec2 samplingUV = (uv_i + .5 - inCellOffset) * pad + vec2(.5);
  float outOfFrame = getUvFrame(samplingUV, pad * stepSize);

  float lum = getLumAtPx(samplingUV, contrast);
  vec4 ballColor = u_image.eval(samplingUV * u_resolution);
  ballColor.rgb *= ballColor.a;
  ballColor *= outOfFrame;

  float ball = 0.;
  if (u_type == 0.) {
    // classic
    ball = getCircle(uv_f, lum, baseR, aa);
  } else if (u_type == 1.) {
    // gooey
    ball = getGooeyBall(uv_f, lum, baseR);
  } else if (u_type == 2.) {
    // holes
    ball = getCircleWithHole(uv_f, lum, baseR, aa);
  } else if (u_type == 3.) {
    // soft
    ball = getSoftBall(uv_f, lum, baseR);
  }

  float shape = ball * outOfFrame;
  return vec4(ballColor.rgb * shape, shape);
}

half4 main(vec2 fragCoord) {
  vec2 imageUV = getImageUV(fragCoord);

  float stepMultiplier = 1.;
  if (u_type == 0.) {
    // classic
    stepMultiplier = 2.;
  } else if (u_type == 1. || u_type == 3.) {
    // gooey & soft
    stepMultiplier = 6.;
  }

  float cellsPerSide = mix(300., 7., pow(u_size, .7));
  cellsPerSide /= stepMultiplier;
  float cellSizeY = 1. / cellsPerSide;
  vec2 pad = cellSizeY * vec2(1. / u_imageAspectRatio, 1.);
  if (u_type == 1. && u_grid == 1.) {
    // gooey diagonal grid works differently
    pad *= .7;
  }

  vec2 uv = imageUV;
  uv -= vec2(.5);
  uv /= pad;

  // Derivatives are unavailable in AGSL: the dot fields vary at cell
  // frequency, so estimate their screen-space width from the cell size in px.
  float aa = 2. / (pad.y * u_resolution.y);

  float contrast = mix(0., 15., pow(u_contrast, 1.5));
  float baseRadius = u_radius;
  if (u_originalColors > .5) {
    contrast = mix(.1, 4., pow(u_contrast, 2.));
    baseRadius = 2. * pow(.5 * u_radius, .3);
  }

  float totalShape = 0.;
  vec3 totalColor = vec3(0.);
  float totalOpacity = 0.;

  float stepSize = 1. / stepMultiplier;
  for (int ix = 0; ix < 6; ix++) {
    for (int iy = 0; iy < 6; iy++) {
      if (float(ix) < stepMultiplier && float(iy) < stepMultiplier) {
        float x = -.5 + float(ix) * stepSize;
        float y = -.5 + float(iy) * stepSize;
        vec2 offset = vec2(x, y);

        bool skip = false;
        if (u_grid == 1.) {
          float rowIndex = float(iy);
          float colIndex = float(ix);
          if (stepSize == 1.) {
            rowIndex = floor(uv.y + y + 1.);
            if (u_type == 1.) {
              colIndex = floor(uv.x + x + 1.);
            }
          }
          if (u_type == 1.) {
            if (mod(rowIndex + colIndex, 2.) == 1.) {
              skip = true;
            }
          } else {
            if (mod(rowIndex, 2.) == 1.) {
              offset.x += .5 * stepSize;
            }
          }
        }

        if (!skip) {
          vec4 ballData = getLumBall(uv, pad, offset, contrast, baseRadius, stepSize, aa);
          totalColor   += ballData.rgb;
          totalShape   += ballData.a;
          totalOpacity += ballData.a;
        }
      }
    }
  }

  const float eps = 1e-4;

  totalColor /= max(totalShape, eps);
  totalOpacity /= max(totalShape, eps);

  float finalShape = 0.;
  if (u_type == 0.) {
    finalShape = min(1., totalShape);
  } else if (u_type == 1.) {
    float th = .5;
    finalShape = smoothstep(th - aa, th + aa, totalShape);
  } else if (u_type == 2.) {
    finalShape = min(1., totalShape);
  } else if (u_type == 3.) {
    finalShape = totalShape;
  }

  vec2 grainSize = mix(2000., 200., u_grainSize) * vec2(1., 1. / u_imageAspectRatio);
  vec2 grainUV = imageUV - .5;
  grainUV *= grainSize;
  grainUV += .5;
  float grain = valueNoise(grainUV);
  grain = smoothstep(.55, .7 + .2 * u_grainMixer, grain);
  grain *= u_grainMixer;
  finalShape = mix(finalShape, 0., grain);

  vec3 color = vec3(0.);
  float opacity = 0.;

  if (u_originalColors > .5) {
    color = totalColor * finalShape;
    opacity = totalOpacity * finalShape;

    vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
    color = color + bgColor * (1. - opacity);
    opacity = opacity + u_colorBack.a * (1. - opacity);
  } else {
    vec3 fgColor = u_colorFront.rgb * u_colorFront.a;
    float fgOpacity = u_colorFront.a;
    vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
    float bgOpacity = u_colorBack.a;

    color = fgColor * finalShape;
    opacity = fgOpacity * finalShape;
    color += bgColor * (1. - opacity);
    opacity += bgOpacity * (1. - opacity);
  }

  float grainOverlay = valueNoise(rotate(grainUV, 1.) + vec2(3.));
  grainOverlay = mix(grainOverlay, valueNoise(rotate(grainUV, 2.) + vec2(-1.)), .5);
  grainOverlay = pow(grainOverlay, 1.3);

  float grainOverlayV = grainOverlay * 2. - 1.;
  vec3 grainOverlayColor = vec3(step(0., grainOverlayV));
  float grainOverlayStrength = u_grainOverlay * abs(grainOverlayV);
  grainOverlayStrength = pow(grainOverlayStrength, .8);
  color = mix(color, grainOverlayColor, .5 * grainOverlayStrength);

  opacity += .5 * grainOverlayStrength;
  opacity = clamp(opacity, 0., 1.);

  return half4(color, opacity);
}
"""

/** Dot style for [halftoneDots]. */
public enum class HalftoneDotsType(public val value: Float) {
  Classic(0f),
  Gooey(1f),
  Holes(2f),
  Soft(3f),
}

/** Grid type for [halftoneDots]. */
public enum class HalftoneDotsGrid(public val value: Float) {
  Square(0f),
  Hex(1f),
}

public object HalftoneDotsDefaults {
  public val ColorBack: Color = Color(0xFFF2F1E8)
  public val ColorFront: Color = Color(0xFF2B2B2B)
}

/**
 * A halftone-dot image filter featuring customizable grids, color palettes,
 * and dot styles.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun Modifier.halftoneDots(
  colorBack: Color = HalftoneDotsDefaults.ColorBack,
  colorFront: Color = HalftoneDotsDefaults.ColorFront,
  size: Float = 0.5f,
  radius: Float = 1.25f,
  contrast: Float = 0.4f,
  originalColors: Boolean = false,
  inverted: Boolean = false,
  grainMixer: Float = 0.2f,
  grainOverlay: Float = 0.2f,
  grainSize: Float = 0.5f,
  grid: HalftoneDotsGrid = HalftoneDotsGrid.Hex,
  type: HalftoneDotsType = HalftoneDotsType.Gooey,
  sizing: SizingParams = SizingParams(fit = ShaderFit.Cover),
): Modifier = mirageEffect(
  shaderSource = SHADER,
  sizing = sizing,
) { _ ->
  setColorUniform4f("u_colorFront", colorFront)
  setColorUniform4f("u_colorBack", colorBack)
  setFloatUniform("u_size", size)
  setFloatUniform("u_radius", radius)
  setFloatUniform("u_contrast", contrast)
  setFloatUniform("u_originalColors", if (originalColors) 1f else 0f)
  setFloatUniform("u_inverted", if (inverted) 1f else 0f)
  setFloatUniform("u_grainMixer", grainMixer)
  setFloatUniform("u_grainOverlay", grainOverlay)
  setFloatUniform("u_grainSize", grainSize)
  setFloatUniform("u_grid", grid.value)
  setFloatUniform("u_type", type.value)
}
