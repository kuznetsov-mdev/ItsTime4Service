# Спецификация: domain-слой

Пакет: `ru.kuznetsov.pitstop.domain` (`shared/src/commonMain/kotlin/ru/kuznetsov/pitstop/domain/`).

Domain-слой — чистый Kotlin без зависимостей от UI и хранилища. Из библиотек использует только `kotlinx-datetime` (`LocalDate`, `LocalTime`) и `kotlinx-coroutines-core` (`Flow`).

```
domain/
├── model/        # модели
├── usecase/      # бизнес-логика; единственная точка входа для ViewModel-ов
└── repository/   # интерфейсы репозиториев
```

Правило слоя: ViewModel-ы работают только с use case-ами и репозиториев не видят. Use case — класс с одним методом `operator fun invoke(...)`. Все use case-ы зарегистрированы в `domainModule` (`di/DomainModule.kt`).

## Модели

Три группы: **хранимые** (то, что ляжет в базу), **черновики** (то же без `id`, до сохранения) и **вычисляемые** (считаются на лету и нигде не сохраняются).

```
Car ──(tasks, вложены)──▶ MaintenanceTask
 ▲                              │
 │ carId                        │ + пробег машины + сегодняшняя дата
 │                              ▼
ServiceHistoryEntry          TaskStat ──▶ TaskStatus
                                │
                 TaskWithStat = MaintenanceTask + TaskStat
                 CarAlerts    = счётчики TaskStatus по всем работам машины
                 TaskReminder = Car + TaskWithStat

Settings ──▶ ThemeMode, DistanceUnit        (от машин не зависит)
```

### Хранимые

#### `Car`

Машина. Корень: работы живут внутри машины и отдельно от неё не существуют.

| Поле | Тип | Смысл |
|---|---|---|
| `id` | `String` | идентификатор, он же аргумент маршрута навигации |
| `brand` | `String` | марка и модель одной строкой |
| `mileageKm` | `Int` | текущий пробег |
| `vin` | `String?` | VIN; `null`, если не указан (прочерк подставляет UI) |
| `tasks` | `List<MaintenanceTask>` | регламентные работы этой машины |

#### `MaintenanceTask`

Регламентная работа — «правило»: что делать и как часто. Срок наступает по пробегу и, опционально, по времени — что раньше.

| Поле | Тип | Смысл |
|---|---|---|
| `id` | `String` | идентификатор |
| `name` | `String` | название работы |
| `intervalKm` | `Int` | интервал по пробегу |
| `lastDoneKm` | `Int` | пробег, на котором работу делали в последний раз |
| `reminderWindowKm` | `Int` | за сколько км до срока статус становится `SOON` |
| `intervalMonths` | `Int?` | интервал по времени |
| `lastDoneDate` | `LocalDate?` | дата последнего выполнения |

Срок по времени учитывается, только когда заданы и `intervalMonths`, и `lastDoneDate`.

#### `ServiceHistoryEntry`

Запись в журнале ТО — «факт»: что сделали, когда и на каком пробеге.

| Поле | Тип | Смысл |
|---|---|---|
| `id` | `String` | идентификатор |
| `carId` | `String` | машина, к которой относится запись |
| `taskName` | `String` | название работы текстом |
| `date` | `LocalDate` | дата выполнения |
| `mileageKm` | `Int` | пробег на момент выполнения |
| `costRub` | `Int?` | стоимость |
| `serviceName` | `String?` | где делали |

С работой запись не связана ссылкой: `taskName` хранится текстом, поэтому запись переживает переименование и удаление работы.

#### `Settings`

Настройки пользователя с экрана «Настройки».

| Поле | Тип | Смысл |
|---|---|---|
| `themeMode` | `ThemeMode` | тема: `LIGHT`, `DARK`, `SYSTEM` (как на устройстве) |
| `distanceUnit` | `DistanceUnit` | единицы показа: `KILOMETERS`, `MILES` |
| `pushEnabled` | `Boolean` | включены ли push-уведомления |
| `defaultReminderWindowKm` | `Int` | окно напоминания по умолчанию для новых работ, в км |
| `reminderTime` | `LocalTime` | время ежедневной проверки статусов |

Данные всегда хранятся в километрах; `distanceUnit` влияет только на показ.

### Черновики

Те же данные без `id`. Идентификатор присваивает репозиторий при сохранении.

| Модель | Отличие от хранимой |
|---|---|
| `NewCar` | нет `id`; `tasks` — список `NewMaintenanceTask` |
| `NewMaintenanceTask` | нет `id`; `lastDoneKm` по умолчанию 0; `reminderWindowKm` может быть `null` — «взять долю интервала по умолчанию» |
| `NewServiceHistoryEntry` | нет `id` |

### Вычисляемые

#### `TaskStatus`

Срочность работы: `OK`, `SOON`, `DUE`. Значения объявлены по возрастанию срочности, поэтому худший из двух статусов — `maxOf(a, b)`.

#### `TaskStat`

Состояние одной работы на данный момент. Считается из работы, пробега машины и сегодняшней даты.

| Поле | Тип | Смысл |
|---|---|---|
| `dueAtKm` | `Int` | пробег, на котором наступает срок |
| `remainingKm` | `Int` | сколько км осталось; отрицательное при просрочке |
| `remainingMonths` | `Int?` | сколько месяцев осталось; `null`, если срока по времени нет; ноль или меньше при просрочке |
| `status` | `TaskStatus` | итоговый статус |
| `progress` | `Float` | доля пройденного интервала по пробегу, от 0 до 1 |

#### `TaskWithStat`

Пара «работа + её состояние» (`task`, `stat`). Нужна экрану работ, чтобы показать название и статус в одной строке.

#### `CarAlerts`

Сводка по машине.

| Поле | Тип | Смысл |
|---|---|---|
| `soon` | `Int` | сколько работ в статусе `SOON` |
| `due` | `Int` | сколько работ в статусе `DUE` |
| `taskCount` | `Int` | сколько всего работ у машины |
| `total` | `Int` | вычисляемое: `soon + due` |
| `worstStatus` | `TaskStatus` | вычисляемое: `DUE`, если есть просроченные; иначе `SOON`, если есть «скоро»; иначе `OK` |

#### `TaskReminder`

Работа, о которой нужно уведомить, вместе с её машиной (`car`, `task: TaskWithStat`). Используется только заготовкой `GetTaskRemindersUseCase`.

## Репозитории

Интерфейсы в `domain/repository/`. Чтение — `Flow`, который переизлучает данные при каждом изменении; изменение — `suspend`-методы. Реализации — на SQLDelight (`data/repository/*RepositoryImpl`).

### `CarRepository`

| Метод | Что делает |
|---|---|
| `observeCars(): Flow<List<Car>>` | все машины |
| `observeCarById(carId): Flow<Car?>` | одна машина; `null`, если такой нет |
| `addCar(car: NewCar): String` | сохраняет машину и её работы, присваивает им `id`, возвращает `id` машины |
| `deleteCar(carId)` | удаляет машину вместе с работами |
| `updateMileage(carId, mileageKm)` | меняет текущий пробег |
| `markTaskDone(carId, taskId, date)` | ставит работе `lastDoneKm` = текущий пробег машины и, если у неё есть интервал по времени, `lastDoneDate` = `date`; в историю не пишет |
| `updateCar(carId, brand, vin)` | меняет марку и VIN |
| `addTask(carId, task: NewMaintenanceTask)` | добавляет работу существующей машине |
| `updateTask(carId, task: MaintenanceTask)` | заменяет работу с тем же `id` |
| `deleteTask(carId, taskId)` | удаляет работу |

### `ServiceHistoryRepository`

| Метод | Что делает |
|---|---|
| `observeHistory(carId): Flow<List<ServiceHistoryEntry>>` | записи журнала одной машины, без гарантии порядка |
| `addEntry(entry: NewServiceHistoryEntry)` | добавляет запись, присваивает ей `id` |
| `updateEntry(entry: ServiceHistoryEntry)` | заменяет запись с тем же `id` |
| `deleteHistory(carId)` | удаляет все записи машины |

### `SettingsRepository`

| Метод | Что делает |
|---|---|
| `observeSettings(): Flow<Settings>` | текущие настройки |
| `updateSettings(settings)` | заменяет настройки целиком |

## Use case-ы

Всего 19: 13 реализованных и 6 заготовок.

### Расчётные

Чистые функции без состояния и без репозиториев. Сегодняшняя дата передаётся параметром `today`.

| Use case | Вход | Выход |
|---|---|---|
| `CalculateTaskStatusUseCase` | `mileageKm`, `task`, `today` | `TaskStat` |
| `SortedTaskStatsUseCase` | `car`, `today` | `List<TaskWithStat>` |
| `CarAlertsUseCase` | `car`, `today` | `CarAlerts` |
| `GetTodayUseCase` | — | `LocalDate` |

`SortedTaskStatsUseCase` и `CarAlertsUseCase` своих правил статуса не содержат: оба вызывают `CalculateTaskStatusUseCase` для каждой работы.

`GetTodayUseCase` возвращает сегодняшнюю дату в часовом поясе устройства. Это единственное место, где domain читает системные часы.

#### Правила расчёта статуса (`CalculateTaskStatusUseCase`)

По пробегу:

- `dueAtKm = lastDoneKm + intervalKm`, `remainingKm = dueAtKm − mileageKm`.
- `remainingKm ≤ 0` → `DUE`; `remainingKm ≤ reminderWindowKm` → `SOON`; иначе `OK`.
- `progress = (mileageKm − lastDoneKm) / intervalKm`, ограничено диапазоном 0..1; при `intervalKm ≤ 0` равен 0.

По времени (только если заданы `intervalMonths > 0` и `lastDoneDate`):

- Прошло месяцев — число полных месяцев между `lastDoneDate` и `today`: разница по году и месяцу, минус один, если день месяца в `today` меньше, чем в `lastDoneDate`; не меньше нуля.
- `remainingMonths = intervalMonths − прошло месяцев`.
- Окно напоминания в месяцах — окно по пробегу, перенесённое на интервал по времени: `round(intervalMonths × reminderWindowKm / intervalKm)`, но не меньше 1; при `intervalKm ≤ 0` равно 1.
- `remainingMonths ≤ 0` → `DUE`; `remainingMonths ≤ окно` → `SOON`; иначе `OK`.

Итоговый статус — худший из двух. Если срока по времени нет, `remainingMonths = null` и статус берётся по пробегу.

#### Сортировка (`SortedTaskStatsUseCase`)

Сначала `DUE`, затем `SOON`, затем `OK`; внутри одного статуса — по возрастанию `remainingKm`.

#### Пример

Toyota Camry, пробег 152 000 км, сегодня 08.10.2026.

| Работа | Параметры | Срок на | Осталось | Статус |
|---|---|---|---|---|
| Свечи зажигания | 40 000 км, последний раз на 110 000, окно 2 000 | 150 000 км | −2 000 км | `DUE` |
| Замена масла и фильтра | 10 000 км / 12 мес, последний раз на 143 000 км 20.11.2025, окно 1 000 | 153 000 км | 1 000 км / 2 мес | `SOON` |
| Салонный фильтр | 20 000 км, последний раз на 135 000, окно 2 000 | 155 000 км | 3 000 км | `OK` |

- Масло: по пробегу остаток равен окну → `SOON`; по времени прошло 10 полных месяцев, осталось 2, окно `round(12 × 1 000 / 10 000) = 1` → `OK`; итог — худший, `SOON`.
- `SortedTaskStatsUseCase` вернёт работы в порядке: свечи, масло, салонный фильтр.
- `CarAlertsUseCase` вернёт `soon = 1`, `due = 1`, `taskCount = 3`; отсюда `total = 2`, `worstStatus = DUE`.

### Машины

| Use case | Вход | Выход | Правила |
|---|---|---|---|
| `ObserveCarsUseCase` | — | `Flow<List<Car>>` | проброс `CarRepository.observeCars()` |
| `ObserveCarByIdUseCase` | `carId` | `Flow<Car?>` | проброс `observeCarById`; `null`, если машины нет (например, сразу после удаления) |
| `AddCarUseCase` | `car: NewCar` | `String?` — `id` новой машины | см. ниже |
| `DeleteCarUseCase` | `carId` | — | удаляет машину (`deleteCar`) и её историю (`deleteHistory`) |
| `UpdateMileageUseCase` | `carId`, `mileageKm` | `Boolean` | `mileageKm ≤ 0` → `false`, ничего не меняется; значение меньше текущего допускается, чтобы исправить опечатку |
| `MarkTaskDoneUseCase` | `carId`, `taskId`, `today` | `Boolean` | см. ниже |
| `NormalizeTaskUseCase` | `task: NewMaintenanceTask` | `NewMaintenanceTask?` | см. ниже |
| `UpdateCarUseCase` | `carId`, `brand`, `vin` | `Boolean` | марка обрезается по краям; пустая → `false`, ничего не меняется; пустой VIN сохраняется как `null` |
| `AddTaskUseCase` | `carId`, `task: NewMaintenanceTask` | `Boolean` | работа проходит `NormalizeTaskUseCase`; отклонённая → `false`, ничего не сохраняется |
| `UpdateTaskUseCase` | `carId`, `taskId`, `task: NewMaintenanceTask` | `Boolean` | те же правила; заменяет все поля работы `taskId`, включая «последний раз» по пробегу и дате |
| `DeleteTaskUseCase` | `carId`, `taskId` | — | удаляет работу без подтверждения; записи истории ТО по ней сохраняются |

#### `AddCarUseCase`

- Возвращает `null` и ничего не сохраняет, если марка пустая или пробег не положительный.
- Марка обрезается по краям; пустой VIN сохраняется как `null`.
- Работы проходят `NormalizeTaskUseCase`; отклонённые отбрасываются.
- Работу по умолчанию use case не подставляет: если список работ пуст, машина сохраняется без работ.

#### `NormalizeTaskUseCase`

Общие правила для работы, введённой в форме авто, — при добавлении авто, добавлении и правке работы.

- Возвращает `null`, если название пустое или интервал по пробегу `≤ 0`.
- Название обрезается по краям.
- Работе без окна напоминания ставится 15 % её интервала по пробегу.
- `intervalMonths ≤ 0` считается отсутствующим; `lastDoneDate` сохраняется только у работ с интервалом по времени.

#### Правка авто в форме

Карандаш на экране машины открывает форму добавления авто, заполненную данными машины и её работ (экран 07 макета). Сохранение — в `CarFormViewModel`, вызовы идут подряд:

1. `UpdateCarUseCase` — марка и VIN;
2. `UpdateMileageUseCase` — если пробег изменился;
3. `DeleteTaskUseCase` — для работ, блоки которых удалены из формы;
4. `UpdateTaskUseCase` — для блоков существующих работ;
5. `AddTaskUseCase` — для новых блоков.

Блок без названия или интервала пропускается: новая работа не создаётся, существующая остаётся без изменений. Работа по умолчанию при правке не подставляется — машина может остаться без работ.

#### `MarkTaskDoneUseCase`

- Возвращает `false` и ничего не меняет, если нет машины или работы.
- Вызывает `CarRepository.markTaskDone(carId, taskId, today)`.
- Добавляет в историю запись: название работы, `today`, текущий пробег машины; стоимость и сервис пустые.
- Две записи идут подряд, без транзакции.

### История и настройки

| Use case | Вход | Выход | Правила |
|---|---|---|---|
| `ObserveHistoryUseCase` | `carId` | `Flow<List<ServiceHistoryEntry>>` | сортирует записи от новых к старым по `date` |
| `ObserveSettingsUseCase` | — | `Flow<Settings>` | проброс `observeSettings()` |
| `UpdateSettingsUseCase` | `settings` | — | `defaultReminderWindowKm` приводится к диапазону 1 000..9 000 км (диапазон степпера на экране настроек) |

### Заготовки

Классы и сигнатуры есть, методы пустые: в макете для них нет экранов. Use case-ы пока не обращаются к репозиториям, хотя нужные методы в интерфейсах уже объявлены.

| Use case | Сигнатура | Метод репозитория | Что нужно решить |
|---|---|---|---|
| `UpdateHistoryEntryUseCase` | `(entry: ServiceHistoryEntry)` | `ServiceHistoryRepository.updateEntry` | решено: меняются только стоимость и сервис, тапом по записи истории; экрана пока нет |
| `GetTaskRemindersUseCase` | `(today): List<TaskReminder>` — сейчас всегда пустой список | — | какие работы попадают в уведомления и правило «не чаще N дней» (этап 8) |

## Открытые вопросы

- Машину с пробегом 0 добавить нельзя (так в макете). Для нового авто это может быть неудобно.
- Откуда берётся иконка работы: в модели её нет; в макете она либо зашита в данные, либо подбирается по названию.
- Где подставляется работа по умолчанию при пустом списке работ: название локализуется, поэтому в domain его нет.
- `MarkTaskDoneUseCase` всё ещё не атомарен: сброс работы и запись в историю идут через два репозитория двумя отдельными операциями.
- Сохранение формы правки авто тоже не атомарно: до пяти видов вызовов подряд, без общей транзакции.

## Что ещё не реализовано

- Две заготовки use case-ов из таблицы выше.
- Юнит-тесты на всё, кроме `CalculateTaskStatusUseCase` и `NormalizeTaskUseCase` (покрыты `CalculateTaskStatusUseCaseTest` и `NormalizeTaskUseCaseTest`).
