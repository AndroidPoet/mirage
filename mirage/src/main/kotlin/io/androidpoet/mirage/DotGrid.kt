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

private val SHADER = """
$SIZING_AGSL

uniform vec4 u_colorBack;
uniform vec4 u_colorFill;
uniform vec4 u_colorStroke;
uniform float u_dotSize;
uniform float u_gapX;
uniform float u_gapY;
uniform float u_strokeWidth;
uniform float u_sizeRange;
uniform float u_opacityRange;
uniform float u_shape;

${Agsl.PI}
${Agsl.SIMPLEX_NOISE}

float polygon(vec2 p, float N, float rot) {
  float a = atan(p.x, p.y) + rot;
  float r = TWO_PI / N;

  return cos(floor(.5 + a / r) * r - a) * length(p);
}

float shapeDist(vec2 p, float baseSize) {
  float dist;
  if (u_shape < 0.5) {
    // Circle
    dist = length(p);
  } else if (u_shape < 1.5) {
    // Diamond
    dist = polygon(1.5 * p, 4., .25 * PI);
  } else if (u_shape < 2.5) {
    // Square
    dist = polygon(1.03 * p, 4., 1e-3);
  } else {
    // Triangle
    p = p * 2. - 1.;
    p *= .9;
    p.y = 1. - p.y;
    p.y -= .75 * baseSize;
    dist = polygon(p, 3., 1e-3);
  }
  return dist;
}

half4 main(vec2 fragCoord) {
  vec2 patternUV = getPatternUV(fragCoord);

  // x100 is a default multiplier between vertex and fragmant shaders
  // we use it to avoid UV presision issues
  vec2 shape_uv = 100. * patternUV;

  vec2 gap = max(abs(vec2(u_gapX, u_gapY)), vec2(1e-6));
  vec2 grid = fract(shape_uv / gap) + 1e-4;
  vec2 grid_idx = floor(shape_uv / gap);
  float sizeRandomizer = .5 + .8 * snoise(2. * vec2(grid_idx.x * 100., grid_idx.y));
  float opacity_randomizer = .5 + .7 * snoise(2. * vec2(grid_idx.y, grid_idx.x));

  vec2 center = vec2(0.5) - 1e-3;
  vec2 p = (grid - center) * vec2(u_gapX, u_gapY);

  float baseSize = u_dotSize * (1. - sizeRandomizer * u_sizeRange);
  float strokeWidth = u_strokeWidth * (1. - sizeRandomizer * u_sizeRange);
  if ((u_shape >= 0.5 && u_shape < 1.5) || u_shape >= 2.5) {
    strokeWidth *= 1.5;
  }

  float dist = shapeDist(p, baseSize);

  // AGSL has no derivative intrinsics: estimate the screen-space derivative
  // of the distance field with finite differences one pixel over.
  vec2 dpx = (getPatternUV(fragCoord + vec2(1., 0.)) - patternUV) * 100.;
  vec2 dpy = (getPatternUV(fragCoord + vec2(0., 1.)) - patternUV) * 100.;
  float distX = shapeDist(p + dpx, baseSize);
  float distY = shapeDist(p + dpy, baseSize);
  float edgeWidth = abs(distX - dist) + abs(distY - dist);

  float shapeOuter = 1. - smoothstep(baseSize - edgeWidth, baseSize + edgeWidth, dist - strokeWidth);
  float shapeInner = 1. - smoothstep(baseSize - edgeWidth, baseSize + edgeWidth, dist);
  float stroke = shapeOuter - shapeInner;

  float dotOpacity = max(0., 1. - opacity_randomizer * u_opacityRange);
  stroke *= dotOpacity;
  shapeInner *= dotOpacity;

  stroke *= u_colorStroke.a;
  shapeInner *= u_colorFill.a;

  vec3 color = vec3(0.);
  color += stroke * u_colorStroke.rgb;
  color += shapeInner * u_colorFill.rgb;
  color += (1. - shapeInner - stroke) * u_colorBack.rgb * u_colorBack.a;

  float opacity = 0.;
  opacity += stroke;
  opacity += shapeInner;
  opacity += (1. - opacity) * u_colorBack.a;

  return half4(color, opacity);
}
"""

public enum class DotGridShape(public val value: Float) {
  Circle(0f),
  Diamond(1f),
  Square(2f),
  Triangle(3f),
}

public object DotGridDefaults {
  public val ColorBack: Color = Color(0xFF000000)
  public val ColorFill: Color = Color(0xFFFFFFFF)
  public val ColorStroke: Color = Color(0xFFFFAA00)
}

/**
 * A static grid pattern made of circles, diamonds, squares or triangles.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
public fun DotGrid(
  modifier: Modifier = Modifier,
  colorBack: Color = DotGridDefaults.ColorBack,
  colorFill: Color = DotGridDefaults.ColorFill,
  colorStroke: Color = DotGridDefaults.ColorStroke,
  size: Float = 2f,
  gapX: Float = 32f,
  gapY: Float = 32f,
  strokeWidth: Float = 0f,
  sizeRange: Float = 0f,
  opacityRange: Float = 0f,
  shape: DotGridShape = DotGridShape.Circle,
  sizing: SizingParams = SizingParams.Pattern,
) {
  MirageSurface(
    shaderSource = SHADER,
    modifier = modifier,
    sizing = sizing,
  ) { _ ->
    setColorUniform4f("u_colorBack", colorBack)
    setColorUniform4f("u_colorFill", colorFill)
    setColorUniform4f("u_colorStroke", colorStroke)
    setFloatUniform("u_dotSize", size)
    setFloatUniform("u_gapX", gapX)
    setFloatUniform("u_gapY", gapY)
    setFloatUniform("u_strokeWidth", strokeWidth)
    setFloatUniform("u_sizeRange", sizeRange)
    setFloatUniform("u_opacityRange", opacityRange)
    setFloatUniform("u_shape", shape.value)
  }
}
