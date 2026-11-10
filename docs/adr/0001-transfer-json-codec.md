# Transfer JSON codec

Date: 2026-11-10. Implements stage 4 of the approved export/import plan.

## Context

The frozen v1 contract requires common Android/iOS JSON without platform or serialization types in domain. Duplicate decoded keys, malformed Unicode, non-integer syntax, excessive depth and aggregate array counts must fail before constructing a domain snapshot. Ordinary permissive tree parsing cannot establish these requirements by itself.

## Decision

Use `org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0`, without the serialization compiler plugin or annotations on domain models. The [official pinned release notes](https://github.com/Kotlin/kotlinx.serialization/blob/v1.12.0/CHANGELOG.md) identify Kotlin 2.4.10 as its base; the project uses Kotlin 2.4.20. The [JsonBuilder API](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-builder/) exposes nesting limits and disabling user input in exceptions, but no duplicate-key rejection switch is documented.

Build the JSON tree through a bounded structural reader: decoded keys are checked before insertion; depth, string bytes and aggregate array counts are checked during reading; only JSON integers/booleans/null are accepted as non-string scalars. Use the library for standard JSON string escape handling and output. Keep explicit property/token mappers and revalidate logical snapshots before encoding and after decoding. Catch parsing failures at the data boundary and return fixed codes without source data or exception messages.

## Consequences

No platform parser, reflection, generated serializer or new Gradle plugin is required. Presence wrappers remain domain values and never appear in JSON. The structural reader and explicit mapper need boundary/round-trip/privacy tests on both platforms. Final bytes remain bounded to 10 MiB; envelope/crypto/file limits remain separate. Runtime, release R8 and maximum-memory checks remain mandatory; selecting a library does not mark those checks passed.
