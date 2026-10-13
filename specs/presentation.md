# Спецификация: presentation-слой

Пакеты: `ru.kuznetsov.pitstop.features`, `ru.kuznetsov.pitstop.ui` и корневые `App.kt`, `AppViewModel.kt` (`shared/src/commonMain/kotlin/ru/kuznetsov/pitstop/`).

Presentation-слой — экраны на Compose Multiplatform, общие для Android и iOS. Из библиотек использует Compose (`foundation`, `material3`, `ui`, `components-resources`), `lifecycle-viewmodel-compose`, `navigation-compose`, Koin (`koin-compose-viewmodel`), иконки Lucide (`com.composables:icons-lucide`) и `kotlinx-datetime`. Внешний вид задаёт макет `docs/index.html`.

```
App.kt, AppViewModel.kt   # корень: тема, разрешение на уведомления, пересборка плана напоминаний
features/
├── navigation/           # Destination, PitStopNavGraph, таб-бар
├── cars/                 # список машин
├── carform/              # одна форма: добавление и правка машины
├── cardetail/            # экран машины с работами
├── history/              # история ТО
└── settings/             # настройки
ui/
├── theme/                # цвета, типографика, размеры, PitStopTheme
├── components/           # ui-kit: buttons, fields, steppers, indicators, cards, dialogs, screen
├── format/               # форматирование чисел, расстояний, дат; TaskStatus → StatusTier
├── icons/                # PitStopIcons
└── notification/         # expect-функция запроса разрешения на уведомления
```

Правила слоя:

- Каждая фича — три файла: `*Screen.kt`, `*UiState.kt`, `*ViewModel.kt`.
- Экран — пара Composable-функций: `XxxScreen` получает ViewModel через `koinViewModel`, собирает состояние через `collectAsStateWithLifecycle()` и передаёт его в `internal XxxContent(state, колбэки…)`. `XxxContent` о ViewModel не знает; на нём же построены `@Preview` в светлой и тёмной теме.
- ViewModel отдаёт один `StateFlow<XxxUiState>` и набор методов `onXxx(...)`. Отдельного канала разовых событий нет: «закрой экран» передаётся флагом в состоянии (`isMissing`, `isSaved`), на который экран реагирует через `LaunchedEffect`.
- ViewModel-ы зависят только от use case-ов domain-слоя (см. `specs/domain.md`); репозиториев и data-слоя не видят.
- `CarsViewModel`, `CarDetailViewModel`, `HistoryViewModel`, `SettingsViewModel` строят состояние через `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), начальное состояние)`. `CarFormViewModel` держит `MutableStateFlow` и читает машину один раз.
- Тексты — только из ресурсов (`composeResources/values*/strings.xml`). Когда ViewModel-у нужна локализованная строка, её передаёт экран параметром.

## Навигация

`features/navigation/Destinations.kt`, `features/navigation/NavGraph.kt`. Маршруты строковые, `sealed class Destination(val route: String)`; у маршрутов с аргументом есть `createRoute(carId)`.

| Destination | Маршрут | Аргументы | Экран | Таб-бар |
|---|---|---|---|---|
| `Cars` | `cars` | — | `CarsScreen`; стартовый | да |
| `AddCar` | `add-car` | — | `CarFormScreen` без `carId` | нет |
| `CarDetail` | `cars/{carId}` | `carId: String` | `CarDetailScreen` | нет |
| `EditCar` | `cars/{carId}/edit` | `carId: String` | `CarFormScreen` с `carId` | нет |
| `History` | `cars/{carId}/history` | `carId: String` | `HistoryScreen` | нет |
| `Settings` | `settings` | — | `SettingsScreen` | да |

Имя аргумента — `Destination.ARG_CAR_ID = "carId"`, тип `NavType.StringType`. Из записи back stack-а он читается расширением `NavBackStackEntry.carId`; если аргумента нет, получается пустая строка.

### Переходы

| Откуда | Действие | Куда |
|---|---|---|
| `Cars` | тап по карточке машины | `navigate(CarDetail.createRoute(carId))` |
| `Cars` | FAB «плюс» | `navigate(AddCar.route)` |
| `AddCar` | «назад» или успешное сохранение | `popBackStack()` — обратно к списку; на экран новой машины переход не делается |
| `CarDetail` | «назад» или машина исчезла (`isMissing`) | `popBackStack(Cars.route, inclusive = false)` |
| `CarDetail` | кнопка-часы | `navigate(History.createRoute(carId))` |
| `CarDetail` | кнопка-карандаш в шапке | `navigate(EditCar.createRoute(carId))` |
| `EditCar` | «назад», успешное сохранение или машины нет (`isMissing`) | `popBackStack()` |
| `History` | «назад» | `popBackStack()` |
| таб-бар | тап по вкладке | `navigateToTab`: `navigate(route)` с `popUpTo(Cars.route) { saveState = true }`, `launchSingleTop = true`, `restoreState = true` |

### Каркас

`PitStopNavGraph` — `Scaffold` на весь экран: `containerColor = PitStopTheme.colors.bg`, `contentWindowInsets = WindowInsets.safeDrawing`, внутри `NavHost` со стартовым `Destination.Cars`.

Таб-бар (`bottomBar`) показывается, только когда текущий маршрут — `cars` или `settings`. Это приватный `TabBar` в `NavGraph.kt`: линия `rule` в 1 dp, под ней два `TabBarItem` равной ширины — «Авто» (`PitStopIcons.Car`) и «Настройки» (`PitStopIcons.Gear`); фон `surface`, снизу `navigationBarsPadding()`.

## Корень приложения

### `App`

`App.kt`. Точка входа для обеих платформ: Android вызывает `App()` из `MainActivity.setContent`, iOS — из `MainViewController()` (`ComposeUIViewController { App() }`).

1. Берёт `AppViewModel` через `koinViewModel()`.
2. Вычисляет тёмную тему: `ThemeMode.LIGHT` → светлая, `DARK` → тёмная, `SYSTEM` → `isSystemInDarkTheme()`.
3. Через `LaunchedEffect(needsNotificationPermission)` запрашивает системное разрешение на уведомления каждый раз, когда флаг становится `true`.
4. Оборачивает `PitStopNavGraph()` в `PitStopTheme(darkTheme)`.

### `AppViewModel`

Состояние над графом навигации. Зависимости: `ObserveSettingsUseCase`, `ObserveCarsUseCase`, `SyncRemindersUseCase`, `UpdateSettingsUseCase`.

| Член | Тип | Смысл |
|---|---|---|
| `themeMode` | `StateFlow<ThemeMode>` | тема из настроек; начальное значение до прихода настроек — `SYSTEM` |
| `needsNotificationPermission` | `StateFlow<Boolean>` | `settings.pushEnabled && cars.isNotEmpty()`; начальное значение `false` |
| `onNotificationPermissionResult(granted)` | метод | ответ системы на запрос разрешения |

| Событие | Что происходит | Use case |
|---|---|---|
| создание ViewModel (`init`) | запускается сбор `combine(observeCars(), observeSettings(), permissionGrants)` через `collectLatest`; на каждое значение план напоминаний строится заново | `SyncRemindersUseCase(cars, settings)` |
| изменились машины или настройки | то же: новое значение `combine` → пересборка плана | `SyncRemindersUseCase` |
| разрешение выдано (`granted = true`) | приватный счётчик `permissionGrants` увеличивается на 1 → `combine` излучает → план строится ещё раз | `SyncRemindersUseCase` |
| в разрешении отказано (`granted = false`) | читаются текущие настройки (`observeSettings().first()`) и сохраняются с `pushEnabled = false` | `UpdateSettingsUseCase` |

Следствия:

- Разрешение запрашивается не на старте «в пустоту», а когда push включён и есть хотя бы одна машина: при запуске приложения с машинами, после добавления первой машины, после включения переключателя push в настройках.
- Отказ выключает push в настройках. Если пользователь включит его снова, флаг опять станет `true` и запрос повторится.
- Сбор для пересборки плана запущен в `viewModelScope` из `init` и от подписчиков не зависит; `themeMode` и `needsNotificationPermission` живут, пока есть подписчик, плюс 5 секунд.

### Запрос разрешения

`ui/notification/NotificationPermission.kt`:

```kotlin
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit
```

Возвращает функцию «запросить разрешение»; ответ приходит в `onResult`.

| Платформа | Реализация |
|---|---|
| Android (`NotificationPermission.android.kt`) | `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`. На API 33+ запускает запрос `Manifest.permission.POST_NOTIFICATIONS`; на более старых сразу вызывает `onResult(true)` |
| iOS (`NotificationPermission.ios.kt`) | `UNUserNotificationCenter.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound)`; ответ переводится на главную очередь (`dispatch_async(dispatch_get_main_queue())`) |

## Фичи

### `cars` — «Мои авто»

Список машин с кольцом-сводкой по работам и кнопкой добавления.

Экран (`CarsContent`):

- `ScreenTitle`: заголовок `screen_cars`, подзаголовок — число машин (`plurals cars_count`) и подсказка `cars_gauge_hint` через « · ».
- Список `LazyColumn` из `ListRowCard`: иконка машины, марка, пробег в выбранных единицах (`distanceText`), справа `GaugeRing`. Ключ элемента — `id` машины. Порядок — как отдал `ObserveCarsUseCase`.
- Пустой список после загрузки — текст `cars_empty` по центру. Пока `isLoading = true`, пустое состояние не показывается.
- `Fab` с плюсом в правом нижнем углу, поверх списка.

`CarsUiState`:

| Поле | Тип | По умолчанию | Смысл |
|---|---|---|---|
| `isLoading` | `Boolean` | `true` | `true` до первого списка из базы |
| `cars` | `List<CarItem>` | пусто | строки списка |
| `distanceUnit` | `DistanceUnit` | `KILOMETERS` | единицы показа пробега |

`CarItem`:

| Поле | Тип | Смысл |
|---|---|---|
| `id` | `String` | идентификатор машины |
| `brand` | `String` | марка |
| `mileageKm` | `Int` | пробег в км |
| `alertCount` | `Int` | `CarAlerts.total` — число работ `SOON` и `DUE`, цифра внутри кольца |
| `gaugeValue` | `Float` | заполненная доля кольца, 0..1 |
| `status` | `TaskStatus` | `CarAlerts.worstStatus` — цвет кольца и цифры |

`CarsViewModel` входящих событий не имеет: тап по машине и по FAB уходят из экрана напрямую в навигацию.

| Источник | Что происходит | Use case |
|---|---|---|
| `combine(observeCars(), observeSettings())` | на каждое изменение машин или настроек состояние собирается заново | `ObserveCarsUseCase`, `ObserveSettingsUseCase` |
| сборка строки | для каждой машины считается сводка на сегодняшнюю дату | `GetTodayUseCase`, `CarAlertsUseCase` |

Правило кольца: `gaugeValue = alerts.total / alerts.taskCount`; если работ «скоро» и «просрочено» нет (в том числе у машины без работ) — `0.06`, чтобы кольцо не выглядело пустым.

### `carform` — форма авто

Одна форма на два режима. `CarFormScreen(onBack, carId: String? = null)`: без `carId` — добавление (`Destination.AddCar`), с `carId` — правка (`Destination.EditCar`).

Экран (`CarFormContent`):

- `ScreenTopBar` с кнопкой «назад», `ScreenTitle`: `screen_add_car` или `screen_edit_car`.
- Пока `isLoading = true`, под заголовком ничего не рисуется — ни полей, ни кнопки сохранения.
- Поля машины: «Марка» (`AppTextField`), в одну строку «Пробег, км» (`AppNumberField`) и «VIN» (`AppTextField`). У подписей марки и пробега — звёздочка цвета `tierDue`.
- Раздел работ: подпись `section_tasks` и счётчик блоков (`plurals tasks_added`, считает все блоки, включая незаполненные).
- Чипы быстрых работ (`PresetChip` во `FlowRow`) — по одному на каждый `TaskPreset`.
- Блоки работ (`TaskBlock`) в порядке добавления: заголовок `task_block_title` с порядковым номером, корзина, название, пары полей «интервал, км» / «интервал, мес» и «последний раз, км» / «дата последней замены», поле «напомнить за, км».
- Пунктирная кнопка `action_add_custom_task` — добавить пустой блок.
- `PrimaryButton` под прокручиваемой частью: `action_save_car` при добавлении, `action_save` при правке; активна, когда `canSave`.
- `LaunchedEffect(isSaved, isMissing)`: при любом из флагов экран вызывает `onBack()`.

`CarFormUiState` — все поля хранятся текстом, как ввёл пользователь; числа и даты разбираются только при сохранении.

| Поле | Тип | По умолчанию | Смысл |
|---|---|---|---|
| `brand` | `String` | `""` | марка |
| `mileageKm` | `String` | `""` | пробег, только цифры |
| `vin` | `String` | `""` | VIN |
| `tasks` | `List<TaskBlockState>` | пусто | блоки работ |
| `isEditing` | `Boolean` | `carId != null` | режим правки |
| `isLoading` | `Boolean` | `carId != null` | `true`, пока читается правимая машина |
| `isMissing` | `Boolean` | `false` | правимой машины нет |
| `isSaved` | `Boolean` | `false` | сохранение прошло |
| `canSave` | `Boolean` | вычисляемое | марка не пустая (`isNotBlank`) и пробег разбирается в число больше 0 |

`TaskBlockState`:

| Поле | Тип | Смысл |
|---|---|---|
| `key` | `Int` | ключ блока в форме; выдаётся счётчиком `nextKey` во ViewModel |
| `taskId` | `String?` | `id` сохранённой работы; `null` у блока, добавленного в форме |
| `name` | `String` | название |
| `intervalKm` | `String` | интервал по пробегу |
| `intervalMonths` | `String` | интервал по времени |
| `lastDoneKm` | `String` | пробег последнего выполнения |
| `lastDoneDate` | `String` | дата последнего выполнения, `дд.мм.гггг` |
| `reminderWindowKm` | `String` | окно напоминания |

`TaskPreset` — готовые работы за чипами; название локализуется, поэтому его подставляет экран.

| Пресет | Чип | Название работы | `intervalKm` | `reminderWindowKm` |
|---|---|---|---|---|
| `OIL` | `preset_oil` | `task_name_oil` | 10 000 | 1 500 |
| `PADS` | `preset_pads` | `task_name_pads` | 30 000 | 3 000 |
| `AIR_FILTER` | `preset_air_filter` | `task_name_air_filter` | 15 000 | 2 000 |
| `TIMING_BELT` | `preset_timing_belt` | `task_name_timing_belt` | 60 000 | 5 000 |

События `CarFormViewModel`:

| Событие | Что происходит | Use case |
|---|---|---|
| создание с `carId` | один раз читается машина (`observeCarById(carId).first()`); нет машины → `isLoading = false`, `isMissing = true`; есть → форма заполняется, запоминаются исходный пробег и `id` исходных работ | `ObserveCarByIdUseCase` |
| `onBrandChange(value)` | текст сохраняется как есть | — |
| `onMileageChange(value)` | остаются только цифры, не больше 7 знаков | — |
| `onVinChange(value)` | перевод в верхний регистр, обрезка до 17 символов | — |
| `onTaskChange(task)` | блок с тем же `key` заменяется; в `intervalKm`, `intervalMonths`, `lastDoneKm`, `reminderWindowKm` остаются только цифры (до 7 знаков); `name` и `lastDoneDate` — как есть | — |
| `onAddTask()` | в конец добавляется пустой блок | — |
| `onAddPreset(preset, name)` | в конец добавляется блок с названием, интервалом и окном пресета; остальные поля пустые | — |
| `onRemoveTask(key)` | блок удаляется из формы | — |
| `onSave(defaultTaskName)` | см. ниже | зависит от режима |

Заполнение формы при правке: марка, пробег, VIN (пустая строка вместо `null`), по блоку на каждую работу в порядке, в котором их отдал `ObserveCarByIdUseCase`; `intervalMonths` и `lastDoneDate` пустые, если в работе они `null`; дата — в виде `дд.мм.гггг`.

#### Сохранение

`onSave` ничего не делает, если пробег не разбирается в число, `canSave = false` или форма ещё грузится. Иначе в `viewModelScope` выполняется ветка режима; при успехе `isSaved = true`.

Блок работы превращается в `NewMaintenanceTask` так:

| Поле блока | Правило |
|---|---|
| `name`, `intervalKm` | название пустое, интервал не число или `≤ 0` → блок пропускается целиком |
| `lastDoneKm` | не число (в том числе пусто) → `0` |
| `reminderWindowKm` | не число → `null` (окно выставит `NormalizeTaskUseCase`) |
| `intervalMonths` | не число → `null` |
| `lastDoneDate` | `parseDisplayDate`; не разобралась → `null` |

Добавление (`carId == null`):

| Шаг | Что происходит | Use case |
|---|---|---|
| 1 | блоки превращаются в работы; пропущенные отбрасываются | — |
| 2 | если годных работ не осталось, подставляется работа по умолчанию: название `defaultTaskName` (экран передаёт `task_name_oil`), интервал 10 000 км, `lastDoneKm` = введённый пробег, окно 1 500 км | — |
| 3 | машина сохраняется; `null` в ответе → форма остаётся открытой | `AddCarUseCase` |

Правка (`carId != null`) — порядок вызовов описан в `specs/domain.md` («Правка авто в форме»):

| Шаг | Что происходит | Use case |
|---|---|---|
| 1 | марка и VIN; `false` в ответе → сохранение прерывается, форма остаётся открытой | `UpdateCarUseCase` |
| 2 | пробег — только если он отличается от исходного | `UpdateMileageUseCase` |
| 3 | работы, чьих блоков в форме больше нет | `DeleteTaskUseCase` |
| 4 | блоки с `taskId` | `UpdateTaskUseCase` |
| 5 | блоки без `taskId` | `AddTaskUseCase` |

Пропущенный блок существующей работы оставляет её без изменений. Работа по умолчанию при правке не подставляется. Результаты шагов 2–5 не проверяются.

### `cardetail` — экран машины

Работы машины по срочности, быстрая правка пробега, отметка «выполнено», удаление машины. `CarDetailScreen(carId, onBack, onOpenHistory, onEditCar)`.

Экран (`CarDetailContent`):

- `ScreenTopBar`: «назад» и три `AppIconButton` — часы (история), карандаш (правка машины), корзина (удаление). Шапка рисуется всегда, остальное — только когда `car != null`.
- `ScreenTitle`: марка; подзаголовок моноширинным шрифтом — `vin_label` с VIN или прочерком `vin_missing`.
- Два `StatCard` в строку:
  - «Пробег» — иконка `Gauge`, пробег в выбранных единицах с разбивкой по разрядам (без подписи единиц) и маленький карандаш; в режиме правки — `InlineEditField`, заполненный текущим пробегом в километрах;
  - «Скоро / просрочено» — `count_of_total` («N из M»), цвет по `worstStatus`.
- Список работ `LazyColumn` с разделителями: плитка с иконкой, название, строка-сводка, `StatusChip`, `ProgressBar` (доля `progress`, цвет по статусу) и кнопка `action_mark_done`. Ключ элемента — `id` работы.
- Нет работ — текст `tasks_empty` по центру.
- `ConfirmDialog` удаления — когда `isConfirmingDelete` и `car != null`: заголовок `dialog_delete_car_title` с маркой, текст `dialog_delete_car_text`, кнопки `action_cancel` / `action_delete`.
- `LaunchedEffect(isMissing)`: машины нет → `onBack()`.

Строка-сводка работы (`taskMetaText`) — части через « · »:

| Часть | Условие | Ресурс |
|---|---|---|
| остаток по пробегу | `remainingKm > 0` | `task_remaining` с расстоянием |
| просрочка по пробегу | `remainingKm ≤ 0` | `task_overdue_by` с `−remainingKm` |
| остаток по времени | `remainingMonths > 0` | `task_or_months` |
| срок по времени вышел | `remainingMonths ≤ 0` | `task_time_expired` |
| интервал | всегда | `task_every` с `intervalKm` |

Части о времени нет, если `remainingMonths == null`. Все расстояния — через `distanceText` в выбранных единицах.

Иконка работы (`TaskIcon.kt`, `taskIcon(taskName)`) подбирается по подстроке в названии без учёта регистра, первое совпадение сверху вниз:

| Ключевые подстроки | Иконка |
|---|---|
| `масл`, `oil`, `aceite` | `PitStopIcons.Droplet` |
| `колод`, `тормоз`, `brake`, `pad`, `freno`, `pastilla` | `PitStopIcons.Disc` |
| `фильтр`, `filter`, `filtro` | `PitStopIcons.Filter` |
| остальное | `PitStopIcons.Wrench` |

`CarDetailUiState`:

| Поле | Тип | По умолчанию | Смысл |
|---|---|---|---|
| `isLoading` | `Boolean` | `true` | `true` до первого значения из базы |
| `car` | `CarDetails?` | `null` | `null` при загрузке и после удаления машины |
| `distanceUnit` | `DistanceUnit` | `KILOMETERS` | единицы показа |
| `isEditingMileage` | `Boolean` | `false` | поле пробега в режиме правки |
| `isConfirmingDelete` | `Boolean` | `false` | открыт диалог удаления |
| `isMissing` | `Boolean` | вычисляемое | `!isLoading && car == null` — сигнал закрыть экран |

`CarDetails`:

| Поле | Тип | Смысл |
|---|---|---|
| `brand` | `String` | марка |
| `vin` | `String?` | VIN |
| `mileageKm` | `Int` | пробег в км |
| `alertCount` | `Int` | `CarAlerts.total` |
| `taskCount` | `Int` | `CarAlerts.taskCount` |
| `worstStatus` | `TaskStatus` | `CarAlerts.worstStatus` |
| `tasks` | `List<TaskItem>` | работы в порядке `SortedTaskStatsUseCase` |

`TaskItem`:

| Поле | Тип | Смысл |
|---|---|---|
| `id` | `String` | идентификатор работы |
| `name` | `String` | название |
| `intervalKm` | `Int` | интервал по пробегу |
| `remainingKm` | `Int` | остаток; отрицательный при просрочке |
| `remainingMonths` | `Int?` | остаток по времени; `null`, если срока по времени нет |
| `status` | `TaskStatus` | статус |
| `progress` | `Float` | доля пройденного интервала |

События `CarDetailViewModel`:

| Событие | Что происходит | Use case |
|---|---|---|
| `combine(observeCarById(carId), observeSettings(), isEditingMileage, isConfirmingDelete)` | состояние собирается заново; для машины считаются сводка и отсортированные работы на сегодняшнюю дату | `ObserveCarByIdUseCase`, `ObserveSettingsUseCase`, `GetTodayUseCase`, `CarAlertsUseCase`, `SortedTaskStatsUseCase` |
| `onEditMileage()` | `isEditingMileage = true` | — |
| `onSaveMileage(text)` | текст обрезается по краям и разбирается в `Int`; число → вызов use case-а; затем в любом случае `isEditingMileage = false` | `UpdateMileageUseCase` |
| `onMarkTaskDone(taskId)` | работа отмечается выполненной сегодняшней датой, сразу, без подтверждения | `GetTodayUseCase`, `MarkTaskDoneUseCase` |
| `onDeleteRequest()` | `isConfirmingDelete = true` | — |
| `onDeleteDismiss()` | `isConfirmingDelete = false` | — |
| `onDeleteConfirm()` | диалог закрывается, машина удаляется; после этого `observeCarById` отдаёт `null` → `isMissing` → экран закрывается | `DeleteCarUseCase` |

Пробег в поле правки вводится в километрах при любых единицах показа. Текст, который не разбирается в число, и значение, отклонённое `UpdateMileageUseCase` (`≤ 0`), молча закрывают поле без изменений. Сам `InlineEditField` ввод не фильтрует — только включает цифровую клавиатуру.

### `history` — история ТО

Журнал одной машины, только чтение. `HistoryScreen(carId, onBack)`.

Экран (`HistoryContent`):

- `ScreenTopBar` с «назад»; `ScreenTitle`: `screen_history`, подзаголовок моноширинным — марка машины.
- Список `LazyColumn` с разделителями. Строка: название работы и дата `дд.мм.гггг`; под ними моноширинная строка из частей через три пробела — пробег (`distanceText`), стоимость (`cost_rub` с разбивкой по разрядам, если `costRub != null`), название сервиса (если есть). Ключ элемента — `id` записи.
- Пустой журнал после загрузки — текст `history_empty`. Пока `isLoading = true`, пустое состояние не показывается.

`HistoryUiState`:

| Поле | Тип | По умолчанию | Смысл |
|---|---|---|---|
| `isLoading` | `Boolean` | `true` | `true` до первого значения |
| `brand` | `String` | `""` | марка; пустая строка, если машины нет |
| `entries` | `List<ServiceHistoryEntry>` | пусто | записи, новые сверху (порядок задаёт `ObserveHistoryUseCase`) |
| `distanceUnit` | `DistanceUnit` | `KILOMETERS` | единицы показа |

`HistoryViewModel` входящих событий не имеет.

| Источник | Что происходит | Use case |
|---|---|---|
| `combine(observeCarById(carId), observeHistory(carId), observeSettings())` | состояние собирается заново при изменении машины, журнала или настроек | `ObserveCarByIdUseCase`, `ObserveHistoryUseCase`, `ObserveSettingsUseCase` |

Записи появляются только как побочный результат «отметить выполненной» на экране машины. Добавить, изменить или удалить запись с этого экрана нельзя.

### `settings` — настройки

Каждое изменение сохраняется сразу; кнопки «Сохранить» нет.

Экран (`SettingsContent`):

- `ScreenTitle` с `screen_settings`. Пока `settings == null`, под заголовком пусто.
- Группа «Тема»: `SegmentedControl` из трёх вариантов в порядке `LIGHT`, `DARK`, `SYSTEM`.
- Группа «Единицы измерения»: `SegmentedControl` из `KILOMETERS`, `MILES`.
- Группа «Уведомления»:
  - строка с колокольчиком и `AppSwitch` — push;
  - `NotificationPreviewCard` — статичный пример: заголовок `app_name`, время `notification_time_now`, текст `notification_sample_full`, подпись `notification_caption`;
  - строка «Напоминать заранее» со `Stepper` в тысячах км, от 1 до 9; значение — `defaultReminderWindowKm / 1000`;
  - строка «Время напоминаний» — кликабельная, справа время `ЧЧ:ММ`.
- `TimePickerDialog` — когда `isPickingReminderTime` и настройки загружены: заголовок `settings_reminder_time`, текст `reminder_time_dialog_text`, начальное время — `settings.reminderTime`.

`SettingsUiState`:

| Поле | Тип | По умолчанию | Смысл |
|---|---|---|---|
| `settings` | `Settings?` | `null` | `null` только до прихода настроек из базы |
| `isPickingReminderTime` | `Boolean` | `false` | открыт диалог выбора времени |

События `SettingsViewModel`:

| Событие | Что происходит | Use case |
|---|---|---|
| `combine(observeSettings(), isPickingReminderTime)` | состояние собирается заново | `ObserveSettingsUseCase` |
| `onThemeSelect(themeMode)` | сохраняется копия настроек с новой темой | `UpdateSettingsUseCase` |
| `onUnitSelect(distanceUnit)` | то же с единицами | `UpdateSettingsUseCase` |
| `onPushToggle(enabled)` | то же с `pushEnabled` | `UpdateSettingsUseCase` |
| `onReminderWindowChange(thousandKm)` | `defaultReminderWindowKm = thousandKm × 1 000` (`SettingsViewModel.KM_PER_STEP`) | `UpdateSettingsUseCase` |
| `onReminderTimeClick()` | `isPickingReminderTime = true` | — |
| `onReminderTimeDismiss()` | `isPickingReminderTime = false` | — |
| `onReminderTimeChange(time)` | диалог закрывается, сохраняется новое `reminderTime` | `UpdateSettingsUseCase` |

Все сохранения идут через приватный `update(change)`: берётся `state.value.settings`; пока оно `null`, событие игнорируется.

Что происходит дальше, экран настроек не знает: смену темы подхватывает `AppViewModel.themeMode`, смену единиц — состояния остальных экранов, включение push — `needsNotificationPermission`, а любое изменение настроек — пересборка плана напоминаний.

## Общий UI

### Тема

`ui/theme/`. `PitStopTheme(darkTheme, content)` кладёт в `CompositionLocal` три набора токенов и оборачивает содержимое в `MaterialTheme`, чья `colorScheme` собрана из тех же цветов (`primary` = `accent`, `background` = `bg`, `surface`, `surfaceVariant` = `surface2`, `outline` = `rule`, `onBackground`/`onSurface` = `ink`). Доступ — `PitStopTheme.colors`, `PitStopTheme.typography`, `PitStopTheme.dimens`.

Цвета (`PitStopColorScheme`, `Color.kt`):

| Токен | Светлая | Тёмная | Назначение |
|---|---|---|---|
| `bg` | `#EEF1F5` | `#0A0E15` | фон экрана |
| `surface` | `#FFFFFF` | `#121823` | карточки, диалоги, таб-бар |
| `surface2` | `#E3E8EE` | `#1A222F` | поля, плитки иконок, второстепенные кнопки |
| `surface3` | `#CCD5DE` | `#242E3D` | подложка кольца, выключенный переключатель |
| `ink` | `#10151D` | `#E9EDF3` | основной текст |
| `inkMuted` | `#5C6774` | `#8D99A8` | вторичный текст, иконки |
| `inkFaint` | `#97A2AE` | `#566072` | подписи, плейсхолдеры |
| `rule` | `#DBE1E8` | `#1E2733` | рамки и разделители |
| `accent` | `#0EA5B7` | то же | акцент |
| `onAccent` | `#FFFFFF` | то же | текст и иконки на акценте |
| `tierOk` | `#1FAB8C` | то же | статус «в норме» |
| `tierSoon` | `#E2922E` | то же | статус «скоро» |
| `tierDue` | `#E34B3F` | то же | статус «просрочено», опасные действия |
| `onTier` | `#FFFFFF` | то же | текст на цвете статуса |

Типографика (`PitStopTypography`, `Type.kt`) — три роли; экраны берут роль и подстраивают размер через `copy(...)`:

| Роль | Шрифт | Базовый стиль | Где |
|---|---|---|---|
| `display` | `FontFamily.Default` | ExtraBold, 28 sp, строка 32 sp, трекинг −0.02 em | заголовки экранов и диалогов, крупные числа |
| `sans` | `FontFamily.Default` | Normal, 15 sp, строка 21 sp | обычный текст, кнопки, подписи |
| `mono` | `FontFamily.Monospace` | SemiBold, 13 sp, строка 17 sp | числа и табличные данные: пробег, VIN, даты |

Своих файлов шрифтов нет — используются системные.

Размеры (`PitStopDimens`, `Dimens.kt`):

| Токен | Значение | Токен | Значение |
|---|---|---|---|
| `radiusS` | 8 dp | `spaceXs` | 4 dp |
| `radiusM` | 10 dp | `spaceS` | 8 dp |
| `radiusL` | 12 dp | `spaceM` | 12 dp |
| `radiusXl` | 14 dp | `spaceL` | 16 dp |
| `radiusXxl` | 24 dp | `spaceXl` | 24 dp |

Горизонтальный отступ экрана — отдельная константа `ScreenHorizontalPadding = 18.dp` в `ui/components/screen/ScreenHeader.kt`.

### Компоненты

`ui/components/`. Компоненты не знают о domain-моделях (кроме `LocalTime` в `TimePickerDialog`) и о ViewModel-ах.

| Группа | Компонент | Назначение |
|---|---|---|
| `buttons` | `AppIconButton` | круглая второстепенная кнопка с иконкой: назад, история, правка, удаление |
| `buttons` | `Fab` | круглая акцентная плавающая кнопка — главное действие списка |
| `buttons` | `PresetChip` | пунктирная «пилюля» с «+ название» — быстрая работа в форме |
| `buttons` | `PrimaryButton` | основная акцентная кнопка; выключенная — полупрозрачная |
| `buttons` | `SegmentedControl` | группа взаимоисключающих вариантов: тема, единицы |
| `buttons` | `TabBarItem` | пункт нижнего таб-бара: иконка и подпись, акцентный, когда выбран |
| `fields` | `FieldBox` | `internal`; общая «коробка» всех полей: заливка `surface2`, рамка `rule`, плейсхолдер, одна строка |
| `fields` | `AppTextField` | текстовое поле |
| `fields` | `AppNumberField` | числовое поле моноширинным шрифтом с цифровой клавиатурой; необязательная подпись единиц справа |
| `fields` | `LabeledMiniField` | компактное поле с подписью сверху — поля блока работы |
| `fields` | `InlineEditField` | числовое поле с акцентной кнопкой-галочкой — быстрая правка пробега |
| `steppers` | `Stepper` | «−  значение  +» с шагом и границами; кнопка у границы выключается |
| `indicators` | `StatusTier` | перечисление `Ok`, `Soon`, `Due` с цветом (`color`) и подписью (`label`) |
| `indicators` | `StatusChip` | залитая «пилюля» с названием статуса |
| `indicators` | `GaugeRing` | кольцо на карточке машины: заполненная доля и число в центре |
| `indicators` | `ProgressBar` | тонкая полоса прогресса; цвет — по переданному `tier` или по доле (до 60 % — ok, до 90 % — soon, выше — due) |
| `indicators` | `AppSwitch` | переключатель вкл/выкл |
| `cards` | `ListRowCard` | карточка-строка: плитка с иконкой, заголовок, подзаголовок, слот справа |
| `cards` | `StatCard` | блок показателя с подписью капсом; две перегрузки — со своим содержимым и с одним крупным значением |
| `cards` | `NotificationPreviewCard` | статичная имитация push-уведомления |
| `dialogs` | `ConfirmDialog` | модальное подтверждение опасного действия: иконка, заголовок, текст, две кнопки; подтверждение цвета `tierDue` |
| `dialogs` | `TimePickerDialog` | выбор времени суток двумя `Stepper`-ами: часы с шагом 1, минуты с шагом 5, оба по кругу |
| `screen` | `ScreenTopBar` | `internal`; верхняя строка экрана: «назад» слева, действия справа |
| `screen` | `ScreenTitle` | `internal`; заголовок экрана с необязательным подзаголовком (обычным или моноширинным) |
| — | `ComponentPreview`, `ScreenPreview` | `internal`; обёртки для `@Preview`: тема и фон |

### Форматирование

`ui/format/Formatting.kt`:

| Функция | Что делает |
|---|---|
| `Int.grouped(): String` | разбивает число на тройки разрядов неразрывным пробелом; знак «−» сохраняется |
| `Int.kmIn(unit): Int` | километры → единицы показа: для `MILES` делит на 1.609344 и округляет до целого |
| `@Composable distanceText(km, unit): String` | расстояние с единицей: ресурс `mileage_km` или `distance_mi` с `km.kmIn(unit).grouped()` |
| `LocalDate.toDisplayText(): String` | дата `дд.мм.гггг` |
| `LocalTime.toDisplayText(): String` | время `ЧЧ:ММ` |
| `parseDisplayDate(text): LocalDate?` | обратный разбор `дд.мм.гггг`; три числа через точку, иначе `null`; несуществующая дата → `null` |

Данные всегда хранятся в километрах; перевод в мили — только при показе. Формат даты и разделитель разрядов от языка не зависят.

`ui/format/StatusTierMapper.kt` — единственное место, где domain-статус превращается в статус ui-kit:

| `TaskStatus` | `StatusTier` | Цвет | Подпись |
|---|---|---|---|
| `OK` | `Ok` | `tierOk` | `status_ok` |
| `SOON` | `Soon` | `tierSoon` | `status_soon` |
| `DUE` | `Due` | `tierDue` | `status_due` |

### Иконки

`ui/icons/PitStopIcons.kt` — объект-обёртка над набором Lucide. Экраны и компоненты обращаются только к `PitStopIcons.*`, чтобы источник иконок можно было сменить в одном месте. 17 иконок: `Car`, `Gauge`, `Droplet`, `Disc`, `Filter`, `Wrench`, `Gear` (`Lucide.Settings`), `Clock`, `Check`, `ChevronLeft`, `Pencil`, `Trash` (`Lucide.Trash2`), `Bell`, `Plus`, `Minus`, `Sun`, `MoonToggle` (`Lucide.Moon`).

### Локализация

Строки — в ресурсах Compose Multiplatform: `shared/src/commonMain/composeResources/values/strings.xml` (английский, по умолчанию), `values-ru/`, `values-es/`. Набор ключей во всех трёх файлах одинаковый. Язык выбирает система; переключателя языка в приложении нет.

- Множественные формы — `plurals`: `cars_count`, `tasks_added`.
- Строки с подстановкой — `%1$s` / `%1$d`: расстояния (`mileage_km`, `distance_mi`), `count_of_total`, `vin_label`, `task_*`, `dialog_delete_car_title`, `cost_rub`, `task_block_title`, `notification_reminder_text`.
- Названия быстрых работ (`task_name_*`) берутся из ресурсов в момент добавления и сохраняются в базу уже текстом на языке устройства.
- Часть строк читает и data-слой — для текста уведомлений (см. `specs/data.md`).

## DI

`di/FeatureModule.kt`, модуль `featureModule`:

| ViewModel | Регистрация | Параметр |
|---|---|---|
| `AppViewModel` | `viewModelOf(::AppViewModel)` | — |
| `CarsViewModel` | `viewModelOf(::CarsViewModel)` | — |
| `CarFormViewModel` | `viewModel { params -> CarFormViewModel(params.getOrNull(), get(), …) }` | `carId: String?` |
| `CarDetailViewModel` | `viewModelOf(::CarDetailViewModel)` | `carId: String` |
| `HistoryViewModel` | `viewModelOf(::HistoryViewModel)` | `carId: String` |
| `SettingsViewModel` | `viewModelOf(::SettingsViewModel)` | — |

- Экран получает ViewModel значением параметра по умолчанию: `viewModel: XxxViewModel = koinViewModel()`.
- `carId` передаётся как параметр Koin: `koinViewModel { parametersOf(carId) }`. Для `CarDetailViewModel` и `HistoryViewModel` Koin сам подставляет строку в конструктор; у `CarFormViewModel` параметр может быть `null`, поэтому фабрика написана явно через `params.getOrNull()`.
- Use case-ы приходят из `domainModule` (все — `factoryOf`).
- Модуль подключается в `initKoin` вместе с `domainModule`, `dataModule`, `platformModule` (см. `specs/data.md`).

## Согласованные изменения (этап 8а)

Всё в этом разделе **согласовано и нарисовано в макете `docs/index.html`, но в коде ещё не сделано**. Остальной документ описывает код как есть. Порядок работ и отметки о готовности — в блоке «Этап 8а» в `dev_plan.md`. Имена новых классов и маршрутов предварительные.

### Экраны макета

| Экран макета | Что показывает |
|---|---|
| 03 · Регламентные работы | новый экран авто: без карандаша, FAB «+», название, VIN и пробег с пунктирным подчёркиванием |
| 07 · Редактирование работы | экран одной работы, заполненный; корзина в топ-баре |
| 09 · Незаполненные поля | форма авто с красными рамками у пустых обязательных полей |
| 10 · Работа выполнена | окно с сервисом, стоимостью и комментарием |
| 11 · Снятие отметки | окно подтверждения снятия отметки |
| 12 · Новая работа | экран одной работы, пустой, с чипами пресетов |
| 13 · Название и VIN | название авто в режиме правки на месте |
| 04 · История ТО | записи, сгруппированные по годам и месяцам |
| 14 · Запись истории | окно одной записи с правкой даты, пробега, сервиса, стоимости и комментария |
| 04 · История ТО, 16–18 | общая история: все авто, выбор авто, фильтр, проданное авто |
| 05 · Настройки, 15 · Открывать по умолчанию | группа «Запуск»: пункт выключен и включён со списком авто |

### Навигация

- Маршрут `EditCar` (`cars/{carId}/edit`) убирается вместе с режимом правки в форме авто.
- Добавляются два маршрута экрана работы: новая работа машины и правка существующей (`cars/{carId}/tasks/new`, `cars/{carId}/tasks/{taskId}`).
- Экран авто открывает их по FAB и по тапу на строку работы; после сохранения или удаления экран работы закрывается.
- История становится маршрутом верхнего уровня без `carId` (сейчас `cars/{carId}/history`) и получает вкладку в нижнем меню. Вкладок три, в порядке «История», «Авто», «Настройки», у каждой иконка; таб-бар показывается на всех трёх. Стартовый маршрут остаётся `Cars`.
- Кнопка-часы на карточке авто переключает на вкладку «История» с уже выбранным этим авто, а не открывает отдельный экран поверх карточки.

### Авто при запуске

- При запуске приложение открывает карточку авто поверх списка: «назад» с неё ведёт в список авто.
- Какое авто: если в настройках включён пункт «Открывать по умолчанию» — выбранное там; иначе — последнее открытое. Последним открытым считается авто, карточку которого пользователь открывал последней.
- Если такого авто нет (ничего не открывали, авто удалено) — открывается список авто, как сейчас.

### `settings`: группа «Запуск»

- Стоит между «Единицами измерения» и «Уведомлениями». Если нет ни одного авто, группы нет.
- Пункт «Открывать по умолчанию» с переключателем (тот же `AppSwitch`, что у push). Подпись под названием: выключен — «Сейчас при запуске открывается последнее открытое авто», включён — «При запуске открывается выбранное авто».
- При включении под пунктом появляется список всех авто (марка, пробег, радиокнопка); выбрано одно. Сразу после включения выбрано последнее открытое авто, а если его нет — первое в списке.
- При удалении выбранного авто вместо него выбирается другое: последнее открытое, а если его нет — первое в списке. Пункт остаётся включённым. Если авто не осталось, настройка выключается (и группа «Запуск» пропадает).

### `settings`: раздел «Удаление авто»

- Последний раздел настроек, под «Уведомлениями». Если нет ни одного авто — ни существующего, ни проданного, — раздела нет.
- Под заголовком пояснение: авто удаляется полностью, вместе с работами и всей историей ТО, восстановить данные нельзя.
- Список всех авто: сначала существующие (марка, пробег), затем проданные (марка серым, подпись «продано»). У каждой строки кнопка-корзина.
- Корзина открывает окно «Удалить {марка} полностью?» с «Отмена» и «Удалить». После удаления авто пропадает из списка авто, из истории и из этого раздела.
- Это единственное место, где удаляется история; у проданного авто — единственный способ убрать его записи.

### Удаление авто с карточки

Окно подтверждения на карточке авто остаётся («Удалить {марка}?», «Отмена» / «Удалить»). Текст сообщает три вещи: авто и его работы будут удалены; история ТО сохранится в разделе «История» с пометкой «продано»; полностью удалить авто вместе с историей можно в настройках.

### Обязательные поля

| Где | Обязательные поля |
|---|---|
| форма авто | марка, пробег |
| блок работы в форме авто | название, интервал замены, «Последний раз, км» |
| экран работы | название, интервал, «Последний раз, км» |
| экран авто, правка на месте | название, пробег |

- Правка на месте — исключение из правила ниже: там нет красной рамки, вместо неё неактивная галочка при пустом поле.
- Кнопка сохранения в формах всегда активна. По нажатию пустые обязательные поля получают красную рамку (цвет `tierDue`, без текста ошибки), форма не сохраняется. Рамка пропадает, когда поле заполнили.
- У «Последний раз, км» нет значения по умолчанию: пустое поле больше не превращается в 0, пользователь вводит значение сам. 0 допустим — так отмечают, что работа ещё не проводилась. Чип пресета это поле не заполняет.
- Блок работы без обязательных полей не даёт сохранить форму авто; молчаливого пропуска блоков больше нет, ненужный блок удаляется корзиной.
- Работа по умолчанию («замена масла») при добавлении авто без блоков остаётся и считается от текущего пробега.

### `carform`

Форма только добавляет авто: параметр `carId`, загрузка машины, флаги `isEditing`/`isLoading`/`isMissing` и ветка сохранения правки уходят.

Когда добавляется блок работы — чипом пресета или кнопкой «Добавить произвольную работу», — форма прокручивается к новому блоку так, чтобы он был виден целиком. Удаление блока и неудачное сохранение прокрутку не сбрасывают.

### `taskform` — экран одной работы (новая фича)

- Два режима: «Новая работа» и «Редактирование работы»; под заголовком — марка авто.
- Поля: название, интервал (км), интервал (мес), «Последний раз, км», дата замены, «Напоминать за N км до срока». Обязательные — см. таблицу выше.
- У новой работы сверху чипы пресетов: чип заполняет название, интервал и окно напоминания.
- У новой работы над «Сохранить» есть кнопка «Добавить ещё»: сохраняет текущую работу и показывает пустую форму для следующей, экран не закрывается. При пустых обязательных полях подсвечивает их и ничего не сохраняет. «Сохранить» по-прежнему сохраняет и возвращает на экран авто. В режиме правки кнопки нет.
- В режиме правки в топ-баре корзина: открывает окно «Удалить работу?» с «Отмена» и «Удалить». Записи истории ТО по работе остаются.
- Вызывает уже существующие `AddTaskUseCase`, `UpdateTaskUseCase`, `DeleteTaskUseCase`.

### `cardetail`

- **Топ-бар:** карандаша нет; остаются «назад», часы (переход на вкладку «История» с выбранным этим авто) и удаление авто.
- **Шапка.** Между названием авто и VIN — вертикальный отступ (в макете 10 px); он сохраняется, когда любое из них превращается в поле ввода.
- **Правка на месте.** Название, VIN и пробег по тапу превращаются в поле ввода с текущим значением и галочкой справа. В блоке пробега иконка слева крупнее, значение прижато вправо, отдельной кнопки-карандаша нет.
- **Одно поле за раз, сохраняет только галочка.** Тап мимо открытого поля — по другому полю, строке работы, кнопке или пустому месту экрана — завершает правку и возвращает полю исходное значение. Правило одно для названия, VIN и пробега. То, по чему тапнули, срабатывает как обычно: другое поле открывается на правку, строка работы открывает работу.
- **Галочка.** Сохраняет значение. У обязательных полей — названия и пробега — галочка неактивна (полупрозрачная), пока поле пустое; красной рамки при правке на месте нет. У VIN галочка активна всегда.
- **Сводка работы.** Под названием работы каждая часть сводки начинается с новой строки: «осталось N км» или «просрочено на N км»; «или N мес» или «время истекло» — только у работ с интервалом по времени; «каждые N км». Сейчас это одна строка через « · ».
- **Список работ.** Каждая строка открывает экран этой работы. Кнопка отметки внутри строки срабатывает отдельно и экран работы не открывает.
- **FAB «+»** открывает экран новой работы.
- **Отметить выполненной.** Нажатие открывает окно «Работа выполнена» с тремя необязательными полями: «Где обслуживались», «Сколько стоило, ₽», «Комментарий». «Отмена» — работа не отмечается; «Сохранить» — отмечается, в том числе с пустыми полями.
- **Выполнено.** Пока пробег авто равен тому, на котором работу отметили, кнопка показывает «Выполнено». Нажатие открывает окно «Снять отметку?» с «Отмена» и «Ok»; «Ok» возвращает работу к прежним значениям и удаляет запись из истории ТО.

### `history`

**Общий раздел.** История — вкладка нижнего меню. Порядок вкладок: «История», «Авто», «Настройки»; у каждой иконка над подписью — часы, автомобиль в профиль (вид сбоку, `Lucide.Car`), шестерёнка. Стартовый экран остаётся «Авто». История — экран верхнего уровня с таб-баром и без кнопки «назад». Маршрут больше не привязан к `carId`.

- **Поле выбора авто** всегда вверху, на всю ширину; справа от него кнопка фильтра. По тапу раскрывается список: «Все авто», затем все авто, затем удалённые — серым, с пометкой «[продано]». По умолчанию выбрано «Все авто».
- **Кнопка фильтра** открывает окно: период «с — по» (даты текстом), поиск по названию работы, пробег «с — по»; «Сбросить» очищает фильтр, «Применить» применяет, тап по затемнению закрывает без изменений. При активном фильтре кнопка подсвечена акцентом и точкой.
- Выбор авто и фильтр действуют вместе. Поиск по названию работы учитывает словоформы: «масло» находит «Замена масла» и «масел» (правило — в `specs/domain.md`).
- **Карточка записи.** Сверху название работы и дата, под ними строка «авто · пробег · стоимость · сервис», ниже комментарий. Название авто в этой строке есть только в режиме «Все авто» (у проданного — серым, с пометкой).
- Пустые состояния: записей нет вообще — прежний текст; под фильтр ничего не попало — «Ничего не найдено — измените фильтр».
- Часы в топ-баре карточки авто остаются и открывают эту вкладку с уже выбранным авто.
- Окно удаления авто сообщает, что история сохранится в разделе «История» с пометкой «продано».

- У записи появляется строка комментария под строкой «пробег · стоимость · сервис»; без комментария строки нет.
- **Группировка.** Записи идут от новых к старым и разбиты по годам и месяцам. Год — разделитель: число по центру, слева и справа линии до краёв экрана. Месяц — рамка с подписью месяца в разрыве верхней линии слева; внутри рамки — записи этого месяца. Фон внутри рамки — `surface`, светлее фона экрана, как у карточек на других экранах. Подпись месяца — плашка со скруглёнными краями: фон `surface` и тонкая рамка `rule`, как у карточки; стоит на верхней линии карточки слева. Месяцы без записей не показываются.
- **Закреплённый год.** При прокрутке разделитель года, записи которого сейчас на экране, остаётся у верхнего края списка под заголовком экрана, с непрозрачным фоном; когда доходит очередь следующего года, его разделитель занимает это место. Заголовок «История ТО» и марка авто не прокручиваются. В макете следующий разделитель накрывает предыдущий; в приложении это `stickyHeader` в `LazyColumn`, где следующий заголовок выталкивает предыдущий, — оба варианта допустимы.
- **Запись по тапу** открывает окно записи: заголовок — название работы, под ним авто (у проданного — с пометкой). Ниже пять полей, заполненных значениями записи: «Дата» и «Пробег, км» в одной строке — обязательные; «Где обслуживались», «Сколько стоило, ₽», «Комментарий» — необязательные. «Сохранить» записывает их в запись; дата, которой не существует, и пробег `≤ 0` не сохраняются и получают красную рамку. «Отмена» и тап по затемнению закрывают окно без изменений. После смены даты запись переезжает в свой год и месяц. Отдельного поля «Что менялось» нет: дополнительные сведения пишутся в комментарии.
- Группировка — дело экрана: `ObserveHistoryUseCase` уже отдаёт записи от новых к старым, новых правил в domain не нужно.
- Несколько записей одного месяца лежат в одной рамке и разделены линией, как строки списка сейчас.
- Подпись месяца — название без года (год виден в разделителе выше); названия месяцев нужны в строках трёх языков.
- В макете группировку можно посмотреть на демо-данных Kia Rio: история за 2023–2026 годы, 23 записи.

### Общий UI

- `isError` у `FieldBox`, `AppTextField`, `AppNumberField`, `LabeledMiniField`, `InlineEditField` — красная рамка.
- `TaskDoneDialog` — окно с тремя полями; окно снятия отметки — вариант `ConfirmDialog` с акцентной, а не красной кнопкой.
- Кнопка отметки работы в двух состояниях: «Отметить выполненной» и «Выполнено».
- Контурная кнопка (вторичное действие рядом с `PrimaryButton`): текст и рамка цвета `accent`, фон — фон экрана (`bg`), те же размеры и скругление.

### Что не решено

- В каких единицах правится пробег на месте, если в настройках выбраны мили (см. «Открытые вопросы»).

## Открытые вопросы

- **Работа из чипа сразу просрочена.** Пустое «последний раз, км» сохраняется как `0` (`CarFormViewModel.kt:163`), хотя плейсхолдер поля — «не проводилась» (`last_done_km_placeholder`). Для машины с пробегом работа из пресета, добавленная без этого поля, сразу получает статус `DUE`. Работа по умолчанию при этом считается от текущего пробега (`CarFormViewModel.kt:137`).
- **Дата вводится свободным текстом.** Поле даты не фильтруется и не проверяется; дата, которая не разобралась, молча превращается в `null` (`CarFormViewModel.kt:166`). При правке это стирает сохранённую дату последнего выполнения.
- **Незаполненный блок пропускается молча** — без подсказки, почему работа не сохранилась. Неудачное сохранение (`AddCarUseCase` вернул `null`, `UpdateCarUseCase` — `false`) тоже ничем не показывается.
- **VIN не проверяется**: только верхний регистр и обрезка до 17 символов; короткий VIN и недопустимые символы сохраняются.
- **Пробег вводится в километрах при любых единицах показа.** На экране машины значение показано в милях, а поле правки открывается с километрами (`CarDetailScreen.kt:210` и `:224`); подпись в форме — всегда «Пробег, км». Шаг «напоминать заранее» в настройках — тоже всегда тысячи километров.
- **Некорректный пробег в быстрой правке закрывает поле без сообщения** (`CarDetailViewModel.kt:60`).
- **«Просрочено на 0 км».** При `remainingKm == 0` строка-сводка работы показывает просрочку с нулём (`CarDetailScreen.kt:313`).
- **`defaultReminderWindowKm` ни на что не влияет.** Значение редактируется в настройках и сохраняется, но форма авто его не читает: работе без окна `NormalizeTaskUseCase` ставит 15 % интервала. KDoc `Settings` при этом говорит, что оно предзаполняет окно новых работ.
- **Повторный запрос разрешения.** Запрос уходит при каждом переходе `needsNotificationPermission` в `true`, в том числе при каждом запуске приложения с машинами и включённым push. Если система отвечает отказом без показа диалога, переключатель push, который пользователь только что включил, сам выключится обратно — без объяснения.
- **Android ниже 13 всегда отвечает «разрешено»** (`NotificationPermission.android.kt:23`), даже если уведомления приложения выключены в системных настройках.
- **Тема до прихода настроек.** Начальное значение `themeMode` — `SYSTEM`; если сохранена другая тема, первый кадр может быть нарисован в системной. `MainActivity` вызывает `enableEdgeToEdge()` без параметров, а тема в манифесте — `Theme.Material.Light.NoActionBar`: выбранная в приложении тема в оформление системных панелей не передаётся.
- **«Сегодня» берётся в момент пересчёта состояния.** `GetTodayUseCase` вызывается внутри `combine`; если приложение открыто через полночь и данные не меняются, статусы на экране не обновятся.
- **Кнопки шапки экрана машины активны до загрузки машины**: история и правка откроются сразу, а нажатие на корзину выставит `isConfirmingDelete` — диалог появится, как только машина загрузится.
- **Форма правки читает машину один раз** (`first()`) и за изменениями не следит.
- **Экран истории не закрывается, если машины нет**: показывает пустую марку и пустой журнал. На практике попасть туда без машины нельзя.
- **Стоимость всегда в рублях** (`cost_rub`, «₽») на всех трёх языках.
- **Пример уведомления в настройках не совпадает с настоящим текстом.** `notification_sample_full` — «…пора заменить воздушный фильтр — осталось 700 км», а настоящее уведомление строится как «{марка}: {работа} — {состояние}».
- **Подбор иконки работы по подстроке**: `pad` и `oil` встречаются внутри других слов; для работ с названием на другом языке всегда будет гаечный ключ.
- **Устаревший KDoc** у `PitStopTheme` (`ui/theme/Theme.kt:27`): написано, что `darkTheme` «будет подключён к настройкам на шаге 6», хотя это уже сделано в `App.kt`.
- **Неиспользуемое**: иконки `PitStopIcons.Sun` и `PitStopIcons.MoonToggle`; строки `screen_car_detail` и `field_last_replacement_date`; строки `unit_km`, `field_interval_km`, `notification_sample_text` и параметр `unit` у `AppNumberField` используются только в `@Preview`; ветка `ProgressBar` с цветом по доле — экран всегда передаёт `tier`.

## Что ещё не реализовано

- Правки дизайна из блока «Этап 8а — Правки дизайна после прогона» в `dev_plan.md`: подсветка пустых обязательных полей вместо выключенной кнопки, окно «Работа выполнена» с сервисом, стоимостью и комментарием, снятие отметки о выполнении, новый блок пробега на экране машины, строка комментария в истории. Сейчас ничего из этого в коде нет.
- Правка записи истории ТО: экрана нет, `UpdateHistoryEntryUseCase` — заготовка (см. `specs/domain.md`). Стоимость и сервис, которые умеет показывать экран истории, ввести негде — у записей, созданных приложением, они всегда пустые.
- Анимации индикаторов и остальная «полировка» из этапа 9 в `dev_plan.md`; сейчас анимирован только ползунок `AppSwitch`.
- Переключатель языка в приложении.
- Юнит-тесты на ViewModel-и и тесты на общие Composable-компоненты.
