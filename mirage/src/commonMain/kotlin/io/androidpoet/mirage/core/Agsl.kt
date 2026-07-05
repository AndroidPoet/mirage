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
package io.androidpoet.mirage.core

/**
 * Shared AGSL snippets, ported from the upstream shader-utils GLSL blocks.
 * AGSL has no preprocessor, so #define constants became const floats, and
 * the pre-baked noise-texture randomizers became procedural hashes.
 */
public object Agsl {

  /** Replaces the upstream declarePI #defines. */
  public const val PI: String = """
const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530718;
"""

  public const val ROTATION2: String = """
vec2 rotate(vec2 uv, float th) {
  return mat2(cos(th), sin(th), -sin(th), cos(th)) * uv;
}
"""

  public const val HASH11: String = """
float hash11(float p) {
  p = fract(p * 0.3183099) + 0.1;
  p *= p + 19.19;
  return fract(p * p);
}
"""

  public const val HASH21: String = """
float hash21(vec2 p) {
  p = fract(p * vec2(0.3183099, 0.3678794)) + 0.1;
  p += dot(p, p + 19.19);
  return fract(p.x * p.y);
}
"""

  public const val HASH22: String = """
vec2 hash22(vec2 p) {
  p = fract(p * vec2(0.3183099, 0.3678794)) + 0.1;
  p += dot(p, p.yx + 19.19);
  return fract(vec2(p.x * p.y, p.x + p.y));
}
"""

  /**
   * Replaces upstream textureRandomizerR (noise-texture lookup).
   * Self-contained hash so it can be injected without HASH21.
   */
  public const val RANDOM_R: String = """
float randomR(vec2 p) {
  vec2 q = fract(floor(p) * vec2(0.3183099, 0.3678794)) + 0.1;
  q += dot(q, q + 19.19);
  return fract(q.x * q.y);
}
"""

  /** Replaces upstream textureRandomizerGB (noise-texture lookup). */
  public const val RANDOM_GB: String = """
vec2 randomGB(vec2 p) {
  vec2 q = fract((floor(p) + 47.13) * vec2(0.3183099, 0.3678794)) + 0.1;
  q += dot(q, q.yx + 19.19);
  return fract(vec2(q.x * q.y, q.x + q.y));
}
"""

  /**
   * Replaces the upstream colorBandingFix statement (which read gl_FragCoord).
   * Usage: color += bandingNoise(fragCoord);
   */
  public const val BANDING_FIX: String = """
float bandingNoise(vec2 fragCoord) {
  return 1. / 256. * (fract(sin(dot(.014 * fragCoord, vec2(12.9898, 78.233))) * 43758.5453123) - .5);
}
"""

  /** Ashima 2D simplex noise, identical to the upstream block. */
  public const val SIMPLEX_NOISE: String = """
vec3 permute(vec3 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }
float snoise(vec2 v) {
  const vec4 C = vec4(0.211324865405187, 0.366025403784439,
    -0.577350269189626, 0.024390243902439);
  vec2 i = floor(v + dot(v, C.yy));
  vec2 x0 = v - i + dot(i, C.xx);
  vec2 i1;
  i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
  vec4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod(i, 289.0);
  vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0))
    + i.x + vec3(0.0, i1.x, 1.0));
  vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy),
      dot(x12.zw, x12.zw)), 0.0);
  m = m * m;
  m = m * m;
  vec3 x = 2.0 * fract(p * C.www) - 1.0;
  vec3 h = abs(x) - 0.5;
  vec3 ox = floor(x + 0.5);
  vec3 a0 = x - ox;
  m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
  vec3 g;
  g.x = a0.x * x0.x + h.x * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}
"""
}
