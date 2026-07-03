# Mirage — Usage Guide

Complete reference for every shader in Mirage, with full signatures and code examples.

- [Setup](#setup)
- [Core concepts](#core-concepts)
- [Procedural shaders](#procedural-shaders)
- [Image filters](#image-filters)
- [Enum reference](#enum-reference)

---

## Setup

Add the dependency:

```kotlin
dependencies {
    implementation("io.github.androidpoet:mirage:0.1.0")
}
```

Every shader requires **Android 13 (API 33)** to render. Gate at the call site:

```kotlin
import android.os.Build
import io.androidpoet.mirage.MeshGradient

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    MeshGradient(Modifier.fillMaxSize())
} else {
    // fallback: static gradient, solid color, image, …
}
```

All snippets below assume this gate is in place.

---

## Core concepts

### Two kinds of shaders

| Kind | API shape | Fills… | Example |
|---|---|---|---|
| **Procedural** | `@Composable fun Name(...)` | its own bounds | `MeshGradient(Modifier.fillMaxSize())` |
| **Image filter** | `Modifier.name(...)` | the content it modifies | `Modifier.water()` on an `Image` |

### Defaults objects

Each shader has a `<Name>Defaults` object holding its default colors so you can reuse or
tweak them instead of hardcoding:

```kotlin
MeshGradient(
    colors = MeshGradientDefaults.Colors,          // the built-in preset
)

Dithering(
    colorFront = DitheringDefaults.ColorFront,      // reuse one default…
    colorBack = Color.Black,                         // …override another
)
```

### Animation: `speed` and `startFrame`

Animated shaders advance a single time uniform each frame.

- `speed: Float` — playback rate. **`0f` freezes the shader and it costs nothing per frame.**
- `startFrame: Float` — a millisecond offset into the animation, for a deterministic
  starting look (or to stagger multiple instances).

```kotlin
Metaballs(speed = 0.5f)                 // half speed
Metaballs(speed = 0f, startFrame = 3000f)  // frozen at the 3s mark, zero per-frame cost
```

### Placement: `SizingParams` and `ShaderFit`

Every shader takes a `sizing: SizingParams` controlling how the graphic is fit and placed:

```kotlin
public data class SizingParams(
    val fit: ShaderFit = ShaderFit.Contain, // None / Contain / Cover
    val scale: Float = 1f,
    val rotation: Float = 0f,               // degrees
    val originX: Float = 0.5f,
    val originY: Float = 0.5f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val worldWidth: Float = 0f,             // 0 = use view size
    val worldHeight: Float = 0f,
)

enum class ShaderFit { None, Contain, Cover }
```

Ready-made presets: `SizingParams.Object` (fit = Contain) and `SizingParams.Pattern`
(fit = None). Use `.copy(...)` to tweak:

```kotlin
Voronoi(
    sizing = SizingParams(fit = ShaderFit.Cover, scale = 1.2f, rotation = 15f),
)

Waves(
    sizing = SizingParams.Pattern.copy(scale = 0.4f),
)
```

### Grain

Many shaders expose `grainMixer` / `grainOverlay` (0f–1f) to add film-grain texture —
`grainMixer` perturbs the pattern, `grainOverlay` adds a light/dark speckle on top.

---

## Procedural shaders

Composables that fill their bounds. All take `modifier: Modifier = Modifier`.

### ColorPanels

Overlapping translucent color panels sliding across a backdrop.

```kotlin
ColorPanels(
    modifier: Modifier = Modifier,
    colors: List<Color> = ColorPanelsDefaults.Colors,
    colorBack: Color = ColorPanelsDefaults.ColorBack,
    angle1: Float = 0f,
    angle2: Float = 0f,
    length: Float = 1.1f,
    edges: Boolean = false,
    blur: Float = 0f,
    fadeIn: Float = 1f,
    fadeOut: Float = 0.3f,
    gradient: Float = 0f,
    density: Float = 3f,
    speed: Float = 0.5f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Object.copy(scale = 0.8f),
)
```

```kotlin
ColorPanels(Modifier.fillMaxSize(), density = 4f, blur = 0.2f)
```

### Dithering

Retro ordered-dither pattern over a moving shape.

```kotlin
Dithering(
    modifier: Modifier = Modifier,
    colorBack: Color = DitheringDefaults.ColorBack,
    colorFront: Color = DitheringDefaults.ColorFront,
    shape: DitheringShape = DitheringShape.Sphere,
    type: DitheringType = DitheringType.Bayer4x4,
    size: Float = 2f,
    speed: Float = 1f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Pattern.copy(scale = 0.6f),
)
```

```kotlin
Dithering(Modifier.fillMaxSize(), shape = DitheringShape.Swirl, type = DitheringType.Bayer8x8)
```

### DotGrid

A grid of dots with per-dot size/opacity jitter.

```kotlin
DotGrid(
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
)
```

```kotlin
DotGrid(Modifier.fillMaxSize(), shape = DotGridShape.Diamond, gapX = 24f, gapY = 24f)
```

### DotOrbit

Dots orbiting on a repeating field.

```kotlin
DotOrbit(
    modifier: Modifier = Modifier,
    colorBack: Color = DotOrbitDefaults.ColorBack,
    colors: List<Color> = DotOrbitDefaults.Colors,
    size: Float = 1f,
    sizeRange: Float = 0f,
    spreading: Float = 1f,
    stepsPerColor: Int = 4,
    speed: Float = 1.5f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Pattern,
)
```

```kotlin
DotOrbit(Modifier.fillMaxSize(), spreading = 1.4f, speed = 1f)
```

### GodRays

Volumetric light rays radiating from an off-screen source, with bloom.

```kotlin
GodRays(
    modifier: Modifier = Modifier,
    colorBack: Color = GodRaysDefaults.ColorBack,
    colorBloom: Color = GodRaysDefaults.ColorBloom,
    colors: List<Color> = GodRaysDefaults.Colors,
    density: Float = 0.3f,
    spotty: Float = 0.3f,
    midSize: Float = 0.2f,
    midIntensity: Float = 0.4f,
    intensity: Float = 0.8f,
    bloom: Float = 0.4f,
    speed: Float = 0.75f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Object.copy(offsetY = -0.55f),
)
```

```kotlin
GodRays(Modifier.fillMaxSize(), intensity = 1f, bloom = 0.6f)
```

### GrainGradient

Soft multi-color gradient with configurable grain and shape.

```kotlin
GrainGradient(
    modifier: Modifier = Modifier,
    colorBack: Color = GrainGradientDefaults.ColorBack,
    colors: List<Color> = GrainGradientDefaults.Colors,
    softness: Float = 0.5f,
    intensity: Float = 0.5f,
    noise: Float = 0.25f,
    shape: GrainGradientShape = GrainGradientShape.Corners,
    speed: Float = 1f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Object,
)
```

```kotlin
GrainGradient(Modifier.fillMaxSize(), shape = GrainGradientShape.Blob, noise = 0.4f)
```

### MeshGradient

Flowing spots of color moving on organic trajectories — the classic animated mesh gradient.
Accepts up to 10 colors.

```kotlin
MeshGradient(
    modifier: Modifier = Modifier,
    colors: List<Color> = MeshGradientDefaults.Colors,
    distortion: Float = 0.8f,
    swirl: Float = 0.1f,
    grainMixer: Float = 0f,
    grainOverlay: Float = 0f,
    speed: Float = 1f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Object,
)
```

```kotlin
MeshGradient(
    modifier = Modifier.fillMaxSize(),
    colors = listOf(
        Color(0xFFE0EAFF), Color(0xFF241D9A), Color(0xFFF75092), Color(0xFF9F50D3),
    ),
    swirl = 0.3f,
    speed = 1f,
)
```

### Metaballs

Merging, gooey blobs.

```kotlin
Metaballs(
    modifier: Modifier = Modifier,
    colorBack: Color = MetaballsDefaults.ColorBack,
    colors: List<Color> = MetaballsDefaults.Colors,
    count: Float = 10f,
    size: Float = 0.83f,
    speed: Float = 1f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Object,
)
```

```kotlin
Metaballs(Modifier.fillMaxSize(), count = 6f, size = 1f)
```

### NeuroNoise

Electric neural-network-like filaments.

```kotlin
NeuroNoise(
    modifier: Modifier = Modifier,
    colorFront: Color = NeuroNoiseDefaults.ColorFront,
    colorMid: Color = NeuroNoiseDefaults.ColorMid,
    colorBack: Color = NeuroNoiseDefaults.ColorBack,
    brightness: Float = 0.05f,
    contrast: Float = 0.3f,
    speed: Float = 1f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Pattern,
)
```

```kotlin
NeuroNoise(Modifier.fillMaxSize(), contrast = 0.5f)
```

### PerlinNoise

Classic Perlin fractal noise between two colors.

```kotlin
PerlinNoise(
    modifier: Modifier = Modifier,
    colorFront: Color = PerlinNoiseDefaults.ColorFront,
    colorBack: Color = PerlinNoiseDefaults.ColorBack,
    proportion: Float = 0.35f,
    softness: Float = 0.1f,
    octaveCount: Int = 1,
    persistence: Float = 1f,
    lacunarity: Float = 1.5f,
    speed: Float = 0.5f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Pattern,
)
```

```kotlin
PerlinNoise(Modifier.fillMaxSize(), octaveCount = 3, persistence = 0.6f)
```

### PulsingBorder

An animated glowing border — great around cards or screens.

```kotlin
PulsingBorder(
    modifier: Modifier = Modifier,
    colorBack: Color = PulsingBorderDefaults.ColorBack,
    colors: List<Color> = PulsingBorderDefaults.Colors,
    roundness: Float = 0.25f,
    thickness: Float = 0.1f,
    marginLeft: Float = 0f,
    marginRight: Float = 0f,
    marginTop: Float = 0f,
    marginBottom: Float = 0f,
    aspectRatio: PulsingBorderAspectRatio = PulsingBorderAspectRatio.Auto,
    softness: Float = 0.75f,
    intensity: Float = 0.2f,
    bloom: Float = 0.25f,
    spots: Int = 5,
    spotSize: Float = 0.5f,
    pulse: Float = 0.25f,
    smoke: Float = 0.3f,
    smokeSize: Float = 0.6f,
    speed: Float = 1f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Object.copy(scale = 0.6f),
)
```

```kotlin
Box {
    content()
    PulsingBorder(Modifier.matchParentSize(), thickness = 0.15f, intensity = 0.4f)
}
```

### SimplexNoise

Banded simplex noise cycling through a color list.

```kotlin
SimplexNoise(
    modifier: Modifier = Modifier,
    colors: List<Color> = SimplexNoiseDefaults.Colors,
    stepsPerColor: Int = 2,
    softness: Float = 0f,
    speed: Float = 0.5f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Pattern.copy(scale = 0.6f),
)
```

```kotlin
SimplexNoise(Modifier.fillMaxSize(), softness = 0.5f, stepsPerColor = 4)
```

### SmokeRing

A turbulent ring of smoke.

```kotlin
SmokeRing(
    modifier: Modifier = Modifier,
    colorBack: Color = SmokeRingDefaults.ColorBack,
    colors: List<Color> = SmokeRingDefaults.Colors,
    noiseScale: Float = 3f,
    noiseIterations: Int = 8,
    radius: Float = 0.25f,
    thickness: Float = 0.65f,
    innerShape: Float = 0.7f,
    speed: Float = 0.5f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Object.copy(scale = 0.8f),
)
```

```kotlin
SmokeRing(Modifier.fillMaxSize(), thickness = 0.8f, radius = 0.3f)
```

### Spiral

A rotating spiral of strokes.

```kotlin
Spiral(
    modifier: Modifier = Modifier,
    colorBack: Color = SpiralDefaults.ColorBack,
    colorFront: Color = SpiralDefaults.ColorFront,
    density: Float = 1f,
    distortion: Float = 0f,
    strokeWidth: Float = 0.5f,
    strokeTaper: Float = 0f,
    strokeCap: Float = 0f,
    noise: Float = 0f,
    noiseFrequency: Float = 0f,
    softness: Float = 0f,
    speed: Float = 1f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Pattern,
)
```

```kotlin
Spiral(Modifier.fillMaxSize(), density = 1.5f, strokeWidth = 0.4f)
```

### StaticMeshGradient

A still (non-animated) mesh gradient — zero per-frame cost.

```kotlin
StaticMeshGradient(
    modifier: Modifier = Modifier,
    colors: List<Color> = StaticMeshGradientDefaults.Colors,
    positions: Float = 2f,
    waveX: Float = 1f,
    waveXShift: Float = 0.6f,
    waveY: Float = 1f,
    waveYShift: Float = 0.21f,
    mixing: Float = 0.93f,
    grainMixer: Float = 0f,
    grainOverlay: Float = 0f,
    sizing: SizingParams = SizingParams.Object.copy(rotation = 270f),
)
```

```kotlin
StaticMeshGradient(Modifier.fillMaxSize())   // perfect as a cheap wallpaper/background
```

### StaticRadialGradient

A still radial gradient with focal control and optional edge distortion.

```kotlin
StaticRadialGradient(
    modifier: Modifier = Modifier,
    colorBack: Color = StaticRadialGradientDefaults.ColorBack,
    colors: List<Color> = StaticRadialGradientDefaults.Colors,
    radius: Float = 0.8f,
    focalDistance: Float = 0.99f,
    focalAngle: Float = 0f,
    falloff: Float = 0.24f,
    mixing: Float = 0.5f,
    distortion: Float = 0f,
    distortionShift: Float = 0f,
    distortionFreq: Float = 12f,
    grainMixer: Float = 0f,
    grainOverlay: Float = 0f,
    sizing: SizingParams = SizingParams.Object,
)
```

```kotlin
StaticRadialGradient(Modifier.fillMaxSize(), radius = 1f, falloff = 0.4f)
```

### Swirl

Concentric bands twisted around a center.

```kotlin
Swirl(
    modifier: Modifier = Modifier,
    colorBack: Color = SwirlDefaults.ColorBack,
    colors: List<Color> = SwirlDefaults.Colors,
    bandCount: Float = 4f,
    twist: Float = 0.1f,
    center: Float = 0.2f,
    proportion: Float = 0.5f,
    softness: Float = 0f,
    noiseFrequency: Float = 0.4f,
    noise: Float = 0.2f,
    speed: Float = 0.32f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Object,
)
```

```kotlin
Swirl(Modifier.fillMaxSize(), bandCount = 6f, twist = 0.3f)
```

### Voronoi

An animated Voronoi cell diagram with gaps and glow.

```kotlin
Voronoi(
    modifier: Modifier = Modifier,
    colors: List<Color> = VoronoiDefaults.Colors,
    stepsPerColor: Int = 3,
    colorGlow: Color = VoronoiDefaults.ColorGlow,
    colorGap: Color = VoronoiDefaults.ColorGap,
    distortion: Float = 0.4f,
    gap: Float = 0.04f,
    glow: Float = 0f,
    speed: Float = 0.5f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Pattern.copy(scale = 0.5f),
)
```

```kotlin
Voronoi(Modifier.fillMaxSize(), gap = 0.06f, glow = 0.3f)
```

### Warp

Distorted repeating patterns (checks, stripes, edges) with swirl.

```kotlin
Warp(
    modifier: Modifier = Modifier,
    colors: List<Color> = WarpDefaults.Colors,
    proportion: Float = 0.45f,
    softness: Float = 1f,
    shape: WarpPattern = WarpPattern.Checks,
    shapeScale: Float = 0.1f,
    distortion: Float = 0.25f,
    swirl: Float = 0.8f,
    swirlIterations: Int = 10,
    speed: Float = 1f,
    startFrame: Float = 0f,
    sizing: SizingParams = SizingParams.Pattern,
)
```

```kotlin
Warp(Modifier.fillMaxSize(), shape = WarpPattern.Stripes, swirl = 1f)
```

### Waves

Clean sine-wave lines.

```kotlin
Waves(
    modifier: Modifier = Modifier,
    colorFront: Color = WavesDefaults.ColorFront,
    colorBack: Color = WavesDefaults.ColorBack,
    shape: Float = 0f,
    frequency: Float = 0.5f,
    amplitude: Float = 0.5f,
    spacing: Float = 1.2f,
    proportion: Float = 0.1f,
    softness: Float = 0f,
    sizing: SizingParams = SizingParams.Pattern.copy(scale = 0.6f),
)
```

```kotlin
Waves(Modifier.fillMaxSize(), frequency = 0.8f, amplitude = 0.7f)
```

---

## Image filters

`Modifier` extensions. Apply them to any composable — `Image`, video, text, or a whole
screen. Filters clip to the content bounds.

### Modifier.flutedGlass()

Refractive fluted / ribbed-glass distortion.

```kotlin
Modifier.flutedGlass(
    colorBack: Color = FlutedGlassDefaults.ColorBack,
    colorShadow: Color = FlutedGlassDefaults.ColorShadow,
    colorHighlight: Color = FlutedGlassDefaults.ColorHighlight,
    shadows: Float = 0.25f,
    size: Float = 0.5f,
    angle: Float = 0f,
    distortionShape: GlassDistortionShape = GlassDistortionShape.Prism,
    highlights: Float = 0.1f,
    shape: GlassGridShape = GlassGridShape.Lines,
    distortion: Float = 0.5f,
    shift: Float = 0f,
    blur: Float = 0f,
    edges: Float = 0.25f,
    stretch: Float = 0f,
    marginLeft: Float = 0f,
    marginRight: Float = 0f,
    marginTop: Float = 0f,
    marginBottom: Float = 0f,
    grainMixer: Float = 0f,
    grainOverlay: Float = 0f,
    sizing: SizingParams = SizingParams(fit = ShaderFit.Cover),
): Modifier
```

```kotlin
Image(
    painter = painterResource(R.drawable.photo),
    contentDescription = null,
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .flutedGlass(shape = GlassGridShape.Wave, distortion = 0.7f),
)
```

### Modifier.gemSmoke()

Smoky, gem-like refraction. Set `isImage = true` when layering over real content.

```kotlin
Modifier.gemSmoke(
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
): Modifier
```

```kotlin
Box(Modifier.size(240.dp).gemSmoke(shape = GemSmokeShape.Metaballs))
```

### Modifier.halftoneCmyk()

Print-style CMYK halftone separation over the content.

```kotlin
Modifier.halftoneCmyk(
    colorBack: Color = HalftoneCmykDefaults.ColorBack,
    colorC: Color = HalftoneCmykDefaults.ColorC,
    colorM: Color = HalftoneCmykDefaults.ColorM,
    colorY: Color = HalftoneCmykDefaults.ColorY,
    colorK: Color = HalftoneCmykDefaults.ColorK,
    size: Float = 0.2f,
    contrast: Float = 1f,
    softness: Float = 1f,
    grainSize: Float = 0.5f,
    grainMixer: Float = 0f,
    grainOverlay: Float = 0f,
    gridNoise: Float = 0.2f,
    floodC: Float = 0.15f,
    floodM: Float = 0f,
    floodY: Float = 0f,
    floodK: Float = 0f,
    gainC: Float = 0.3f,
    gainM: Float = 0f,
    gainY: Float = 0.2f,
    gainK: Float = 0f,
    type: HalftoneCmykType = HalftoneCmykType.Ink,
    sizing: SizingParams = SizingParams(fit = ShaderFit.Cover),
): Modifier
```

```kotlin
Image(
    painter = painterResource(R.drawable.photo),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 5f).halftoneCmyk(),
)
```

### Modifier.halftoneDots()

Monochrome halftone dots (square or hex grid).

```kotlin
Modifier.halftoneDots(
    colorBack: Color = HalftoneDotsDefaults.ColorBack,
    colorFront: Color = HalftoneDotsDefaults.ColorFront,
    size: Float = 0.5f,
    radius: Float = 1.25f,
    contrast: Float = 0.4f,
    originalColors: Boolean = false,
    inverted: Boolean = false,
    grainMixer: Float = 0.2f,
    grainOverlay: Float = 0.2f,
    grainSize: Float = 0.5f,
    grid: HalftoneDotsGrid = HalftoneDotsGrid.Hex,
    type: HalftoneDotsType = HalftoneDotsType.Gooey,
    sizing: SizingParams = SizingParams(fit = ShaderFit.Cover),
): Modifier
```

```kotlin
Modifier.halftoneDots(grid = HalftoneDotsGrid.Square, type = HalftoneDotsType.Classic)
```

### Modifier.heatmap()

Thermal-camera style color mapping with contour lines.

```kotlin
Modifier.heatmap(
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
): Modifier
```

```kotlin
Modifier.heatmap(contour = 0.7f)
```

### Modifier.imageDithering()

Ordered dithering + color quantization of the content.

```kotlin
Modifier.imageDithering(
    colorFront: Color = ImageDitheringDefaults.ColorFront,
    colorBack: Color = ImageDitheringDefaults.ColorBack,
    colorHighlight: Color = ImageDitheringDefaults.ColorHighlight,
    type: ImageDitheringType = ImageDitheringType.Bayer8x8,
    size: Float = 2f,
    colorSteps: Int = 2,
    originalColors: Boolean = false,
    inverted: Boolean = false,
    sizing: SizingParams = SizingParams(fit = ShaderFit.Cover),
): Modifier
```

```kotlin
// keep the source colors, just dither them
Modifier.imageDithering(originalColors = true, colorSteps = 4)
```

### Modifier.liquidMetal()

Chromatic, molten-metal refraction. Set `isImage = true` over real content.

```kotlin
Modifier.liquidMetal(
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
): Modifier
```

```kotlin
Box(Modifier.size(240.dp).liquidMetal(shape = LiquidMetalShape.Circle))
```

### Modifier.paperTexture()

Overlays a paper texture — fibers, crumples, folds, drops.

```kotlin
Modifier.paperTexture(
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
): Modifier
```

```kotlin
Modifier.paperTexture(crumples = 0.5f, folds = 0.8f)
```

### Modifier.water()

Caustic water-surface distortion over the content.

```kotlin
Modifier.water(
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
): Modifier
```

```kotlin
Image(
    painter = painterResource(R.drawable.pool),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxSize().water(waves = 0.5f, caustic = 0.2f),
)
```

---

## Enum reference

| Enum | Values |
|---|---|
| `ShaderFit` | `None`, `Contain`, `Cover` |
| `DitheringShape` | `Simplex`, `Warp`, `Dots`, `Wave`, `Ripple`, `Swirl`, `Sphere` |
| `DitheringType` | `Random`, `Bayer2x2`, `Bayer4x4`, `Bayer8x8` |
| `DotGridShape` | `Circle`, `Diamond`, `Square`, `Triangle` |
| `GrainGradientShape` | `Wave`, `Dots`, `Truchet`, `Corners`, `Ripple`, `Blob`, `Sphere` |
| `GlassGridShape` | `Lines`, `LinesIrregular`, `Wave`, `Zigzag`, `Pattern` |
| `GlassDistortionShape` | `Prism`, `Lens`, `Contour`, `Cascade`, `Flat` |
| `GemSmokeShape` | `None`, `Circle`, `Daisy`, `Diamond`, `Metaballs` |
| `LiquidMetalShape` | `None`, `Circle`, `Daisy`, `Diamond`, `Metaballs` |
| `HalftoneCmykType` | `Dots`, `Ink`, `Sharp` |
| `HalftoneDotsType` | `Classic`, `Gooey`, `Holes`, `Soft` |
| `HalftoneDotsGrid` | `Square`, `Hex` |
| `ImageDitheringType` | `Random`, `Bayer2x2`, `Bayer4x4`, `Bayer8x8` |
| `PulsingBorderAspectRatio` | `Auto`, `Square` |
| `WarpPattern` | `Checks`, `Stripes`, `Edge` |

---

Every symbol above lives in `io.androidpoet.mirage`. See the [README](../README.md) for the
shader catalog and installation.
