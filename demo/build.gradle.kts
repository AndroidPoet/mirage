// Designed and developed by 2026 androidpoet (Ranbir Singh)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import io.androidpoet.mirage.Configuration
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }
  jvm()
  listOf(
    iosArm64(),
    iosSimulatorArm64(),
  ).forEach { it.binaries.framework { baseName = "MirageDemo"; isStatic = true } }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":mirage"))
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.ui)
    }
    androidMain.dependencies {
      implementation(libs.androidx.activity.compose)
    }
    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.kotlinx.coroutines.swing)
    }
  }
}

android {
  namespace = "io.androidpoet.miragedemo"
  compileSdk = Configuration.COMPILE_SDK

  defaultConfig {
    applicationId = "io.androidpoet.miragedemo"
    minSdk = Configuration.MIN_SDK
    targetSdk = Configuration.COMPILE_SDK
    versionCode = 1
    versionName = Configuration.VERSION
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  lint {
    abortOnError = false
  }
}

compose.desktop {
  application {
    mainClass = "io.androidpoet.miragedemo.MainKt"
    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "MirageDemo"
      packageVersion = "1.0.0"
    }
  }
}
