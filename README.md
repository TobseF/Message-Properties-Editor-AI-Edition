# Resource Bundle Editor Fork

> **Why this fork?** JetBrains decided to drop the built-in Resource Bundle Editor and move it to obsolete plugins
> (see [IDEA-379833](https://youtrack.jetbrains.com/issue/IDEA-379833/Move-Resource-Bundle-editor-to-obsolete-plugins)).
> This repository continues active development and maintenance of the plugin so the community can keep using it on the latest IntelliJ Platform versions.

An IntelliJ IDEA plugin that provides a dedicated split-pane editor for Java `.properties` resource bundles (i18n).  
When your project has multiple locale files sharing the same base name (e.g. `messages.properties`, `messages_en.properties`, `messages_fr.properties`), the plugin lets you view and edit all translations for each property key side-by-side.


**Plugin ID:** `github.beansoft.properties.bundle.editor.fork`

---

## Features

- **Side-by-side translation editing** – Select a property key and see/edit its value in every locale at once
- **Structure View tree** – Hierarchical view of property keys with grouping by prefix separator
- **Incomplete property inspection** – Highlights keys missing translations in one or more locales (severity: ERROR)
- **Unused property detection** – Integrates with IntelliJ's unused property inspection
- **Quick fixes** – Ignore specific locales, remove unused properties from all locales
- **Navigate between locales** – "Go to Related" to jump between locale files or property declarations
- **Rename refactoring** – Rename property keys, prefix groups, or bundle base names from the editor
- **Add new property** – Insert a new key with optional positional placement
- **Propagate value** – Copy a value across all locale files from the context menu
- **Show only incomplete** – Filter the tree to show only untranslated properties

---

## Requirements

| Requirement | Version |
|---|---|
| IntelliJ IDEA | 2026.1+ (build 261 – 263.*) |
| JDK | 21+ |
| Gradle | 9.2.1 (included via wrapper) |

---

## Building

### Prerequisites

- **JDK 21** or later installed and available on your `PATH` (or set via `JAVA_HOME`)
- No global Gradle installation required – the project includes the Gradle wrapper

### Clone & Build

```bash
git clone https://github.com/beansoft/properties-resource-bundle-editor-fork.git
cd properties-resource-bundle-editor-fork

# Build the plugin (compiles, runs tests, and packages the plugin ZIP)
./gradlew buildPlugin
```

The distributable plugin ZIP will be generated at:

```
build/distributions/Properties Resource Bundle Editor-<version>.zip
```

### Useful Gradle Tasks

| Task | Description |
|---|---|
| `./gradlew buildPlugin` | Compile, test, and package the plugin into a ZIP |
| `./gradlew runIde` | Launch a sandboxed IntelliJ IDEA instance with the plugin installed |
| `./gradlew test` | Run unit tests only |
| `./gradlew verifyPlugin` | Verify plugin structure and compatibility against target IDE versions |
| `./gradlew clean` | Remove all build outputs |
| `./gradlew buildSearchableOptions` | Build searchable options for plugin settings |

### Running in a Sandbox IDE

To test the plugin in a development IntelliJ IDEA instance:

```bash
./gradlew runIde
```

This downloads the target IntelliJ IDEA version (build `261.22158.182`) and launches it with the plugin pre-installed.

---

## Installing Locally (from ZIP)

If you just want to install a locally-built plugin without publishing:

1. Build the plugin: `./gradlew buildPlugin`
2. Open IntelliJ IDEA → **Settings** → **Plugins** → ⚙️ gear icon → **Install Plugin from Disk…**
3. Select the ZIP file from `build/distributions/`
4. Restart the IDE

---

## Publishing to JetBrains Marketplace

### One-time Setup

1. **Create a JetBrains Marketplace account** at [plugins.jetbrains.com](https://plugins.jetbrains.com/)

2. **Generate a Permanent Token:**
   - Go to [My Tokens](https://plugins.jetbrains.com/author/me/tokens) on JetBrains Marketplace
   - Click **Generate Token**
   - Copy the token – you'll need it for publishing

3. **Add the token to your environment** (do NOT commit it to version control):

   ```bash
   # Option A: Environment variable (recommended)
   export PUBLISH_TOKEN="your-marketplace-token-here"

   # Option B: Add to ~/.gradle/gradle.properties (user-level, not project-level)
   echo "jetbrainsMarketplaceToken=your-marketplace-token-here" >> ~/.gradle/gradle.properties
   ```

4. **Add signing configuration** (required for Marketplace since 2021.2):

   Generate a key pair for plugin signing following the [JetBrains guide](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html):

   ```bash
   # Generate a private key
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out private.pem

   # Generate the certificate chain
   openssl req -new -x509 -key private.pem -out chain.crt -days 3650 \
     -subj "/CN=Resource Bundle Editor Fork"
   ```

   Add signing credentials to your environment or `~/.gradle/gradle.properties`:

   ```bash
   export PRIVATE_KEY_PATH="/path/to/private.pem"
   export CERTIFICATE_CHAIN_PATH="/path/to/chain.crt"
   export PRIVATE_KEY_PASSWORD=""  # leave empty if no passphrase
   ```

5. **Update `build.gradle.kts`** – add the signing and publishing configuration inside the `intellijPlatform` block:

   ```kotlin
   intellijPlatform {
       // ...existing pluginConfiguration and pluginVerification...

       signing {
           privateKey.set(providers.environmentVariable("PRIVATE_KEY_PATH").map { file(it).readText() })
           certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN_PATH").map { file(it).readText() })
           password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
       }

       publishing {
           token.set(providers.environmentVariable("PUBLISH_TOKEN"))
       }
   }
   ```

### Version Bump

Before each release, update the version in `build.gradle.kts`:

```kotlin
version = "2026.1.1"  // Increment as needed
```

And update `CHANGELOG.md` with the release notes:

```markdown
## 2026.1.1

🚀 Added
* New feature description

🪲 Fixed
* Bug fix description
```

### Build, Verify, Sign & Publish

```bash
# 1. Clean build
./gradlew clean

# 2. Build the plugin
./gradlew buildPlugin

# 3. Verify plugin compatibility (catches API issues before publishing)
./gradlew verifyPlugin

# 4. Sign the plugin (optional — publishPlugin signs automatically if configured)
./gradlew signPlugin

# 5. Publish to JetBrains Marketplace
./gradlew publishPlugin
```

Or run them all at once:

```bash
./gradlew clean verifyPlugin publishPlugin
```

After publishing, the plugin will appear on the [JetBrains Marketplace](https://plugins.jetbrains.com/) pending review (usually takes 1–2 business days for the first submission).

### Compatibility Verification

The `verifyPlugin` task checks for:
- **Internal API usages** – using private/internal IntelliJ APIs
- **Compatibility problems** – binary incompatibilities with target IDE versions

These are configured in `build.gradle.kts` under `pluginVerification.failureLevel`.

---

## Project Structure

See [ARCHITECTURE.md](ARCHITECTURE.md) for a detailed breakdown of the codebase, components, data flow, and design decisions.

```
src/                          → Plugin source (Java + Kotlin)
resources/META-INF/plugin.xml → Plugin descriptor
resources/messages/            → UI string bundles
test/                          → Unit tests
testData/                      → Test fixture data
build.gradle.kts              → Build configuration
```

---

## Development Tips

- **Hot-reload during development:** Use `./gradlew runIde` and make changes — rebuild with `Ctrl+F9` / `Cmd+F9` in the sandbox IDE
- **Debug the plugin:** Run `./gradlew runIde --debug-jvm` then attach a remote debugger on port 5005
- **Update target IDE version:** Change `intellijIdea("261.22158.182")` in `build.gradle.kts` and adjust `sinceBuild`/`untilBuild`
- **Add new UI strings:** Add entries to `resources/messages/ResourceBundleEditorBundle.properties` and reference them via `ResourceBundleEditorBundle.message("key")`

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.

---

## Links

- **Source code:** [github.com/beansoft/properties-resource-bundle-editor-fork](https://github.com/beansoft/properties-resource-bundle-editor-fork)
- **JetBrains Marketplace:** [plugins.jetbrains.com](https://plugins.jetbrains.com/) (search "Resource Bundle Editor Fork")
- **IntelliJ Platform Plugin SDK:** [plugins.jetbrains.com/docs/intellij](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- **Plugin Signing Guide:** [plugins.jetbrains.com/docs/intellij/plugin-signing.html](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)
