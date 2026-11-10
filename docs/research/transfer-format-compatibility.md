# Transfer v1 crypto compatibility proof

Date: 2026-11-10. Scope: isolated test-only proof for [transfer-format v1](../../specs/transfer-format.md), not a production codec or completed file-exchange feature. Only public synthetic vectors are used. No user files, secrets or application database are read.

## Pinned candidate

| Platform | Explicit provider | Pinned dependencies (test-only at the proof checkpoint) |
|---|---|---|
| Android/JVM | `CryptographyProvider.JDK(BouncyCastleProvider())` | cryptography-core/provider-jdk/provider-jdk-bc `0.6.0`; bcprov-jdk18on `1.83` |
| iOS | `CryptographyProvider.Openssl3` | cryptography-core/provider-openssl3-api/provider-openssl3-prebuilt-nativebuilds `0.6.0`; `com.ensody.nativebuilds:openssl-libcrypto` strictly `3.6.5` |

The first proof below used provider-openssl3-prebuilt with embedded OpenSSL 3.6.0. The patch verification section records its replacement; the earlier run history is retained as historical evidence, not the current dependency selection.

The [official JDK guide](https://whyoleg.github.io/cryptography-kotlin/getting-started/providers/jdk/) documents explicit Java Provider selection. The [pinned factory source](https://github.com/whyoleg/cryptography-kotlin/blob/0.6.0/cryptography-providers/jdk/src/jvmMain/kotlin/JdkCryptographyProvider.kt) exposes that overload. The [pinned dependency catalog](https://github.com/whyoleg/cryptography-kotlin/blob/0.6.0/gradle/libs.versions.toml) fixes BC 1.83. The [OpenSSL3 guide at 0.6.0](https://github.com/whyoleg/cryptography-kotlin/blob/0.6.0/docs/getting-started/providers/openssl3.md) requires API plus exactly one linking module; prebuilt embeds OpenSSL 3.6.0 and needs no separately installed libcrypto. No Swift bridge, system OpenSSL installation or provider discovery is used by the proof.

The [0.6.0 PBKDF2 JDK implementation](https://github.com/whyoleg/cryptography-kotlin/blob/0.6.0/cryptography-providers/jdk/src/jvmMain/kotlin/algorithms/JdkPbkdf2.kt) strictly decodes UTF-8 bytes to Java characters before PBEKeySpec. This makes testing Unicode and NUL necessary. The exact expected outputs below agree with Python hashlib, independently of both selected providers. The [explicit-IV AEAD API](https://github.com/whyoleg/cryptography-kotlin/blob/0.6.0/cryptography-core/src/commonMain/kotlin/operations/IvAuthenticatedCipher.kt) returns ciphertext followed by tag without prepending the IV, matching the wire contract.

## Public vectors

PBKDF2 parameters: HMAC SHA-256; 600,000 iterations; salt = bytes 0 through 15; output = 32 bytes. Independently executed reference: `hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), bytes(range(16)), 600000, 32).hex()`.

| Exact password | Derived key hex |
|---|---|
| `test-password` | `cae9c801374596f17de48ba4ed7061692b5d0ab433932a7d3cdf698dfd8bb3e8` |
| `Сервис café 🔧 e\u0301` (final e plus combining acute) | `2a3b7f04213df04e79f07f5d4584c8261db95c6836c675450f5f73dd525009c8` |
| `a\u0000b` (embedded NUL) | `38b4da2e52f2e5c97dda9620bb28f1b204be3a436d284e830af78676e45169f9` |

The AES-256-GCM zero vector uses a 32-byte zero key, 12-byte zero nonce, 16-byte zero plaintext, empty AAD: ciphertext `cea7403d4d606b6e074ec5d3baf39d18`, tag `d0d1c8a799996bf0265b98b5d48ab919`. This is also present in the [OpenSSL 3.6.0 official AES test corpus](https://github.com/openssl/openssl/blob/openssl-3.6.0/test/recipes/30-test_evp_data/evpciph_aes_common.txt#L727).

The shared proof fixes an additional deterministic encrypted-body vector. Password: the exact Unicode password above; salt = 00..0f; nonce = 00..0b. Plaintext is the UTF-8 JSON text `{"formatVersion":1,"note":"План ТО 🔧 café e\u0301"}` (the final accent is a JSON escape). This is deliberately a primitive vector rather than a valid full application snapshot. Metadata bytes are the exact compact ASCII JSON below; AAD is the 14-byte prefix `495434530101000000920000004d` plus those bytes:

```json
{"cipher":"AES-256-GCM","kdf":"PBKDF2-HMAC-SHA256","iterations":600000,"salt":"AAECAwQFBgcICQoLDA0ODw==","nonce":"AAECAwQFBgcICQoL","tagBytes":16}
```

Independent reference execution used JDK SunJCE AES/GCM/NoPadding with the Python-derived key, `GCMParameterSpec(128, nonce)`, `updateAAD(prefix + metadata)`, then `doFinal(plaintext)`. Expected ciphertext plus 16-byte tag:

```text
369a1d394222aa5cfc1e003dfb41d1c6f752e453078b85025e5b1e15b00ef193635ec0674ed0b2865bb7b30d59e88850e55b83d1aefb0f1f9c14532e9487fbbb092f9b5e6bce0f96d8b1b767cb
```

Both providers must encrypt to that same body and decrypt it. Additional tests reject a key derived from a wrong password, altered original prefix bytes, altered metadata bytes, ciphertext and tag. Random-source tests request independent 16-byte salts and 12-byte nonces and check lengths and freshness; this is a smoke test, not a statistical entropy assessment. Mutable test password/derived-key byte arrays are cleared in finally; provider and immutable runtime copies are not promised securely erasable.

## Executed checks

Gradle owner: Task 1B. All Gradle commands use the standalone gradle-run wrapper; workflow `b1fc60e4184a73ed3e702d4e50defd6e`. The managed full logs are never published or reopened. Workflow finished successfully after sequence 6; finish removed only wrapper-owned temporary logs. The fresh linked simulator binary also contains the embedded 3.6.0 version string; this artifact check supports the pinned prebuilt version, while the native test XML confirms actual execution.

| Sequence | Nested Gradle command | Actual bounded result |
|---|---|---|
| 1 | `./gradlew :shared:testAndroidHostTest --tests '*TransferCryptoAndroidHostProofTest'` | PASS, 5 tests / 0 failures / 0 errors / 0 skipped; 112.086s build, XML suite 57.845s |
| 2 | `./gradlew -p /private/tmp/it4s-transfer-crypto-android-proof connectedAndroidDeviceTest` | PASS, 5 tests / 0 failures / 0 errors / 0 skipped on emulator-5554, Pixel 9 Android 15/API35; 92.121s build, XML suite 8.922s |
| 3 | `./gradlew :shared:iosSimulatorArm64Test --tests '*TransferCryptoIosProofTest*' --device 8471AC1C-FEE2-462B-8FE1-3EE4F6BBB36D` | INTERRUPTED signal 15 / exit 143 after 256.699s at linkDebugTestIosSimulatorArm64; no native test execution yet |
| 4 | `./gradlew :shared:linkDebugTestIosSimulatorArm64` | PASS, exit0; 61.303s; no warnings/failure fingerprints |
| 5 | `./gradlew :shared:iosSimulatorArm64Test --tests '*TransferCryptoIosProofTest*' --device 8471AC1C-FEE2-462B-8FE1-3EE4F6BBB36D` | PASS, 5 tests / 0 failures / 0 errors / 0 skipped on iPhone 16 Pro / iOS 18.3; 9.898s build, XML suite 1.877s |
| 6 | `./gradlew :shared:linkDebugTestIosArm64` | PASS, exit 0; 263.045s; compileKotlinIosArm64 + compileTestKotlinIosArm64 + linkDebugTestIosArm64, no warnings/failure fingerprints; no device execution claim |

Sequence 3 received external SIGTERM15; coordinator confirmed neither owner nor user requested cancellation. The origin is unknown and no compiler/source failure fingerprint was reported. The wrapper recorded `interrupted`, not `uncertain`; after checking ownership the coordinator authorized completing linking in the same workflow with a retained TTY session, then separately executing tests. This event is not evidence of candidate incompatibility.

Sequence 1 is JVM BC under Robolectric SDK24, not native Android API24 execution. Sequence 2 is actual Android execution. The temporary probe is a minimal standalone KMP Android library with namespace `ru.kuznetsov.itstime4service.cryptoproof`, compileSdk37/minSdk24, Kotlin2.4.20/AGP9.1.1, AndroidJUnitRunner and the same repository common proof source directory. Its AndroidJUnit4 subclass selects the same explicit JDK+BC provider; no domain, database or UI tests are imported. It uses AndroidX runner1.7.0/ext-junit1.3.0 and the pinned dependencies above. The designated repository `./gradlew -p ...` runs it; the main application's package/data are untouched. Temporary source/build files are retained, not deleted. This device proof is intentionally outside production modules.

## Android minimum-OS evidence and limits

The [Android SecretKeyFactory reference](https://developer.android.com/reference/javax/crypto/SecretKeyFactory) lists system PBKDF2WithHmacSHA256 only from API26. The proof deliberately passes a bundled BC Provider instance. The [pinned JDK state implementation](https://github.com/whyoleg/cryptography-kotlin/blob/0.6.0/cryptography-providers/jdk/src/jvmMain/kotlin/JdkCryptographyState.kt) passes it to SecretKeyFactory/Cipher.getInstance, avoiding that system-algorithm assumption. [GCMParameterSpec exists from API19](https://developer.android.com/reference/javax/crypto/spec/GCMParameterSpec). Together with SDK24 Robolectric and a minSdk24 D8-built probe, this is source/build/host evidence; API35 execution does not prove hardware API24 timing or runtime behavior.

## OpenSSL 3.6.5 patch verification

Verified 2026-11-10. The [official OpenSSL release notes](https://openssl-library.org/news/openssl-3.6-notes/) list 3.6.5 (2026-09-29), following the 3.6.4 empty-ciphertext AEAD fix. [Maven Central metadata](https://repo.maven.apache.org/maven2/com/ensody/nativebuilds/openssl-libcrypto/maven-metadata.xml) publishes 3.6.5; its [module metadata](https://repo.maven.apache.org/maven2/com/ensody/nativebuilds/openssl-libcrypto/3.6.5/openssl-libcrypto-3.6.5.module) declares iosArm64 and iosSimulatorArm64 variants. Both architectures were resolved and linked successfully, beyond checking publication alone.

The test configuration now uses API + prebuilt-nativebuilds 0.6.0 and a **strict 3.6.5** libcrypto dependency. Nativebuilds' default transitive requirement is 3.6.1_1, so the direct strict constraint is intentional. The earlier prebuilt module is no longer declared. At this proof checkpoint production source sets had no crypto dependency; stage 4 subsequently promotes the same pins to runtime source sets. No provider discovery, Swift bridge, system OpenSSL installation or minimum-OS change was added.

`emptyCiphertextRequiresValidAuthentication` exercises an empty plaintext with both empty and nonempty AAD. Valid tag-only bodies decrypt; changed first/last tag bytes, truncated tags, wrong nonce, altered AAD and wrong key are rejected. Existing NIST/Unicode/PBKDF2/tamper tests still pass. This is a regression guard for the application's provider API, not a claim to reproduce every OpenSSL advisory path on the older build.

`TransferCryptoIosProofTest.linkedLibraryUsesPinnedPatchVersion` calls the linked `OpenSSL_version(0)` function and requires the `OpenSSL 3.6.5 ` prefix. Its C binding comes from the pinned provider's cinterop declarations, only in iosTest. Both final test binaries also contain the 3.6.5 version string and no 3.6.0 version string. The runtime assertion confirms the simulator's actual linked version; iosArm64 was linked but not executed on a physical device.

All following commands ran through standalone gradle-run workflow `6c953a6291dfca30e1025f6d08cabc00`. Only bounded wrapper summaries, test XML attributes/case names and specific artifact metadata were inspected; managed full logs were not reopened. The workflow state is `completed` after sequence 6. `finish` was not invoked because it deletes managed logs and the user's instructions prohibit file deletion without explicit permission.

| Sequence | Nested Gradle command / verification question | Result |
|---|---|---|
| 1 | `:shared:testAndroidHostTest --tests '*TransferCryptoAndroidHostProofTest'` — «Проходят ли исходные Android crypto proof тесты перед обновлением iOS provider?» | PASS, exit 0; task UP-TO-DATE, historical baseline reused |
| 2 | Android proof + `:shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64` — «Проходят ли Android crypto proof и компиляция iOS production/test после выбора OpenSSL 3.6.5 и добавления проверки пустого ciphertext?» | PASS, exit 0; 64.847s; Android XML 6 tests, 0 failures/errors/skips; both iOS compilations passed |
| 3 | `:shared:iosSimulatorArm64Test --tests '*TransferCryptoIosProofTest*' --device 8471AC1C-FEE2-462B-8FE1-3EE4F6BBB36D` — «Проходят ли 6 crypto proof тестов на iOS 18.3 с OpenSSL 3.6.5, включая аутентификацию пустого ciphertext?» | PASS, exit 0; 31.269s; iOS XML 6 tests, 0 failures/errors/skips |
| 4 | iOS proof + `:shared:linkDebugTestIosArm64` — «Подтверждает ли iOS-тест фактическую версию libcrypto 3.6.5 и проходят ли crypto proof вместе с линковкой iosArm64?» | FAIL, exit 1; 26.885s; newly added version test imported a function absent from nativebuilds' empty wrapper package; test compilation stopped before execution/linking |
| 5 | `:shared:compileTestKotlinIosSimulatorArm64 :shared:compileTestKotlinIosArm64` — «Проходит ли компиляция iOS-тестов после исправления импорта функции проверки версии OpenSSL?» | PASS, exit 0; 4.285s; inspected published cinterop metadata and corrected the import to the provider's actual declarations package |
| 6 | iOS proof + `:shared:linkDebugTestIosArm64` — «Подтверждает ли iOS-тест фактическую версию libcrypto 3.6.5 и проходят ли crypto proof вместе с линковкой iosArm64 после исправления импорта?» | PASS, exit 0; 22.611s; iOS XML 7 tests, 0 failures/errors/skips; both architecture test binaries linked |

No warnings or unresolved failure fingerprints remain in the final checks. Test-suite XML elapsed values are not treated as minimum-OS KDF benchmarks. The patch selection and native primitive proof are complete; full codec/file-exchange and release validation remain separate gates.

## Remaining implementation and release gates

- No physical API24 device or API24 system image was available. The current device check finds no Android devices attached. On 2026-11-10 the user explicitly authorized continuing implementation and moving real API24 execution/measurement to a mandatory pre-release gate. PBKDF2 timing, cancellation responsiveness and memory measurement on API24 remain unverified and still block release. No OS image download was performed.
- iOS native execution completed with 5 tests on iOS 18.3 (app deployment 18.2); no iOS18.2 runtime/device was used. iosArm64 compilation and test-binary linking passed; no physical-device execution was performed.
- Codec/parser duplicate-key, depth, size-boundary and privacy fixtures, maximum-size memory, actual saved-file Android→iOS/iOS→Android exchange and import transactions belong to later implementation steps. Identical primitive vectors prove provider interoperability, not the complete feature.
- Stage 4 now connects the pinned crypto providers and kotlinx-serialization-json 1.12.0 to runtime source sets, with a bounded structural reader and explicit mapper. Production R8 execution, distribution-size delta, third-party notices/license packaging, security update policy and saved-file interoperability remain release checks; selecting and linking dependencies does not prove those checks.
- Password input validation (valid Unicode, nonempty, maximum 1,024 UTF-8 bytes, no trimming/case/normalization), consistent AuthenticationFailed mapping and bounded framing are implemented in the stage 4 adapters, beyond this original primitive proof. UI authentication-before-preview and password lifecycle still require integration acceptance.

Plaintext full backups and personal selective snapshots remain valid after the user's explicit export-side refusal of default protection. The importer must not reject them merely because of their groups and must not claim cryptographic authorship/authenticity.
