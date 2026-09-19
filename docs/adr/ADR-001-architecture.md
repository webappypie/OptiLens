# ADR-001: Modular Clean Architecture and Core Technical Foundations

## Status
Accepted

## Context
OptiLens is a modern Android computational photography camera application targeting high-performance real-time processing, multi-frame acquisition, low-latency previews, and AI enhancements. To prevent monolithic degradation, maintain clean separation of concerns, allow independent modular compilation, and enforce safety and privacy invariants, a solid foundation architecture must be established before any camera hardware or image processing code is written.

## Decision

### 1. Modularization Strategy
The codebase is structured into feature and foundational modules under a multi-module Gradle architecture:
- `:app`: Application entry point, Hilt dependency graph assembly, root navigation shell host, global Application lifecycle.
- `:core:common`: Core primitives, Coroutine dispatcher abstraction (`AppDispatchers`), typed error model (`OptiError`), typed result model (`OptiResult<T>`), feature flag contracts (`FeatureFlags`), and build diagnostics (`BuildInfo`).
- `:core:logging`: Structured logging abstraction (`AppLogger`) with privacy invariants (no image bytes, no OCR results, no GPS, no biometrics) and build-variant aware bindings (`LogcatLogger` in debug, `NoOpLogger` in release with zero runtime overhead).
- `:core:settings`: Reactive persistent settings layer built on AndroidX DataStore Preferences (`AppSettings`), exposing `Flow<T>` for all user preferences.
- `:core:navigation`: Centralized, decoupled type-safe navigation contracts (`AppDestination`, `NavigationManager`, `AppNavHost`) based on Navigation Compose 2.10+ and Kotlin Serialization.
- `:core:ui`: Base UI contracts, immutable state models (`UiState<T>`), and foundational ViewModel abstraction (`BaseViewModel<S>`).
- `:core:camera`: Camera hardware capability models (`CameraCapability`) and hardware controller interface (`CameraController`) stubs, isolating hardware-level camera logic.
- `:core:imaging`: Computational imaging request/result models (`ProcessingRequest`, `ProcessingResult`) and pipeline interface (`ImagingPipeline`) stubs.

### 2. Dependency Injection
- **Framework**: Dagger Hilt with Kotlin Symbol Processing (KSP).
- **Scope Invariants**:
  - `AppDispatchers`, `DataStore<Preferences>`, `AppSettings`, `AppLogger`, `NavigationManager`, `CameraController`, `ImagingPipeline` are scoped to `@Singleton`.
  - Feature ViewModels utilize Hilt `@HiltViewModel`.

### 3. Concurrency and Threading
- Direct usage of hardcoded `Dispatchers.IO` or `Dispatchers.Default` is forbidden across business logic and ViewModels.
- All coroutine dispatching is routed via `AppDispatchers`, allowing deterministic, synchronous test execution using `TestAppDispatchers` / `UnconfinedTestDispatcher`.

### 4. Error Handling and State Modeling
- Exceptions are never propagated unhandled across layer boundaries.
- Domain and data operations return `OptiResult<T>` (`Success`, `Loading`, `Error`).
- UI layers represent screen status using immutable `UiState<T>` (`Idle`, `Loading`, `Success`, `Error`).

### 5. Logging and Privacy
- Logs must never contain PII, raw sensor or bitmap byte buffers, recognized text, or precise location coordinates.
- In release builds, `NoOpLogger` is bound, allowing R8 to strip or inline log invocations completely.

## Consequences
- Clean dependency flow without cyclical dependencies.
- True unit testability for domain, settings, ViewModels, and navigation without mocking android frameworks or launching emulators.
- Foundation is completely decoupled from concrete CameraX or NDK implementations, which will plug into `:core:camera` and `:core:imaging` in subsequent phases.
