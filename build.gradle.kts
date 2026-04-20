import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2025.2.6.1")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        description = providers.provider {
            """
            <p>Android Strings Manager — utilities for managing <code>strings.xml</code> in Android projects:</p>
            <ul>
              <li><b>Clean</b>: remove entries marked <code>translatable="false"</code> from every <code>values-*/strings.xml</code>.</li>
              <li><b>Export missing</b>: export a CSV of keys missing a translation in each locale.</li>
              <li><b>Prune unused</b>: scan source for <code>R.string.X</code> / <code>@string/X</code> references and list or delete unreferenced keys.</li>
            </ul>
            <p>Invoke from <b>Tools → Android Strings Manager</b> or by right-clicking a <code>res/</code> folder in the Project view.</p>
            """.trimIndent()
        }
    }

    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
        channels = providers.environmentVariable("JETBRAINS_MARKETPLACE_CHANNEL")
            .orElse("default")
            .map { listOf(it) }
    }
}
