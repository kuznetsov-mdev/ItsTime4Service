# План разработки PitStop (KMP)

## Контекст

Проект `PitStop` (Android + iOS, Kotlin Multiplatform) сейчас — это чистый wizard-скаффолд: `shared/src/commonMain` содержит только дефолтные `App.kt`/`Greeting.kt`/`Platform.kt`, `androidApp` и `iosApp` уже корректно хостят общий Compose-экран (`MainActivity` → `setContent { App() }`, `ContentView.swift` → `MainViewController()`), но никакой прикладной логики, экранов, темы или данных ещё нет.

Цель — пошагово построить приложение по дизайн-макету (`docs/index.html`): сначала design-tokens и переиспользуемые компоненты, затем экраны на фейковых данных, затем domain/data слой и связка через domain, затем уведомления и полировка. Подключение каждой новой библиотеки привязано к конкретному шагу, на котором она впервые понадобится — ничего не тянем заранее "про запас".

**Важное отличие от исходного предположения в задаче:** Compose Multiplatform **уже подключён и настроен** в `shared/build.gradle.kts` — плагины `composeMultiplatform`/`composeCompiler` применены, а `compose.{runtime,foundation,material3,ui,components.resources}` и `androidx.lifecycle.{viewmodelCompose,runtimeCompose}` уже объявлены в зависимостях `commonMain`. Отдельный шаг "подключить Compose Multiplatform" не нужен — этап 1 сразу переходит к написанию тем/токенов.

## Структура пакетов

Базовый пакет: `ru.kuznetsov.pitstop` (`shared/src/commonMain/kotlin/ru/kuznetsov/pitstop/`).

Как и просил пользователь — `features`, `domain`, `data`, `di`. Плюс один дополнительный пакет `ui` — для темы, переиспользуемых компонентов и иконок, которые не относятся ни к одной конкретной фиче и нужны до того, как появится хоть один экран:

```
ru.kuznetsov.pitstop/
├── ui/
│   ├── theme/        # Color.kt, Type.kt, Theme.kt, Dimens.kt
│   ├── components/   # ComponentPreview.kt (общий @Preview-хелпер) + подпакеты по группам из каталога макета:
│   │   ├── buttons/      # PrimaryButton, Fab, AppIconButton, PresetChip, SegmentedControl, TabBarItem
│   │   ├── fields/       # AppTextField, AppNumberField, LabeledMiniField, InlineEditField
│   │   ├── steppers/     # Stepper
│   │   ├── indicators/   # AppSwitch, StatusChip, ProgressBar, GaugeRing
│   │   ├── cards/        # ListRowCard, StatCard, NotificationPreviewCard
│   │   ├── dialogs/      # ConfirmDialog
│   │   └── screen/       # ScreenTopBar, ScreenTitle — общая шапка экранов
│   ├── format/        # форматирование чисел/дат/расстояний, TaskStatus → StatusTier
│   └── icons/         # PitStopIcons.kt — иконки из макета как ImageVector
├── domain/
│   ├── model/         # Car, MaintenanceTask, ServiceHistoryEntry, TaskStatus, TaskStat
│   ├── usecase/        # CalculateTaskStatus, SortedTaskStats, CarAlerts, MarkTaskDone...
│   └── repository/    # интерфейсы: CarRepository, ServiceHistoryRepository, SettingsRepository
├── data/
│   ├── local/          # SQLDelight driver (expect/actual), .sq-схемы
│   ├── repository/     # *RepositoryImpl на SQLDelight (этап 7; до него — Fake*RepositoryImpl)
│   └── mapper/         # маппинг SQLDelight-строк ↔ domain-модели
├── AppViewModel.kt    # тема из настроек для корневого `App`
├── di/                # Koin: FeatureModule.kt, DomainModule.kt, DataModule.kt (по модулю на файл) + Koin.kt (initKoin)
└── features/
    ├── navigation/      # Destinations, NavGraph
    ├── cars/            # экран "Мои авто"
    ├── carform/         # форма авто: "Добавление авто" и "Редактирование авто"
    ├── cardetail/       # экран "Регламентные работы"
    ├── history/         # экран "История ТО"
    └── settings/        # экран "Настройки"
```

ViewModel-ы — `StateFlow<UiState>`, экран подписывается через `collectAsState()`; DI — Koin, инжект через `koinViewModel()` прямо в Composable (подтверждено пользователем).

## Этапы

Маркеры: ✅ — пункт реализован, ⬜ — ещё не начат.

### Этап 1 — Тема и шрифты
**Библиотеки:** не требуются — Compose Multiplatform уже подключён.
- ✅ `ui/theme/Color.kt` — нейтральные токены (light/dark): `--bg/--surface/--surface-2/--surface-3/--ink/--ink-muted/--ink-faint/--rule`, плюс 3 статус-константы `tier-ok/soon/due` и акцент `#0ea5b7`, одинаковые в обеих темах.
- ✅ `ui/theme/Type.kt` — три роли шрифта из макета: display/sans (`FontFamily.Default`, т.к. это уже системный шрифт на каждой платформе — San Francisco на iOS, Roboto на Android, кастомные файлы не нужны) и mono (`FontFamily.Monospace`) для чисел/VIN/дат.
- ✅ `ui/theme/Theme.kt` — `PitStopTheme` поверх `MaterialTheme`, плюс свой `CompositionLocal` для tier-цветов (их нет в стандартной `ColorScheme` Material3).
- ✅ `ui/theme/Dimens.kt` — отступы/радиусы (8/10/12/14/24px и т.д.) по макету.
- ✅ Проверка: тема подключена в `App.kt`, палитра и три шрифтовые роли проверены на Android-эмуляторе в светлой и тёмной теме, сборка под Android и iOS (`:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`) зелёная.

### Этап 2 — Общие компоненты (ui-kit)
**Библиотека:** `com.composables:icons-lucide:1.1.0` (KMP-порт [Lucide Icons](https://lucide.dev), Android/iOS/JVM/Wasm/JS) — alias в `gradle/libs.versions.toml`, зависимость в `commonMain.dependencies` модуля `shared`. Решение заменить ручной перенос SVG из макета: все 17 иконок макета 1:1 совпадают по имени с иконками Lucide (car, gauge, droplet, disc, filter, wrench, settings, clock, check, chevron-left, pencil, trash-2, bell, plus, minus, sun, moon) — похоже, макет изначально на них и ориентировался.
- ✅ Подключить `com.composables:icons-lucide:1.1.0`, проверено на Android (`:androidApp:assembleDebug`) и iOS (`:shared:compileKotlinIosSimulatorArm64`).
- ✅ `ui/icons/PitStopIcons.kt` — тонкая обёртка: 17 семантических имён из макета (car, gauge, droplet, disc, filter, wrench, gear, clock, check, chevronLeft, pencil, trash, bell, plus, minus, sun, moonToggle) смаплены на `Lucide.*`, чтобы экраны не зависели от конкретной иконочной библиотеки напрямую.

Кнопки (`ui.components.buttons`):
- ✅ `PrimaryButton`
- ✅ `Fab`
- ✅ `AppIconButton` (переименован из `IconButton` из плана — так называется компонент в `androidx.compose.material3`, конфликт имён)
- ✅ `PresetChip`
- ✅ `SegmentedControl`
- ✅ `TabBarItem`

Поля (`ui.components.fields`):
- ✅ `AppTextField`
- ✅ `AppNumberField`
- ✅ `LabeledMiniField`
- ✅ `InlineEditField`

Степпер (`ui.components.steppers`):
- ✅ `Stepper`

Индикаторы (`ui.components.indicators`):
- ✅ `AppSwitch`
- ✅ `StatusChip` (+ `StatusTier` — ui-kit enum статуса с цветом и подписью; доменный `TaskStatus` из этапа 5 маппится в него на экранах)
- ✅ `ProgressBar` (цвет заливки выводится из значения: до 60% — `tierOk`, до 90% — `tierSoon`, выше — `tierDue`)
- ✅ `GaugeRing`

Карточки (`ui.components.cards`):
- ✅ `ListRowCard`
- ✅ `StatCard`
- ✅ `NotificationPreviewCard`

Диалоги (`ui.components.dialogs`):
- ✅ `ConfirmDialog` — модальное окно подтверждения необратимого действия по макету (экран 06 «Удаление авто»): иконка, заголовок, текст, слева «Отмена», справа действие цветом `tierDue`. *Добавлен в макет после этапа 2; реализован вместе с экраном `cardetail` на этапе 6.*

Локализация:
- ✅ Строки вынесены в `composeResources/values*/strings.xml`: английский — по умолчанию (`values`), русский (`values-ru`) и испанский (`values-es`) — дополнительно. В коде — `stringResource(Res.string.*)`, язык берётся из системной локали. Для iOS языки объявлены в `Info.plist` (`CFBundleLocalizations`). Новые строки сразу добавляются во все три файла.

Проверка:
- ⬜ `@Preview` каждого компонента в обеих темах (как в макете — light/dark exhibit).

### Этап 3 — Навигация
- ✅ Подключить `org.jetbrains.androidx.navigation:navigation-compose:2.10.0-beta01` (JetBrains-форк Navigation Compose для Compose Multiplatform; "чистый" `androidx.navigation` 2.10.2 — Android-only) — alias в `gradle/libs.versions.toml`, зависимость в `commonMain.dependencies` модуля `shared`.
- ✅ `features/navigation/Destinations.kt` — sealed-класс: `Cars`, `AddCar`, `CarDetail(carId)`, `History(carId)`, `Settings`.
- ✅ `features/navigation/NavGraph.kt` — `NavHost`, становится новым корневым composable вместо временной витрины темы из этапа 1. *Корень — `PitStopNavGraph` (`Scaffold` + таб-бар на `Cars`/`Settings`); маршруты пока ведут на временный `PlaceholderScreen` — заменится экранами на этапе 6.*

*Почему именно сейчас: экраны на следующем этапе сразу пишутся с переходами между собой, retrofit-навигацию потом не делаем.*

### Этап 4 — DI (Koin)
- ✅ Подключить `io.insert-koin:koin-bom:4.2.2` + `koin-core`, `koin-compose`, `koin-compose-viewmodel` — alias'ы в каталог, зависимости в `commonMain.dependencies`. Проверено на Android (`:androidApp:assembleDebug`) и iOS (`:shared:compileKotlinIosSimulatorArm64`).
- ✅ `di/` — `featureModule` (все зависимости для UI)/`domainModule`/`dataModule`, каждый в своём файле (`FeatureModule.kt`, `DomainModule.kt`, `DataModule.kt`), и `initKoin()` в `Koin.kt`. *Модули пока пустые. `initKoin()` вызывается из `PitStopApplication.onCreate()` (Android, класс зарегистрирован в манифесте) и из `iOSApp.init()` (iOS; в Swift функция называется `KoinKt.doInitKoin()`). Проверено запуском на Android-устройстве и в iOS-симуляторе.*
- ✅ Зарегистрировать в `dataModule` **фейковые** реализации репозиториев (см. этап 5) — реальные появятся на этапе 7 без изменений в `di`-API, только подменой binding'а. *Сделано вместе с этапом 5: три `Fake*RepositoryImpl` зарегистрированы как `single` с привязкой к domain-интерфейсам.*

*Почему именно сейчас: экраны (этап 6) с первого дня инжектят `ViewModel` через `koinViewModel()`, как подтверждено пользователем.*

### Этап 5 — Domain-модели и бизнес-логика + фейковые данные
- ✅ Подключить `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0` (явный `Flow` в репозиториях) и `org.jetbrains.kotlinx:kotlinx-datetime:0.8.0` (разница дат для интервала по месяцам) в `commonMain.dependencies`.
- ✅ `domain/model/` — `Car`, `MaintenanceTask`, `ServiceHistoryEntry`, `TaskStatus` (OK/SOON/DUE), `TaskStat`.
- ✅ `domain/usecase/` — перенос JS-логики из макета 1:1 на Kotlin: `CalculateTaskStatusUseCase` (аналог `taskStat`/`worseStatus`), `SortedTaskStatsUseCase` (`sortedTaskStats`), `CarAlertsUseCase`/`worstStatus` — чистые функции, без зависимости от data-слоя.
- ✅ `domain/repository/` — интерфейсы `CarRepository`, `ServiceHistoryRepository`, `SettingsRepository` (методы возвращают `Flow<...>`).
- ✅ `data/repository/Fake*RepositoryImpl` — in-memory реализация, засеянная теми же тремя машинами (Kia Rio/Toyota Camry/Hyundai Solaris), что и в макете; регистрируется в `dataModule` (этап 4).
- ✅ Юнит-тесты в `commonTest` на `CalculateTaskStatusUseCase`: статус по км, по времени, "что раньше" — закрывает требование плана `.md` "перенос расчёта статуса из JS в Kotlin + unit-тесты" ещё до того, как появится UI. *`CalculateTaskStatusUseCaseTest` — 23 теста, зелёные на `:shared:testAndroidHostTest` и `:shared:iosSimulatorArm64Test`.*

### Этап 6 — Вёрстка экранов (на фейковых данных)
**Библиотеки:** не требуются — все уже подключены на этапах 2–5.
- ✅ `features/cars/` — список авто (`ui-kit` карточки + `GaugeRing`), `CarsViewModel: StateFlow<CarsUiState>`.
- ✅ `features/addcar/` — форма + preset-чипы, `AddCarViewModel`. *На этапе 7а переименованы в `features/carform/` и `CarFormViewModel`.*
- ✅ `features/cardetail/` — список работ, инлайн-правка пробега, "отметить выполненной", `CarDetailViewModel`.
- ✅ Удаление авто с подтверждением: корзина в топ-баре `cardetail` открывает `ConfirmDialog` («Удалить {марка}?»); «Удалить» вызывает `DeleteCarUseCase` (машина, её работы и история ТО) и возвращает на список авто, «Отмена» и тап по затемнению закрывают окно.
- ✅ `features/history/` — список истории ТО, `HistoryViewModel`.
- ✅ `features/settings/` — тема/единицы/push, `SettingsViewModel` (состояние пока в памяти — персистентность появится на этапе 7 вместе с остальными данными).
- ✅ Проверка: ручной прогон на Android-эмуляторе (и по возможности iOS-симуляторе) по всем 5 экранам и окну подтверждения удаления на фейковых данных — соответствует этапам 3–7 исходного `План_разработки_Одометр.md`. *Пройдено на iOS-симуляторе (iPhone 17 Pro, iOS 26.5) UI-тестом `iosApp/iosAppUITests/ScreensWalkthroughUITests` в светлой и тёмной теме; на Android — только сборка `assembleDebug`, прогон на эмуляторе не делался. По ходу исправлено: маршрут формы `cars/new` перехватывался маршрутом деталей `cars/{carId}`, из-за чего «+» на списке авто не открывал форму (теперь `add-car`); форма добавления открывается без блоков работ; выключенная `PrimaryButton` бледнеет целиком; поле ввода занимает всю ширину `FieldBox`.*

### Этап 7 — Реальное хранилище (SQLDelight) и связка presentation ↔ data через domain
- ✅ Подключить `app.cash.sqldelight` plugin `2.4.0` (`apply false` в корневом `build.gradle.kts`, применяется в `shared/build.gradle.kts`) + `app.cash.sqldelight:runtime`, `coroutines-extensions` (`commonMain`), `android-driver` (`androidMain`), `native-driver` (`iosMain`). *Дополнительно: `koin-android` (нужен `androidContext()` для драйвера) и `-lsqlite3` в `OTHER_LDFLAGS` таргета `iosApp` — без него статический фреймворк с native-драйвером не линкуется.*
- ✅ `.sq`-схемы в `data/local/` по схеме из README: `cars`, `maintenance_tasks`, `service_history` (+ таблица/key-value для настроек из этапа 6). *Лежат в `shared/src/commonMain/sqldelight/ru/kuznetsov/pitstop/data/local/`; настройки — таблица `settings` из одной строки. Внешних ключей нет: связанные строки удаляются явно в транзакции. Даты и время хранятся как ISO-текст.*
- ✅ `expect/actual DatabaseDriverFactory` (Android: `AndroidSqliteDriver` с `Context` из Koin; iOS: `NativeSqliteDriver`).
- ✅ `data/repository/*RepositoryImpl` на SQLDelight + `data/mapper/` для маппинга строк в domain-модели. *`id` — случайные UUID; `markTaskDone` читает пробег и обновляет работу в одной транзакции.*
- ✅ Подменить binding'и в `dataModule` (этап 4): `Fake*RepositoryImpl` → реальные `*RepositoryImpl` — `ViewModel`/экраны из этапа 6 не меняются вообще, потому что изначально зависели от `domain`-интерфейсов, а не от конкретной реализации. Это и есть прямая реализация "свяжем presentation и data слои через domain" из исходного запроса. *Фейковые репозитории и `FakeSeed` удалены — приложение стартует с пустой базой; `DatabaseDriverFactory` регистрируется в новом `expect/actual platformModule`.*
- ✅ Проверка: тот же ручной прогон, что в этапе 6, но с проверкой, что данные переживают перезапуск приложения. *iOS: UI-тест `ScreensWalkthroughUITests` переписан под пустую базу и дважды перезапускает приложение — машины, работы, пробег, история и настройки сохраняются, удалённое авто не возвращается. Android: на эмуляторе проверен только запуск с пустой базой и сохранение добавленного авто после перезапуска.*

### Этап 7а — Редактирование авто и работ
**Библиотеки:** не требуются.
- ✅ Макет `docs/index.html`: экран «07 · Редактирование авто» в обоих статичных наборах; в интерактивном макете карандаш на экране авто открывает заполненную форму, «Сохранить» применяет изменения.
- ✅ `NormalizeTaskUseCase` — общие правила работы из формы (вынесены из `AddCarUseCase`), `NormalizeTaskUseCaseTest` — 11 тестов.
- ✅ `UpdateCarUseCase`, `AddTaskUseCase`, `UpdateTaskUseCase`, `DeleteTaskUseCase` — реализованы вместо заготовок.
- ✅ `features/carform/` (бывший `addcar`) — одна форма на добавление и правку: `CarFormViewModel(carId?)` заполняет форму машиной и её работами, при сохранении обновляет авто и пробег, удаляет, переписывает и добавляет работы.
- ✅ Маршрут `Destination.EditCar` (`cars/{carId}/edit`), карандаш «Редактировать авто» на экране `cardetail`.
- ⬜ Проверка: прогон правки на симуляторе/эмуляторе. *Собрано (`:androidApp:assembleDebug`), юнит-тесты на `testAndroidHostTest` зелёные. На iOS-симуляторе подтверждено только открытие заполненной формы (заголовок, марка, пробег, счётчик работ, блок работы). Изменение полей, удаление и добавление работ и сохранение на устройстве не проверены: шаг правки в `ScreensWalkthroughUITests` написан, но падает на вводе в поле блока работы и не закоммичен.*
- ⬜ Правка записи истории ТО (стоимость и сервис) — решение принято, экрана и `UpdateHistoryEntryUseCase` нет.

### Этап 8 — Локальные уведомления
- ⬜ Android: `WorkManager`/`AlarmManager` для периодической проверки статусов (опц. `androidx.work:work-runtime-ktx`, только `androidMain`).
- ⬜ iOS: `UNUserNotificationCenter` через `expect/actual`, без новых библиотек.
- ⬜ Источник данных для уведомлений — `CalculateTaskStatusUseCase`/`CarAlertsUseCase` из этапа 5.
- ⬜ Анти-спам правило по README (не напоминать об одной и той же работе чаще N дней).

### Этап 9 — Полировка
Без новых библиотек.
- ⬜ QA тёмной темы на всех экранах.
- ⬜ Анимации (`ProgressBar`/`GaugeRing` transitions).
- ⬜ iOS-специфика: safe area, жесты навигации (Compose insets уже прокинуты через `.ignoresSafeArea()`).

### Этап 10 — Тестирование и релиз
- ⬜ Расширение `commonTest` (сортировка, граничные случаи дат).
- ⬜ Прогон на реальных Android/iOS устройствах.
- ⬜ Сборка Google Play / TestFlight.

## Сводная таблица "когда что подключаем"

| Этап | Библиотека | Где |
|---|---|---|
| 1 | — (Compose MP уже подключён) | — |
| 2 | `com.composables:icons-lucide:1.1.0` | `commonMain` |
| 3 | `org.jetbrains.androidx.navigation:navigation-compose:2.10.0-beta01` | `commonMain` |
| 4 | `io.insert-koin:koin-bom:4.2.2` + core/compose/compose-viewmodel | `commonMain` |
| 5 | `kotlinx-coroutines-core:1.11.0`, `kotlinx-datetime:0.8.0` | `commonMain` |
| 6 | — | — |
| 7 | `app.cash.sqldelight:2.4.0` (plugin + runtime/coroutines-extensions/android-driver/native-driver) | plugin: root+`shared`; driver-deps: `androidMain`/`iosMain` |
| 8 | (опц.) `androidx.work:work-runtime-ktx` | `androidMain` |

## Верификация

- После этапа 2: превью всех компонентов в обеих темах рендерятся без ошибок (`./gradlew :shared:assembleDebug` или Preview в IDE).
- После этапа 6: ручной клик-тест всех 5 экранов и переходов между ними на Android-эмуляторе, на фейковых данных.
- После этапа 5 и расширений в этапе 10: `./gradlew :shared:testAndroidHostTest` / `:shared:iosSimulatorArm64Test` — юнит-тесты бизнес-логики статусов зелёные.
- После этапа 7: перезапуск приложения — данные (добавленное авто, отметка "выполнено", изменённый пробег) сохраняются.
- Критерий готовности MVP (из `План_разработки_Одометр.md`) не меняется: этапы 1–7 данного плана = этапы 1–7 исходного, завершаются рабочим приложением на Android и iOS с корректным расчётом статусов и реальным хранилищем.