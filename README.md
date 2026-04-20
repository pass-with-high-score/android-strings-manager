# Android Strings Manager

IntelliJ / Android Studio plugin for managing `res/values*/strings.xml` in Android projects.

## Features

- **Clean Non-Translatable From Locales** — remove keys marked `translatable="false"` in the default locale from every `values-*/strings.xml`.
- **Export Missing Translations (CSV)** — export a `locale,name,default_value` CSV listing keys present in `values/strings.xml` but missing in each locale.
- **Find Unused Strings...** — scan `.kt` / `.java` / `.xml` files across project content roots for `R.string.X` and `@string/X` references, list unreferenced keys, and (optionally) delete them from `values/` and every `values-*/strings.xml`.

## Installation

### From zip

1. Download / build `build/distributions/android-strings-manager-<ver>.zip`.
2. In Android Studio (or IntelliJ IDEA): **Settings → Plugins → ⚙ → Install Plugin from Disk...** → pick the zip.
3. Restart the IDE.

### Build from source

```bash
git clone https://github.com/pass-with-high-score/android-strings-manager.git
cd android-strings-manager
./gradlew buildPlugin
# zip is produced in build/distributions/
```

## Usage

After installation, open **Tools → Android Strings Manager** or right-click the `res/` directory in the Project view:

- **Clean Non-Translatable From Locales** — runs immediately.
- **Export Missing Translations (CSV)** — prompts for a CSV save location.
- **Find Unused Strings...** — opens a dialog listing unreferenced keys. Keep ticks on the keys you want to delete, untick anything you want to preserve, then click **Delete selected**.

If the action is invoked from a file or folder outside a `res/` tree, the plugin opens a directory chooser so you can select the `res/` folder manually (it must contain `values/strings.xml`).

## Notes

- **XML formatting**: on save, the plugin reads the IDE Code Style indent for the target file (falling back to 4 spaces). The first Clean/Prune run may produce a wider diff than expected if the original file used a different format.
- **Prune uses regexes** `R\.string\.X` and `@string/X`. Dynamic references such as `resources.getIdentifier("name", "string", ...)` or runtime-built resource names are **not** detected — always review the list before deleting.
- Only top-level `<string>`, `<string-array>`, and `<plurals>` elements inside `strings.xml` under `values/` and `values-*/` are touched.

## Compatibility

- IntelliJ Platform 2025.2+
- Android Studio (any build based on IntelliJ 2025.2+)
- No Android SDK module dependency — only `com.intellij.modules.platform`.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
