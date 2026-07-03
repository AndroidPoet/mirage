<h1 align="center">Mirage</h1>

<p align="center">
  ✨ 29 ultra-fast, zero-dependency AGSL shaders for Jetpack Compose — animated gradients,
  noise fields, and image filters that run entirely on the GPU.
</p>

<p align="center">
  <img src="art/demo.gif" width="300" alt="Mirage shaders running on a Pixel 6a" />
</p>

## Why Mirage

Beautiful shader backgrounds and image effects usually mean WebGL — not anymore.
Mirage brings a complete set of production-quality fragment shaders to Android
as `RuntimeShader` (AGSL) with idiomatic Compose APIs:

- **Procedural graphics** are plain composables: `MeshGradient()`, `Metaballs()`, `Voronoi()` …
- **Image filters** are `Modifier` extensions that work on *any* composable content:
  `Modifier.halftoneCmyk()`, `Modifier.water()`, `Modifier.liquidMetal()` …
- **Zero dependencies** beyond Compose itself. No WebView, no GL surface, no assets.
- Static shaders cost **nothing per frame**; animated ones drive a single time uniform.

Requires Android 13 (API 33) for rendering; the library's minSdk is 21 — gate usage
with `Build.VERSION.SDK_INT >= 33`.

## Shaders

| Procedural | Procedural | Image filters |
|---|---|---|
| Color Panels | Pulsing Border | Fluted Glass |
| Dithering | Simplex Noise | Gem Smoke |
| Dot Grid | Smoke Ring | Halftone CMYK |
| Dot Orbit | Spiral | Halftone Dots |
| God Rays | Static Mesh Gradient | Heatmap |
| Grain Gradient | Static Radial Gradient | Image Dithering |
| Mesh Gradient | Swirl | Liquid Metal |
| Metaballs | Voronoi | Paper Texture |
| Neuro Noise | Warp | Water |
| Perlin Noise | Waves | |

## Usage

Procedural shaders fill their bounds:

```kotlin
MeshGradient(
  modifier = Modifier.fillMaxSize(),
  colors = listOf(Color(0xFFE0EAFF), Color(0xFF241D9A), Color(0xFFF75092), Color(0xFF9F50D3)),
  speed = 1f,
)
```

Image filters apply to any content — images, video, whole screens:

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

Every shader ships with the upstream default preset and exposes all its parameters,
plus shared `SizingParams` (fit/scale/rotation/origin/offset) and `speed`/`startFrame`
animation controls.

## Demo

The `app` module is a gallery of all 29 shaders — run it and tap any card for fullscreen.

## License & attribution

```
Copyright 2026 androidpoet (Ranbir Singh)

Licensed under the Apache License, Version 2.0
```

Shader algorithms are derived from [Paper Shaders](https://github.com/paper-design/shaders)
by Paper Design (Apache-2.0), translated from GLSL ES 3.00 to AGSL. See [NOTICE](NOTICE).
