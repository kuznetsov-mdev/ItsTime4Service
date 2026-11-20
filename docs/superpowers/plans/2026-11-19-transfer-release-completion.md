# Transfer release completion implementation plan

**Goal:** Завершить 1.1.0: файловые сценарии, интерфейс экспорта/импорта, приёмочные проверки и подготовленный релиз вместе с четырьмя исправлениями отзывов.

**Architecture:** Существующие domain/data и TransferFiles остаются источниками операций. Один TransferViewModel владеет текущей сессией и асинхронной операцией; plain TransferContent принимает UiState и callbacks. Подэкраны экспорта/импорта разделены по шагам; пароли только в несохраняемом состоянии ввода и временном SecretBytes, байты очищаются после обработки. Маршрут содержит только ID исходной машины, без содержимого/пароля/решений. Процесс восстанавливается безопасным новым выбором файла.

**Tech Stack:** Existing Compose Multiplatform, StateFlow/ViewModel, Koin, TransferCodec/Cipher/Repository/Files; новых библиотек нет.

**Spec:** `specs/export-import.md`, согласованный `docs/index.html`, основной план `2026-11-09-export-import.md`. Пользователь 19.11.2026 поручил доделать весь оставшийся объём. Основная сессия исполняет самостоятельно; новые коммиты/push/публикация отдельно по разрешению.

## Constraints and review focus

- Офлайн, без аккаунта/сети/широких разрешений; 16 MiB и криптополитика сохраняются. Никаких чтений секретных файлов, пользовательской БД или снимков экрана.
- Все машины полной копии назначаются явно. Имена/VIN не создают молчаливое сопоставление. UPDATE_PARAMETERS требует конкретной цели среди одноимённых задач и one-to-one.
- Личные группы независимы; отсутствующая группа не стирает данные. Пробег новой машины подтверждается. Защита личного файла включена по умолчанию; отключение требует предупреждения.
- Смена файла/назначения сбрасывает решения. Замена БД требует SAVED либо EXPLICITLY_DECLINED страховки. Ошибка/отмена сохранения не отмечает SAVED.
- После commit сбой уведомлений не повторяет импорт. После успеха Back и завершение закрывают сессию. Повтор/отмена/смена темы/процесса не доставляют повторную запись.
- Физический API24/минимальная iOS, минимальная ОС crypto и R8 runtime проверяются по доступному окружению; недоступные устройства не выдаются за проверенные.

## Tasks

- [x] Native file checks: `tools/transfer-files-check/run_ios.py` — явный bootstatus, последовательное исполнение, ограниченные ожидания; Swift harness — однозначный собственный каталог для сохранения/открытия и проверка реальных bytes/hash. Сначала конкретный share/reopen, затем полные матрицы; отсутствие сохранённых screen attachments обязательно. Исправлять production только по доказанному дефекту.
- [x] `features/transfer/TransferUiState.kt`, `TransferViewModel.kt`, `TransferImportDecisions.kt`: состояния hub/export/import/preview/result, один владелец Job, in-memory snapshot, private file bytes и ImportPlan, защищённые операции и retry. Использовать CreateTransferSnapshotUseCase/TransferCodec/TransferCipher/PrepareImportUseCase/ApplyImportUseCase/ReconcileImportNotificationsUseCase/TransferFiles и существующие репозитории. Unit tests: все группы, password/cancel/failure, повтор, stale plan, смена цели/файла и lifecycle.
- [x] `TransferExportContent.kt`, `TransferImportContent.kt`, `TransferContent.kt`, `TransferScreen.kt`: экран данных, выбор машины/работ/групп, password+confirmation, предупреждение о plaintext, открытие/разблокировка, назначения всех машин, решения всех типов, preview, safety backup, результат/повтор уведомлений. Все надписи EN/RU/ES. UI tests фиксируют фактические callbacks, значения по умолчанию, недоступность неправильного подтверждения, scroll/маленький экран и Back.
- [x] `di/DataModule.kt`, `FeatureModule.kt`, `features/navigation/{Destinations,NavGraph}.kt`, `SettingsScreen.kt`, `CarDetailScreen.kt`: два согласованных входа. Back из success завершает сессию; navigation не хранит PII. Существующие тесты экранов обновить без потери прежней проверки.
- [x] Сквозные тесты через настоящий TestApp/SQLite и файлы-fakes: обе разновидности, encrypted export→import, оба режима копии, личный selective, одноимённые задачи, safety failure/cancel, notifications retry, stale/repeat и многомашинность. Проверить 17 критериев и U1–U5 по спецификации; реальные ОС отдельно.
- [ ] Release: полный Android suite, обе iOS compilation и релевантные native/UI tests, Android debug/release R8 builds, обновление с 1.0.0 и сохранность данных. После реализации поднять 1.1.0/build number, обновить release notes, specs/test-plan/dev_plan/сессию. Предоставить готовый артефакт и фактические ограничения; не публиковать и не объявлять Done до приёмки.


## Result — 2026-11-19

Available software work is ready for user acceptance: full715 Android tests and56 native iOS tests (including9 UI/navigation scenarios), real two-way encrypted documents, actual schema5→7 install upgrade, R8 runtime codec/files, system matrices7/7 on iOS18.3 and27, signed1.1.0(3) AAB with the existing certificate. Final self-review also fixed exact-kilometer preservation of unchanged prefilled miles, visibility comparison and protected-file retry after a transient DB failure. See [verification](../../research/transfer-ui-release-verification.md) and [release candidate](../../releases/1.1.0.md).

The release task remains unchecked because physical API24 performance/memory/cancellation and exact minimum-iOS validation are unavailable and explicitly required before publication. New commits, push and upload remain separate user-authorized actions; Done requires user acceptance.
