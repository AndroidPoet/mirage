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

uniform vec4 u_colorFront;
uniform vec4 u_colorBack;

uniform shader u_image;

uniform float u_contrast;
uniform float u_roughness;
uniform float u_fiber;
uniform float u_fiberSize;
uniform float u_crumples;
uniform float u_crumpleSize;
uniform float u_folds;
uniform float u_foldCount;
uniform float u_drops;
uniform float u_seed;
uniform float u_fade;

float getUvFrame(vec2 uv, vec2 aa) {
  float aax = 2. * aa.x;
  float aay = 2. * aa.y;

  float left = smoothstep(0., aax, uv.x);
  float right = 1. - smoothstep(1. - aax, 1., uv.x);
  float bottom = smoothstep(0., aay, uv.y);
  float top = 1. - smoothstep(1. - aay, 1., uv.y);

  return left * right * bottom * top;
}

${Agsl.PI}
${Agsl.ROTATION2}
${Agsl.RANDOM_R}
float valueNoise(vec2 st) {
  vec2 i = floor(st);
  vec2 f = fract(st);
  float a = randomR(i);
  float b = randomR(i + vec2(1.0, 0.0));
  float c = randomR(i + vec2(0.0, 1.0));
  float d = randomR(i + vec2(1.0, 1.0));
  vec2 u = f * f * (3.0 - 2.0 * f);
  float x1 = mix(a, b, u.x);
  float x2 = mix(c, d, u.x);
  return mix(x1, x2, u.y);
}
float fbm(vec2 n) {
  float total = 0.0, amplitude = .4;
  for (int i = 0; i < 3; i++) {
    total += valueNoise(n) * amplitude;
    n *= 1.99;
    amplitude *= 0.65;
  }
  return total;
}

${Agsl.RANDOM_GB}

float randomG(vec2 p) {
  return randomGB(p).x;
}
float roughness(vec2 p) {
  p *= .1;
  float o = 0.;
  for (int i = 0; i < 3; i++) {
    vec4 w = vec4(floor(p), ceil(p));
    vec2 f = fract(p);
    o += mix(
    mix(randomG(w.xy), randomG(w.xw), f.y),
    mix(randomG(w.zy), randomG(w.zw), f.y),
    f.x);
    o += .2 / exp(2. * abs(sin(.2 * p.x + .5 * p.y)));
    p *= 2.1;
  }
  return o / 3.;
}

float fiberRandom(vec2 p) {
  vec2 q = fract((floor(p) + 91.7) * vec2(0.3183099, 0.3678794)) + 0.1;
  q += dot(q, q + 19.19);
  return fract(q.x * q.y);
}

float fiberValueNoise(vec2 st) {
  vec2 i = floor(st);
  vec2 f = fract(st);
  float a = fiberRandom(i);
  float b = fiberRandom(i + vec2(1.0, 0.0));
  float c = fiberRandom(i + vec2(0.0, 1.0));
  float d = fiberRandom(i + vec2(1.0, 1.0));
  vec2 u = f * f * (3.0 - 2.0 * f);
  float x1 = mix(a, b, u.x);
  float x2 = mix(c, d, u.x);
  return mix(x1, x2, u.y);
}

float fiberNoiseFbm(vec2 n, vec2 seedOffset) {
  float total = 0.0, amplitude = 1.;
  for (int i = 0; i < 4; i++) {
    n = rotate(n, .7);
    total += fiberValueNoise(n + seedOffset) * amplitude;
    n *= 2.;
    amplitude *= 0.6;
  }
  return total;
}

float fiberNoise(vec2 uv, vec2 seedOffset) {
  float epsilon = 0.001;
  float n1 = fiberNoiseFbm(uv + vec2(epsilon, 0.0), seedOffset);
  float n2 = fiberNoiseFbm(uv - vec2(epsilon, 0.0), seedOffset);
  float n3 = fiberNoiseFbm(uv + vec2(0.0, epsilon), seedOffset);
  float n4 = fiberNoiseFbm(uv - vec2(0.0, epsilon), seedOffset);
  return length(vec2(n1 - n2, n3 - n4)) / (2.0 * epsilon);
}

float crumpledNoise(vec2 t, float pw) {
  vec2 p = floor(t);
  float wsum = 0.;
  float cl = 0.;
  for (int y = -1; y < 2; y += 1) {
    for (int x = -1; x < 2; x += 1) {
      vec2 b = vec2(float(x), float(y));
      vec2 q = b + p;
      vec2 q2 = q - floor(q / 8.) * 8.;
      vec2 c = q + randomGB(q2);
      vec2 r = c - t;
      float w = pow(smoothstep(0., 1., 1. - abs(r.x)), pw) * pow(smoothstep(0., 1., 1. - abs(r.y)), pw);
      cl += (.5 + .5 * sin((q2.x + q2.y * 5.) * 8.)) * w;
      wsum += w;
    }
  }
  return pow(wsum != 0.0 ? cl / wsum : 0.0, .5) * 2.;
}
float crumplesShape(vec2 uv) {
  return crumpledNoise(uv * .25, 16.) * crumpledNoise(uv * .5, 2.);
}


vec2 folds(vec2 uv) {
  vec3 pp = vec3(0.);
  float l = 9.;
  for (float i = 0.; i < 15.; i++) {
    if (i < u_foldCount) {
      vec2 rand = randomGB(vec2(i, i * u_seed));
      float an = rand.x * TWO_PI;
      vec2 p = vec2(cos(an), sin(an)) * rand.y;
      float dist = distance(uv, p);
      l = min(l, dist);

      if (l == dist) {
        pp.xy = (uv - p.xy);
        pp.z = dist;
      }
    }
  }
  return mix(pp.xy, vec2(0.), pow(pp.z, .25));
}

float drops(vec2 uv) {
  vec2 iDropsUV = floor(uv);
  vec2 fDropsUV = fract(uv);
  float dropsMinDist = 1.;
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      vec2 neighbor = vec2(float(i), float(j));
      vec2 offset = randomGB(iDropsUV + neighbor);
      offset = .5 + .5 * sin(10. * u_seed + TWO_PI * offset);
      vec2 pos = neighbor + offset - fDropsUV;
      float dist = length(pos);
      dropsMinDist = min(dropsMinDist, dropsMinDist * dist);
    }
  }
  return 1. - smoothstep(.05, .09, pow(dropsMinDist, .5));
}

float lst(float edge0, float edge1, float x) {
  return clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
}

half4 main(vec2 fragCoord) {
  vec2 imageUV = getImageUV(fragCoord);

  // fwidth() is unavailable in AGSL: estimate the screen-space derivative of
  // the image UV with finite differences one pixel over.
  vec2 duvx = getImageUV(fragCoord + vec2(1., 0.)) - imageUV;
  vec2 duvy = getImageUV(fragCoord + vec2(0., 1.)) - imageUV;
  vec2 uvFrameAA = vec2(abs(duvx.x) + abs(duvy.x), abs(duvx.y) + abs(duvy.y));

  vec2 patternUV = imageUV - .5;
  patternUV = 5. * (patternUV * vec2(u_imageAspectRatio, 1.));

  vec2 roughnessUv = 1.5 * (fragCoord - .5 * u_resolution) / u_pixelRatio;
  float roughnessVal = roughness(roughnessUv + vec2(1., 0.)) - roughness(roughnessUv - vec2(1., 0.));

  vec2 crumplesUV = fract(patternUV * .02 / u_crumpleSize - u_seed) * 32.;
  float crumples = u_crumples * (crumplesShape(crumplesUV + vec2(.05, 0.)) - crumplesShape(crumplesUV));

  vec2 fiberUV = 2. / u_fiberSize * patternUV;
  float fiber = fiberNoise(fiberUV, vec2(0.));
  fiber = .5 * u_fiber * (fiber - 1.);

  vec2 normal = vec2(0.);
  vec2 normalImage = vec2(0.);

  vec2 foldsUV = patternUV * .12;
  foldsUV = rotate(foldsUV, 4. * u_seed);
  vec2 w = folds(foldsUV);
  foldsUV = rotate(foldsUV + .007 * cos(u_seed), .01 * sin(u_seed));
  vec2 w2 = folds(foldsUV);

  float dropsVal = u_drops * drops(patternUV * 2.);

  float fade = u_fade * fbm(.17 * patternUV + 10. * u_seed);
  fade = clamp(8. * fade * fade * fade, 0., 1.);

  w = mix(w, vec2(0.), fade);
  w2 = mix(w2, vec2(0.), fade);
  crumples = mix(crumples, 0., fade);
  dropsVal = mix(dropsVal, 0., fade);
  fiber *= mix(1., .5, fade);
  roughnessVal *= mix(1., .5, fade);

  normal.xy += u_folds * min(5. * u_contrast, 1.) * 4. * max(vec2(0.), w + w2);
  normalImage.xy += u_folds * 2. * w;

  normal.xy += crumples;
  normalImage.xy += 1.5 * crumples;

  normal.xy += 3. * dropsVal;
  normalImage.xy += .2 * dropsVal;

  normal.xy += u_roughness * 1.5 * roughnessVal;
  normal.xy += fiber;

  normalImage += u_roughness * .75 * roughnessVal;
  normalImage += .2 * fiber;

  vec3 lightPos = vec3(1., 2., 1.);
  float res = dot(normalize(vec3(normal, 9.5 - 9. * pow(u_contrast, .1))), normalize(lightPos));

  vec3 fgColor = u_colorFront.rgb * u_colorFront.a;
  float fgOpacity = u_colorFront.a;
  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  float bgOpacity = u_colorBack.a;

  imageUV += .02 * normalImage;
  float frame = getUvFrame(imageUV, uvFrameAA);
  vec4 image = u_image.eval(imageUV * u_resolution);
  image.rgb += .6 * pow(u_contrast, .4) * (res - .7);

  frame *= image.a;

  vec3 color = fgColor * res;
  float opacity = fgOpacity * res;

  color += bgColor * (1. - opacity);
  opacity += bgOpacity * (1. - opacity);
  opacity = mix(opacity, 1., frame);

  color -= .007 * dropsVal;

  color.rgb = mix(color, image.rgb, frame);

  return half4(color, opacity);
}
"""

public object PaperTextureDefaults {
  public val ColorFront: Color = Color(0xFF9FADBC)
  public val ColorBack: Color = Color(0xFFFFFFFF)
}

/**
 * A static texture built from multiple noise layers, usable for realistic
 * paper and cardboard surfaces, applied over the composable content.
 */
@Composable
public fun Modifier.paperTexture(
  colorFront: Color = PaperTextureDefaults.ColorFront,
  colorBack: Color = PaperTextureDefaults.ColorBack,
  contrast: Float = 0.3f,
  roughness: Float = 0.4f,
  fiber: Float = 0.3f,
  fiberSize: Float = 0.2f,
  crumples: Float = 0.3f,
  crumpleSize: Float = 0.35f,
  folds: Float = 0.65f,
  foldCount: Int = 5,
  fade: Float = 0f,
  drops: Float = 0.2f,
  seed: Float = 5.8f,
  sizing: SizingParams = SizingParams(fit = ShaderFit.Cover, scale = 0.6f),
): Modifier = mirageEffect(
  shaderSource = SHADER,
  sizing = sizing,
) { _ ->
  setColorUniform4f("u_colorFront", colorFront)
  setColorUniform4f("u_colorBack", colorBack)
  setFloatUniform("u_contrast", contrast)
  setFloatUniform("u_roughness", roughness)
  setFloatUniform("u_fiber", fiber)
  setFloatUniform("u_fiberSize", fiberSize)
  setFloatUniform("u_crumples", crumples)
  setFloatUniform("u_crumpleSize", crumpleSize)
  setFloatUniform("u_folds", folds)
  setFloatUniform("u_foldCount", foldCount.toFloat())
  setFloatUniform("u_fade", fade)
  setFloatUniform("u_drops", drops)
  setFloatUniform("u_seed", seed)
}
