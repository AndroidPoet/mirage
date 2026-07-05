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
import io.androidpoet.mirage.core.SizingParams
import io.androidpoet.mirage.core.mirageEffect
import io.androidpoet.mirage.core.setColorUniform4f
import io.androidpoet.mirage.core.setColorsUniform

private const val MAX_COLORS = 6

private val SHADER = """
$SIZING_AGSL

uniform shader u_image;

uniform float u_time;

uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;
uniform vec4 u_colorBack;
uniform vec4 u_colorInner;

uniform float u_innerDistortion;
uniform float u_outerDistortion;
uniform float u_outerGlow;
uniform float u_innerGlow;
uniform float u_offset;
uniform float u_angle;
uniform float u_size;

uniform float u_shape;
uniform float u_isImage;

${Agsl.PI}
${Agsl.ROTATION2}

vec2 getResponsiveBoxGivenSize() {
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.)) * u_pixelRatio;
  return vec2(
    (u_worldWidth == 0.) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.) ? u_resolution.y : givenBoxSize.y);
}

float sst(float a, float b, float x) {
  return smoothstep(a, b, x);
}

float gaussK9(float i) {
  // Pascal's row 8: 1, 8, 28, 56, 70, 56, 28, 8, 1
  float ai = abs(i);
  if (ai < .5) return 70.0;
  if (ai < 1.5) return 56.0;
  if (ai < 2.5) return 28.0;
  if (ai < 3.5) return 8.0;
  return 1.0;
}

// 9x9 Gaussian blur on R and G channels
vec2 gaussBlur9x9RG(vec2 uv, float radius) {
  vec2 texel = 1.0 / u_resolution;
  vec2 r = max(radius, 0.0) * texel;
  // Pascal's row 8: sum = 256, 2D norm = 65536
  vec2 sum = vec2(0.0);

  for (int j = -4; j <= 4; ++j) {
    float wy = gaussK9(float(j));
    for (int i = -4; i <= 4; ++i) {
      float w = gaussK9(float(i)) * wy;
      vec2 off = vec2(float(i) * r.x, float(j) * r.y);
      sum += w * u_image.eval((uv + off) * u_resolution).rg;
    }
  }

  return sum / 65536.0;
}

float getShapeEdge(vec2 fragCoord, float time) {
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
    r *= (1. + .05 * sin(3. * a + 2. * time));
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
      vec2 traj = .4 * (dir1 * sin(time * speed + fi * 1.23) + dir2 * cos(time * (speed * 0.7) + fi * 2.17));
      float d = length(shapeUV + traj);
      edge += pow(1.0 - clamp(d, 0.0, 1.0), 4.0);
    }
    edge = 1. - smoothstep(.65, .9, edge);
    edge = pow(edge, 4.);
  }
  return edge;
}

vec2 getSmokeUV(vec2 fragCoord) {
  vec2 smokeUV = getObjectUV(fragCoord);
  smokeUV = rotate(smokeUV, u_angle * PI / 180.);
  smokeUV *= mix(4., 1., u_size);
  return smokeUV;
}

vec2 getInnerUV(vec2 smokeUV, float roundness) {
  vec2 innerUV = smokeUV;
  innerUV.y += u_innerDistortion * (1. - sst(0., 1., length(.4 * innerUV)));
  innerUV.y -= .4 * u_innerDistortion;
  innerUV.y += .7 * u_offset * roundness;
  return innerUV;
}

vec2 getOuterUV(vec2 smokeUV) {
  vec2 outerUV = smokeUV;
  outerUV.y += u_outerDistortion * (1. - sst(0., 1., length(.4 * outerUV)));
  outerUV.y -= .4 * u_outerDistortion;
  return outerUV;
}

half4 main(vec2 fragCoord) {
  float time = u_time;

  float roundness = 0.;
  float imgAlpha = 0.;
  float roundnessX = 0.;
  float roundnessY = 0.;

  if (u_isImage > .5) {
    // Image sampling (UV scaled inward to account for padding)
    vec2 imageUV = getImageUV(fragCoord);
    imageUV -= .5;
    imageUV *= .95;
    imageUV += .5;

    // Blurred image: x = roundness, y = alpha
    vec2 blurred = gaussBlur9x9RG(imageUV, 10.);
    roundness = 1. - blurred.x;
    vec2 texelA = 1.0 / u_resolution;
    for (int j = -1; j <= 1; ++j) {
      for (int i = -1; i <= 1; ++i) {
        float k3 = (2. - abs(float(i))) * (2. - abs(float(j)));
        imgAlpha += k3 * u_image.eval((imageUV + vec2(float(i) * texelA.x, float(j) * texelA.y)) * u_resolution).g;
      }
    }
    imgAlpha /= 16.0;

    // The blurred roundness field is smooth, so the neighbor swirl seeds
    // reuse the center value instead of re-running the blur per pixel.
    roundnessX = roundness;
    roundnessY = roundness;
  } else {
    // Screen-space derivatives are unavailable in AGSL: estimate them with
    // finite differences one pixel over, reusing the center evaluation.
    float edge = getShapeEdge(fragCoord, time);
    float edgeX = getShapeEdge(fragCoord + vec2(1., 0.), time);
    float edgeY = getShapeEdge(fragCoord + vec2(0., 1.), time);
    float fwEdge = abs(edgeX - edge) + abs(edgeY - edge);

    imgAlpha = 1. - smoothstep(.9 - 2. * fwEdge, .9, edge);
    roundness = 1. - edge;
    roundnessX = 1. - edgeX;
    roundnessY = 1. - edgeY;
  }

  // Smoke UV setup, tracked at the center plus one pixel over in x and y so
  // the per-iteration UV stretch can be measured without derivatives
  vec2 smokeUV = getSmokeUV(fragCoord);
  vec2 smokeUVx = getSmokeUV(fragCoord + vec2(1., 0.));
  vec2 smokeUVy = getSmokeUV(fragCoord + vec2(0., 1.));

  // Two swirl paths: inner (shape-masked) and outer (free), each with independent distortion
  // Vertical displacement — applied independently to inner and outer
  vec2 innerUV = getInnerUV(smokeUV, roundness);
  vec2 innerUVx = getInnerUV(smokeUVx, roundnessX);
  vec2 innerUVy = getInnerUV(smokeUVy, roundnessY);

  vec2 outerUV = getOuterUV(smokeUV);
  vec2 outerUVx = getOuterUV(smokeUVx);
  vec2 outerUVy = getOuterUV(smokeUVy);

  float innerSwirl = u_innerDistortion * roundness;
  float innerSwirlX = u_innerDistortion * roundnessX;
  float innerSwirlY = u_innerDistortion * roundnessY;
  float outerSwirl = u_outerDistortion;

  for (int i = 1; i < 5; i++) {
    float fi = float(i);

    float stretchIn = max(length(innerUVx - innerUV), length(innerUVy - innerUV));
    float dampenIn = 1. / (1. + stretchIn * 8.);
    float sIn = innerSwirl * dampenIn;
    float sInX = innerSwirlX * dampenIn;
    float sInY = innerSwirlY * dampenIn;
    innerUV.x += sIn / fi * cos(time + fi * 2.9 * innerUV.y);
    innerUV.y += sIn / fi * cos(time + fi * 1.5 * innerUV.x);
    innerUVx.x += sInX / fi * cos(time + fi * 2.9 * innerUVx.y);
    innerUVx.y += sInX / fi * cos(time + fi * 1.5 * innerUVx.x);
    innerUVy.x += sInY / fi * cos(time + fi * 2.9 * innerUVy.y);
    innerUVy.y += sInY / fi * cos(time + fi * 1.5 * innerUVy.x);

    float stretchOut = max(length(outerUVx - outerUV), length(outerUVy - outerUV));
    float dampenOut = 1. / (1. + stretchOut * 8.);
    float sOut = outerSwirl * dampenOut;
    outerUV.x += sOut / fi * cos(time + fi * 2.9 * outerUV.y);
    outerUV.y += sOut / fi * cos(time + fi * 1.5 * outerUV.x);
    outerUVx.x += sOut / fi * cos(time + fi * 2.9 * outerUVx.y);
    outerUVx.y += sOut / fi * cos(time + fi * 1.5 * outerUVx.x);
    outerUVy.x += sOut / fi * cos(time + fi * 2.9 * outerUVy.y);
    outerUVy.y += sOut / fi * cos(time + fi * 1.5 * outerUVy.x);
  }

  // Smoke shapes from swirl fields
  float innerShape = exp(-1.5 * dot(innerUV, innerUV));
  float outerShape = exp(-1.5 * dot(outerUV, outerUV));

  // Visibility masks
  float outerMask = pow(u_outerGlow, 2.) * (1. - imgAlpha);
  float innerMask = (.01 + .99 * u_innerGlow) * imgAlpha;

  innerShape *= innerMask;
  outerShape *= outerMask;

  // Color gradient
  float mixer = (innerShape + outerShape) * u_colorsCount;
  vec4 gradient = u_colors[0];
  gradient.rgb *= gradient.a;

  float smokeMask = 0.;
  for (int i = 1; i < ${MAX_COLORS + 1}; i++) {
    if (i <= int(u_colorsCount)) {
      float m = sst(0., 1., clamp(mixer - float(i - 1), 0., 1.));
      if (i == 1) smokeMask = m;

      vec4 c = u_colors[i - 1];
      c.rgb *= c.a;
      gradient = mix(gradient, c, m);
    }
  }

  // Compositing (premultiplied alpha, front-to-back)
  vec3 color = gradient.rgb * smokeMask;
  float opacity = gradient.a * smokeMask;

  float innerOpacity = u_colorInner.a * imgAlpha;
  vec3 innerColor = u_colorInner.rgb * innerOpacity;
  color += innerColor * (1.0 - opacity);
  opacity += innerOpacity * (1.0 - opacity);

  vec3 backColor = u_colorBack.rgb * u_colorBack.a;
  color += backColor * (1.0 - opacity);
  opacity += u_colorBack.a * (1.0 - opacity);

  return half4(color, opacity);
}
"""

/** Mask shape used when [Modifier.gemSmoke] is not driven by image content. */
public enum class GemSmokeShape(public val value: Float) {
  None(0f),
  Circle(1f),
  Daisy(2f),
  Diamond(3f),
  Metaballs(4f),
}

public object GemSmokeDefaults {
  public val Colors: List<Color> = listOf(
    Color(0xFF333333),
    Color(0xFFE7E6DF),
  )
  public val ColorBack: Color = Color(0xFFF0EFEA)
  public val ColorInner: Color = Color(0xFFFAFAF5)
}

/**
 * Animated color fields giving the illusion of smoky noise behind a glassy
 * shape. Renders over a procedural [shape] by default; with [isImage] the
 * content is treated as a pre-processed mask (R = edge gradient, G = alpha).
 */
@Composable
public fun Modifier.gemSmoke(
  colors: List<Color> = GemSmokeDefaults.Colors,
  colorBack: Color = GemSmokeDefaults.ColorBack,
  colorInner: Color = GemSmokeDefaults.ColorInner,
  innerDistortion: Float = 0.8f,
  outerDistortion: Float = 0.6f,
  outerGlow: Float = 0.55f,
  innerGlow: Float = 1f,
  offset: Float = 0f,
  angle: Float = 0f,
  size: Float = 0.8f,
  shape: GemSmokeShape = GemSmokeShape.Diamond,
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
  setColorsUniform("u_colors", "u_colorsCount", colors, MAX_COLORS)
  setColorUniform4f("u_colorBack", colorBack)
  setColorUniform4f("u_colorInner", colorInner)
  setFloatUniform("u_innerDistortion", innerDistortion)
  setFloatUniform("u_outerDistortion", outerDistortion)
  setFloatUniform("u_outerGlow", outerGlow)
  setFloatUniform("u_innerGlow", innerGlow)
  setFloatUniform("u_offset", offset)
  setFloatUniform("u_angle", angle)
  setFloatUniform("u_size", size)
  setFloatUniform("u_shape", shape.value)
  setFloatUniform("u_isImage", if (isImage) 1f else 0f)
}
