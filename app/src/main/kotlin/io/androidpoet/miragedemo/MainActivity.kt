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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.androidpoet.miragedemo.ui.theme.MirageDemoTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MirageDemoTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          ShaderGallery()
        }
      }
    }
  }
}

@Composable
private fun ShaderGallery() {
  var selected by remember { mutableStateOf<DemoEntry?>(null) }
  val current = selected

  if (current != null) {
    BackHandler { selected = null }
    Box(Modifier.fillMaxSize()) {
      current.content()
      Text(
        text = current.name,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier =
        Modifier
          .align(Alignment.TopStart)
          .safeDrawingPadding()
          .padding(16.dp)
          .background(Color(0x99000000), RoundedCornerShape(8.dp))
          .padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize().safeDrawingPadding(),
      contentPadding = PaddingValues(16.dp),
    ) {
      items(Registry.all, key = { it.name }) { entry ->
        Box(
          Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { selected = entry },
        ) {
          entry.content()
          Text(
            text = entry.name,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier =
            Modifier
              .align(Alignment.BottomStart)
              .padding(12.dp)
              .background(Color(0x99000000), RoundedCornerShape(6.dp))
              .padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }
    }
  }
}
