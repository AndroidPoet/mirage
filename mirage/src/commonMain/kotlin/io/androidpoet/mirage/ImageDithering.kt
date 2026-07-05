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
uniform vec4 u_colorHighlight;

uniform float u_type;
uniform float u_pxSize;
uniform float u_originalColors;
uniform float u_inverted;
uniform float u_colorSteps;

${Agsl.HASH21}
${Agsl.PI}

float getUvFrame(vec2 uv, vec2 pad) {
  float aa = 0.0001;

  float left   = smoothstep(-pad.x, -pad.x + aa, uv.x);
  float right  = smoothstep(1.0 + pad.x, 1.0 + pad.x - aa, uv.x);
  float bottom = smoothstep(-pad.y, -pad.y + aa, uv.y);
  float top    = smoothstep(1.0 + pad.y, 1.0 + pad.y - aa, uv.y);

  return left * right * bottom * top;
}

vec2 getDitherImageUV(vec2 uv) {
  vec2 boxOrigin = vec2(.5 - u_originX, u_originY - .5);
  float r = u_rotation * PI / 180.;
  mat2 graphicRotation = mat2(cos(r), sin(r), -sin(r), cos(r));
  vec2 graphicOffset = vec2(-u_offsetX, u_offsetY);

  vec2 imageBoxSize;
  if (u_fit == 1.) { // contain
    imageBoxSize.x = min(u_resolution.x / u_imageAspectRatio, u_resolution.y) * u_imageAspectRatio;
  } else if (u_fit == 2.) { // cover
    imageBoxSize.x = max(u_resolution.x / u_imageAspectRatio, u_resolution.y) * u_imageAspectRatio;
  } else {
    imageBoxSize.x = min(10.0, 10.0 / u_imageAspectRatio * u_imageAspectRatio);
  }
  imageBoxSize.y = imageBoxSize.x / u_imageAspectRatio;
  vec2 imageBoxScale = u_resolution.xy / imageBoxSize;

  vec2 imageUV = uv;
  imageUV *= imageBoxScale;
  imageUV += boxOrigin * (imageBoxScale - 1.);
  imageUV += graphicOffset;
  imageUV /= u_scale;
  imageUV.x *= u_imageAspectRatio;
  imageUV = graphicRotation * imageUV;
  imageUV.x /= u_imageAspectRatio;

  imageUV += .5;
  imageUV.y = 1. - imageUV.y;

  return imageUV;
}

// AGSL has no const arrays or dynamic indexing: the Bayer matrices become
// row constants selected with constant-bound branches.
float bayer2Value(int px, int py) {
  if (py == 0) {
    return (px == 0) ? 0. : 2.;
  }
  return (px == 0) ? 3. : 1.;
}

float bayer4Value(int px, int py) {
  vec4 row = vec4(0., 8., 2., 10.);
  if (py == 1) {
    row = vec4(12., 4., 14., 6.);
  } else if (py == 2) {
    row = vec4(3., 11., 1., 9.);
  } else if (py == 3) {
    row = vec4(15., 7., 13., 5.);
  }
  if (px == 0) { return row.x; }
  if (px == 1) { return row.y; }
  if (px == 2) { return row.z; }
  return row.w;
}

float bayer8Value(int px, int py) {
  vec4 rowA = vec4(0., 32., 8., 40.);
  vec4 rowB = vec4(2., 34., 10., 42.);
  if (py == 1) {
    rowA = vec4(48., 16., 56., 24.);
    rowB = vec4(50., 18., 58., 26.);
  } else if (py == 2) {
    rowA = vec4(12., 44., 4., 36.);
    rowB = vec4(14., 46., 6., 38.);
  } else if (py == 3) {
    rowA = vec4(60., 28., 52., 20.);
    rowB = vec4(62., 30., 54., 22.);
  } else if (py == 4) {
    rowA = vec4(3., 35., 11., 43.);
    rowB = vec4(1., 33., 9., 41.);
  } else if (py == 5) {
    rowA = vec4(51., 19., 59., 27.);
    rowB = vec4(49., 17., 57., 25.);
  } else if (py == 6) {
    rowA = vec4(15., 47., 7., 39.);
    rowB = vec4(13., 45., 5., 37.);
  } else if (py == 7) {
    rowA = vec4(63., 31., 55., 23.);
    rowB = vec4(61., 29., 53., 21.);
  }
  if (px == 0) { return rowA.x; }
  if (px == 1) { return rowA.y; }
  if (px == 2) { return rowA.z; }
  if (px == 3) { return rowA.w; }
  if (px == 4) { return rowB.x; }
  if (px == 5) { return rowB.y; }
  if (px == 6) { return rowB.z; }
  return rowB.w;
}

float getBayerValue(vec2 uv, int size) {
  vec2 cell = fract(uv / float(size)) * float(size);
  int px = int(cell.x);
  int py = int(cell.y);

  if (size == 2) {
    return bayer2Value(px, py) / 4.0;
  } else if (size == 4) {
    return bayer4Value(px, py) / 16.0;
  } else if (size == 8) {
    return bayer8Value(px, py) / 64.0;
  }
  return 0.0;
}

half4 main(vec2 fragCoord) {
  float pxSize = u_pxSize * u_pixelRatio;
  // GLSL frag coords are y-up while AGSL fragCoord is y-down: flip y so the
  // pixel grid and image transforms match the original.
  vec2 flippedCoord = vec2(fragCoord.x, u_resolution.y - fragCoord.y);
  vec2 pxSizeUV = flippedCoord - .5 * u_resolution;
  pxSizeUV /= pxSize;
  vec2 canvasPixelizedUV = (floor(pxSizeUV) + .5) * pxSize;
  vec2 normalizedUV = canvasPixelizedUV / u_resolution;

  vec2 imageUV = getDitherImageUV(normalizedUV);
  vec2 ditheringNoiseUV = canvasPixelizedUV;
  vec4 image = u_image.eval(imageUV * u_resolution);
  float frame = getUvFrame(imageUV, pxSize / u_resolution);

  int type = int(floor(u_type));
  float dithering = 0.0;

  float lum = dot(vec3(.2126, .7152, .0722), image.rgb);
  lum = (u_inverted > .5) ? (1. - lum) : lum;

  if (type == 1) {
    dithering = step(hash21(ditheringNoiseUV), lum);
  } else if (type == 2) {
    dithering = getBayerValue(pxSizeUV, 2);
  } else if (type == 3) {
    dithering = getBayerValue(pxSizeUV, 4);
  } else {
    dithering = getBayerValue(pxSizeUV, 8);
  }

  float colorSteps = max(floor(u_colorSteps), 1.);
  vec3 color = vec3(0.0);
  float opacity = 1.;

  dithering -= .5;
  float brightness = clamp(lum + dithering / colorSteps, 0.0, 1.0);
  brightness = mix(0.0, brightness, frame);
  brightness = mix(0.0, brightness, image.a);
  float quantLum = floor(brightness * colorSteps + 0.5) / colorSteps;
  quantLum = mix(0.0, quantLum, frame);

  if (u_originalColors > .5) {
    vec3 normColor = image.rgb / max(lum, 0.001);
    color = normColor * quantLum;

    float quantAlpha = floor(image.a * colorSteps + 0.5) / colorSteps;
    opacity = mix(quantLum, 1., quantAlpha);
  } else {
    vec3 fgColor = u_colorFront.rgb * u_colorFront.a;
    float fgOpacity = u_colorFront.a;
    vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
    float bgOpacity = u_colorBack.a;
    vec3 hlColor = u_colorHighlight.rgb * u_colorHighlight.a;
    float hlOpacity = u_colorHighlight.a;

    fgColor = mix(fgColor, hlColor, step(1.02 - .02 * u_colorSteps, brightness));
    fgOpacity = mix(fgOpacity, hlOpacity, step(1.02 - .02 * u_colorSteps, brightness));

    color = fgColor * quantLum;
    opacity = fgOpacity * quantLum;
    color += bgColor * (1.0 - opacity);
    opacity += bgOpacity * (1.0 - opacity);
  }

  return half4(color, opacity);
}
"""

/** Dithering pattern for [imageDithering]. */
public enum class ImageDitheringType(public val value: Float) {
  Random(1f),
  Bayer2x2(2f),
  Bayer4x4(3f),
  Bayer8x8(4f),
}

public object ImageDitheringDefaults {
  public val ColorFront: Color = Color(0xFF94FFAF)
  public val ColorBack: Color = Color(0xFF000C38)
  public val ColorHighlight: Color = Color(0xFFEAFF94)
}

/**
 * A dithering image filter with 4 dithering modes and multiple color palettes,
 * using either predefined colors or colors sampled from the content.
 */
@Composable
public fun Modifier.imageDithering(
  colorFront: Color = ImageDitheringDefaults.ColorFront,
  colorBack: Color = ImageDitheringDefaults.ColorBack,
  colorHighlight: Color = ImageDitheringDefaults.ColorHighlight,
  type: ImageDitheringType = ImageDitheringType.Bayer8x8,
  size: Float = 2f,
  colorSteps: Int = 2,
  originalColors: Boolean = false,
  inverted: Boolean = false,
  sizing: SizingParams = SizingParams(fit = ShaderFit.Cover),
): Modifier = mirageEffect(
  shaderSource = SHADER,
  sizing = sizing,
) { _ ->
  setColorUniform4f("u_colorFront", colorFront)
  setColorUniform4f("u_colorBack", colorBack)
  setColorUniform4f("u_colorHighlight", colorHighlight)
  setFloatUniform("u_type", type.value)
  setFloatUniform("u_pxSize", size)
  setFloatUniform("u_colorSteps", colorSteps.toFloat())
  setFloatUniform("u_originalColors", if (originalColors) 1f else 0f)
  setFloatUniform("u_inverted", if (inverted) 1f else 0f)
}
