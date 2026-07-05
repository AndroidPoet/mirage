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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * Drives the u_time uniform with the same semantics as the upstream mount:
 * time advances by (delta seconds * speed) each frame; speed 0 stops the
 * frame loop entirely so static shaders cost nothing per frame; [startFrame]
 * is a millisecond offset for deterministic starting points.
 */
@Composable
public fun rememberShaderTime(speed: Float = 1f, startFrame: Float = 0f): State<Float> {
  val time = remember { mutableFloatStateOf(startFrame / 1000f) }
  LaunchedEffect(speed) {
    if (speed == 0f) return@LaunchedEffect
    var lastNanos = -1L
    while (true) {
      withFrameNanos { now ->
        if (lastNanos >= 0) {
          time.floatValue += (now - lastNanos) / 1_000_000_000f * speed
        }
        lastNanos = now
      }
    }
  }
  return time
}
