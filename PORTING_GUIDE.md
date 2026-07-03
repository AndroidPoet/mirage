# Mirage porting guide (GLSL ES 3.00 → AGSL)

Mirage ports the 29 open-source Paper Shaders (Apache-2.0, see NOTICE) to
Jetpack Compose via `RuntimeShader` (AGSL, API 33+).

## Where things live

- Upstream fragment sources (GLSL, TypeScript template strings):
  `/private/tmp/claude-501/-Users-ranbirsingh/0fd297dd-4ee2-4c03-828b-cac690d028e8/scratchpad/paper-shaders/<name>.ts`
- Upstream React wrappers with **default preset values** (use these for Kotlin defaults):
  `/private/tmp/claude-501/-Users-ranbirsingh/0fd297dd-4ee2-4c03-828b-cac690d028e8/scratchpad/paper-shaders/react/<name>.tsx`
- Shared infra (already written, do not modify):
  `mirage/src/main/kotlin/io/github/androidpoet/mirage/core/` — `Sizing.kt`
  (SIZING_AGSL + SizingParams), `Agsl.kt` (snippets), `ShaderTime.kt`,
  `MirageHost.kt` (MirageSurface + Modifier.mirageEffect), `Colors.kt`.
- **Exemplar port — copy its structure exactly**:
  `mirage/src/main/kotlin/io/github/androidpoet/mirage/SimplexNoise.kt`

## Deliverables per shader

1. `mirage/src/main/kotlin/io/github/androidpoet/mirage/<PascalName>.kt`
   - `private val SHADER = """ ... """` AGSL source
   - `<PascalName>Defaults` object with the upstream `defaultPreset` values
   - Procedural shaders: `@Composable fun <PascalName>(modifier, ...params)` calling `MirageSurface`
   - Image filters (halftone-*, image-dithering, water, paper-texture, liquid-metal, gem-smoke, heatmap, fluted-glass): `@Composable fun Modifier.<camelName>(...params): Modifier` calling `mirageEffect`
   - Everything `@RequiresApi(Build.VERSION_CODES.TIRAMISU)`
2. `demo/src/main/kotlin/io/github/androidpoet/mirage/demo/entries/<PascalName>Entry.kt`:
   `val <camelName>Entry = DemoEntry("<Display Name>") { <PascalName>(Modifier.fillMaxSize()) }`
   — for image filters use `DemoImage(Modifier.fillMaxSize().<camelName>())`.
   `Registry.kt` already imports your val by that exact name; match it.

## Translation rules

1. **Skeleton**: fragment becomes
   ```
   private val SHADER = """
   ${'$'}SIZING_AGSL
   uniform float u_time;            // only if the shader animates
   <other uniforms verbatim>
   <helper functions>
   half4 main(vec2 fragCoord) {
     ...
     return half4(color, opacity);
   }
   """
   ```
   Delete `#version`, `precision`, `in`/`out` declarations, and `fragColor =` (return instead).
2. **Varyings → sizing functions** (compute once at the top of main):
   `v_objectUV`→`getObjectUV(fragCoord)`, `v_patternUV`→`getPatternUV(fragCoord)`,
   `v_imageUV`→`getImageUV(fragCoord)`, `v_responsiveUV`→`getResponsiveUV(fragCoord)`,
   `v_objectBoxSize`→`getObjectBoxSize()`, `v_patternBoxSize`→`getPatternBoxSize()`.
3. **Never redeclare** uniforms already in SIZING_AGSL: u_resolution, u_pixelRatio,
   u_imageAspectRatio, u_fit, u_scale, u_rotation, u_originX/Y, u_offsetX/Y,
   u_worldWidth/Height. Fragment code that reads them (e.g. u_scale for AA) just works.
4. **Template snippets**: `${'$'}{declarePI}` → `${'$'}{Agsl.PI}`; `${'$'}{rotation2}` → `${'$'}{Agsl.ROTATION2}`;
   `${'$'}{proceduralHash11/21/22}` → `${'$'}{Agsl.HASH11/21/22}`; `${'$'}{simplexNoise}` → `${'$'}{Agsl.SIMPLEX_NOISE}`.
   `${'$'}{colorBandingFix}` is a *statement*: inject `${'$'}{Agsl.BANDING_FIX}` with the helpers and
   replace the statement with `color += bandingNoise(fragCoord);`.
5. **Noise texture**: drop `uniform sampler2D u_noiseTexture`. `${'$'}{textureRandomizerR}` →
   `${'$'}{Agsl.RANDOM_R}`, `${'$'}{textureRandomizerGB}` → `${'$'}{Agsl.RANDOM_GB}`. For inline
   `texture(u_noiseTexture, ...)` lookups inside custom noise functions, substitute the same
   hash pattern (see Agsl.RANDOM_R) keeping the surrounding function identical.
6. **Image input** (filters only): add `uniform shader u_image;`.
   `texture(u_image, uv)` → `u_image.eval(uv * u_resolution)` (eval takes pixels; the
   content layer is bound as "u_image" by `mirageEffect`, so content size == u_resolution).
   eval returns premultiplied colors — fine for opaque content; only unpremultiply
   (`if (c.a > 0.) c.rgb /= c.a;`) if output is visibly wrong at transparent edges.
7. **Derivatives — AGSL has none.**
   - `fwidth()` used purely for edge anti-aliasing → replace with a pixel-scale estimate,
     e.g. `float aa = <feature scale in units per px>;` derived from u_resolution
     (see halftone notes) — or the finite-difference pattern below when correctness matters.
   - `dFdx(expr)` / `dFdy(expr)` / precise `fwidth` → finite differences: re-evaluate the
     expression at `fragCoord + vec2(1., 0.)` and `fragCoord + vec2(0., 1.)` and subtract
     (see SimplexNoise.kt exemplar). Costs extra evaluations; correct and portable.
   Leaving any fwidth/dFdx/dFdy in the source is a runtime compile failure.
8. **Loops/indexing**: keep constant loop bounds. Replace `break`/`continue` on dynamic
   conditions with `if (i < int(u_colorsCount)) { ... }` guards. Dynamic array indexing
   (`u_colors[int(x)]`) → constant-bound select loop (see exemplar).
9. `gl_FragCoord.xy` → `fragCoord` (thread it into helpers as a parameter).
10. Types: `vec2/3/4`, `mat2` aliases are valid AGSL. Add explicit `float()` casts where
    GLSL was lax about int/float mixing. `mod`, `mix`, `smoothstep`, `clamp`, ternaries,
    `if/else`, out/inout params all exist. Return `half4`.
11. **Colors**: keep uniforms as plain `vec4` (no layout(color)); set from Kotlin with
    `setColorUniform4f` / `setColorsUniform` (straight alpha, matching the WebGL mount).
12. **u_time**: if the shader animates, declare `uniform float u_time;` and set it in the
    wrapper's setUniforms lambda (`setFloatUniform("u_time", time)`). Static shaders must
    not declare it (AGSL strips unused uniforms and setting a stripped uniform throws).
13. Kotlin defaults come from `react/<name>.tsx` `defaultPreset` — including `speed` and
    sizing values (scale/fit/rotation). Map `fit` strings to ShaderFit; pass preset scale
    etc. via the wrapper's default `sizing: SizingParams` parameter.
14. Escape literal `$` inside the Kotlin raw string as `${'$'}{'${'$'}'}` (rare in GLSL).
15. Name collisions: SIZING_AGSL already defines `getBoxSize`, `sizingBaseUV`, `SIZING_PI`
    and the get*UV functions. If the shader defines helpers with the same name, rename its
    local copy. Inject each Agsl snippet at most once.

## Process

- Port faithfully line-by-line; do not "improve" the math or rename upstream uniforms.
- Do NOT run gradle (a central build runs after all ports land). Do not edit core/,
  Registry.kt, other shaders' files, or build files.
- Do not add explanatory/porting-note comments in code beyond what the exemplar shows.
