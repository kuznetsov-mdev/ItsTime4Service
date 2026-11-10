# Transfer codec and cipher verification

Date: 2026-11-10. Stage 4 of the [implementation plan](../superpowers/plans/2026-11-09-export-import.md). [Wire contract](../../specs/transfer-format.md); [JSON decision](../adr/0001-transfer-json-codec.md); [primitive proof](transfer-format-compatibility.md).

## Result

Implemented domain codec/cipher ports and operation-local SecretBytes; bounded JSON reader/writer, explicit field/token mappings and logical validator; plain/encrypted framing and runtime AES-256-GCM/PBKDF2 adapters. Providers are explicit JDK/BC 1.83 on Android and OpenSSL 3.6.5 on iOS, with cryptography-kotlin 0.6.0. JSON 1.12.0 is used without a compiler plugin/domain annotations. File transport/import/UI are not implemented by this stage.

The JSON reader rejects duplicate decoded keys before insertion, bounds depth/aggregate array counts/decoded string bytes during parsing and accepts only integer non-string numeric syntax. Exact object fields, enum tokens, presence and links are validated before encode and after decode. Writer accounts for escaping and UTF-8 before appending tokens. Metadata/framing limits and supported parameters are checked before borrowing a password/KDF. Authentication uses original header bytes. Secret/key copies are cleared in finally; cancellation is propagated and produced output is cleared when cancellation prevents delivery. Managed/provider/string copies are not claimed securely erasable.

## Executed checks

All commands ran through standalone gradle-run workflow `6c953a6291dfca30e1025f6d08cabc00`; only bounded summaries and selected synthetic test XML diagnostics were inspected. Full logs were not reopened. Lifecycle finish was not requested because it deletes managed logs and the user prohibits deletion without permission.

| Sequence | Question | Result / nested tasks |
|---|---|---|
| 13 | «Фиксируют ли тесты кодека отсутствующую реализацию и разрешается ли JSON 1.12.0 на Android?» | Expected RED, exit 1: only unresolved TransferCodecImpl; JSON dependency resolved |
| 14 | «Проходят ли первые round-trip/strict/privacy тесты кодека JSON 1.12.0 на Android и компиляция iOS production/test?» | PASS, exit 0, 22.244s; targeted TransferCodecTest + simulator production/test compilation |
| 15 | «Фиксируют ли контрактные тесты конверта отсутствующий TransferCipherImpl перед подключением runtime provider?» | Expected RED, exit 1: unresolved cipher implementation |
| 16 | «Проходят ли контрактные тесты рабочего конверта и кодека на Android SDK24 с runtime crypto зависимостями?» | FAIL, exit 1, 20.155s: invalid Unicode password raised platform MalformedInputException rather than the fixed precondition exception; fixed by catching the common CharacterCodingException |
| 17 | «Исправлена ли классификация некорректного Unicode в пароле и проходят ли cipher/codec тесты с компиляцией iOS production/test?» | PASS, exit 0, 20.625s; focused cipher/codec plus simulator production/test compilation |
| 18 | «Проходят ли строгие границы JSON, framing и полный независимый криптовектор рабочего конверта на Android?» | PASS, exit 0, 16.234s; cipher, codec, strict JSON, envelope tests |
| 19 | «Проходят ли новые codec/framing/cipher сценарии и прежние snapshot/crypto proof на iOS с runtime OpenSSL 3.6.5?» | PASS, exit 0, 144.694s; 55 native tests, both device production/test compilations and debug simulator framework; one bundle-ID inference warning, addressed explicitly |
| 20 | «Проходит ли полный Android host suite с кодеком, рабочим шифрованием и обновлёнными лицензиями, а также minifyReleaseWithR8 и debug APK?» | PASS, exit 0, 111.348s; `:shared:testAndroidHostTest :androidApp:minifyReleaseWithR8 :androidApp:assembleDebug`; XML 570 tests, 0 failures/errors/skips; no warnings |
| 21 | «Проходят ли 55 transfer-тестов iOS и обе production/test compilation, а debug/release frameworks линкуются без предупреждения о bundle ID?» | PASS, exit 0, 333.459s; selected 55 tests on iOS18.3, device test compilation, debug simulator framework and release iosArm64 framework; no warnings |
| 22 | «Проходят ли дополнительные границы скаляров/дат и проверки связей валидатора Transfer v1?» | FAIL, exit 1: syntax error in new nested test fixtures; simplified into fixture helpers, no production change |
| 23 | «Проходят ли границы скаляров/дат и связей после упрощения тестовых fixtures валидатора?» | PASS, exit 0, 7.425s; 4 additional TransferValidationTest cases on Android |
| 24 | «Проходят ли дополнительные четыре теста числовых/UTF-8/календарных границ валидатора на native iOS?» | PASS, exit 0, 36.905s; the same 4 additional cases on native iOS |

The full Android run contains the original 545 tests plus 25 stage-4 tests. Four validation cases were added afterward and passed separately on each platform; these are not presented as a new full-suite run. The 55-test iOS run includes 23 selection/snapshot tests, 7 primitive proofs and 25 stage-4 tests. Physical iPhone execution is not claimed.

Coverage includes both snapshot kinds and personal plaintext, precise omission/null semantics, Unicode/NUL/whitespace passwords, independent complete-envelope vector on both providers, wrong password/body/tag/salt/AAD, reordered valid metadata, empty authenticated plaintext, unsupported headers/parameters before secret access, malformed UTF-8/JSON/integer syntax, duplicate escaped keys, type/enum/field/presence/identity/ownership errors, 24/25 nesting, aggregate array bounds, escaped byte budgets, decoded Unicode string bounds, maximum Int/months/text/date/time values, overflow/negative values, invalid leap-day/hour/second syntax, future/dangling/duplicate reminder traces.

## Packaging evidence

The debug APK was built at 20,817,699 bytes and includes all four new license assets. This is an interim debug size, not a release size or baseline delta. BC license text was extracted from pinned bcprov-jdk18on 1.83; cryptography-kotlin/OpenSSL license files came from their pinned upstream releases, Native Builds from its upstream repository, consistent with published Apache 2.0 metadata. Existing license-screen/notices tests passed in the full suite; kotlinx.serialization and kotlinx-io are listed too.

Both framework Info.plists declare `ru.kuznetsov.itstime4service.shared`, removing the warning. Debug simulator and release device archives contain the OpenSSL 3.6.5 marker and no 3.6.0 marker; the native primitive test also calls the linked version function. Static framework archive sizes are not the final IPA/install size.

## Remaining gates

- Real API24 execution, KDF cancellation/timing and maximum-memory measurements remain mandatory before release, as explicitly agreed by the user. Exact minimum iOS runtime/physical-device measurements are also unverified.
- R8 minification succeeded; executing the complete import/export flow in a minified app and actual saved-file Android↔iOS transport await stages 6–7. No distribution-size delta against an equivalent baseline is claimed.
- Atomic import/provenance/notification reconciliation await stage 5; chooser/permissions/share lifecycle await stage 6; password input, authentication-before-preview and final user confirmation await stage 7.
- Before a release, review upstream patch advisories and licenses for the pinned crypto/JSON stack, repeat both provider vectors/negative fixtures and R8/device smoke after upgrades. Packaging/provider patch changes do not silently change v1 algorithms, KDF parameters or minimum OS versions.

At the stage-4 verification checkpoint, no commit, push, publication, screenshots or user-database access had been performed. Commit authorization and the subsequent readiness check are recorded below; push/publication remain separate actions.

## Commit readiness check

After user authorization to commit, sequence 25 repeated the full Android suite with the four additional validation cases included: `:shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64`. Question: «Готов ли текущий код к коммиту: полный Android host suite с четырьмя добавленными validation cases и компиляция iOS production/test?» PASS, exit 0, 49.471s; fresh XML 574 tests, 0 failures/errors/skips; both iOS compilations passed, no warnings. This supersedes the earlier 570-test full-suite checkpoint while retaining its R8/APK evidence. The mock target checks also passed again (22). Secret-file ignore coverage was completed before staging; candidate/index checks report no forbidden secret paths or credential-pattern matches without printing values.
