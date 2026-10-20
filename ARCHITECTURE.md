# Architecture – Resource Bundle Editor Fork

## Overview

This is an IntelliJ IDEA plugin that provides a dedicated split-pane editor for Java `.properties` resource bundles (i18n). When a project contains multiple locale-specific `.properties` files sharing the same base name (e.g., `messages.properties`, `messages_en.properties`, `messages_fr.properties`), the plugin opens them together in a unified editor, allowing developers to view and edit all translations for each property key side-by-side.

On top of that, the fork can generate missing translations with any OpenAI compatible endpoint (e.g. NanoGPT or LM Studio), see [AI Translation](#11-ai-translation).

The plugin is a community fork of the original JetBrains Resource Bundle Editor. It is distributed under the **Apache 2.0 License**.

**Plugin ID:** `github.tobsef.message.properties.editor.ai`  
**Vendor:** TobseF  
**Target Platform:** IntelliJ IDEA 2026.1+ (build 261–263.*)  
**JVM:** Java 21  
**Build System:** Gradle with IntelliJ Platform Plugin 2.13.1

---

## Project Structure

```
properties-resource-bundle-editor-fork/
├── build.gradle.kts              # Build configuration (IntelliJ Platform Gradle Plugin)
├── gradle.properties             # Gradle settings
├── settings.gradle.kts           # Root project name
├── CHANGELOG.md                  # Release notes (rendered to HTML via CommonMark)
│
├── resources/
│   ├── META-INF/
│   │   └── plugin.xml            # Plugin descriptor (extensions, actions, dependencies)
│   ├── messages/
│   │   ├── ResourceBundleEditorBundle.properties            # UI strings
│   │   └── ResourceBundleEditorDeprecatedMessagesBundle.properties  # Legacy fallback strings
│   ├── inspectionDescriptions/
│   │   └── IncompleteProperty.html   # Inspection description for "Property is incomplete"
│   └── pluginIcon.svg            # Plugin marketplace icon
│
├── src/
│   └── com/intellij/
│       ├── codeInspection/unused/
│       │   └── UnusedPropertyUtil.java         # Unused property detection in resource bundles
│       └── lang/properties/
│           ├── editor/                          # ★ Core editor module
│           │   ├── inspections/                 # Editor-specific inspections
│           │   │   ├── incomplete/              # "Incomplete Property" inspection
│           │   │   ├── InspectedPropertyProblems.java
│           │   │   ├── ResourceBundleEditorProblemDescriptor.java
│           │   │   └── ResourceBundleEditorRenderer.java
│           │   ├── ai/                          # ★ AI translation (settings, HTTP client, actions)
│           │   │   ├── AiTranslationSettings.java
│           │   │   ├── AiTranslationConfigurable.java
│           │   │   ├── AiTranslationClient.java
│           │   │   ├── AiTranslationPlan.java
│           │   │   ├── AiTranslator.java
│           │   │   ├── TranslateMissingAction.java
│           │   │   ├── TranslateAllMissingAction.java
│           │   │   └── OpenAiSettingsAction.java
│           │   └── ... (editor classes)
│           ├── refactoring/rename/              # Rename refactoring support
│           │   └── ResourceBundleFromEditorRenameHandler.java
│           └── util/                            # Utility classes
│               ├── MergingUpdateQueueFork.kt    # Forked MergingUpdateQueue for async batching
│               └── UpdateExecutor.kt            # Reflective access to Update.execute()
│
├── test/                         # Unit tests (JUnit 4, BasePlatformTestCase)
│   └── com/intellij/lang/properties/
│       ├── ResourceBundleTest.java
│       ├── ResourceBundleEditorInsertManagerTest.java
│       ├── IgnoredPropertiesFilesSuffixesTest.java
│       └── ResourceBundleGetFilesNullTest.java
│
└── testData/                     # Test fixture data for insert-manager tests
    └── insertManager/
        ├── addToAlphaOrdered/
        ├── addToOrdered/
        ├── addToUnordered/
        └── isAlphaSorted/
```

---

## Core Components

### 1. Plugin Descriptor (`plugin.xml`)

Declares the plugin's identity, dependencies, extensions, and actions:

| Extension Point | Implementation | Purpose |
|---|---|---|
| `fileEditorProvider` | `ResourceBundleEditorProvider` | Registers the custom editor for `.properties` bundles |
| `localInspection` | `IncompletePropertyInspection` | Highlights properties missing translations |
| `renameHandler` | `ResourceBundleFromEditorRenameHandler` | Enables rename refactoring from bundle editor |
| `gotoRelatedProvider` | `GotoResourceBundleLocalizationsProvider` | Navigate to other locale files |
| `gotoRelatedProvider` | `GotoPropertyDeclarationsProvider` | Navigate to property declarations across locales |
| `refactoring.copyHandler` | `PropertiesCopyHandlerEx` | Copy property keys across bundles |
| `applicationConfigurable` | `AiTranslationConfigurable` | Settings page *Tools \| Resource Bundle Editor AI Translation* |

**Actions:**
- `NewPropertyAction` – Add a new property key to the bundle
- `ChooseSubsequentPropertyValueEditorAction` (Next/Prev) – Navigate between locale editors
- `GotoNextIncompletePropertyAction` – Jump to next property missing translations
- `ResourceBundleEditorShowQuickFixesAction` – Show intention actions

### 2. Editor Provider (`ResourceBundleEditorProvider`)

Implements `FileEditorProvider` + `DumbAware`. Decides whether to show the bundle editor tab:

- **Acceptance criteria:** The file is a `.properties` or XML properties file, and its resource bundle contains **more than one** locale file.
- **Policy:** `PLACE_AFTER_DEFAULT_EDITOR` – appears as a secondary tab alongside the default text editor.
- Creates `ResourceBundleEditor` instances.

### 3. Main Editor (`ResourceBundleEditor`)

The central class (~1100 lines). Implements `DocumentsEditor` providing a full custom file editor experience.

**UI Layout:**
```
┌──────────────────────────────────────────────────┐
│  JBSplitter (horizontal)                          │
│ ┌──────────────┬─────────────────────────────────┐│
│ │ Structure    │ ┌─────────────────────────────┐ ││
│ │ View Panel   │ │ AI toolbar                   │ ││
│ │              │ ├─────────────────────────────┤ ││
│ │ (tree of     │ │ messages.properties       [x]│ ││
│ │  property    │ │ [Editor]                     │ ││
│ │  keys)       │ ├─────────────────────────────┤ ││
│ │              │ │ messages_en.properties    [x]│ ││
│ │              │ │ [Editor]                     │ ││
│ │              │ ├─────────────────────────────┤ ││
│ │              │ │ messages_fr.properties    [x]│ ││
│ │              │ │ [Editor]                     │ ││
│ │              │ └─────────────────────────────┘ ││
│ └──────────────┴─────────────────────────────────┘│
└──────────────────────────────────────────────────┘
```

**Key responsibilities:**
- **Left panel:** `StructureViewComponent` displaying all property keys in a tree (with grouping by prefix separator)
- **Right panel:** A toolbar with the AI actions, below it one `EditorEx` instance per locale file, each showing the value for the selected property key. Each locale panel has a "x" button (`clearTranslation()`) that removes the translation (in the default file only the value is emptied, since deleting the key would remove it from the whole bundle).
- **Selection sync:** Selecting a key in the tree loads values into all editors; switching from a text editor syncs the caret position back
- **Write-back:** On editor focus loss or key change, the modified value is written back to the `.properties` file via `ResourceBundlePropertiesUpdateManager`
- **Propagate value:** Context menu action to copy a value to all locales
- **Keep empty values:** Toggle to suppress creation/deletion of empty property values
- **AI translation:** `translateMissingTranslations()` / `translateAllMissingTranslations()` orchestrate the translation (see below)
- **File listener:** `ResourceBundleEditorFileListener` monitors VFS events for file creation, deletion, renames, and content changes

### 4. Structure View (`ResourceBundleStructureViewModel` + related)

Provides the tree model for the left panel:

| Class | Role |
|---|---|
| `ResourceBundleStructureViewModel` | Tree model: grouping, sorting, filtering |
| `ResourceBundleFileStructureViewElement` | Root element representing the bundle |
| `PropertyBundleEditorStructureViewElement` | Leaf element for each property key; holds inspection problems |
| `ResourceBundleStructureViewComponent` | The `StructureViewComponent` subclass with toolbar, popup, data providers |
| `ResourceBundleEditorRenderer` | Custom tree cell renderer with error highlighting |

**Features:**
- **Group by word prefix** (e.g., `app.title` and `app.description` grouped under `app`)
- **Alpha sorting** toggle
- **Show only incomplete** filter – hides fully-translated properties
- **Inspection overlay** – tree nodes display error/warning text attributes from inspections

### 5. Inspections

#### Incomplete Property Inspection (`IncompletePropertyInspection`)
- **Short name:** `IncompleteProperty`
- **Severity:** ERROR (enabled by default)
- Checks each property key against all locale files; if any locale is missing a translation (excluding ignored suffixes), it flags the property
- **Quick fix:** `IgnoreLocalesQuickFix` – lets users mark locale suffixes to ignore
- Options panel (`IncompletePropertyInspectionOptionsPanel`) for managing ignored suffixes

#### Unused Property Detection (`UnusedPropertyUtil`)
- Integrates with the platform's `UnusedPropertyInspection`
- Builds a visitor that checks if each property is referenced in code
- **Quick fix:** `RemovePropertiesFromAllLocalesFix` – deletes the property from all locale files

### 6. Background Highlighter (`ResourceBundleEditorHighlighter`)

Implements `BackgroundEditorHighlighter` to run inspections in the background:
- Creates `ResourceBundleEditorHighlightingPass` which iterates over all tree elements
- Runs both incomplete-property and unused-property visitors
- Stores `InspectedPropertyProblems` on each tree element for rendering

### 7. File Change Listener (`ResourceBundleEditorFileListener`)

A Kotlin class monitoring `VirtualFileListener` events:
- Uses a custom `MergingUpdateQueueFork` to debounce rapid file changes (200ms merge window)
- Handles file creation/deletion (recreates editor panel), content changes (reloads editors), and property renames
- Ensures editor viewer state reflects file writability

### 8. VFS/Update Queue Utilities

- **`MergingUpdateQueueFork`** – A forked version of IntelliJ's `MergingUpdateQueue` with coroutine-based async execution. Provides debounced, batched execution of updates.
- **`UpdateExecutor`** – Uses reflection to access `Update.execute()` and `executeInWriteAction` field for compatibility.

### 9. Navigation & Refactoring

| Class | Purpose |
|---|---|
| `GotoResourceBundleLocalizationsProvider` | "Go to Related" from a `.properties` file shows other locale files |
| `GotoPropertyDeclarationsProvider` | "Go to Related" from bundle editor shows property across all locales |
| `ResourceBundleFromEditorRenameHandler` | Rename property keys, prefix groups, or bundle base name from the editor |
| `PropertiesCopyHandlerEx` | Handles copy operations and refreshes the bundle editor |

### 10. Actions

| Action | Description |
|---|---|
| `NewPropertyAction` | Shows dialog to add a new key; supports inserting after selected property |
| `ChooseSubsequentPropertyValueEditorAction` | Next/Prev navigation between locale editor fields |
| `GotoNextIncompletePropertyAction` | Cycles to the next incomplete (untranslated) property |
| `ResourceBundleEditorShowQuickFixesAction` | Shows available quick fixes for the selected property |
| `ResourceBundleEditorKeepEmptyValueToggleAction` | Toggles whether empty values are preserved or deleted |
| `TranslateMissingAction` | Translates the missing locales of the selected key with AI |
| `TranslateAllMissingAction` | Translates all missing translations of the whole bundle with AI |
| `OpenAiSettingsAction` | Opens the AI translation settings page |

The three AI actions are not registered in `plugin.xml`. They are instantiated per editor (they hold a reference to their `ResourceBundleEditor`) and added to the toolbar above the translation fields and to the editor context menu.

### 11. AI Translation

Package `com.intellij.lang.properties.editor.ai`. Talks to any OpenAI compatible server (`/models`, `/chat/completions`), with NanoGPT and LM Studio as the primary targets.

| Class | Role |
|---|---|
| `AiTranslationSettings` | Application service (`PersistentStateComponent`, stored in `resourceBundleEditorAi.xml`): base URL, model, main language, timeout, parallel requests (1–16, default 1), extra instructions. The API key lives in the IDE `PasswordSafe` and is cached in memory; reading it may block, so never on the EDT |
| `AiTranslationConfigurable` | Settings page under *Tools*: provider presets (LM Studio, NanoGPT, OpenAI, Custom), editable model combo with *Load Models*, and a *Test Connection* button that calls `/models` and sends a tiny chat request. Network calls run on pooled threads and use the values currently typed in the form, not the saved ones |
| `AiTranslationClient` | Thin `java.net.http.HttpClient` wrapper with Gson. Polls the async future so progress cancellation works. Strips `<think>` blocks and code fences from replies; surfaces the server's error message |
| `AiTranslationPlan` | Per property key: picks the source text and the target locales (see below). Must be built under a read action |
| `AiTranslator` | Builds the system and user prompt for one key and one target language (keeps placeholders, tags and whitespace) and appends the user's extra instructions |
| `TranslateMissingAction`, `TranslateAllMissingAction`, `OpenAiSettingsAction` | Toolbar and context menu entry points |

**Source and target selection (`AiTranslationPlan.build`):**
1. Source text: the file whose language equals the configured main language, if it has a non-blank value; otherwise the default file; otherwise the first file with a value.
2. Targets: every locale file other than the source whose value for the key is missing or blank. The default file is never a target because its language is unknown.

Example: DE and EN exist, main language is DE, PL and NL are missing, so both are translated from the DE text.

**Flow (`ResourceBundleEditor.runTranslation`):**
```
Action (single key or whole bundle)
    │  flush unsaved text of the focused field, build AiTranslationPlan(s)
    │  bundle-wide: confirmation dialog with number of properties / requests
    ▼
Task.Backgroundable (cancellable)
    │  fixed thread pool, size = "parallel requests" setting
    │  one job per (key, target language) ──► AiTranslator ──► AiTranslationClient
    │  results are consumed in submission order; first error or cancel stops the pool
    ▼
onFinished() on the EDT        (also after error / cancel, so finished results are kept)
    │  preparePsiElementsForWrite + one WriteCommandAction (single undo step)
    ▼
ResourceBundlePropertiesUpdateManager.insertOrUpdateTranslation() per result
    │
    ▼
updateEditorsFromProperties()
```

Notes:
- Values are exchanged in the editor representation (`getPropertyEditorValue`), i.e. unescaped, and written back through the same update manager as manual edits, so escaping is handled in one place.
- Requests only ever go to the configured base URL; there is no other network access.

---

## Build & Dependencies

**Build tool:** Gradle with Kotlin DSL  
**Plugins:**
- `java`
- `org.jetbrains.kotlin.jvm` 2.3.0
- `org.jetbrains.intellij.platform` 2.13.1

**Platform dependency:** IntelliJ IDEA `261.22158.182`  
**Bundled plugin dependency:** `com.intellij.properties`  
**Test framework:** JUnit 4 + IntelliJ Platform Test Framework

**AI translation:** uses only JDK `java.net.http` and Gson (both available on the IDE classpath), so no extra Gradle dependencies are needed.

**Changelog rendering:** Uses [CommonMark](https://github.com/commonmark/commonmark-java) 0.22.0 to convert `CHANGELOG.md` to HTML for the plugin description.

---

## Data Flow

```
User selects property key in Structure View tree
    │
    ▼
ResourceBundleEditor.selectionChanged()
    │
    ▼
updateEditorsFromProperties() ──► For each locale:
    │                              │
    │                              ▼
    │                         Find IProperty by key in PropertiesFile
    │                              │
    │                              ▼
    │                         Update EditorEx document text
    │
User edits value in an EditorEx
    │
    ▼
FocusChangeListener.focusLost()
    │
    ▼
writeEditorPropertyValue()
    │
    ▼
ResourceBundlePropertiesUpdateManager.insertOrUpdateTranslation()
    │
    ▼
PropertiesFile modified (triggers VFS event)
    │
    ▼
ResourceBundleEditorFileListener ──► MergingUpdateQueueFork (debounce)
    │
    ▼
Editor panel refreshed if needed
```

---

## Testing Strategy

Tests extend `BasePlatformTestCase` from the IntelliJ test framework:

| Test Class | Focus |
|---|---|
| `ResourceBundleTest` | Default file resolution, renaming bundle files, renaming property keys |
| `ResourceBundleEditorInsertManagerTest` | Property insertion ordering (alpha-sorted, ordered, unordered) |
| `IgnoredPropertiesFilesSuffixesTest` | Incomplete property inspection with ignored locale suffixes |
| `ResourceBundleGetFilesNullTest` | Null safety when resource bundle is invalidated |

The AI translation package has no automated tests yet. Candidates are `AiTranslationPlan.build` (source selection) and `AiTranslationClient.cleanup`, which can be tested without a network.

Test data is in `testData/insertManager/` with before/after `.properties` file pairs.

