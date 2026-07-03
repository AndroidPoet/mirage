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
package io.androidpoet.miragedemo

import io.androidpoet.miragedemo.entries.colorPanelsEntry
import io.androidpoet.miragedemo.entries.ditheringEntry
import io.androidpoet.miragedemo.entries.dotGridEntry
import io.androidpoet.miragedemo.entries.dotOrbitEntry
import io.androidpoet.miragedemo.entries.flutedGlassEntry
import io.androidpoet.miragedemo.entries.gemSmokeEntry
import io.androidpoet.miragedemo.entries.godRaysEntry
import io.androidpoet.miragedemo.entries.grainGradientEntry
import io.androidpoet.miragedemo.entries.halftoneCmykEntry
import io.androidpoet.miragedemo.entries.halftoneDotsEntry
import io.androidpoet.miragedemo.entries.heatmapEntry
import io.androidpoet.miragedemo.entries.imageDitheringEntry
import io.androidpoet.miragedemo.entries.liquidMetalEntry
import io.androidpoet.miragedemo.entries.meshGradientEntry
import io.androidpoet.miragedemo.entries.metaballsEntry
import io.androidpoet.miragedemo.entries.neuroNoiseEntry
import io.androidpoet.miragedemo.entries.paperTextureEntry
import io.androidpoet.miragedemo.entries.perlinNoiseEntry
import io.androidpoet.miragedemo.entries.pulsingBorderEntry
import io.androidpoet.miragedemo.entries.simplexNoiseEntry
import io.androidpoet.miragedemo.entries.smokeRingEntry
import io.androidpoet.miragedemo.entries.spiralEntry
import io.androidpoet.miragedemo.entries.staticMeshGradientEntry
import io.androidpoet.miragedemo.entries.staticRadialGradientEntry
import io.androidpoet.miragedemo.entries.swirlEntry
import io.androidpoet.miragedemo.entries.voronoiEntry
import io.androidpoet.miragedemo.entries.warpEntry
import io.androidpoet.miragedemo.entries.waterEntry
import io.androidpoet.miragedemo.entries.wavesEntry

object Registry {
  val all: List<DemoEntry> = listOf(
    colorPanelsEntry,
    ditheringEntry,
    dotGridEntry,
    dotOrbitEntry,
    flutedGlassEntry,
    gemSmokeEntry,
    godRaysEntry,
    grainGradientEntry,
    halftoneCmykEntry,
    halftoneDotsEntry,
    heatmapEntry,
    imageDitheringEntry,
    liquidMetalEntry,
    meshGradientEntry,
    metaballsEntry,
    neuroNoiseEntry,
    paperTextureEntry,
    perlinNoiseEntry,
    pulsingBorderEntry,
    simplexNoiseEntry,
    smokeRingEntry,
    spiralEntry,
    staticMeshGradientEntry,
    staticRadialGradientEntry,
    swirlEntry,
    voronoiEntry,
    warpEntry,
    waterEntry,
    wavesEntry,
  )
}
