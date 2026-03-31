# Skill – Resource Bundle Editor IntelliJ Plugin Development

## Domain

IntelliJ IDEA plugin development for Java i18n (internationalization) resource bundle editing. This plugin provides a dedicated editor for `.properties` resource bundles, allowing side-by-side editing of all locale translations for each property key.

## When to Use

Use this skill when:
- Developing, modifying, or extending the Resource Bundle Editor plugin
- Working with IntelliJ Platform Plugin SDK for custom file editors
- Implementing inspections, quick fixes, or refactoring handlers within the IntelliJ plugin framework
- Building UI with IntelliJ Platform's Swing-based components (Structure View, Editor, Splitters, Action System)
- Handling Java `.properties` file resource bundles and i18n workflows
- Working with VFS (Virtual File System) listeners and PSI (Program Structure Interface) tree change events

## Technology Stack

| Technology | Version / Details |
|---|---|
| **Language** | Java 21 + Kotlin 2.3.0 |
| **Platform** | IntelliJ IDEA 2026.1 (build 261+) |
| **Build** | Gradle (Kotlin DSL) with `org.jetbrains.intellij.platform` plugin 2.13.1 |
| **Dependency** | Bundled `com.intellij.properties` plugin |
| **Testing** | JUnit 4 + IntelliJ Platform Test Framework (`BasePlatformTestCase`) |
| **License** | Apache 2.0 |

## Key Concepts

### IntelliJ Plugin Architecture

1. **`plugin.xml`** is the entry point. It declares:
   - Plugin ID, dependencies, and compatible IDE version ranges (`sinceBuild`/`untilBuild`)
   - Extension points: `fileEditorProvider`, `localInspection`, `renameHandler`, `gotoRelatedProvider`, `refactoring.copyHandler`
   - Actions with shortcut bindings and group placements
   - Resource bundles for i18n of the plugin's own UI strings

2. **`FileEditorProvider`** (`ResourceBundleEditorProvider`):
   - `accept()` determines when the editor tab appears: file must be `.properties` or XML properties, and its resource bundle must have **more than one** locale file
   - `acceptRequiresReadAction()` returns `false` for quick checks; the heavy check runs inside `ReadAction.compute()`
   - `getPolicy()` returns `PLACE_AFTER_DEFAULT_EDITOR` so the bundle view is a secondary tab
   - `createEditor()` instantiates `ResourceBundleEditor`

3. **Custom `FileEditor`** (`ResourceBundleEditor`):
   - Extends `UserDataHolderBase` and implements `DocumentsEditor`
   - Uses `JBSplitter` for the left (Structure View) and right (values) panels
   - Creates one `EditorEx` per locale file using `EditorFactory`
   - Manages selection sync between the tree and the editors
   - Implements state save/restore via `ResourceBundleEditorState`
   - The editor is disposable and cleans up all sub-editors on `dispose()`

### Properties File Handling

- **`ResourceBundle`**: IntelliJ's model grouping all `.properties` files sharing a base name (e.g., `messages.properties`, `messages_en.properties`)
- **`PropertiesFile`**: Represents a single `.properties` file, providing access to key-value pairs as `IProperty` objects
- **`PropertiesImplUtil`**: Utility for resolving `PsiFile` to `PropertiesFile`, finding properties by key, etc.
- **`ResourceBundlePropertiesUpdateManager`**: Manages insertion and update of properties, respecting existing order (alphabetical, original order, or unordered)
- **`PropertiesResourceBundleUtil`**: Conversion between raw property values and editor-friendly representations (handles escaping)

### Structure View

- **`ResourceBundleStructureViewModel`**: Implements `PropertiesGroupingStructureViewModel` with:
  - `GroupByWordPrefixes` grouper for hierarchical key display (e.g., `app.title` → `app` → `title`)
  - `Sorter.ALPHA_SORTER` for alphabetical ordering
  - "Show only incomplete" filter
- **`ResourceBundleFileStructureViewElement`**: Root node; computes children as `PropertyBundleEditorStructureViewElement` nodes
- **`PropertyBundleEditorStructureViewElement`**: Leaf node per property key; carries `InspectedPropertyProblems` for highlighting
- **`ResourceBundleStructureViewComponent`**: Customizes toolbar (add property, settings, help) and popup menu; provides `DataSink` for delete, copy, usage targets

### Inspections System

1. **`IncompletePropertyInspection`** (local inspection, unfair):
   - Checks if every locale file has a translation for each key
   - Allows configuring ignored suffixes (e.g., ignore `_ru` locale)
   - Quick fix opens a dialog to select locale suffixes to exclude
   - Settings persisted via XML serialization (`readSettings`/`writeSettings`)

2. **`UnusedPropertyUtil`**:
   - Wraps IntelliJ's built-in `UnusedPropertyInspection`
   - Checks if a property is actually referenced in Java/Kotlin code
   - Quick fix deletes the property from all locale files

3. **`ResourceBundleEditorHighlighter`**:
   - Implements `BackgroundEditorHighlighter`; creates `HighlightingPass` instances
   - Runs both inspections in background, storing results on tree elements
   - Results are rendered via `ResourceBundleEditorRenderer` with colored text attributes

### Event Handling & Concurrency

- **`ResourceBundleEditorFileListener`** (Kotlin): Listens to VFS events (file create/delete/rename/content change)
- Uses **`MergingUpdateQueueFork`** (a forked `MergingUpdateQueue`):
  - Debounces rapid events with a 200ms merge window
  - Executes updates using coroutine-based async processing
  - Read actions compute what changes are needed; EDT dispatches UI updates
- **`UpdateExecutor`**: Reflective utility to invoke `Update.execute()` for compatibility with internal API changes

### Actions & Navigation

- **`NewPropertyAction`**: Dialog-based property creation with optional "insert after selected" for sorted bundles
- **`ChooseSubsequentPropertyValueEditorAction`**: Keyboard navigation (Prev/Next) between locale editor fields using `UserData` keys linking editors
- **`GotoNextIncompletePropertyAction`**: Wraps `ResourceBundleEditor.selectNextIncompleteProperty()`
- **`ResourceBundleEditorShowQuickFixesAction`**: Collects all quick fixes from inspected problems and shows them in an `IntentionListStep` popup
- **`GotoPropertyDeclarationsProvider`** / **`GotoResourceBundleLocalizationsProvider`**: "Go to Related" navigation
- **`ResourceBundleFromEditorRenameHandler`**: Supports renaming property keys, prefix groups, and bundle base names

## Coding Conventions

### Java
- Use `@NotNull` / `@Nullable` annotations from `org.jetbrains.annotations`
- Use IntelliJ's `Logger` (`Logger.getInstance(MyClass.class)`) for logging
- Follow IntelliJ coding style: `my` prefix for instance fields (e.g., `myProject`, `myEditors`)
- Use `WriteCommandAction.runWriteCommandAction()` for PSI modifications
- Use `ApplicationManager.getApplication().runWriteAction()` for write-lock contexts
- Use `ReadAction.compute()` for read-lock contexts

### Kotlin
- Use coroutine-based async patterns (`kotlinx.coroutines`)
- Use `readAction { }` and `withContext(Dispatchers.EDT)` for thread safety
- Prefer `AtomicReference` with `ConcurrentCollectionFactory` for concurrent data structures

### Testing
- Extend `BasePlatformTestCase`
- Use `myFixture.addFileToProject()` to set up test files
- Use `myFixture.configureByFile()` + `myFixture.checkResultByFile()` for before/after tests
- Test data files go in `testData/` with `-after` suffix for expected results

## File Organization Rules

| Directory | Contents |
|---|---|
| `src/com/intellij/lang/properties/editor/` | Core editor classes, actions, structure view |
| `src/com/intellij/lang/properties/editor/inspections/` | Editor-level inspections and renderers |
| `src/com/intellij/lang/properties/editor/inspections/incomplete/` | Incomplete property inspection |
| `src/com/intellij/lang/properties/refactoring/rename/` | Rename handlers |
| `src/com/intellij/lang/properties/util/` | Utility classes (queue, executor) |
| `src/com/intellij/codeInspection/unused/` | Unused property detection utility |
| `resources/META-INF/` | `plugin.xml` |
| `resources/messages/` | `.properties` files for plugin UI strings |
| `resources/inspectionDescriptions/` | HTML descriptions for inspections |
| `test/` | JUnit test classes |
| `testData/` | Test fixture files |

## Common Patterns

### Adding a New Action
1. Create a class extending `AnAction` in the `editor` package
2. Override `actionPerformed()`, `update()`, and `getActionUpdateThread()`
3. Register in `plugin.xml` under `<actions>` with optional `use-shortcut-of`
4. Add display text to `ResourceBundleEditorBundle.properties`

### Adding a New Inspection
1. Create inspection class extending `LocalInspectionTool` in `inspections/`
2. Implement `buildPropertyGroupVisitor()` returning a `Function<IProperty[], ResourceBundleEditorProblemDescriptor[]>`
3. Register in `plugin.xml` as `<localInspection>`
4. Add HTML description in `resources/inspectionDescriptions/`
5. Wire into `ResourceBundleEditorHighlighter` to run in background

### Modifying the Editor UI
- The editor layout lives in `ResourceBundleEditor.recreateEditorsPanel()`
- Each locale gets a `JPanel` with `TitledBorder` containing an `EditorEx`
- Editor settings (line numbers, margin, highlighter) configured in `reinitSettings()`
- Context menu defined in the `installPopupHandler()` section

### Working with Properties Files
```java
// Get PropertiesFile from VirtualFile
PropertiesFile pf = PropertiesImplUtil.getPropertiesFile(virtualFile, project);

// Find a property by key
IProperty prop = propertiesFile.findPropertyByKey("my.key");

// Get all files in a bundle
List<PropertiesFile> files = resourceBundle.getPropertiesFiles();

// Insert or update a translation
updateManager.insertOrUpdateTranslation("key", "value", propertiesFile);
```

## Gotchas & Notes

- The `MergingUpdateQueueFork` is a **forked** internal IntelliJ class because the original is not public API. It uses reflection in `UpdateExecutor` to access private fields. This is fragile and may break across IDE versions.
- `IncompletePropertyInspection` is marked as `UnfairLocalInspectionTool`, meaning it doesn't run via the normal local inspection pass; it only runs through the `ResourceBundleEditorHighlighter`.
- The `ResourceBundleEditorBundle` class has a fallback mechanism: if a key is not found in the main bundle, it falls back to `ResourceBundleEditorDeprecatedMessagesBundle`.
- Editor viewer mode (`editor.setViewer(true)`) is toggled based on file writability and `ReadonlyStatusHandler`.
- The plugin depends on the **bundled** `com.intellij.properties` plugin, not a standalone dependency. This is declared in both `plugin.xml` (`<depends>`) and `build.gradle.kts` (`bundledPlugin()`).

