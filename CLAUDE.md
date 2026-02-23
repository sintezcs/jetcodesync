# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a JetBrains IntelliJ Platform plugin project called "jetcodesync" built with Kotlin and Gradle. It uses the IntelliJ Platform Plugin Template and targets IntelliJ Platform 2025.2.5+ (build 252+).

**Current Status**: This is a template-based plugin with sample/placeholder code. The actual plugin functionality has not yet been implemented.

## Build & Development Commands

### Building
```bash
./gradlew build                    # Build and test the plugin
./gradlew buildPlugin              # Build plugin ZIP for deployment
./gradlew jar                      # Assemble plugin JAR
```

### Running & Testing
```bash
./gradlew runIde                   # Launch IDE with plugin for manual testing
./gradlew test                     # Run unit tests
./gradlew runIdeForUiTests         # Run IDE with UI test configuration
./gradlew testIdePerformance       # Run performance tests
```

### Plugin Verification
```bash
./gradlew verifyPlugin             # Check binary compatibility with target IDEs
./gradlew verifyPluginProjectConfiguration  # Validate plugin project setup
./gradlew verifyPluginStructure    # Validate plugin.xml and archive structure
```

### Publishing
```bash
./gradlew patchChangelog           # Update changelog from CHANGELOG.md
./gradlew publishPlugin            # Publish to JetBrains Marketplace (requires PUBLISH_TOKEN)
```

### Code Quality
```bash
./gradlew koverReport              # Generate test coverage reports (XML output enabled)
```

## Project Structure

### Key Configuration Files
- `gradle.properties`: Plugin metadata, version, platform version, and build configuration
- `src/main/resources/META-INF/plugin.xml`: Plugin descriptor defining extensions, dependencies, and tool windows
- `build.gradle.kts`: Build configuration using IntelliJ Platform Gradle Plugin
- `CHANGELOG.md`: Version history (extracted during build for plugin marketplace)

### Source Organization
```
src/main/kotlin/com/github/sintezcs/jetcodesync/
├── services/          # Project-level services (e.g., MyProjectService)
├── toolWindow/        # Tool window factories and UI components
├── startup/           # Project startup activities
└── MyBundle.kt        # Internationalization message bundle accessor

src/main/resources/
├── META-INF/plugin.xml           # Plugin configuration
└── messages/MyBundle.properties   # Localized messages

src/test/kotlin/      # Unit tests
```

### Architecture Components

**Services**: IntelliJ services are registered in `plugin.xml` and accessed via `project.service<ServiceClass>()`. Services can be PROJECT, APPLICATION, or MODULE level.

**Tool Windows**: Registered in `plugin.xml` under `<extensions>`. Factory classes implement `ToolWindowFactory` and create UI content using Swing components (JBPanel, JBLabel, etc.).

**Startup Activities**: Classes implementing `ProjectActivity` are executed when a project opens. Register them in `plugin.xml` with `<postStartupActivity>`.

**Resource Bundles**: Messages are externalized using `MyBundle.message("key", args...)` for internationalization support.

## Plugin Development Notes

### Extension Points
The plugin currently registers:
- Tool Window: `MyToolWindowFactory` (ID: "MyToolWindow")
- Startup Activity: `MyProjectActivity`

New extensions should be added to `plugin.xml` under the `<extensions>` section.

### Platform Version
- Target Platform: IntelliJ IDEA 2025.2.5
- Minimum Build: 252
- JVM Toolchain: Java 21
- Kotlin: Used with stdlib explicitly excluded (set in gradle.properties)

### Template Cleanup Required
The current codebase contains sample template code with warnings to remove non-needed files. Before implementing actual functionality:
1. Remove or replace sample files: `MyProjectService`, `MyToolWindowFactory`, `MyProjectActivity`
2. Update package structure in `gradle.properties` and throughout codebase
3. Update plugin description in README.md (between `<!-- Plugin description -->` tags)
4. Set proper `MARKETPLACE_ID` after first publication

### Gradle Configuration
- Configuration cache: ENABLED
- Build cache: ENABLED
- Gradle version: 9.2.1
- Uses version catalog for dependency management

### Testing
- Test framework: IntelliJ Platform Test Framework
- Dependencies: JUnit, opentest4j
- UI tests run on custom IDE instance (port 8082) with robot-server-plugin

### Plugin Signing & Publishing
Requires environment variables:
- `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`: For plugin signing
- `PUBLISH_TOKEN`: For marketplace publication
- Release channels determined by version suffix (e.g., "alpha", "beta", or "default")
