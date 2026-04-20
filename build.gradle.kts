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
            <p>Android Strings Manager — tiện ích quản lý <code>strings.xml</code> cho dự án Android:</p>
            <ul>
              <li><b>Clean</b>: xoá các entry <code>translatable="false"</code> lẫn trong <code>values-*/strings.xml</code>.</li>
              <li><b>Export missing</b>: xuất CSV các key bị thiếu dịch theo locale.</li>
              <li><b>Prune unused</b>: quét source tìm <code>R.string.X</code>/<code>@string/X</code>, liệt kê hoặc xoá các key không dùng.</li>
            </ul>
            """.trimIndent()
        }
    }
}
