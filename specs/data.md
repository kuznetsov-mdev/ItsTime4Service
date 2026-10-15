# Спецификация: data-слой

Пакет: `ru.kuznetsov.itstime4service.data` (`shared/src/commonMain/kotlin/ru/kuznetsov/itstime4service/data/`), схема базы — `shared/src/commonMain/sqldelight/`, платформенные части — `shared/src/androidMain/` и `shared/src/iosMain/`.

Data-слой реализует интерфейсы из `domain/repository/` (см. `specs/domain.md`): хранит машины, работы, историю ТО, настройки и план уведомлений в локальной базе SQLite и ставит локальные уведомления в очередь платформы. Сети нет. Из библиотек использует SQLDelight (`runtime`, `coroutines-extensions`, драйверы `android-driver` и `native-driver`), `kotlinx-coroutines-core`, `kotlinx-datetime`, `kotlin.uuid`, ресурсы Compose Multiplatform (для текста уведомлений); на Android — `androidx.work` и `androidx.core`, на iOS — `UserNotifications`.

```
data/
├── local/          # expect DatabaseDriverFactory, имя файла базы
├── mapper/         # строка таблицы → domain-модель
├── repository/     # *RepositoryImpl на SQLDelight
└── notification/   # ReminderSchedulerImpl, LocalNotification, expect LocalNotifications

sqldelight/
├── ru/kuznetsov/itstime4service/data/local/*.sq   # таблицы и именованные запросы
├── migrations/1.sqm                       # миграция 1 → 2
└── migrations/2.sqm                       # миграция 2 → 3
```

Правила слоя:

- Наружу видны только интерфейсы domain-слоя; presentation о классах `data` не знает. Связывает их Koin (`dataModule`, `platformModule`).
- Чтение — `Flow` поверх запросов SQLDelight, он переизлучает результат при каждом изменении затронутых таблиц. Запись — `suspend`-методы.
- Вся работа с базой идёт на `Dispatchers.Default`: записи — через `withContext(dispatcher)`, чтение потоков — через `mapToList(dispatcher)` / `mapToOneOrNull(dispatcher)`.
- Идентификаторы машин, работ и записей истории создаёт репозиторий: `Uuid.random().toString()` (`kotlin.uuid`, `@OptIn(ExperimentalUuidApi::class)`).
- Бизнес-правил в слое нет: проверка и нормализация данных — в use case-ах.

## База данных

SQLDelight, база `ItsTime4ServiceDatabase`, пакет сгенерированного кода — `ru.kuznetsov.itstime4service.data.local` (`shared/build.gradle.kts`). Файл базы — `itstime4service.db` (`DATABASE_NAME` в `data/local/DatabaseDriverFactory.kt`). Пять таблиц, каждая в своём `.sq`-файле.

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

Внешних ключей в схеме нет: `car_id` и `task_id` — обычные текстовые колонки. Целостность поддерживает код репозиториев: продажа и полное удаление машины сами удаляют её работы и (при полном удалении) историю в одной транзакции; ссылки на машину в настройках сбрасывает domain.

Порядок строк: во всех списочных запросах, кроме плана уведомлений, — `ORDER BY rowid`, то есть порядок добавления.

### `cars`

`Car.sq`. Машины — действующие и проданные.

| Колонка | Тип | NULL | Смысл |
|---|---|---|---|
| `id` | `TEXT` | нет | первичный ключ |
| `brand` | `TEXT` | нет | марка и модель |
| `mileage_km` | `INTEGER` | нет | текущий пробег |
| `vin` | `TEXT` | да | VIN |
| `is_sold` | `INTEGER AS kotlin.Boolean` | нет, по умолчанию 0 | машина удалена с её экрана, история сохранена |

| Запрос | SQL |
|---|---|
| `selectAll` | `SELECT * FROM cars WHERE is_sold = 0 ORDER BY rowid` |
| `selectById` | `SELECT * FROM cars WHERE id = ? AND is_sold = 0` |
| `selectAllWithSold` | `SELECT * FROM cars ORDER BY is_sold, rowid` — действующие, затем проданные |
| `insert` | `INSERT INTO cars (id, brand, mileage_km, vin) VALUES (?, ?, ?, ?)` |
| `updateMileage` | `UPDATE cars SET mileage_km = ? WHERE id = ?` |
| `updateDetails` | `UPDATE cars SET brand = ?, vin = ? WHERE id = ?` |
| `markSold` | `UPDATE cars SET is_sold = 1 WHERE id = ?` |
| `deleteById` | `DELETE FROM cars WHERE id = ?` |

Проданная машина невидима для `selectAll` и `selectById`: её нет в списке машин, экран машины для неё закрывается, отметка работ по ней невозможна.

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
| `markDone` | `UPDATE … SET last_done_km = ?, last_done_date = ? WHERE id = ? AND car_id = ?` — и для отметки, и для отката |
| `updateLastDoneKm` | `UPDATE … SET last_done_km = ? WHERE id = ? AND car_id = ?` — при правке пробега записи истории |
| `deleteById` | `DELETE … WHERE id = ? AND car_id = ?` |
| `deleteByCar` | `DELETE … WHERE car_id = ?` |

Запросы по одной работе всегда уточняются парой `id` + `car_id`: работа чужой машины не затрагивается.

### `service_history`

`ServiceHistory.sq`. Журнал ТО. Индекс `service_history_car_id` по `car_id`.

| Колонка | Тип | NULL | Смысл |
|---|---|---|---|
| `id` | `TEXT` | нет | первичный ключ |
| `car_id` | `TEXT` | нет | машина |
| `task_name` | `TEXT` | нет | название работы текстом |
| `date` | `TEXT` | нет | дата выполнения |
| `mileage_km` | `INTEGER` | нет | пробег на момент выполнения |
| `cost_rub` | `INTEGER` | да | стоимость |
| `service_name` | `TEXT` | да | где делали |
| `task_id` | `TEXT` | да | работа, отметка которой создала запись; пусто у записей до миграции на версию 3 |
| `comment` | `TEXT` | да | комментарий |
| `prev_last_done_km` | `INTEGER` | да | `last_done_km` работы до отметки — для отката |
| `prev_last_done_date` | `TEXT` | да | `last_done_date` работы до отметки — для отката |

| Запрос | SQL |
|---|---|
| `selectByCar` | `SELECT * FROM service_history WHERE car_id = ? ORDER BY rowid` |
| `selectAll` | `SELECT * FROM service_history ORDER BY rowid` |
| `countByCar` | `SELECT COUNT(*) FROM service_history WHERE car_id = ?` |
| `selectById` | `SELECT * FROM service_history WHERE id = ?` |
| `insert` | `INSERT` всех одиннадцати колонок |
| `update` | `UPDATE … SET task_name, date, mileage_km, cost_rub, service_name, comment WHERE id = ?` — колонки отката не меняются |
| `deleteById` | `DELETE … WHERE id = ?` |
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
| `reminder_time` | `TEXT` | нет | время ежедневных уведомлений |
| `last_opened_car_id` | `TEXT` | да | машина, экран которой открывали последним |
| `open_default_car` | `INTEGER AS kotlin.Boolean` | нет, по умолчанию 0 | включён ли пункт «Открывать по умолчанию» |
| `default_car_id` | `TEXT` | да | машина, выбранная для открытия при запуске |

| Запрос | SQL |
|---|---|
| `select` | `SELECT * FROM settings WHERE id = 1` |
| `upsert` | `INSERT OR REPLACE INTO settings (id, …) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)` — переписывает строку целиком |

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

Версия схемы — 3 (`ItsTime4ServiceDatabase.Schema.version`). Миграции:

- `migrations/1.sqm` — с версии 1 на 2: создаёт таблицу `planned_reminders`.
- `migrations/2.sqm` — с версии 2 на 3: добавляет колонки `task_id`, `comment`, `prev_last_done_km`, `prev_last_done_date` в `service_history`; `last_opened_car_id`, `open_default_car`, `default_car_id` в `settings`; `is_sold` в `cars`. У старых записей истории новые колонки пустые — снять отметку по ним нельзя.

Новая установка: драйвер создаёт все пять таблиц из `.sq`-файлов сразу в версии 3. Обновление: драйвер выполняет нужные миграции по порядку. Создание и миграцию запускают сами драйверы при открытии базы по переданной `ItsTime4ServiceDatabase.Schema`; своего кода миграции в проекте нет.

## Мапперы

`data/mapper/`. `internal`-расширения `toDomain()` для сгенерированных классов строк. Обратных мапперов нет: при записи репозитории передают поля в запрос напрямую.

| Функция | Из | В | Преобразования |
|---|---|---|---|
| `Cars.toDomain(tasks)` | `Cars` + список `Maintenance_tasks` | `Car` | `mileage_km` → `Int`; работы — через `Maintenance_tasks.toDomain()`; `is_sold` в модель не попадает |
| `Cars.toSummary()` | `Cars` | `CarSummary` | `mileage_km` → `Int`, `is_sold` → `isSold` |
| `Maintenance_tasks.toDomain()` | `Maintenance_tasks` | `MaintenanceTask` | числа → `Int`; `last_done_date` → `LocalDate.parse`, `null` остаётся `null`; `car_id` в модель не попадает |
| `Service_history.toDomain()` | `Service_history` | `ServiceHistoryEntry` | `date`, `prev_last_done_date` → `LocalDate.parse`; `mileage_km`, `cost_rub`, `prev_last_done_km` → `Int` |
| `SettingsRow.toDomain()` | `Settings` (строка таблицы, импортирована под именем `SettingsRow`) | `Settings` | `ThemeMode.valueOf`, `DistanceUnit.valueOf`, `LocalTime.parse`; `default_reminder_window_km` → `Int`; три колонки запуска — как есть |

`ReminderRecord` собирается без маппера, прямо в `ReminderRepositoryImpl`: `ReminderRecord(task_id, LocalDateTime.parse(fire_at))`.

## Репозитории

`data/repository/`. Каждый получает в конструктор `ItsTime4ServiceDatabase` и берёт из неё нужные `*Queries`.

### `CarRepositoryImpl`

Реализует `CarRepository`. Работает с `carQueries`, `maintenanceTaskQueries` и `serviceHistoryQueries`. Каждая запись, которая трогает больше одной строки или таблицы, идёт одной транзакцией.

| Метод | Что выполняет |
|---|---|
| `observeCars()` | `combine` двух потоков: `cars.selectAll` (без проданных) и `maintenance_tasks.selectAll`. Работы группируются по `car_id` и раскладываются по машинам; машина без работ получает пустой список |
| `observeCarById(carId)` | `combine` потоков `cars.selectById(carId)` (`mapToOneOrNull`) и `maintenance_tasks.selectByCar(carId)`. Нет строки или машина продана → `null` |
| `observeCarSummaries()` | поток `cars.selectAllWithSold`, строки → `toSummary()` |
| `addCar(car)` | создаёт `id` машины; в одной транзакции — `cars.insert` и `maintenance_tasks.insert` на каждую работу; возвращает `id` машины |
| `sellCar(carId)` | в одной транзакции: `maintenance_tasks.deleteByCar`; если `service_history.countByCar > 0` — `cars.markSold`, иначе `cars.deleteById` |
| `purgeCar(carId)` | в одной транзакции: `service_history.deleteByCar`, `maintenance_tasks.deleteByCar`, `cars.deleteById` |
| `updateMileage(carId, mileageKm)` | `cars.updateMileage` |
| `markTaskDone(…)` | в одной транзакции: читает машину и работу (нет — `false`); `markDone` с `last_done_km` = текущий пробег машины и `last_done_date` = `date`, если у работы есть `interval_months`, иначе прежнее значение; `service_history.insert` с новым `id`, `task_id`, переданными сервисом, стоимостью и комментарием и прежними `last_done_km`/`last_done_date` работы в `prev_*`; `true` |
| `unmarkTaskDone(carId, taskId, entryId)` | в одной транзакции: читает запись; если её нет, она другой машины или работы или `prev_last_done_km` пуст — `false`; иначе `markDone` с `prev_*` и `service_history.deleteById`; `true` |
| `updateCar(carId, brand, vin)` | `cars.updateDetails` |
| `addTask(carId, task)` | `maintenance_tasks.insert` с новым `id` |
| `updateTask(carId, task)` | `maintenance_tasks.update` всех полей работы по `id` + `car_id` |
| `deleteTask(carId, taskId)` | `maintenance_tasks.deleteById` по `id` + `car_id` |

- Вставка работы (приватный `insertTask`): `id` — новый UUID; `reminderWindowKm == null` записывается как `0`; `lastDoneDate` — через `toString()`.
- Порядок машин и работ — порядок добавления (`rowid`).
- Потоки строятся из двух независимых запросов: изменение любой из двух таблиц даёт новое значение. Запись, затронувшая обе таблицы (например, `addCar`), может дать два значения подряд.
- Обновление или удаление по несуществующему `id` ошибкой не считается — запрос просто не затронет строк.

### `ServiceHistoryRepositoryImpl`

Реализует `ServiceHistoryRepository`. Работает с `serviceHistoryQueries` и `maintenanceTaskQueries`.

| Метод | Что выполняет |
|---|---|
| `observeHistory(carId)` | поток `selectByCar(carId)`, строки — через `toDomain()`; порядок добавления |
| `observeAllHistory()` | поток `selectAll`; порядок добавления (от новых к старым сортирует `ObserveAllHistoryUseCase`) |
| `updateEntry(entry, taskLastDoneKm)` | в одной транзакции: `update` по `id`; если передан `taskLastDoneKm` и у записи есть `task_id` — `maintenance_tasks.updateLastDoneKm` этой работы |

### `SettingsRepositoryImpl`

Реализует `SettingsRepository`. Работает с `settingsQueries`.

| Метод | Что выполняет |
|---|---|
| `observeSettings()` | поток `select` (`mapToOneOrNull`); строки нет → настройки по умолчанию |
| `updateSettings(change)` | в одной транзакции: `select` (или настройки по умолчанию), `change`, `upsert` всей строки с `id = 1` |

Настройки по умолчанию (`defaultSettings`) в базу не записываются — они подставляются при чтении, пока таблица пуста:

| Поле | Значение |
|---|---|
| `themeMode` | `ThemeMode.SYSTEM` |
| `distanceUnit` | `DistanceUnit.KILOMETERS` |
| `pushEnabled` | `true` |
| `defaultReminderWindowKm` | `2 000` |
| `reminderTime` | `09:00` |
| `lastOpenedCarId`, `defaultCarId` | `null` |
| `openDefaultCar` | `false` |

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
| Android | `DatabaseDriverFactory(context: Context)` | `AndroidSqliteDriver(ItsTime4ServiceDatabase.Schema, context, "itstime4service.db")` |
| iOS | `DatabaseDriverFactory()` | `NativeSqliteDriver(ItsTime4ServiceDatabase.Schema, "itstime4service.db")` |

Параметров сверх схемы и имени файла драйверам не передаётся. `ItsTime4ServiceDatabase` создаётся один раз на процесс — как `single` в `dataModule`, при первом обращении к любому репозиторию.

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
| `ItsTime4ServiceDatabase` | `single { ItsTime4ServiceDatabase(get<DatabaseDriverFactory>().create()) }` |
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
| Android | `ItsTime4ServiceApplication.onCreate()` (`androidApp/`) | `initKoin { androidContext(this@ItsTime4ServiceApplication) }` — контекст нужен `platformModule` |
| iOS | `iOSApp.init()` (`iosApp/iosApp/iOSApp.swift`) | `KoinKt.doInitKoin()` — вариант без параметров |

## Открытые вопросы

- **Data зависит от UI.** `ReminderSchedulerImpl` импортирует `ru.kuznetsov.itstime4service.ui.format.grouped` и `kmIn` (`ReminderSchedulerImpl.kt:15-16`) и повторяет у себя логику `distanceText` из `ui/format/Formatting.kt` — тот вариант `@Composable` и из data недоступен. Форматирование расстояний живёт в двух местах.
- **Нет внешних ключей.** Связанные строки удаляются вручную в транзакциях `sellCar` и `purgeCar`. В `planned_reminders` записи удалённых работ остаются до следующей пересборки плана.
- **`planned_reminders` без первичного ключа**: ничто не мешает одинаковым строкам.
- **Ввод-вывод на `Dispatchers.Default`**, а не на `Dispatchers.IO` — во всех четырёх репозиториях.
- **Окно напоминания `null` записывается как `0`** (`CarRepositoryImpl.kt:107`). Репозиторий полагается на то, что `NormalizeTaskUseCase` уже подставил значение; работа, дошедшая сюда без окна, по пробегу никогда не станет `SOON`.
- **Перечисления хранятся именами** и читаются через `valueOf` (`SettingsMapper.kt:10-11`): переименование константы `ThemeMode` или `DistanceUnit` уронит чтение настроек у тех, у кого старое имя уже сохранено. То же с форматом дат: `LocalDate.parse` / `LocalTime.parse` / `LocalDateTime.parse` бросают исключение на строке другого формата.
- **Проверки миграций нет**: снимков схемы прежних версий в репозитории нет, `verifyMigrations` в `shared/build.gradle.kts` не включён.
- **Текст уведомления не упоминает срок по времени, пока он не истёк.** Работа, ставшая `SOON` только из-за интервала по времени, получит текст «осталось N км» (`ReminderSchedulerImpl.kt:46-54`).
- **Android: уведомления об одной работе накапливаются.** Идентификатор показа — `hashCode()` строки `"{taskId}@{fireAt}"` (`ReminderWorker.kt:46`), он разный для разных дней, поэтому новое уведомление не заменяет предыдущее по той же работе.
- **Android: имя канала — заголовок уведомления**, то есть название приложения (`ReminderWorker.kt:28-31`); отдельной локализованной строки для канала нет. Канал создаётся при каждом срабатывании задачи.
- **Android: задержка считается один раз при планировании.** Смена часового пояса или системного времени не сдвинет уже поставленные задачи до следующей пересборки плана. На iOS триггер календарный и привязан к показаниям часов.
- **Android: до 60 отдельных вызовов `enqueue`** на каждую пересборку плана, а пересборка идёт после каждого изменения машин и настроек, от которых зависит план (push, время напоминаний, единицы).
- **Android ниже 13: наличие разрешения проверяется только в `ReminderWorker`**, в момент показа. Если уведомления выключены в системе, задачи всё равно ставятся и отрабатывают впустую.
- **iOS: делегат `UNUserNotificationCenter` не назначен.** По умолчанию система не показывает баннер, пока приложение открыто на переднем плане, — уведомление, чьё время пришло в этот момент, пользователь может не увидеть.
- **iOS: ошибки постановки не обрабатываются** (`withCompletionHandler = null`), а domain считает «показанным» всё, чьё время прошло (см. `specs/domain.md`).
- **Остаток шаблона проекта**: `Platform.kt` с `expect fun getPlatform()` и его `actual`-реализации нигде не используются.

## Что ещё не реализовано

- Юнит-тесты на репозитории, мапперы и `ReminderSchedulerImpl`; тесты миграций.
- Синхронизация, резервная копия средствами приложения, экспорт и импорт данных — данные живут только в локальном файле `itstime4service.db`.
