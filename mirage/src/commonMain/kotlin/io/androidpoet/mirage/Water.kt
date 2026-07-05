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

private val SHADER = """
$SIZING_AGSL

uniform float u_time;

uniform vec4 u_colorBack;
uniform vec4 u_colorHighlight;

uniform shader u_image;

uniform float u_size;
uniform float u_highlights;
uniform float u_layering;
uniform float u_edges;
uniform float u_caustic;
uniform float u_waves;

${Agsl.PI}
${Agsl.ROTATION2}
${Agsl.SIMPLEX_NOISE}

float getUvFrame(vec2 uv, vec2 aa) {
  float aax = 2. * aa.x;
  float aay = 2. * aa.y;

  float left = smoothstep(0., aax, uv.x);
  float right = 1.0 - smoothstep(1. - aax, 1., uv.x);
  float bottom = smoothstep(0., aay, uv.y);
  float top = 1.0 - smoothstep(1. - aay, 1., uv.y);

  return left * right * bottom * top;
}

mat2 rotate2D(float r) {
  return mat2(cos(r), sin(r), -sin(r), cos(r));
}

float getCausticNoise(vec2 uv, float t, float scale) {
  vec2 n = vec2(.1);
  vec2 N = vec2(.1);
  mat2 m = rotate2D(.5);
  for (int j = 0; j < 6; j++) {
    uv *= m;
    n *= m;
    vec2 q = uv * scale + float(j) + n + (.5 + .5 * float(j)) * (mod(float(j), 2.) - 1.) * t;
    n += sin(q);
    N += cos(q) / scale;
    scale *= 1.1;
  }
  return (N.x + N.y + 1.);
}

half4 main(vec2 fragCoord) {
  vec2 imageUV = getImageUV(fragCoord);

  // fwidth() is unavailable in AGSL: estimate the screen-space derivative of
  // the image UV with finite differences one pixel over.
  vec2 duvx = getImageUV(fragCoord + vec2(1., 0.)) - imageUV;
  vec2 duvy = getImageUV(fragCoord + vec2(0., 1.)) - imageUV;
  vec2 uvFrameAA = vec2(abs(duvx.x) + abs(duvy.x), abs(duvx.y) + abs(duvy.y));

  vec2 patternUV = imageUV - .5;
  patternUV = (patternUV * vec2(u_imageAspectRatio, 1.));
  patternUV /= (.01 + .09 * u_size);

  float t = u_time;

  float wavesNoise = snoise((.3 + .1 * sin(t)) * .1 * patternUV + vec2(0., .4 * t));

  float causticNoise = getCausticNoise(patternUV + u_waves * vec2(1., -1.) * wavesNoise, 2. * t, 1.5);

  causticNoise += u_layering * getCausticNoise(patternUV + 2. * u_waves * vec2(1., -1.) * wavesNoise, 1.5 * t, 2.);
  causticNoise = causticNoise * causticNoise;

  float edgesDistortion = smoothstep(0., .1, imageUV.x);
  edgesDistortion *= smoothstep(0., .1, imageUV.y);
  edgesDistortion *= (smoothstep(1., 1.1, imageUV.x) + (1.0 - smoothstep(.8, .95, imageUV.x)));
  edgesDistortion *= (1.0 - smoothstep(.9, 1., imageUV.y));
  edgesDistortion = mix(edgesDistortion, 1., u_edges);

  float causticNoiseDistortion = .02 * causticNoise * edgesDistortion;

  float wavesDistortion = .1 * u_waves * wavesNoise;

  imageUV += vec2(wavesDistortion, -wavesDistortion);
  imageUV += (u_caustic * causticNoiseDistortion);

  float frame = getUvFrame(imageUV, uvFrameAA);

  vec4 image = u_image.eval(imageUV * u_resolution);
  vec4 backColor = u_colorBack;
  backColor.rgb *= backColor.a;

  vec3 color = mix(backColor.rgb, image.rgb, image.a * frame);
  float opacity = backColor.a + image.a * frame;

  causticNoise = max(-.2, causticNoise);

  float hightlight = .025 * u_highlights * causticNoise;
  hightlight *= u_colorHighlight.a;
  color = mix(color, u_colorHighlight.rgb, .05 * u_highlights * causticNoise);
  opacity += hightlight;

  color += hightlight * (.5 + .5 * wavesNoise);
  opacity += hightlight * (.5 + .5 * wavesNoise);

  opacity = clamp(opacity, 0., 1.);

  return half4(color, opacity);
}
"""

public object WaterDefaults {
  public val ColorBack: Color = Color(0xFF909090)
  public val ColorHighlight: Color = Color(0xFFFFFFFF)
}

/**
 * Water-like surface distortion with natural caustic realism, applied over
 * the composable content.
 */
@Composable
public fun Modifier.water(
  colorBack: Color = WaterDefaults.ColorBack,
  colorHighlight: Color = WaterDefaults.ColorHighlight,
  highlights: Float = 0.07f,
  layering: Float = 0.5f,
  edges: Float = 0.8f,
  waves: Float = 0.3f,
  caustic: Float = 0.1f,
  size: Float = 1f,
  speed: Float = 1f,
  startFrame: Float = 0f,
  sizing: SizingParams = SizingParams.Object.copy(scale = 0.8f),
): Modifier = mirageEffect(
  shaderSource = SHADER,
  speed = speed,
  startFrame = startFrame,
  sizing = sizing,
) { time ->
  setFloatUniform("u_time", time)
  setColorUniform4f("u_colorBack", colorBack)
  setColorUniform4f("u_colorHighlight", colorHighlight)
  setFloatUniform("u_highlights", highlights)
  setFloatUniform("u_layering", layering)
  setFloatUniform("u_edges", edges)
  setFloatUniform("u_waves", waves)
  setFloatUniform("u_caustic", caustic)
  setFloatUniform("u_size", size)
}
