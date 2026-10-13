# Спецификация: data-слой

Пакет: `ru.kuznetsov.pitstop.data` (`shared/src/commonMain/kotlin/ru/kuznetsov/pitstop/data/`), схема базы — `shared/src/commonMain/sqldelight/`, платформенные части — `shared/src/androidMain/` и `shared/src/iosMain/`.

Data-слой реализует интерфейсы из `domain/repository/` (см. `specs/domain.md`): хранит машины, работы, историю ТО, настройки и план уведомлений в локальной базе SQLite и ставит локальные уведомления в очередь платформы. Сети нет. Из библиотек использует SQLDelight (`runtime`, `coroutines-extensions`, драйверы `android-driver` и `native-driver`), `kotlinx-coroutines-core`, `kotlinx-datetime`, `kotlin.uuid`, ресурсы Compose Multiplatform (для текста уведомлений); на Android — `androidx.work` и `androidx.core`, на iOS — `UserNotifications`.

```
data/
├── local/          # expect DatabaseDriverFactory, имя файла базы
├── mapper/         # строка таблицы → domain-модель
├── repository/     # *RepositoryImpl на SQLDelight
└── notification/   # ReminderSchedulerImpl, LocalNotification, expect LocalNotifications

sqldelight/
├── ru/kuznetsov/pitstop/data/local/*.sq   # таблицы и именованные запросы
└── migrations/1.sqm                       # миграция 1 → 2
```

Правила слоя:

- Наружу видны только интерфейсы domain-слоя; presentation о классах `data` не знает. Связывает их Koin (`dataModule`, `platformModule`).
- Чтение — `Flow` поверх запросов SQLDelight, он переизлучает результат при каждом изменении затронутых таблиц. Запись — `suspend`-методы.
- Вся работа с базой идёт на `Dispatchers.Default`: записи — через `withContext(dispatcher)`, чтение потоков — через `mapToList(dispatcher)` / `mapToOneOrNull(dispatcher)`.
- Идентификаторы машин, работ и записей истории создаёт репозиторий: `Uuid.random().toString()` (`kotlin.uuid`, `@OptIn(ExperimentalUuidApi::class)`).
- Бизнес-правил в слое нет: проверка и нормализация данных — в use case-ах.

## База данных

SQLDelight, база `PitStopDatabase`, пакет сгенерированного кода — `ru.kuznetsov.pitstop.data.local` (`shared/build.gradle.kts`). Файл базы — `pitstop.db` (`DATABASE_NAME` в `data/local/DatabaseDriverFactory.kt`). Пять таблиц, каждая в своём `.sq`-файле.

Как хранятся значения:

| Что | Как |
|---|---|
| идентификаторы | `TEXT`, строка UUID |
| целые числа (пробег, интервалы, стоимость) | `INTEGER`; в Kotlin — `Long`, мапперы приводят к `Int` |
| дата (`LocalDate`) | `TEXT`, ISO-8601 из `toString()`: `2026-10-08` |
| время (`LocalTime`) | `TEXT`, ISO-8601 из `toString()`: `09:00` |
| дата и время (`LocalDateTime`) | `TEXT`, ISO-8601 из `toString()`: `2026-10-08T09:00`; в часовом поясе устройства, без смещения |
| `Boolean` | `INTEGER AS kotlin.Boolean` |
| перечисления (`ThemeMode`, `DistanceUnit`) | `TEXT`, имя константы (`name`) |
| расстояния | всегда в километрах |

Внешних ключей в схеме нет: `car_id` и `task_id` — обычные текстовые колонки. Целостность поддерживает код репозиториев и use case-ов (удаление машины само удаляет её работы; историю удаляет отдельный вызов).

Порядок строк: во всех списочных запросах, кроме плана уведомлений, — `ORDER BY rowid`, то есть порядок добавления.

### `cars`

`Car.sq`. Машины.

| Колонка | Тип | NULL | Смысл |
|---|---|---|---|
| `id` | `TEXT` | нет | первичный ключ |
| `brand` | `TEXT` | нет | марка и модель |
| `mileage_km` | `INTEGER` | нет | текущий пробег |
| `vin` | `TEXT` | да | VIN |

| Запрос | SQL |
|---|---|
| `selectAll` | `SELECT * FROM cars ORDER BY rowid` |
| `selectById` | `SELECT * FROM cars WHERE id = ?` |
| `insert` | `INSERT INTO cars (id, brand, mileage_km, vin) VALUES (?, ?, ?, ?)` |
| `updateMileage` | `UPDATE cars SET mileage_km = ? WHERE id = ?` |
| `updateDetails` | `UPDATE cars SET brand = ?, vin = ? WHERE id = ?` |
| `deleteById` | `DELETE FROM cars WHERE id = ?` |

### `maintenance_tasks`

`MaintenanceTask.sq`. Регламентные работы. Индекс `maintenance_tasks_car_id` по `car_id`.

| Колонка | Тип | NULL | Смысл |
|---|---|---|---|
| `id` | `TEXT` | нет | первичный ключ |
| `car_id` | `TEXT` | нет | машина-владелец |
| `name` | `TEXT` | нет | название работы |
| `interval_km` | `INTEGER` | нет | интервал по пробегу |
| `last_done_km` | `INTEGER` | нет | пробег последнего выполнения |
| `reminder_window_km` | `INTEGER` | нет | окно напоминания |
| `interval_months` | `INTEGER` | да | интервал по времени |
| `last_done_date` | `TEXT` | да | дата последнего выполнения |

| Запрос | SQL |
|---|---|
| `selectAll` | `SELECT * FROM maintenance_tasks ORDER BY rowid` |
| `selectByCar` | `SELECT * FROM maintenance_tasks WHERE car_id = ? ORDER BY rowid` |
| `selectById` | `SELECT * FROM maintenance_tasks WHERE id = ? AND car_id = ?` |
| `insert` | `INSERT` всех восьми колонок |
| `update` | `UPDATE … SET name, interval_km, last_done_km, reminder_window_km, interval_months, last_done_date WHERE id = ? AND car_id = ?` |
| `markDone` | `UPDATE … SET last_done_km = ?, last_done_date = ? WHERE id = ? AND car_id = ?` |
| `deleteById` | `DELETE … WHERE id = ? AND car_id = ?` |
| `deleteByCar` | `DELETE … WHERE car_id = ?` |

Запросы по одной работе всегда уточняются парой `id` + `car_id`: работа чужой машины не затрагивается.

### `service_history`

`ServiceHistory.sq`. Журнал ТО. Индекс `service_history_car_id` по `car_id`.

| Колонка | Тип | NULL | Смысл |
|---|---|---|---|
| `id` | `TEXT` | нет | первичный ключ |
| `car_id` | `TEXT` | нет | машина |
| `task_name` | `TEXT` | нет | название работы текстом, без ссылки на работу |
| `date` | `TEXT` | нет | дата выполнения |
| `mileage_km` | `INTEGER` | нет | пробег на момент выполнения |
| `cost_rub` | `INTEGER` | да | стоимость |
| `service_name` | `TEXT` | да | где делали |

| Запрос | SQL |
|---|---|
| `selectByCar` | `SELECT * FROM service_history WHERE car_id = ? ORDER BY rowid` |
| `insert` | `INSERT` всех семи колонок |
| `update` | `UPDATE … SET task_name, date, mileage_km, cost_rub, service_name WHERE id = ?` |
| `deleteByCar` | `DELETE … WHERE car_id = ?` |

### `settings`

`Settings.sq`. Таблица из одной строки: `id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1)`.

| Колонка | Тип | NULL | Смысл |
|---|---|---|---|
| `id` | `INTEGER` | нет | первичный ключ, всегда 1 |
| `theme_mode` | `TEXT` | нет | имя `ThemeMode` |
| `distance_unit` | `TEXT` | нет | имя `DistanceUnit` |
| `push_enabled` | `INTEGER AS kotlin.Boolean` | нет | включены ли push-уведомления |
| `default_reminder_window_km` | `INTEGER` | нет | окно напоминания по умолчанию |
| `reminder_time` | `TEXT` | нет | время ежедневной проверки |

| Запрос | SQL |
|---|---|
| `select` | `SELECT * FROM settings WHERE id = 1` |
| `upsert` | `INSERT OR REPLACE INTO settings (id, …) VALUES (1, ?, ?, ?, ?, ?)` |

Пока пользователь ничего не менял, таблица пуста.

### `planned_reminders`

`PlannedReminder.sq`. След плана уведомлений (domain-модель `ReminderRecord`). Первичного ключа и индексов нет.

| Колонка | Тип | NULL | Смысл |
|---|---|---|---|
| `task_id` | `TEXT` | нет | работа, о которой уведомление |
| `fire_at` | `TEXT` | нет | момент показа |

| Запрос | SQL |
|---|---|
| `selectAll` | `SELECT * FROM planned_reminders ORDER BY fire_at` |
| `insert` | `INSERT INTO planned_reminders (task_id, fire_at) VALUES (?, ?)` |
| `deleteAll` | `DELETE FROM planned_reminders` |

Сортировка по `fire_at` — текстовая; для ISO-строк одного формата она совпадает с хронологической.

### Версия схемы и миграция

Версия схемы — 2 (`PitStopDatabase.Schema.version`). Единственная миграция — `migrations/1.sqm` — переводит базу с версии 1 на 2: создаёт таблицу `planned_reminders`.

- Новая установка: драйвер создаёт все пять таблиц из `.sq`-файлов сразу в версии 2.
- Обновление с версии 1: драйвер выполняет `1.sqm`.

Создание и миграцию запускают сами драйверы при открытии базы по переданной `PitStopDatabase.Schema`; своего кода миграции в проекте нет.

## Мапперы

`data/mapper/`. `internal`-расширения `toDomain()` для сгенерированных классов строк. Обратных мапперов нет: при записи репозитории передают поля в запрос напрямую.

| Функция | Из | В | Преобразования |
|---|---|---|---|
| `Cars.toDomain(tasks)` | `Cars` + список `Maintenance_tasks` | `Car` | `mileage_km` → `Int`; работы — через `Maintenance_tasks.toDomain()` |
| `Maintenance_tasks.toDomain()` | `Maintenance_tasks` | `MaintenanceTask` | числа → `Int`; `last_done_date` → `LocalDate.parse`, `null` остаётся `null`; `car_id` в модель не попадает |
| `Service_history.toDomain()` | `Service_history` | `ServiceHistoryEntry` | `date` → `LocalDate.parse`; `mileage_km`, `cost_rub` → `Int` |
| `SettingsRow.toDomain()` | `Settings` (строка таблицы, импортирована под именем `SettingsRow`) | `Settings` | `ThemeMode.valueOf`, `DistanceUnit.valueOf`, `LocalTime.parse`; `default_reminder_window_km` → `Int` |

`ReminderRecord` собирается без маппера, прямо в `ReminderRepositoryImpl`: `ReminderRecord(task_id, LocalDateTime.parse(fire_at))`.

## Репозитории

`data/repository/`. Каждый получает в конструктор `PitStopDatabase` и берёт из неё нужные `*Queries`.

### `CarRepositoryImpl`

Реализует `CarRepository`. Работает с `carQueries` и `maintenanceTaskQueries`. Каждая запись, которая трогает больше одной строки, идёт одной транзакцией.

| Метод | Что выполняет |
|---|---|
| `observeCars()` | `combine` двух потоков: `cars.selectAll` и `maintenance_tasks.selectAll`. Работы группируются по `car_id` и раскладываются по машинам; машина без работ получает пустой список |
| `observeCarById(carId)` | `combine` потоков `cars.selectById(carId)` (`mapToOneOrNull`) и `maintenance_tasks.selectByCar(carId)`. Нет строки машины → `null` |
| `addCar(car)` | создаёт `id` машины; в одной транзакции — `cars.insert` и `maintenance_tasks.insert` на каждую работу; возвращает `id` машины |
| `deleteCar(carId)` | в одной транзакции — `maintenance_tasks.deleteByCar`, затем `cars.deleteById` |
| `updateMileage(carId, mileageKm)` | `cars.updateMileage` |
| `markTaskDone(carId, taskId, date)` | в одной транзакции: читает машину (`cars.selectById`) и работу (`maintenance_tasks.selectById`); если чего-то нет — выходит без изменений; иначе `markDone` с `last_done_km` = текущий пробег машины и `last_done_date` = `date`, если у работы есть `interval_months`, иначе прежнее значение |
| `updateCar(carId, brand, vin)` | `cars.updateDetails` |
| `addTask(carId, task)` | `maintenance_tasks.insert` с новым `id` |
| `updateTask(carId, task)` | `maintenance_tasks.update` всех полей работы по `id` + `car_id` |
| `deleteTask(carId, taskId)` | `maintenance_tasks.deleteById` по `id` + `car_id` |

- Вставка работы (приватный `insertTask`): `id` — новый UUID; `reminderWindowKm == null` записывается как `0`; `lastDoneDate` — через `toString()`.
- Порядок машин и работ — порядок добавления (`rowid`).
- Потоки строятся из двух независимых запросов: изменение любой из двух таблиц даёт новое значение. Запись, затронувшая обе таблицы (например, `addCar`), может дать два значения подряд.
- Обновление или удаление по несуществующему `id` ошибкой не считается — запрос просто не затронет строк.

### `ServiceHistoryRepositoryImpl`

Реализует `ServiceHistoryRepository`. Работает с `serviceHistoryQueries`.

| Метод | Что выполняет |
|---|---|
| `observeHistory(carId)` | поток `selectByCar(carId)`, строки — через `toDomain()`; порядок добавления (от новых к старым сортирует `ObserveHistoryUseCase`) |
| `addEntry(entry)` | `insert` с новым `id` |
| `updateEntry(entry)` | `update` всех полей, кроме `car_id`, по `id` |
| `deleteHistory(carId)` | `deleteByCar` |

Транзакций нет: каждый метод — один запрос.

### `SettingsRepositoryImpl`

Реализует `SettingsRepository`. Работает с `settingsQueries`.

| Метод | Что выполняет |
|---|---|
| `observeSettings()` | поток `select` (`mapToOneOrNull`); строки нет → настройки по умолчанию |
| `updateSettings(settings)` | `upsert` всей строки с `id = 1` |

Настройки по умолчанию (`defaultSettings`) в базу не записываются — они подставляются при чтении, пока таблица пуста:

| Поле | Значение |
|---|---|
| `themeMode` | `ThemeMode.SYSTEM` |
| `distanceUnit` | `DistanceUnit.KILOMETERS` |
| `pushEnabled` | `true` |
| `defaultReminderWindowKm` | `2 000` |
| `reminderTime` | `09:00` |

### `ReminderRepositoryImpl`

Реализует `ReminderRepository`. Работает с `plannedReminderQueries`. Потоков нет — только разовые чтение и запись.

| Метод | Что выполняет |
|---|---|
| `getRecords()` | `selectAll().executeAsList()`, строки → `ReminderRecord`; порядок — по `fire_at` |
| `replaceRecords(records)` | в одной транзакции — `deleteAll`, затем `insert` на каждую запись |

## Драйвер базы

`data/local/DatabaseDriverFactory.kt`:

```kotlin
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}
```

| Платформа | Конструктор | `create()` |
|---|---|---|
| Android | `DatabaseDriverFactory(context: Context)` | `AndroidSqliteDriver(PitStopDatabase.Schema, context, "pitstop.db")` |
| iOS | `DatabaseDriverFactory()` | `NativeSqliteDriver(PitStopDatabase.Schema, "pitstop.db")` |

Параметров сверх схемы и имени файла драйверам не передаётся. `PitStopDatabase` создаётся один раз на процесс — как `single` в `dataModule`, при первом обращении к любому репозиторию.

## Уведомления

Локальные уведомления без сервера. Что и на когда ставить, решает domain (`PlanRemindersUseCase`, `SyncRemindersUseCase`); data-слой превращает план в тексты и передаёт платформе.

```
SyncRemindersUseCase ──▶ ReminderScheduler.schedule(reminders, distanceUnit)
                              │  ReminderSchedulerImpl: PlannedReminder → LocalNotification
                              ▼
                         LocalNotifications.replaceAll(notifications)
                              ├─ Android: WorkManager → ReminderWorker → NotificationManagerCompat
                              └─ iOS: UNUserNotificationCenter
```

### `ReminderSchedulerImpl`

`data/notification/ReminderSchedulerImpl.kt`. Реализует `ReminderScheduler`; зависит от `LocalNotifications`.

`schedule(reminders, distanceUnit)` на каждый `PlannedReminder` строит `LocalNotification` и передаёт весь список в `LocalNotifications.replaceAll`. Пустой список тоже передаётся — это отмена всего запланированного.

| Поле `LocalNotification` | Тип | Откуда |
|---|---|---|
| `id` | `String` | `"{taskId}@{fireAt}"` — уникален в пределах одного плана |
| `title` | `String` | ресурс `app_name` |
| `text` | `String` | ресурс `notification_reminder_text` — «{марка}: {работа} — {состояние}» |
| `fireAt` | `LocalDateTime` | `PlannedReminder.fireAt`, в часовом поясе устройства |

Состояние в тексте — первое подходящее правило:

| Условие | Ресурс | Подстановка |
|---|---|---|
| `remainingKm ≤ 0` | `task_overdue_by` | расстояние `−remainingKm` |
| `remainingMonths != null` и `remainingMonths ≤ 0` | `task_time_expired` | — |
| иначе | `task_remaining` | расстояние `remainingKm` |

Расстояние — ресурс `mileage_km` или `distance_mi` (по `distanceUnit`) с числом после `kmIn(distanceUnit).grouped()`.

Строки читаются `suspend`-функцией `getString` из ресурсов Compose Multiplatform — на языке устройства в момент планирования. Текст уже поставленного уведомления после смены языка не меняется до следующей пересборки плана.

### `LocalNotifications`

`data/notification/LocalNotifications.kt`:

```kotlin
expect class LocalNotifications {
    fun replaceAll(notifications: List<LocalNotification>)
}
```

`replaceAll` — «снять всё, что ещё не показано, и поставить переданное». Уже показанные уведомления не трогаются ни на одной платформе.

| | Android | iOS |
|---|---|---|
| Файл | `LocalNotifications.android.kt`, `ReminderWorker.kt` | `LocalNotifications.ios.kt` |
| Конструктор | `LocalNotifications(context: Context)` | `LocalNotifications()` |
| Механизм | `WorkManager`: одно уведомление — одна отложенная разовая задача `ReminderWorker` | `UNUserNotificationCenter`: одно уведомление — один ожидающий запрос |
| «Снять всё» | `cancelAllWorkByTag("task-reminder")` | `removeAllPendingNotificationRequests()` |
| Момент показа | задержка `fireAt − сейчас` в миллисекундах, не меньше 0; `fireAt` переводится в момент времени по текущему часовому поясу при планировании | `UNCalendarNotificationTrigger` по компонентам год, месяц, день, час, минута; `repeats = false` |
| Идентификатор | `LocalNotification.id` во входных данных задачи | `LocalNotification.id` — идентификатор запроса |
| Содержимое | заголовок и текст во входных данных задачи | `title`, `body`, звук `UNNotificationSound.defaultSound` |
| Без разрешения | задача выполняется и ничего не показывает | запросы отбрасывает система |

#### Android: `ReminderWorker`

`CoroutineWorker`, показывает одно уведомление, когда истекает задержка. Входные данные: `KEY_ID = "id"`, `KEY_TITLE = "title"`, `KEY_TEXT = "text"`; тег задачи — `TAG = "task-reminder"`.

| Шаг | Что происходит |
|---|---|
| 1 | нет `id` во входных данных → `Result.failure()` |
| 2 | `NotificationManagerCompat.areNotificationsEnabled()` возвращает `false` → `Result.success()` без показа |
| 3 | на API 26+ создаётся канал `task-reminders` с важностью `IMPORTANCE_DEFAULT`; имя канала — заголовок уведомления |
| 4 | собирается уведомление: маленькая иконка `R.drawable.ic_notification` (`shared/src/androidMain/res/drawable/`), заголовок, текст, `BigTextStyle`, `setAutoCancel(true)`; по тапу открывается приложение (`getLaunchIntentForPackage`, `PendingIntent.FLAG_IMMUTABLE`) |
| 5 | `notify(id.hashCode(), notification)`; `SecurityException` проглатывается; итог — `Result.success()` |

Разрешение `android.permission.POST_NOTIFICATIONS` объявлено в `androidApp/src/main/AndroidManifest.xml`. Своей настройки `WorkManager` в приложении нет — используется инициализация по умолчанию. Задачи `WorkManager` переживают перезагрузку устройства.

#### iOS

Запросы добавляются через `addNotificationRequest(…, withCompletionHandler = null)` — ошибка добавления не обрабатывается. Разрешение запрашивает presentation-слой (`rememberNotificationPermissionRequest`, см. `specs/presentation.md`) с опциями «баннер» и «звук». После выдачи разрешения `AppViewModel` пересобирает план, чтобы запросы, отброшенные системой до этого, были поставлены заново.

## DI

### `dataModule`

`di/DataModule.kt`. Всё — `single`.

| Что | Регистрация |
|---|---|
| `PitStopDatabase` | `single { PitStopDatabase(get<DatabaseDriverFactory>().create()) }` |
| `CarRepository` | `singleOf(::CarRepositoryImpl) bind CarRepository::class` |
| `ServiceHistoryRepository` | `singleOf(::ServiceHistoryRepositoryImpl) bind …` |
| `SettingsRepository` | `singleOf(::SettingsRepositoryImpl) bind …` |
| `ReminderRepository` | `singleOf(::ReminderRepositoryImpl) bind …` |
| `ReminderScheduler` | `singleOf(::ReminderSchedulerImpl) bind …` |

### `platformModule`

`di/PlatformModule.kt` — `expect val platformModule: Module`.

| Платформа | Содержимое |
|---|---|
| Android (`PlatformModule.android.kt`) | `single { DatabaseDriverFactory(androidContext()) }`, `single { LocalNotifications(androidContext()) }` |
| iOS (`PlatformModule.ios.kt`) | `single { DatabaseDriverFactory() }`, `single { LocalNotifications() }` |

### `initKoin`

`di/Koin.kt`:

```kotlin
fun initKoin() = initKoin {}

fun initKoin(appDeclaration: KoinAppDeclaration) {
    startKoin {
        appDeclaration()
        modules(featureModule, domainModule, dataModule, platformModule)
    }
}
```

| Платформа | Где вызывается | Как |
|---|---|---|
| Android | `PitStopApplication.onCreate()` (`androidApp/`) | `initKoin { androidContext(this@PitStopApplication) }` — контекст нужен `platformModule` |
| iOS | `iOSApp.init()` (`iosApp/iosApp/iOSApp.swift`) | `KoinKt.doInitKoin()` — вариант без параметров |

## Согласованные изменения (этап 8а)

Всё в этом разделе **согласовано, но в коде ещё не сделано**; остальной документ описывает код как есть. Правила — в `specs/domain.md`, порядок работ — в `dev_plan.md`. Имена колонок предварительные.

### `service_history`: новые колонки

| Колонка | Тип | NULL | Смысл |
|---|---|---|---|
| `task_id` | `TEXT` | да | работа, отметка которой создала запись; пусто у записей, созданных до миграции |
| `comment` | `TEXT` | да | комментарий из окна «Работа выполнена» |
| `prev_last_done_km` | `INTEGER` | да | `last_done_km` работы до отметки — для отката |
| `prev_last_done_date` | `TEXT` | да | `last_done_date` работы до отметки — для отката |

- Миграция `2.sqm` добавляет колонки к существующей таблице; схема становится версии 3.
- У старых записей все четыре колонки пустые: отметку по ним снять нельзя.
- Мапперы `ServiceHistoryMapper` и запросы `insert`/`update` расширяются на новые поля; нужен запрос удаления одной записи по `id`.

### `settings`: новые колонки

| Колонка | Тип | NULL | Смысл |
|---|---|---|---|
| `last_opened_car_id` | `TEXT` | да | авто, карточку которого открывали последней |
| `open_default_car` | `INTEGER AS kotlin.Boolean` | нет, по умолчанию 0 | включён ли пункт «Открывать по умолчанию» |
| `default_car_id` | `TEXT` | да | авто, выбранное в настройках |

Добавляются той же миграцией `2.sqm`. Внешних ключей по-прежнему нет: ссылки на удалённое авто сбрасывает domain.

### Проданные авто

Предварительно: в `cars` добавляется признак удаления (`is_sold INTEGER AS kotlin.Boolean`, по умолчанию 0), той же миграцией `2.sqm`.

- Удаление авто удаляет его строки в `maintenance_tasks` и выставляет признак; строка `cars` и записи `service_history` остаются.
- `selectAll`/`selectById` для списка авто и карточки отдают только неудалённые; для истории нужен запрос всех записей с маркой авто и признаком (соединение `service_history` с `cars`).
- Альтернатива, если строку авто хранить не нужно, — колонка с маркой прямо в `service_history`; выбор между вариантами не сделан.

### Отметка и снятие одной транзакцией

- Отметка: обновление `maintenance_tasks` (`markDone`) и вставка в `service_history` — в одной транзакции.
- Снятие: возврат `last_done_km` и `last_done_date` работы из `prev_*` и удаление записи истории — в одной транзакции.
- Сейчас эти таблицы пишут два разных репозитория; операция переезжает в одно место data-слоя.

### Что не меняется

Группировка истории по годам и месяцам новых запросов не требует: экран читает тот же `selectByCar`. Правка записи из окна истории идёт через уже существующие `ServiceHistoryRepository.updateEntry` и запрос `update`, расширенные на `comment`.

Правка названия, VIN и пробега на экране авто и экран одной работы используют уже существующие запросы и методы `CarRepositoryImpl` (`updateDetails`, `updateMileage`, вставка, обновление и удаление работы) — новых таблиц и запросов под них не нужно.

## Открытые вопросы

- **Data зависит от UI.** `ReminderSchedulerImpl` импортирует `ru.kuznetsov.pitstop.ui.format.grouped` и `kmIn` (`ReminderSchedulerImpl.kt:15-16`) и повторяет у себя логику `distanceText` из `ui/format/Formatting.kt` — тот вариант `@Composable` и из data недоступен. Форматирование расстояний живёт в двух местах.
- **Нет внешних ключей.** Работы удаляются вместе с машиной вручную в `deleteCar`, а история — отдельным вызовом другого репозитория из `DeleteCarUseCase`, вне этой транзакции. Сбой между вызовами оставит «осиротевшие» записи истории. В `planned_reminders` записи удалённых работ остаются до следующей пересборки плана.
- **`planned_reminders` без первичного ключа**: ничто не мешает одинаковым строкам.
- **Ввод-вывод на `Dispatchers.Default`**, а не на `Dispatchers.IO` — во всех четырёх репозиториях.
- **Окно напоминания `null` записывается как `0`** (`CarRepositoryImpl.kt:107`). Репозиторий полагается на то, что `NormalizeTaskUseCase` уже подставил значение; работа, дошедшая сюда без окна, по пробегу никогда не станет `SOON`.
- **Перечисления хранятся именами** и читаются через `valueOf` (`SettingsMapper.kt:10-11`): переименование константы `ThemeMode` или `DistanceUnit` уронит чтение настроек у тех, у кого старое имя уже сохранено. То же с форматом дат: `LocalDate.parse` / `LocalTime.parse` / `LocalDateTime.parse` бросают исключение на строке другого формата.
- **Проверки миграций нет**: снимков схемы прежних версий в репозитории нет, `verifyMigrations` в `shared/build.gradle.kts` не включён.
- **Текст уведомления не упоминает срок по времени, пока он не истёк.** Работа, ставшая `SOON` только из-за интервала по времени, получит текст «осталось N км» (`ReminderSchedulerImpl.kt:46-54`).
- **Android: уведомления об одной работе накапливаются.** Идентификатор показа — `hashCode()` строки `"{taskId}@{fireAt}"` (`ReminderWorker.kt:46`), он разный для разных дней, поэтому новое уведомление не заменяет предыдущее по той же работе.
- **Android: имя канала — заголовок уведомления**, то есть название приложения (`ReminderWorker.kt:28-31`); отдельной локализованной строки для канала нет. Канал создаётся при каждом срабатывании задачи.
- **Android: задержка считается один раз при планировании.** Смена часового пояса или системного времени не сдвинет уже поставленные задачи до следующей пересборки плана. На iOS триггер календарный и привязан к показаниям часов.
- **Android: до 60 отдельных вызовов `enqueue`** на каждую пересборку плана, а пересборка идёт после каждого изменения машин или настроек — в том числе после смены темы.
- **Android ниже 13: наличие разрешения проверяется только в `ReminderWorker`**, в момент показа. Если уведомления выключены в системе, задачи всё равно ставятся и отрабатывают впустую.
- **iOS: делегат `UNUserNotificationCenter` не назначен.** По умолчанию система не показывает баннер, пока приложение открыто на переднем плане, — уведомление, чьё время пришло в этот момент, пользователь может не увидеть.
- **iOS: ошибки постановки не обрабатываются** (`withCompletionHandler = null`), а domain считает «показанным» всё, чьё время прошло (см. `specs/domain.md`).
- **Остаток шаблона проекта**: `Platform.kt` с `expect fun getPlatform()` и его `actual`-реализации нигде не используются.

## Что ещё не реализовано

- `ServiceHistoryRepository.updateEntry` и запрос `service_history.update` готовы, но не вызываются: `UpdateHistoryEntryUseCase` — заготовка (см. `specs/domain.md`).
- Изменения из шага 3 блока «Этап 8а — Правки дизайна после прогона» в `dev_plan.md` — см. раздел «Согласованные изменения (этап 8а)» выше. Сейчас ничего из этого в коде нет.
- Юнит-тесты на репозитории, мапперы и `ReminderSchedulerImpl`; тесты миграции.
- Синхронизация, резервная копия средствами приложения, экспорт и импорт данных — данные живут только в локальном файле `pitstop.db`.
