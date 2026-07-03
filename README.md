<div align="center">

# Mirage

✨ 29 ultra-fast, zero-dependency AGSL shaders for Jetpack Compose — animated gradients, noise fields, and image filters that run entirely on the GPU.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.androidpoet/mirage.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.androidpoet/mirage)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![API](https://img.shields.io/badge/API-33%2B-brightgreen.svg)](https://developer.android.com/reference/android/graphics/RuntimeShader)
[![GitHub](https://img.shields.io/badge/GitHub-AndroidPoet-181717.svg?logo=github)](https://github.com/AndroidPoet)

<img src="art/demo.gif" width="300" alt="Mirage shaders running on a Pixel 6a" />

</div>

## Why Mirage

Beautiful shader backgrounds and image effects usually mean WebGL — not anymore. Mirage
brings a complete set of production-quality fragment shaders to Android as
`RuntimeShader` (AGSL) with idiomatic Compose APIs:

- 🎨 **Procedural graphics** are plain composables — `MeshGradient()`, `Metaballs()`, `Voronoi()` …
- 🖼️ **Image filters** are `Modifier` extensions that work on *any* composable content — `Modifier.water()`, `Modifier.liquidMetal()`, `Modifier.halftoneCmyk()` …
- 📦 **Zero dependencies** beyond Compose itself. No WebView, no GL surface, no assets.
- ⚡ **Cheap** — static shaders cost nothing per frame; animated ones drive a single time uniform.
- 🎛️ **Fully parameterised** — every shader exposes its uniforms plus shared sizing and animation controls.

## Include in your project

[![Maven Central](https://img.shields.io/maven-central/v/io.github.androidpoet/mirage.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.androidpoet/mirage)

### Gradle

Add the dependency below to your **module**'s `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("io.github.androidpoet:mirage:$version")
}
```

Replace `$version` with the latest version shown on the badge above (currently `0.1.0`).

## Requirements

Rendering uses `RuntimeShader` (AGSL), which requires **Android 13 (API 33)**. The
library's `minSdk` is **21**, so you can depend on it from any project — just gate the
shader composables at the call site:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    MeshGradient(Modifier.fillMaxSize())
} else {
    // your own fallback (a static gradient, solid color, image…)
}
```

## Usage

### Procedural graphics

Procedural shaders are composables that fill their bounds. Pass colors, tune the
parameters, and set `speed` to animate (`speed = 0f` makes it static and free per frame):

```kotlin
MeshGradient(
    modifier = Modifier.fillMaxSize(),
    colors = listOf(
        Color(0xFFE0EAFF),
        Color(0xFF241D9A),
        Color(0xFFF75092),
        Color(0xFF9F50D3),
    ),
    speed = 1f,
)
```

### Image filters

Image-filter shaders are `Modifier` extensions — apply them to **any** content: images,
video, text, or a whole screen:

```kotlin
Image(
    painter = painterResource(R.drawable.photo),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(4f / 5f)
        .halftoneCmyk(),
)
```

### Shared controls

Every shader exposes its own uniforms **plus** a common set of controls:

| Control | Type | What it does |
|---|---|---|
| `speed` | `Float` | Animation speed; `0f` freezes the shader (no per-frame cost). |
| `startFrame` | `Float` | Millisecond offset into the animation for a deterministic starting point. |
| `sizing` | `SizingParams` | Fit (`None` / `Contain` / `Cover`), `scale`, `rotation`, `origin`, and `offset` of the graphic. |

```kotlin
Voronoi(
    modifier = Modifier.fillMaxSize(),
    speed = 0.5f,
    sizing = SizingParams(fit = ShaderFit.Cover, scale = 1.2f, rotation = 15f),
)
```

## Shaders

**Procedural** — composables, e.g. `Metaballs(Modifier.fillMaxSize())`:

| | | |
|---|---|---|
| `ColorPanels` | `GodRays` | `PulsingBorder` |
| `Dithering` | `GrainGradient` | `SimplexNoise` |
| `DotGrid` | `MeshGradient` | `SmokeRing` |
| `DotOrbit` | `Metaballs` | `Spiral` |
| `PerlinNoise` | `NeuroNoise` | `StaticMeshGradient` |
| `Swirl` | `Voronoi` | `StaticRadialGradient` |
| `Warp` | `Waves` | |

**Image filters** — modifiers, e.g. `Modifier.water()`:

| | | |
|---|---|---|
| `Modifier.flutedGlass()` | `Modifier.halftoneDots()` | `Modifier.liquidMetal()` |
| `Modifier.gemSmoke()` | `Modifier.heatmap()` | `Modifier.paperTexture()` |
| `Modifier.halftoneCmyk()` | `Modifier.imageDithering()` | `Modifier.water()` |

## Demo

The `app` module is a gallery of all 29 shaders — run it and tap any card to view it
fullscreen. That's what the recording above shows, running on a Pixel 6a.

## Support

### Find this repository useful? :heart:

Support it by joining __[stargazers](https://github.com/AndroidPoet/mirage/stargazers)__ for this repository. :star:
Also, __[follow me](https://github.com/AndroidPoet)__ on GitHub for more cool projects! 🤩

## License & attribution

```
Copyright 2026 androidpoet (Ranbir Singh)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Shader algorithms are derived from [Paper Shaders](https://github.com/paper-design/shaders)
by Paper Design (Apache-2.0), translated from GLSL ES 3.00 to AGSL. See [NOTICE](NOTICE).
