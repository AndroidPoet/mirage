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

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * How the shader graphic is fit into the drawing bounds.
 * Mirrors the upstream `u_fit` uniform (0 = none, 1 = contain, 2 = cover).
 */
public enum class ShaderFit(public val value: Float) {
  None(0f),
  Contain(1f),
  Cover(2f),
}

/**
 * Graphic placement parameters shared by all shaders: fit, zoom, rotation,
 * origin and offset, plus an optional fixed world size in dp.
 */
public data class SizingParams(
  val fit: ShaderFit = ShaderFit.Contain,
  val scale: Float = 1f,
  val rotation: Float = 0f,
  val originX: Float = 0.5f,
  val originY: Float = 0.5f,
  val offsetX: Float = 0f,
  val offsetY: Float = 0f,
  val worldWidth: Float = 0f,
  val worldHeight: Float = 0f,
) {
  public companion object {
    /** Default sizing for object-like graphics (matches upstream defaultObjectSizing). */
    public val Object: SizingParams = SizingParams(fit = ShaderFit.Contain)

    /** Default sizing for repeating patterns (matches upstream defaultPatternSizing). */
    public val Pattern: SizingParams = SizingParams(fit = ShaderFit.None)
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public fun RuntimeShader.setSizingUniforms(
  sizing: SizingParams,
  width: Float,
  height: Float,
  pixelRatio: Float,
  imageAspectRatio: Float = 1f,
) {
  setFloatUniform("u_resolution", width, height)
  setFloatUniform("u_pixelRatio", pixelRatio)
  setFloatUniform("u_imageAspectRatio", imageAspectRatio)
  setFloatUniform("u_fit", sizing.fit.value)
  setFloatUniform("u_scale", sizing.scale)
  setFloatUniform("u_rotation", sizing.rotation)
  setFloatUniform("u_originX", sizing.originX)
  setFloatUniform("u_originY", sizing.originY)
  setFloatUniform("u_offsetX", sizing.offsetX)
  setFloatUniform("u_offsetY", sizing.offsetY)
  setFloatUniform("u_worldWidth", sizing.worldWidth)
  setFloatUniform("u_worldHeight", sizing.worldHeight)
}

/**
 * AGSL port of the upstream vertex-shader sizing logic. AGSL has no vertex
 * stage, so the varyings (v_objectUV, v_patternUV, v_imageUV, v_responsiveUV)
 * become functions of fragCoord. Prepend [SIZING_AGSL] to a fragment source
 * and call the matching function instead of reading the varying.
 *
 * Coordinate note: WebGL clip space is y-up while fragCoord is y-down;
 * sizingBaseUV() flips y so all downstream math matches the original.
 */
public const val SIZING_AGSL: String = """
uniform vec2 u_resolution;
uniform float u_pixelRatio;
uniform float u_imageAspectRatio;
uniform float u_originX;
uniform float u_originY;
uniform float u_worldWidth;
uniform float u_worldHeight;
uniform float u_fit;
uniform float u_scale;
uniform float u_rotation;
uniform float u_offsetX;
uniform float u_offsetY;

const float SIZING_PI = 3.14159265358979323846;

vec3 getBoxSize(float boxRatio, vec2 givenBoxSize) {
  vec2 box = vec2(0.);
  box.x = boxRatio * min(givenBoxSize.x / boxRatio, givenBoxSize.y);
  float noFitBoxWidth = box.x;
  if (u_fit == 1.) {
    box.x = boxRatio * min(u_resolution.x / boxRatio, u_resolution.y);
  } else if (u_fit == 2.) {
    box.x = boxRatio * max(u_resolution.x / boxRatio, u_resolution.y);
  }
  box.y = box.x / boxRatio;
  return vec3(box, noFitBoxWidth);
}

vec2 sizingBaseUV(vec2 fragCoord) {
  vec2 uv = fragCoord / u_resolution - .5;
  uv.y = -uv.y;
  return uv;
}

vec2 getObjectBoxSize() {
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.)) * u_pixelRatio;
  vec2 fixedRatioBoxGivenSize = vec2(
    (u_worldWidth == 0.) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.) ? u_resolution.y : givenBoxSize.y);
  return getBoxSize(1., fixedRatioBoxGivenSize).xy;
}

vec2 getObjectUV(vec2 fragCoord) {
  vec2 uv = sizingBaseUV(fragCoord);
  vec2 boxOrigin = vec2(.5 - u_originX, u_originY - .5);
  float r = u_rotation * SIZING_PI / 180.;
  mat2 graphicRotation = mat2(cos(r), sin(r), -sin(r), cos(r));
  vec2 graphicOffset = vec2(-u_offsetX, u_offsetY);
  vec2 objectBoxSize = getObjectBoxSize();
  vec2 objectWorldScale = u_resolution.xy / objectBoxSize;
  uv *= objectWorldScale;
  uv += boxOrigin * (objectWorldScale - 1.);
  uv += graphicOffset;
  uv /= u_scale;
  uv = graphicRotation * uv;
  return uv;
}

vec2 getResponsiveUV(vec2 fragCoord) {
  vec2 uv = sizingBaseUV(fragCoord);
  vec2 boxOrigin = vec2(.5 - u_originX, u_originY - .5);
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.)) * u_pixelRatio;
  float r = u_rotation * SIZING_PI / 180.;
  mat2 graphicRotation = mat2(cos(r), sin(r), -sin(r), cos(r));
  vec2 graphicOffset = vec2(-u_offsetX, u_offsetY);
  vec2 responsiveBoxGivenSize = vec2(
    (u_worldWidth == 0.) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.) ? u_resolution.y : givenBoxSize.y);
  float responsiveRatio = responsiveBoxGivenSize.x / responsiveBoxGivenSize.y;
  vec2 responsiveBoxSize = getBoxSize(responsiveRatio, responsiveBoxGivenSize).xy;
  vec2 responsiveBoxScale = u_resolution.xy / responsiveBoxSize;
  uv *= responsiveBoxScale;
  uv += boxOrigin * (responsiveBoxScale - 1.);
  uv += graphicOffset;
  uv /= u_scale;
  uv.x *= responsiveRatio;
  uv = graphicRotation * uv;
  uv.x /= responsiveRatio;
  return uv;
}

vec2 getPatternBoxSize() {
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.)) * u_pixelRatio;
  vec2 patternBoxGivenSize = vec2(
    (u_worldWidth == 0.) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.) ? u_resolution.y : givenBoxSize.y);
  float patternBoxRatio = patternBoxGivenSize.x / patternBoxGivenSize.y;
  return getBoxSize(patternBoxRatio, patternBoxGivenSize).xy;
}

vec2 getPatternUV(vec2 fragCoord) {
  vec2 uv = sizingBaseUV(fragCoord);
  vec2 boxOrigin = vec2(.5 - u_originX, u_originY - .5);
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.)) * u_pixelRatio;
  float r = u_rotation * SIZING_PI / 180.;
  mat2 graphicRotation = mat2(cos(r), sin(r), -sin(r), cos(r));
  vec2 graphicOffset = vec2(-u_offsetX, u_offsetY);
  vec2 patternBoxGivenSize = vec2(
    (u_worldWidth == 0.) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.) ? u_resolution.y : givenBoxSize.y);
  float patternBoxRatio = patternBoxGivenSize.x / patternBoxGivenSize.y;
  vec3 boxSizeData = getBoxSize(patternBoxRatio, patternBoxGivenSize);
  vec2 patternBoxSize = boxSizeData.xy;
  float patternBoxNoFitBoxWidth = boxSizeData.z;
  vec2 patternBoxScale = u_resolution.xy / patternBoxSize;

  uv += graphicOffset / patternBoxScale;
  uv += boxOrigin;
  uv -= boxOrigin / patternBoxScale;
  uv *= u_resolution.xy;
  uv /= u_pixelRatio;
  if (u_fit > 0.) {
    uv *= (patternBoxNoFitBoxWidth / patternBoxSize.x);
  }
  uv /= u_scale;
  uv = graphicRotation * uv;
  uv += boxOrigin / patternBoxScale;
  uv -= boxOrigin;
  uv *= .01;
  return uv;
}

vec2 getImageUV(vec2 fragCoord) {
  vec2 uv = sizingBaseUV(fragCoord);
  vec2 boxOrigin = vec2(.5 - u_originX, u_originY - .5);
  float r = u_rotation * SIZING_PI / 180.;
  mat2 graphicRotation = mat2(cos(r), sin(r), -sin(r), cos(r));
  vec2 graphicOffset = vec2(-u_offsetX, u_offsetY);
  vec2 imageBoxSize;
  if (u_fit == 1.) {
    imageBoxSize.x = min(u_resolution.x / u_imageAspectRatio, u_resolution.y) * u_imageAspectRatio;
  } else if (u_fit == 2.) {
    imageBoxSize.x = max(u_resolution.x / u_imageAspectRatio, u_resolution.y) * u_imageAspectRatio;
  } else {
    imageBoxSize.x = min(10.0, 10.0 / u_imageAspectRatio * u_imageAspectRatio);
  }
  imageBoxSize.y = imageBoxSize.x / u_imageAspectRatio;
  vec2 imageBoxScale = u_resolution.xy / imageBoxSize;
  uv *= imageBoxScale;
  uv += boxOrigin * (imageBoxScale - 1.);
  uv += graphicOffset;
  uv /= u_scale;
  uv.x *= u_imageAspectRatio;
  uv = graphicRotation * uv;
  uv.x /= u_imageAspectRatio;
  uv += .5;
  uv.y = 1. - uv.y;
  return uv;
}
"""
