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

private const val MAX_COLORS = 10

private val SHADER = """
$SIZING_AGSL

uniform shader u_image;
uniform float u_time;

uniform vec4 u_colorBack;
uniform vec4 u_colors[$MAX_COLORS];
uniform float u_colorsCount;

uniform float u_angle;
uniform float u_noise;
uniform float u_innerGlow;
uniform float u_outerGlow;
uniform float u_contour;

${Agsl.PI}

float getImgFrame(vec2 uv, float th) {
  float frame = 1.;
  frame *= smoothstep(0., th, uv.y);
  frame *= 1. - smoothstep(1. - th, 1., uv.y);
  frame *= smoothstep(0., th, uv.x);
  frame *= 1. - smoothstep(1. - th, 1., uv.x);
  return frame;
}

float circle(vec2 uv, vec2 c, vec2 r) {
  return 1. - smoothstep(r.x, r.y, length(uv - c));
}

float lst(float edge0, float edge1, float x) {
  return clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
}

float sst(float edge0, float edge1, float x) {
  return smoothstep(edge0, edge1, x);
}

float shadowShape(vec2 uv, float t, float contour) {
  vec2 scaledUV = uv;

  // base shape tranjectory
  float posY = mix(-1., 2., t);

  // scaleX when it's moving down
  scaledUV.y -= .5;
  float mainCircleScale = sst(0., .8, posY) * lst(1.4, .9, posY);
  scaledUV *= vec2(1., 1. + 1.5 * mainCircleScale);
  scaledUV.y += .5;

  // base shape
  float innerR = .4;
  float outerR = 1. - .3 * (sst(.1, .2, t) * (1. - sst(.2, .5, t)));
  float s = circle(scaledUV, vec2(.5, posY - .2), vec2(innerR, outerR));
  float shapeSizing = sst(.2, .3, t) * sst(.6, .3, t);
  s = pow(s, 1.4);
  s *= 1.2;

  // flat gradient to take over the shadow shape
  float topFlattener = 0.;
  {
    float pos = posY - uv.y;
    float edge = 1.2;
    topFlattener = lst(-.4, 0., pos) * (1. - sst(.0, edge, pos));
    topFlattener = pow(topFlattener, 3.);
    float topFlattenerMixer = (1. - sst(.0, .3, pos));
    s = mix(topFlattener, s, topFlattenerMixer);
  }

  // apple right circle
  {
    float visibility = sst(.6, .7, t) * (1. - sst(.8, .9, t));
    float angle = -2. - t * TWO_PI;
    float rightCircle = circle(uv, vec2(.95 - .2 * cos(angle), .4 - .1 * sin(angle)), vec2(.15, .3));
    rightCircle *= visibility;
    s = mix(s, 0., rightCircle);
  }

  // apple top circle
  {
    float topCircle = circle(uv, vec2(.5, .19), vec2(.05, .25));
    topCircle += 2. * contour * circle(uv, vec2(.5, .19), vec2(.2, .5));
    float visibility = .55 * sst(.2, .3, t) * (1. - sst(.3, .45, t));
    topCircle *= visibility;
    s = mix(s, 0., topCircle);
  }

  float leafMask = circle(uv, vec2(.53, .13), vec2(.08, .19));
  leafMask = mix(leafMask, 0., 1. - sst(.4, .54, uv.x));
  leafMask = mix(0., leafMask, sst(.0, .2, uv.y));
  leafMask *= (sst(.5, 1.1, posY) * sst(1.5, 1.3, posY));
  s += leafMask;

  // apple bottom circle
  {
    float visibility = sst(.0, .4, t) * (1. - sst(.6, .8, t));
    s = mix(s, 0., visibility * circle(uv, vec2(.52, .92), vec2(.09, .25)));
  }

  // random balls that are invisible if apple logo is selected
  {
    float pos = sst(.0, .6, t) * (1. - sst(.6, 1., t));
    s = mix(s, .5, circle(uv, vec2(.0, 1.2 - .5 * pos), vec2(.1, .3)));
    s = mix(s, .0, circle(uv, vec2(1., .5 + .5 * pos), vec2(.1, .3)));

    s = mix(s, 1., circle(uv, vec2(.95, .2 + .2 * sst(.3, .4, t) * sst(.7, .5, t)), vec2(.07, .22)));
    s = mix(s, 1., circle(uv, vec2(.95, .2 + .2 * sst(.3, .4, t) * (1. - sst(.5, .7, t))), vec2(.07, .22)));
    s /= max(1e-4, sst(1., .85, uv.y));
  }

  s = clamp(0., 1., s);
  return s;
}

// The upstream blur passed explicit UV gradients purely for mip selection;
// u_image.eval has no mip chain, so they are dropped.
float blurEdge3x3(vec2 uv, float radius, float centerSample) {
  vec2 texel = 1.0 / u_resolution;
  vec2 r = radius * texel;

  float w1 = 1.0;
  float w2 = 2.0;
  float w4 = 4.0;
  float norm = 16.0;
  float sum = w4 * centerSample;

  sum += w2 * u_image.eval((uv + vec2(0.0, -r.y)) * u_resolution).g;
  sum += w2 * u_image.eval((uv + vec2(0.0, r.y)) * u_resolution).g;
  sum += w2 * u_image.eval((uv + vec2(-r.x, 0.0)) * u_resolution).g;
  sum += w2 * u_image.eval((uv + vec2(r.x, 0.0)) * u_resolution).g;

  sum += w1 * u_image.eval((uv + vec2(-r.x, -r.y)) * u_resolution).g;
  sum += w1 * u_image.eval((uv + vec2(r.x, -r.y)) * u_resolution).g;
  sum += w1 * u_image.eval((uv + vec2(-r.x, r.y)) * u_resolution).g;
  sum += w1 * u_image.eval((uv + vec2(r.x, r.y)) * u_resolution).g;

  return sum / norm;
}

half4 main(vec2 fragCoord) {
  vec2 uv = getObjectUV(fragCoord) + .5;
  uv.y = 1. - uv.y;

  vec2 imgUV = getImageUV(fragCoord);
  imgUV -= .5;
  imgUV *= 0.5714285714285714;
  imgUV += .5;
  float imgSoftFrame = getImgFrame(imgUV, .03);

  vec4 img = vec4(u_image.eval(imgUV * u_resolution));

  if (img.a == 0.) {
    return half4(u_colorBack);
  }

  float t = .1 * u_time;
  t -= .3;

  float tCopy = t + 1. / 3.;
  float tCopy2 = t + 2. / 3.;

  t = mod(t, 1.);
  tCopy = mod(tCopy, 1.);
  tCopy2 = mod(tCopy2, 1.);

  vec2 animationUV = imgUV - vec2(.5);
  float angle = -u_angle * PI / 180.;
  float cosA = cos(angle);
  float sinA = sin(angle);
  animationUV = vec2(
    animationUV.x * cosA - animationUV.y * sinA,
    animationUV.x * sinA + animationUV.y * cosA
  ) + vec2(.5);

  float shape = img.r;

  img.g = blurEdge3x3(imgUV, 8., img.g);

  float outerBlur = 1. - mix(1., img.g, shape);
  float innerBlur = mix(img.g, 0., shape);
  float contour = mix(img.b, 0., shape);

  outerBlur *= imgSoftFrame;

  float shadow = shadowShape(animationUV, t, innerBlur);
  float shadowCopy = shadowShape(animationUV, tCopy, innerBlur);
  float shadowCopy2 = shadowShape(animationUV, tCopy2, innerBlur);

  float inner = .8 + .8 * innerBlur;
  inner = mix(inner, 0., shadow);
  inner = mix(inner, 0., shadowCopy);
  inner = mix(inner, 0., shadowCopy2);

  inner *= mix(0., 2., u_innerGlow);

  inner += (u_contour * 2.) * contour;
  inner = min(1., inner);
  inner *= (1. - shape);

  float outer = 0.;
  {
    t *= 3.;
    t = mod(t - .1, 1.);

    outer = .9 * pow(outerBlur, .8);
    float y = mod(animationUV.y - t, 1.);
    float animatedMask = sst(.3, .65, y) * (1. - sst(.65, 1., y));
    animatedMask = .5 + animatedMask;
    outer *= animatedMask;
    outer *= mix(0., 5., pow(u_outerGlow, 2.));
    outer *= imgSoftFrame;
  }

  inner = pow(inner, 1.2);
  float heat = clamp(inner + outer, 0., 1.);

  heat += (.005 + .35 * u_noise) * (fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453123) - .5);

  float mixer = heat * u_colorsCount;
  vec4 gradient = u_colors[0];
  gradient.rgb *= gradient.a;
  float outerShape = 0.;
  for (int i = 1; i < ${MAX_COLORS + 1}; i++) {
    if (i <= int(u_colorsCount)) {
      float m = clamp(mixer - float(i - 1), 0., 1.);
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

  color += .02 * (fract(sin(dot(uv + 1., vec2(12.9898, 78.233))) * 43758.5453123) - .5);

  return half4(color, opacity);
}
"""

public object HeatmapDefaults {
  public val Colors: List<Color> = listOf(
    Color(0xFF11206A),
    Color(0xFF1F3BA2),
    Color(0xFF2F63E7),
    Color(0xFF6BD7FF),
    Color(0xFFFFE679),
    Color(0xFFFF991E),
    Color(0xFFFF4C00),
  )
  public val ColorBack: Color = Color(0xFF000000)
}

/**
 * A glowing gradient of colors flowing through an input shape; the effect
 * creates a smoothly animated wave of intensity across the image.
 *
 * Upstream feeds a pre-blurred channel-packed image (R = contour, G = outer
 * blur, B = inner blur); here the layer content is sampled directly.
 */
@Composable
public fun Modifier.heatmap(
  colors: List<Color> = HeatmapDefaults.Colors,
  colorBack: Color = HeatmapDefaults.ColorBack,
  contour: Float = 0.5f,
  angle: Float = 0f,
  noise: Float = 0f,
  innerGlow: Float = 0.5f,
  outerGlow: Float = 0.5f,
  speed: Float = 1f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Object.copy(scale = 0.75f),
): Modifier = mirageEffect(
  shaderSource = SHADER,
  speed = speed,
  startFrame = startFrame,
  sizing = sizing,
) { time ->
  setFloatUniform("u_time", time)
  setColorUniform4f("u_colorBack", colorBack)
  setColorsUniform("u_colors", "u_colorsCount", colors, MAX_COLORS)
  setFloatUniform("u_contour", contour)
  setFloatUniform("u_angle", angle)
  setFloatUniform("u_noise", noise)
  setFloatUniform("u_innerGlow", innerGlow)
  setFloatUniform("u_outerGlow", outerGlow)
}
