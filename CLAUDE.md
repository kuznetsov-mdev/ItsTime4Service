# CLAUDE.md — ItsTime4Service

Приложение «Пора на ТО» (EN/ES — «It’s Time 4 Service»): учёт регламентного ТО авто по пробегу и времени. Kotlin Multiplatform, Android + iOS, общий UI на Compose Multiplatform. Данные только локальные, сети нет. Прежнее рабочее имя — PitStop.

## Структура

- `shared` — вся логика и UI; пакет `ru.kuznetsov.itstime4service`.
- `androidApp` — хост Android (`MainActivity` → `App()`).
- `iosApp` — хост iOS (`MainViewController()`), UI-тест `iosAppUITests/ScreensWalkthroughUITests.swift`.
- Пакеты `shared/src/commonMain/kotlin/...`: `ui` (тема, компоненты, форматирование, иконки), `domain` (модели, use case-ы, интерфейсы репозиториев), `data` (SQLDelight, `*RepositoryImpl`, мапперы), `di` (Koin, модуль на файл), `features` (экраны и навигация). Подробно — раздел «Структура пакетов» в `dev_plan.md`.

## Документация

- `dev_plan.md` — план по этапам, статус работ, таблица библиотек.
- `specs/domain.md`, `specs/data.md`, `specs/presentation.md` — спецификации слоёв.
- `specs/test-plan.md` — тест-план, матрица трассировки, текущее покрытие.
- `docs/index.html` — макет, в репозитории, публикуется через GitHub Pages (https://kuznetsov-mdev.github.io/ItsTime4Service/).
- `specs/security.md` — только локально, в `.gitignore`. Подробности из него в коммитящиеся файлы не переносить.

Документацию обновлять в той же задаче, что и код. При изменении поведения или интерфейса порядок: макет → спека → согласование → код.

## Архитектура и соглашения

- ViewModel отдаёт `StateFlow<UiState>`, экран подписывается через `collectAsState()`; ViewModel внедряется через `koinViewModel()` прямо в Composable.
- Бизнес-логика — в use case-ах `domain`; `data` зависит от `domain`, не наоборот.
- Схема БД — `.sq` в `shared/src/commonMain/sqldelight`, изменения схемы — только через миграции `sqldelight/migrations/*.sqm` с тестом миграции.
- Ошибки базы: запись — плашка `ErrorBar`, форма сохраняет ввод; чтение — экран `LoadError` с «Повторить»; во ViewModel — обёртки из `ui/error/DatabaseErrors.kt`. Подробно — раздел «Ошибки базы» в `specs/presentation.md`.
- Строки — в `composeResources/values`, `values-ru`, `values-es`: новые строки добавлять во все три.
- Новую библиотеку подключать только на шаге, где она нужна, и вносить в таблицу библиотек `dev_plan.md`.

## Сборка и тесты

Gradle запускать через скилл `gradle-run`.

- Android host-тесты (unit, data, presentation, Compose UI на Robolectric): `:shared:testAndroidHostTest`
- iOS-тесты на симуляторе: `:shared:iosSimulatorArm64Test`
- Сборка Android: `:androidApp:assembleDebug`
- Компиляция iOS: `:shared:compileKotlinIosSimulatorArm64`, тестовые исходники — `:shared:compileTestKotlinIosSimulatorArm64`
- iOS UI-тест: `xcodebuild` со схемой `iosAppUITests`

Изменение в `commonMain` проверять на обеих платформах: минимум host-тесты Android и компиляция iOS с тестовыми исходниками.

## Тесты

- Общие тесты — в `commonTest`; дата, время и корутины — через управляемые часы и тестовый scheduler, без реального времени и часового пояса.
- Compose UI-тесты — наследники `ui/test/UiTest.kt`, `androidx.compose.ui.test.v2.runComposeUiTest`. На Android они идут в Robolectric (SDK 36) на маленьком экране: элементы ниже видимой области — через `performScrollTo`.
- Окружение экранов и сбои базы — `features/TestApp.kt` (`failWrites`, `hideTable`).
- Для `Test` в `shared/build.gradle.kts` заданы `--add-exports`/`--add-opens` — нужны Robolectric на JDK 21, не удалять.

## Git

Сообщения коммитов — на английском, префикс — область изменения: `doc:`, `features:`, `common-ui:`, `domain:`, `data:`, `test:`, `navigation:`, `di:`, `ios:`.
