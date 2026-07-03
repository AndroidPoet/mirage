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

private const val MAX_BLUR_RADIUS = 50

private val SHADER = """
$SIZING_AGSL

uniform shader u_image;

uniform vec4 u_colorBack;
uniform vec4 u_colorShadow;
uniform vec4 u_colorHighlight;

uniform float u_size;
uniform float u_shadows;
uniform float u_angle;
uniform float u_stretch;
uniform float u_shape;
uniform float u_distortion;
uniform float u_highlights;
uniform float u_distortionShape;
uniform float u_shift;
uniform float u_blur;
uniform float u_edges;
uniform float u_marginLeft;
uniform float u_marginRight;
uniform float u_marginTop;
uniform float u_marginBottom;
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

// Screen-space derivatives are unavailable in AGSL: callers pass fw = the
// per-pixel step of uv measured with one-pixel finite differences.
float getUvFrame(vec2 uv, float softness, vec2 fw) {
  float aax = 2. * fw.x;
  float aay = 2. * fw.y;
  float left   = smoothstep(0., aax + softness, uv.x);
  float right  = 1. - smoothstep(1. - softness - aax, 1., uv.x);
  float bottom = smoothstep(0., aay + softness, uv.y);
  float top    = 1. - smoothstep(1. - softness - aay, 1., uv.y);
  return left * right * bottom * top;
}

vec4 samplePremultiplied(vec2 uv) {
  vec4 c = vec4(u_image.eval(uv * u_resolution));
  c.rgb *= c.a;
  return c;
}

vec4 getBlur(vec2 uv, vec2 texelSize, vec2 dir, float sigma) {
  if (sigma <= .5) {
    return vec4(u_image.eval(uv * u_resolution));
  }
  int radius = int(min(float($MAX_BLUR_RADIUS), ceil(3.0 * sigma)));

  float twoSigma2 = 2.0 * sigma * sigma;
  float gaussianNorm = 1.0 / sqrt(TWO_PI * sigma * sigma);

  vec4 sum = samplePremultiplied(uv) * gaussianNorm;
  float weightSum = gaussianNorm;

  for (int i = 1; i <= $MAX_BLUR_RADIUS; i++) {
    if (i <= radius) {
      float x = float(i);
      float w = exp(-(x * x) / twoSigma2) * gaussianNorm;

      vec2 offset = dir * texelSize * x;
      vec4 s1 = samplePremultiplied(uv + offset);
      vec4 s2 = samplePremultiplied(uv - offset);

      sum += (s1 + s2) * w;
      weightSum += 2.0 * w;
    }
  }

  vec4 result = sum / weightSum;
  if (result.a > 0.) {
    result.rgb /= result.a;
  }

  return result;
}

vec2 rotateAspect(vec2 p, float a, float aspect) {
  p.x *= aspect;
  p = rotate(p, a);
  p.x /= aspect;
  return p;
}

vec2 toPatternUV(vec2 imageUV, float patternSize, float patternRotation) {
  vec2 p = imageUV - .5;
  p *= patternSize;
  p = rotateAspect(p, patternRotation, u_imageAspectRatio);
  return p;
}

float gridCurve(vec2 uv) {
  float curve = 0.;
  float patternY = uv.y / u_imageAspectRatio;
  if (u_shape > 4.5) {
    // pattern
    curve = .5 + .5 * sin(.5 * PI * uv.x) * cos(.5 * PI * patternY);
  } else if (u_shape > 3.5) {
    // zigzag
    curve = 10. * abs(fract(.1 * patternY) - .5);
  } else if (u_shape > 2.5) {
    // wave
    curve = 4. * sin(.23 * patternY);
  } else if (u_shape > 1.5) {
    // lines irregular
    curve = .5 + .5 * sin(.5 * uv.x) * sin(1.7 * uv.x);
  }
  return curve;
}

float smoothFract(float x, float w) {
  float f = fract(x);

  float edge = abs(f - 0.5) - 0.5;
  float band = smoothstep(-w, w, edge);

  return mix(f, 1.0 - f, band);
}

half4 main(vec2 fragCoord) {

  float patternRotation = -u_angle * PI / 180.;
  float patternSize = mix(200., 5., u_size);

  vec2 imageUV = getImageUV(fragCoord);
  // getImageUV is affine in fragCoord, so one-pixel finite differences give
  // the exact per-pixel UV step the upstream derivatives measured.
  vec2 imageUVdx = getImageUV(fragCoord + vec2(1., 0.));
  vec2 imageUVdy = getImageUV(fragCoord + vec2(0., 1.));

  vec2 uv = imageUV;

  vec2 uvMask = vec2(fragCoord.x, u_resolution.y - fragCoord.y) / u_resolution.xy;
  vec2 sw = vec2(.005);
  vec4 margins = vec4(u_marginLeft, u_marginTop, u_marginRight, u_marginBottom);
  float mask =
  smoothstep(margins.x, margins.x + sw.x, uvMask.x + sw.x) *
  smoothstep(margins.z, margins.z + sw.x, 1.0 - uvMask.x + sw.x) *
  smoothstep(margins.y, margins.y + sw.y, uvMask.y + sw.y) *
  smoothstep(margins.w, margins.w + sw.y, 1.0 - uvMask.y + sw.y);
  float maskOuter =
  smoothstep(margins.x - sw.x, margins.x, uvMask.x + sw.x) *
  smoothstep(margins.z - sw.x, margins.z, 1.0 - uvMask.x + sw.x) *
  smoothstep(margins.y - sw.y, margins.y, uvMask.y + sw.y) *
  smoothstep(margins.w - sw.y, margins.w, 1.0 - uvMask.y + sw.y);
  float maskStroke = maskOuter - mask;
  float maskInner =
  smoothstep(margins.x - 2. * sw.x, margins.x, uvMask.x) *
  smoothstep(margins.z - 2. * sw.x, margins.z, 1.0 - uvMask.x) *
  smoothstep(margins.y - 2. * sw.y, margins.y, uvMask.y) *
  smoothstep(margins.w - 2. * sw.y, margins.w, 1.0 - uvMask.y);
  float maskStrokeInner = maskInner - mask;

  uv = toPatternUV(uv, patternSize, patternRotation);
  vec2 uvDx = toPatternUV(imageUVdx, patternSize, patternRotation);
  vec2 uvDy = toPatternUV(imageUVdy, patternSize, patternRotation);

  float curve = gridCurve(uv);

  vec2 UvToFract = uv + curve;
  vec2 fractOrigUV = fract(uv);
  vec2 floorOrigUV = floor(uv);

  float utfXdx = uvDx.x + gridCurve(uvDx);
  float utfXdy = uvDy.x + gridCurve(uvDy);
  float fwUvToFractX = abs(utfXdx - UvToFract.x) + abs(utfXdy - UvToFract.x);

  float x = smoothFract(UvToFract.x, fwUvToFractX);
  float xNonSmooth = fract(UvToFract.x) + .0001;

  float highlightsWidth = 2. * max(.001, fwUvToFractX);
  highlightsWidth += 2. * maskStrokeInner;
  float highlights = smoothstep(0., highlightsWidth, xNonSmooth);
  highlights *= smoothstep(1., 1. - highlightsWidth, xNonSmooth);
  highlights = 1. - highlights;
  highlights *= u_highlights;
  highlights = clamp(highlights, 0., 1.);
  highlights *= mask;

  float shadows = pow(x, 1.3);
  float distortion = 0.;
  float fadeX = 1.;
  float frameFade = 0.;

  float xNonSmoothDx = fract(utfXdx) + .0001;
  float xNonSmoothDy = fract(utfXdy) + .0001;
  float aa = abs(xNonSmoothDx - xNonSmooth) + abs(xNonSmoothDy - xNonSmooth);
  aa = max(aa, abs(uvDx.x - uv.x) + abs(uvDy.x - uv.x));
  aa = max(aa, fwUvToFractX);
  aa = max(aa, .0001);

  if (u_distortionShape == 1.) {
    distortion = -pow(1.5 * x, 3.);
    distortion += (.5 - u_shift);

    frameFade = pow(1.5 * x, 3.);
    aa = max(.2, aa);
    aa += mix(.2, 0., u_size);
    fadeX = smoothstep(0., aa, xNonSmooth) * smoothstep(1., 1. - aa, xNonSmooth);
    distortion = mix(.5, distortion, fadeX);
  } else if (u_distortionShape == 2.) {
    distortion = 2. * pow(x, 2.);
    distortion -= (.5 + u_shift);

    frameFade = pow(abs(x - .5), 4.);
    aa = max(.2, aa);
    aa += mix(.2, 0., u_size);
    fadeX = smoothstep(0., aa, xNonSmooth) * smoothstep(1., 1. - aa, xNonSmooth);
    distortion = mix(.5, distortion, fadeX);
    frameFade = mix(1., frameFade, .5 * fadeX);
  } else if (u_distortionShape == 3.) {
    distortion = pow(abs(2. * (xNonSmooth - .5)), 6.);
    distortion -= .25;
    distortion -= u_shift;

    frameFade = 1. - 2. * pow(abs(x - .4), 2.);
    aa = .15;
    aa += mix(.1, 0., u_size);
    fadeX = smoothstep(0., aa, xNonSmooth) * smoothstep(1., 1. - aa, xNonSmooth);
    frameFade = mix(1., frameFade, fadeX);

  } else if (u_distortionShape == 4.) {
    x = xNonSmooth;
    distortion = sin((x + .25) * TWO_PI);
    shadows = .5 + .5 * asin(distortion) / (.5 * PI);
    distortion *= .5;
    distortion -= u_shift;
    frameFade = .5 + .5 * sin(x * TWO_PI);
  } else if (u_distortionShape == 5.) {
    distortion -= pow(abs(x), .2) * x;
    distortion += .33;
    distortion -= 3. * u_shift;
    distortion *= .33;

    frameFade = .3 * (smoothstep(.0, 1., x));
    shadows = pow(x, 2.5);

    aa = max(.1, aa);
    aa += mix(.1, 0., u_size);
    fadeX = smoothstep(0., aa, xNonSmooth) * smoothstep(1., 1. - aa, xNonSmooth);
    distortion *= fadeX;
  }

  vec2 dudx = imageUVdx - imageUV;
  vec2 dudy = imageUVdy - imageUV;
  vec2 grainUV = imageUV - .5;
  grainUV *= (.8 / vec2(length(dudx), length(dudy)));
  grainUV += .5;
  float grain = valueNoise(grainUV);
  grain = smoothstep(.4, .7, grain);
  grain *= u_grainMixer;
  distortion = mix(distortion, 0., grain);

  shadows = min(shadows, 1.);
  shadows += maskStrokeInner;
  shadows *= mask;
  shadows = min(shadows, 1.);
  shadows *= pow(u_shadows, 2.);
  shadows = clamp(shadows, 0., 1.);

  distortion *= 3. * u_distortion;
  frameFade *= u_distortion;

  fractOrigUV.x += distortion;
  floorOrigUV = rotateAspect(floorOrigUV, -patternRotation, u_imageAspectRatio);
  fractOrigUV = rotateAspect(fractOrigUV, -patternRotation, u_imageAspectRatio);

  uv = (floorOrigUV + fractOrigUV) / patternSize;
  uv += pow(maskStroke, 4.);

  uv += vec2(.5);

  uv = mix(imageUV, uv, smoothstep(0., .7, mask));
  float blur = mix(0., 50., u_blur);
  blur = mix(0., blur, smoothstep(.5, 1., mask));

  float edgeDistortion = mix(.0, .04, u_edges);
  edgeDistortion += .06 * frameFade * u_edges;
  edgeDistortion *= mask;
  vec2 uvFw = vec2(abs(dudx.x) + abs(dudy.x), abs(dudx.y) + abs(dudy.y));
  float frame = getUvFrame(uv, edgeDistortion, uvFw);

  float stretch = 1. - smoothstep(0., .5, xNonSmooth) * smoothstep(1., 1. - .5, xNonSmooth);
  stretch = pow(stretch, 2.);
  stretch *= mask;
  stretch *= getUvFrame(uv, .1 + .05 * mask * frameFade, uvFw);
  uv.y = mix(uv.y, .5, u_stretch * stretch);

  vec4 image = getBlur(uv, 1. / u_resolution / u_pixelRatio, vec2(0., 1.), blur);
  image.rgb *= image.a;
  vec4 backColor = u_colorBack;
  backColor.rgb *= backColor.a;
  vec4 highlightColor = u_colorHighlight;
  highlightColor.rgb *= highlightColor.a;
  vec4 shadowColor = u_colorShadow;

  vec3 color = highlightColor.rgb * highlights;
  float opacity = highlightColor.a * highlights;

  shadows = mix(shadows * shadowColor.a, 0., highlights);
  color = mix(color, shadowColor.rgb * shadowColor.a, .5 * shadows);
  color += .5 * pow(shadows, .5) * shadowColor.rgb;
  opacity += shadows;
  color = clamp(color, vec3(0.), vec3(1.));
  opacity = clamp(opacity, 0., 1.);

  color += image.rgb * (1. - opacity) * frame;
  opacity += image.a * (1. - opacity) * frame;

  color += backColor.rgb * (1. - opacity);
  opacity += backColor.a * (1. - opacity);

  float grainOverlay = valueNoise(rotate(grainUV, 1.) + vec2(3.));
  grainOverlay = mix(grainOverlay, valueNoise(rotate(grainUV, 2.) + vec2(-1.)), .5);
  grainOverlay = pow(grainOverlay, 1.3);

  float grainOverlayV = grainOverlay * 2. - 1.;
  vec3 grainOverlayColor = vec3(step(0., grainOverlayV));
  float grainOverlayStrength = u_grainOverlay * abs(grainOverlayV);
  grainOverlayStrength = pow(grainOverlayStrength, .8);
  grainOverlayStrength *= mask;
  color = mix(color, grainOverlayColor, .35 * grainOverlayStrength);

  opacity += .5 * grainOverlayStrength;
  opacity = clamp(opacity, 0., 1.);

  return half4(color, opacity);
}
"""

/** Grid shape of the fluted-glass stripes (upstream GlassGridShapes). */
public enum class GlassGridShape(public val value: Float) {
  Lines(1f),
  LinesIrregular(2f),
  Wave(3f),
  Zigzag(4f),
  Pattern(5f),
}

/** Shape of the distortion inside each stripe (upstream GlassDistortionShapes). */
public enum class GlassDistortionShape(public val value: Float) {
  Prism(1f),
  Lens(2f),
  Contour(3f),
  Cascade(4f),
  Flat(5f),
}

public object FlutedGlassDefaults {
  public val ColorBack: Color = Color(0x00000000)
  public val ColorShadow: Color = Color(0xFF000000)
  public val ColorHighlight: Color = Color(0xFFFFFFFF)
}

/**
 * Fluted glass image filter that transforms the content into streaked,
 * ribbed distortions, giving a mix of clarity and obscurity.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun Modifier.flutedGlass(
  colorBack: Color = FlutedGlassDefaults.ColorBack,
  colorShadow: Color = FlutedGlassDefaults.ColorShadow,
  colorHighlight: Color = FlutedGlassDefaults.ColorHighlight,
  shadows: Float = 0.25f,
  size: Float = 0.5f,
  angle: Float = 0f,
  distortionShape: GlassDistortionShape = GlassDistortionShape.Prism,
  highlights: Float = 0.1f,
  shape: GlassGridShape = GlassGridShape.Lines,
  distortion: Float = 0.5f,
  shift: Float = 0f,
  blur: Float = 0f,
  edges: Float = 0.25f,
  stretch: Float = 0f,
  marginLeft: Float = 0f,
  marginRight: Float = 0f,
  marginTop: Float = 0f,
  marginBottom: Float = 0f,
  grainMixer: Float = 0f,
  grainOverlay: Float = 0f,
  sizing: SizingParams = SizingParams(fit = ShaderFit.Cover),
): Modifier = mirageEffect(
  shaderSource = SHADER,
  sizing = sizing,
) { _ ->
  setColorUniform4f("u_colorBack", colorBack)
  setColorUniform4f("u_colorShadow", colorShadow)
  setColorUniform4f("u_colorHighlight", colorHighlight)
  setFloatUniform("u_shadows", shadows)
  setFloatUniform("u_size", size)
  setFloatUniform("u_angle", angle)
  setFloatUniform("u_distortionShape", distortionShape.value)
  setFloatUniform("u_highlights", highlights)
  setFloatUniform("u_shape", shape.value)
  setFloatUniform("u_distortion", distortion)
  setFloatUniform("u_shift", shift)
  setFloatUniform("u_blur", blur)
  setFloatUniform("u_edges", edges)
  setFloatUniform("u_stretch", stretch)
  setFloatUniform("u_marginLeft", marginLeft)
  setFloatUniform("u_marginRight", marginRight)
  setFloatUniform("u_marginTop", marginTop)
  setFloatUniform("u_marginBottom", marginBottom)
  setFloatUniform("u_grainMixer", grainMixer)
  setFloatUniform("u_grainOverlay", grainOverlay)
}
