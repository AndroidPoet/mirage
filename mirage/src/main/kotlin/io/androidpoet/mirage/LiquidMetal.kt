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
import io.androidpoet.mirage.core.SizingParams
import io.androidpoet.mirage.core.mirageEffect
import io.androidpoet.mirage.core.setColorUniform4f

private val SHADER = """
$SIZING_AGSL

uniform shader u_image;

uniform float u_time;

uniform vec4 u_colorBack;
uniform vec4 u_colorTint;

uniform float u_softness;
uniform float u_repetition;
uniform float u_shiftRed;
uniform float u_shiftBlue;
uniform float u_distortion;
uniform float u_contour;
uniform float u_angle;

uniform float u_shape;
uniform float u_isImage;

${Agsl.PI}
${Agsl.ROTATION2}
${Agsl.SIMPLEX_NOISE}
${Agsl.BANDING_FIX}

vec2 getResponsiveBoxGivenSize() {
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.)) * u_pixelRatio;
  return vec2(
    (u_worldWidth == 0.) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.) ? u_resolution.y : givenBoxSize.y);
}

float getColorChanges(float c1, float c2, float stripe_p, vec3 w, float blur, float bump, float tint) {

  float ch = mix(c2, c1, smoothstep(.0, 2. * blur, stripe_p));

  float border = w.x;
  ch = mix(ch, c2, smoothstep(border, border + 2. * blur, stripe_p));

  if (u_isImage > .5) {
    bump = smoothstep(.2, .8, bump);
  }
  border = w.x + .4 * (1. - bump) * w.y;
  ch = mix(ch, c1, smoothstep(border, border + 2. * blur, stripe_p));

  border = w.x + .5 * (1. - bump) * w.y;
  ch = mix(ch, c2, smoothstep(border, border + 2. * blur, stripe_p));

  border = w.x + w.y;
  ch = mix(ch, c1, smoothstep(border, border + 2. * blur, stripe_p));

  float gradient_t = (stripe_p - w.x - w.y) / w.z;
  float gradient = mix(c1, c2, smoothstep(0., 1., gradient_t));
  ch = mix(ch, gradient, smoothstep(border, border + .5 * blur, stripe_p));

  // Tint color is applied with color burn blending
  ch = mix(ch, 1. - min(1., (1. - ch) / max(tint, 0.0001)), u_colorTint.a);
  return ch;
}

float getImgFrame(vec2 uv, float th) {
  float frame = 1.;
  frame *= smoothstep(0., th, uv.y);
  frame *= 1.0 - smoothstep(1. - th, 1., uv.y);
  frame *= smoothstep(0., th, uv.x);
  frame *= 1.0 - smoothstep(1. - th, 1., uv.x);
  return frame;
}

float blurEdge3x3(vec2 uv, float radius, float centerSample) {
  vec2 texel = 1.0 / u_resolution;
  vec2 r = radius * texel;

  float w1 = 1.0;
  float w2 = 2.0;
  float w4 = 4.0;
  float norm = 16.0;
  float sum = w4 * centerSample;

  sum += w2 * u_image.eval((uv + vec2(0.0, -r.y)) * u_resolution).r;
  sum += w2 * u_image.eval((uv + vec2(0.0, r.y)) * u_resolution).r;
  sum += w2 * u_image.eval((uv + vec2(-r.x, 0.0)) * u_resolution).r;
  sum += w2 * u_image.eval((uv + vec2(r.x, 0.0)) * u_resolution).r;

  sum += w1 * u_image.eval((uv + vec2(-r.x, -r.y)) * u_resolution).r;
  sum += w1 * u_image.eval((uv + vec2(r.x, -r.y)) * u_resolution).r;
  sum += w1 * u_image.eval((uv + vec2(-r.x, r.y)) * u_resolution).r;
  sum += w1 * u_image.eval((uv + vec2(r.x, r.y)) * u_resolution).r;

  return sum / norm;
}

float lst(float edge0, float edge1, float x) {
  return clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
}

float getShapeEdge(vec2 fragCoord, float t) {
  vec2 uv = getObjectUV(fragCoord) + .5;
  uv.y = 1. - uv.y;
  float edge = 0.;
  if (u_shape < 1.) {
    // full-fill on canvas
    vec2 borderUV = getResponsiveUV(fragCoord) + .5;
    vec2 responsiveBoxGivenSize = getResponsiveBoxGivenSize();
    vec2 mask = min(borderUV, 1. - borderUV);
    vec2 pixel_thickness = min(250. / responsiveBoxGivenSize, vec2(.5));
    float maskX = smoothstep(0.0, pixel_thickness.x, mask.x);
    float maskY = smoothstep(0.0, pixel_thickness.y, mask.y);
    maskX = pow(maskX, .25);
    maskY = pow(maskY, .25);
    edge = clamp(1. - maskX * maskY, 0., 1.);
  } else if (u_shape < 2.) {
    // circle
    vec2 shapeUV = uv - .5;
    shapeUV *= .67;
    edge = pow(clamp(3. * length(shapeUV), 0., 1.), 18.);
  } else if (u_shape < 3.) {
    // daisy
    vec2 shapeUV = uv - .5;
    shapeUV *= 1.68;

    float r = length(shapeUV) * 2.;
    float a = atan(shapeUV.y, shapeUV.x) + .2;
    r *= (1. + .05 * sin(3. * a + 2. * t));
    float f = abs(cos(a * 3.));
    edge = smoothstep(f, f + .7, r);
    edge *= edge;
  } else if (u_shape < 4.) {
    // diamond
    vec2 shapeUV = uv - .5;
    shapeUV = rotate(shapeUV, .25 * PI);
    shapeUV *= 1.42;
    shapeUV += .5;
    vec2 mask = min(shapeUV, 1. - shapeUV);
    vec2 pixel_thickness = vec2(.15);
    float maskX = smoothstep(0.0, pixel_thickness.x, mask.x);
    float maskY = smoothstep(0.0, pixel_thickness.y, mask.y);
    maskX = pow(maskX, .25);
    maskY = pow(maskY, .25);
    edge = clamp(1. - maskX * maskY, 0., 1.);
  } else if (u_shape < 5.) {
    // metaballs
    vec2 shapeUV = uv - .5;
    shapeUV *= 1.3;
    edge = 0.;
    for (int i = 0; i < 5; i++) {
      float fi = float(i);
      float speed = 1.5 + 2. / 3. * sin(fi * 12.345);
      float angle = -fi * 1.5;
      vec2 dir1 = vec2(cos(angle), sin(angle));
      vec2 dir2 = vec2(cos(angle + 1.57), sin(angle + 1.));
      vec2 traj = .4 * (dir1 * sin(t * speed + fi * 1.23) + dir2 * cos(t * (speed * 0.7) + fi * 2.17));
      float d = length(shapeUV + traj);
      edge += pow(1.0 - clamp(d, 0.0, 1.0), 4.0);
    }
    edge = 1. - smoothstep(.65, .9, edge);
    edge = pow(edge, 4.);
  }
  return edge;
}

vec3 getStripePhases(vec2 fragCoord, float t,
    out float oEdge, out float oBump, out float oW1, out float oW2, out float oW3,
    out float oOpacity, out float oDiagBLtoTR, out float oDiagTLtoBR) {

  vec2 imageUV = getImageUV(fragCoord);
  vec2 uv = imageUV;
  vec4 img = u_image.eval(uv * u_resolution);

  if (u_isImage < .5) {
    uv = getObjectUV(fragCoord) + .5;
    uv.y = 1. - uv.y;
  }

  float cycleWidth = u_repetition;
  float edge = 0.;
  float contOffset = 1.;

  vec2 rotatedUV = uv - vec2(.5);
  float angle = (-u_angle + 70.) * PI / 180.;
  float cosA = cos(angle);
  float sinA = sin(angle);
  rotatedUV = vec2(
  rotatedUV.x * cosA - rotatedUV.y * sinA,
  rotatedUV.x * sinA + rotatedUV.y * cosA
  ) + vec2(.5);

  float opacity = 0.;

  if (u_isImage > .5) {
    float edgeRaw = img.r;
    edge = blurEdge3x3(uv, 6., edgeRaw);
    edge = pow(edge, 1.6);
    edge *= mix(0.0, 1.0, smoothstep(0.0, 0.4, u_contour));

    opacity = img.g;
    float frame = getImgFrame(imageUV, 0.);
    opacity *= frame;
  } else {
    // Screen-space derivatives are unavailable in AGSL: estimate them with
    // finite differences one pixel over, reusing the center evaluation.
    float edgeRawC = getShapeEdge(fragCoord, t);
    float edgeRawX = getShapeEdge(fragCoord + vec2(1., 0.), t);
    float edgeRawY = getShapeEdge(fragCoord + vec2(0., 1.), t);
    float fwEdgeRaw = abs(edgeRawX - edgeRawC) + abs(edgeRawY - edgeRawC);
    float contourQ = smoothstep(0.0, 0.4, u_contour);
    edge = mix(smoothstep(.9 - 2. * fwEdgeRaw, .9, edgeRawC), edgeRawC, contourQ);
    float edgeMixX = mix(smoothstep(.9 - 2. * fwEdgeRaw, .9, edgeRawX), edgeRawX, contourQ);
    float edgeMixY = mix(smoothstep(.9 - 2. * fwEdgeRaw, .9, edgeRawY), edgeRawY, contourQ);
    float fwEdgeMix = abs(edgeMixX - edge) + abs(edgeMixY - edge);

    opacity = 1. - smoothstep(.9 - 2. * fwEdgeMix, .9, edge);
    if (u_shape < 2.) {
      edge = 1.2 * edge;
    } else if (u_shape < 5.) {
      edge = 1.8 * pow(edge, 1.5);
    }

    if (u_shape < 1.) {
      // full-fill on canvas
      vec2 responsiveBoxGivenSize = getResponsiveBoxGivenSize();
      float ratio = responsiveBoxGivenSize.x / responsiveBoxGivenSize.y;
      uv = getResponsiveUV(fragCoord);
      if (ratio > 1.) {
        uv.y /= ratio;
      } else {
        uv.x *= ratio;
      }
      uv += .5;
      uv.y = 1. - uv.y;

      cycleWidth *= 2.;
      contOffset = 1.5;
    } else if ((u_shape >= 2.) && (u_shape < 3.)) {
      // daisy
      uv *= .8;
      cycleWidth *= 1.6;
    }
  }

  float diagBLtoTR = rotatedUV.x - rotatedUV.y;
  float diagTLtoBR = rotatedUV.x + rotatedUV.y;

  vec2 grad_uv = uv - .5;

  float dist = length(grad_uv + vec2(0., .2 * diagBLtoTR));
  grad_uv = rotate(grad_uv, (.25 - .2 * diagBLtoTR) * PI);
  float direction = grad_uv.x;

  float bump = pow(1.8 * dist, 1.2);
  bump = 1. - bump;
  bump *= pow(uv.y, .3);

  float thin_strip_1_ratio = .12 / cycleWidth * (1. - .4 * bump);
  float thin_strip_2_ratio = .07 / cycleWidth * (1. + .4 * bump);
  float wide_strip_ratio = (1. - thin_strip_1_ratio - thin_strip_2_ratio);

  float thin_strip_1_width = cycleWidth * thin_strip_1_ratio;
  float thin_strip_2_width = cycleWidth * thin_strip_2_ratio;

  float noise = snoise(uv - t);

  edge += (1. - edge) * u_distortion * noise;

  direction += diagBLtoTR;
  direction -= 2. * noise * diagBLtoTR * (smoothstep(0., 1., edge) * (1.0 - smoothstep(0., 1., edge)));
  direction *= mix(1., 1. - edge, smoothstep(.5, 1., u_contour));
  direction -= 1.7 * edge * smoothstep(.5, 1., u_contour);
  direction += .2 * pow(u_contour, 4.) * (1.0 - smoothstep(0., 1., edge));

  bump *= clamp(pow(uv.y, .1), .3, 1.);
  direction *= (.1 + (1.1 - edge) * bump);

  direction *= (.4 + .6 * (1.0 - smoothstep(.5, 1., edge)));
  direction += .18 * (smoothstep(.1, .2, uv.y) * (1.0 - smoothstep(.2, .4, uv.y)));
  direction += .03 * (smoothstep(.1, .2, 1. - uv.y) * (1.0 - smoothstep(.2, .4, 1. - uv.y)));

  direction *= (.5 + .5 * pow(uv.y, 2.));
  direction *= cycleWidth;
  direction -= t;

  float colorDispersion = (1. - bump);
  colorDispersion = clamp(colorDispersion, 0., 1.);
  float dispersionRed = colorDispersion;
  dispersionRed += .03 * bump * noise;
  dispersionRed += 5. * (smoothstep(-.1, .2, uv.y) * (1.0 - smoothstep(.1, .5, uv.y))) * (smoothstep(.4, .6, bump) * (1.0 - smoothstep(.4, 1., bump)));
  dispersionRed -= diagBLtoTR;

  float dispersionBlue = colorDispersion;
  dispersionBlue *= 1.3;
  dispersionBlue += (smoothstep(0., .4, uv.y) * (1.0 - smoothstep(.1, .8, uv.y))) * (smoothstep(.4, .6, bump) * (1.0 - smoothstep(.4, .8, bump)));
  dispersionBlue -= .2 * edge;

  dispersionRed *= (u_shiftRed / 20.);
  dispersionBlue *= (u_shiftBlue / 20.);

  oEdge = edge;
  oBump = bump;
  oW1 = thin_strip_1_width;
  oW2 = thin_strip_2_width;
  oW3 = wide_strip_ratio;
  oOpacity = opacity;
  oDiagBLtoTR = diagBLtoTR;
  oDiagTLtoBR = diagTLtoBR;

  return vec3(direction + dispersionRed, direction, direction - dispersionBlue);
}

half4 main(vec2 fragCoord) {

  const float firstFrameOffset = 2.8;
  float t = .3 * (u_time + firstFrameOffset);

  float edge = 0.;
  float bump = 0.;
  float w1v = 0.;
  float w2v = 0.;
  float w3v = 0.;
  float opacity = 0.;
  float diagBLtoTR = 0.;
  float diagTLtoBR = 0.;
  vec3 phases = getStripePhases(fragCoord, t, edge, bump, w1v, w2v, w3v, opacity, diagBLtoTR, diagTLtoBR);
  vec3 w = vec3(w1v, w2v, w3v);

  // The stripe anti-aliasing width comes from screen-space derivatives of the
  // stripe phases upstream; recompute the phases one pixel over instead.
  float dEdge = 0.;
  float dBump = 0.;
  float dW1 = 0.;
  float dW2 = 0.;
  float dW3 = 0.;
  float dOpacity = 0.;
  float dDiagBL = 0.;
  float dDiagTL = 0.;
  vec3 phasesX = getStripePhases(fragCoord + vec2(1., 0.), t, dEdge, dBump, dW1, dW2, dW3, dOpacity, dDiagBL, dDiagTL);
  vec3 phasesY = getStripePhases(fragCoord + vec2(0., 1.), t, dEdge, dBump, dW1, dW2, dW3, dOpacity, dDiagBL, dDiagTL);
  vec3 fwPhases = abs(phasesX - phases) + abs(phasesY - phases);

  vec3 color = vec3(0.);
  vec3 color1 = vec3(.98, 0.98, 1.);
  vec3 color2 = vec3(.1, .1, .1 + .1 * smoothstep(.7, 1.3, diagTLtoBR));

  float blur = 0.;
  float rExtraBlur = 0.;
  float gExtraBlur = 0.;
  if (u_isImage > .5) {
    float softness = 0.05 * u_softness;
    blur = softness + .5 * smoothstep(1., 10., u_repetition) * smoothstep(.0, 1., edge);
    float smallCanvasT = 1.0 - smoothstep(100., 500., min(u_resolution.x, u_resolution.y));
    blur += smallCanvasT * smoothstep(.0, 1., edge);
    rExtraBlur = softness * (0.05 + .1 * (u_shiftRed / 20.) * bump);
    gExtraBlur = softness * 0.05 / max(0.001, abs(1. - diagBLtoTR));
  } else {
    float contour = 0.;
    blur = u_softness / 15. + .3 * contour;
  }

  w.y -= .02 * smoothstep(.0, 1., edge + bump);
  float stripe_r = fract(phases.x);
  float r = getColorChanges(color1.r, color2.r, stripe_r, w, blur + fwPhases.x + rExtraBlur, bump, u_colorTint.r);
  float stripe_g = fract(phases.y);
  float g = getColorChanges(color1.g, color2.g, stripe_g, w, blur + fwPhases.y + gExtraBlur, bump, u_colorTint.g);
  float stripe_b = fract(phases.z);
  float b = getColorChanges(color1.b, color2.b, stripe_b, w, blur + fwPhases.z, bump, u_colorTint.b);

  color = vec3(r, g, b);
  color *= opacity;

  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  color = color + bgColor * (1. - opacity);
  opacity = opacity + u_colorBack.a * (1. - opacity);

  color += bandingNoise(fragCoord);

  return half4(color, opacity);
}
"""

/** Mask shape used when [Modifier.liquidMetal] is not driven by image content. */
public enum class LiquidMetalShape(public val value: Float) {
  None(0f),
  Circle(1f),
  Daisy(2f),
  Diamond(3f),
  Metaballs(4f),
}

public object LiquidMetalDefaults {
  public val ColorBack: Color = Color(0xFFAAAAAC)
  public val ColorTint: Color = Color(0xFFFFFFFF)
}

/**
 * Futuristic liquid-metal material: an animated stripe pattern distorted along
 * shape edges with chromatic dispersion. Renders over a procedural [shape] by
 * default; with [isImage] the content is treated as a pre-processed mask
 * (R = edge gradient, G = opacity).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun Modifier.liquidMetal(
  colorBack: Color = LiquidMetalDefaults.ColorBack,
  colorTint: Color = LiquidMetalDefaults.ColorTint,
  repetition: Float = 2f,
  softness: Float = 0.1f,
  shiftRed: Float = 0.3f,
  shiftBlue: Float = 0.3f,
  distortion: Float = 0.07f,
  contour: Float = 0.4f,
  angle: Float = 70f,
  shape: LiquidMetalShape = LiquidMetalShape.Diamond,
  isImage: Boolean = false,
  speed: Float = 1f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Object.copy(scale = 0.6f),
): Modifier = mirageEffect(
  shaderSource = SHADER,
  speed = speed,
  startFrame = startFrame,
  sizing = sizing,
) { time ->
  setFloatUniform("u_time", time)
  setColorUniform4f("u_colorBack", colorBack)
  setColorUniform4f("u_colorTint", colorTint)
  setFloatUniform("u_softness", softness)
  setFloatUniform("u_repetition", repetition)
  setFloatUniform("u_shiftRed", shiftRed)
  setFloatUniform("u_shiftBlue", shiftBlue)
  setFloatUniform("u_distortion", distortion)
  setFloatUniform("u_contour", contour)
  setFloatUniform("u_angle", angle)
  setFloatUniform("u_shape", shape.value)
  setFloatUniform("u_isImage", if (isImage) 1f else 0f)
}
