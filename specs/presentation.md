# Спецификация: presentation-слой

Пакеты: `ru.kuznetsov.itstime4service.features`, `ru.kuznetsov.itstime4service.ui` и корневые `App.kt`, `AppViewModel.kt` (`shared/src/commonMain/kotlin/ru/kuznetsov/itstime4service/`).

Presentation-слой — экраны на Compose Multiplatform, общие для Android и iOS. Из библиотек использует Compose (`foundation`, `material3`, `ui`, `components-resources`), `lifecycle-viewmodel-compose`, `navigation-compose`, Koin (`koin-compose-viewmodel`), иконки Lucide (`com.composables:icons-lucide`) и `kotlinx-datetime`. Внешний вид задаёт макет `docs/index.html`.

```
App.kt, AppViewModel.kt   # корень: тема, запуск, разрешение на уведомления, пересборка плана напоминаний
features/
├── navigation/           # Destination, ItsTime4ServiceNavGraph, таб-бар
├── cars/                 # список машин
├── carform/              # форма добавления машины
├── cardetail/            # экран машины: пробег, работы, отметки
├── taskform/             # экран одной работы: новая и правка
├── history/              # общая история ТО
├── settings/             # настройки
└── licenses/             # лицензии открытого ПО
ui/
├── theme/                # цвета, типографика, размеры, ItsTime4ServiceTheme
├── components/           # ui-kit: buttons, fields, steppers, indicators, cards, dialogs, screen
├── format/               # числа, расстояния и единицы, ввод дат, поля работы, пресеты, TaskStatus → StatusTier
├── icons/                # ItsTime4ServiceIcons
└── notification/         # expect-функция запроса разрешения на уведомления
```

Правила слоя:

- Каждая фича — `*Screen.kt`, `*UiState.kt`, `*ViewModel.kt` и вынесенные части экрана: самостоятельный компонент или окно — отдельный файл, `internal`, с собственными превью. Сейчас это:
  - `cardetail`: `CarDetailHeader.kt` (шапка: марка, номер, VIN, правка на месте), `CarDetailCards.kt` (карточки пробега и работы);
  - `cars`: `CarCards.kt`;
  - `carform`: `CarFormTaskBlock.kt` (блок работы, свёрнутый и развёрнутый);
  - `history`: `HistoryBar.kt` (выбор машины и кнопка фильтра), `HistoryList.kt` (год, рамка месяца, строка записи, пустое состояние), `HistoryFilterDialog.kt`, `HistoryEntryDialog.kt`, `NewHistoryEntryDialog.kt`, `HistoryCarLabel.kt` (подпись машины с пометкой «продано»), `HistoryRows.kt` (чистая логика списка);
  - `settings`: `SettingsRows.kt` (группа, строка настройки, списки машин).
- Экран — пара Composable-функций: `XxxScreen` получает ViewModel через `koinViewModel`, собирает состояние через `collectAsStateWithLifecycle()` и передаёт его в `internal XxxContent(state, колбэки…)`. `XxxContent` о ViewModel не знает; на нём же построены `@Preview` в светлой и тёмной теме.
- ViewModel отдаёт один `StateFlow<XxxUiState>` и набор методов `onXxx(...)`. Отдельного канала разовых событий нет: «закрой экран» передаётся флагом в состоянии (`isMissing`, `isSaved`, `isClosed`), на который экран реагирует через `LaunchedEffect`.
- ViewModel-ы зависят только от use case-ов domain-слоя (см. `specs/domain.md`); репозиториев и data-слоя не видят.
- Экранное состояние, которое нигде не хранится (открытые окна, правка на месте, фильтр), лежит в приватном `MutableStateFlow` ViewModel-а и объединяется с данными через `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`. Формы (`CarFormViewModel`, `TaskFormViewModel`) держат один `MutableStateFlow`.
- Тексты — только из ресурсов (`composeResources/values*/strings.xml`). Когда ViewModel-у нужна локализованная строка, её передаёт экран параметром.
- Расстояния показываются и вводятся в единицах из настроек, хранятся в километрах (см. «Единицы расстояния»).

## Навигация

`features/navigation/Destinations.kt`, `features/navigation/NavGraph.kt`. Маршруты строковые, `sealed class Destination(val route: String)`; у маршрутов с аргументами есть `createRoute(...)`.

| Destination | Маршрут | Экран | Таб-бар |
|---|---|---|---|
| `Cars` | `cars` | `CarsScreen`; стартовый | да |
| `AddCar` | `add-car` | `CarFormScreen` | нет |
| `CarDetail` | `cars/{carId}` | `CarDetailScreen` | нет |
| `NewTask` | `cars/{carId}/new-task` | `TaskFormScreen` без `taskId` | нет |
| `EditTask` | `cars/{carId}/tasks/{taskId}` | `TaskFormScreen` с `taskId` | нет |
| `History` | `history?carId={carId}`, `carId` необязателен | `HistoryScreen` | да |
| `Settings` | `settings` | `SettingsScreen` | да |
| `Licenses` | `settings/licenses` | `LicensesScreen` | нет |

### Переходы

| Откуда | Действие | Куда |
|---|---|---|
| запуск | `launchCarId != null` (см. «Корень приложения») | один раз `navigate(CarDetail)` поверх `Cars` |
| `Cars` | тап по машине (строка, верх или работы карточки) / `AddCarRow` | `CarDetail` / `AddCar` |
| `Cars` | тап по истории в карточке | вкладка «История» с этой машиной: `navigate(History.createRoute(carId)) { popUpTo(Cars.route); launchSingleTop = true }` |
| `AddCar` | «назад» или сохранение | `popBackStack()` |
| `CarDetail` | «назад» или машина исчезла (`isMissing`) | `popBackStack(Cars.route, inclusive = false)` |
| `CarDetail` | часы | вкладка «История» с выбранной машиной: `navigate(History.createRoute(carId)) { popUpTo(Cars.route); launchSingleTop = true }` |
| `CarDetail` | FAB / тап по карточке работы | `NewTask` / `EditTask` |
| `NewTask`, `EditTask` | «назад», сохранение, удаление, работы или машины нет | `popBackStack()` |
| `Settings` | «Лицензии открытого ПО» | `Licenses` |
| `Licenses` | «назад» | `popBackStack()` |
| таб-бар | «Авто» | `navigate(Cars) { popUpTo(Cars); launchSingleTop = true }` — список, а не сохранённое состояние вкладки |
| таб-бар | «История», «Настройки» | `navigate(route) { popUpTo(Cars) { saveState = true }; launchSingleTop = true; restoreState = true }` |

### Каркас

`ItsTime4ServiceNavGraph(launchCarId)` — `Scaffold` на весь экран: `containerColor = bg`, `contentWindowInsets = WindowInsets.safeDrawing`, внутри `NavHost` со стартовым `Cars`. Переход на машину запуска — `LaunchedEffect` после `NavHost`; флаг «уже открыли» в `rememberSaveable`, поэтому поворот экрана и восстановление процесса его не повторяют.

Таб-бар (`bottomBar`) показывается на `History`, `Cars` и `Settings`: линия `rule` в 1 dp, под ней три `TabBarItem` равной ширины — «История» (`Clock`), «Авто» (`Car`), «Настройки» (`Gear`); фон `surface`, снизу `navigationBarsPadding()`.

**iOS.** Compose рисуется на весь экран (`ComposeView().ignoresSafeArea()` в `ContentView.swift`), отступы под вырез, home indicator и клавиатуру даёт `WindowInsets.safeDrawing` у `Scaffold`, у таб-бара — `navigationBarsPadding()`. Свайп от левого края — системное «назад» Compose Multiplatform (встроенный распознаватель жеста в 1.12, без настройки) — переход назад между экранами навигации, тот же `popBackStack`, что и кнопка «назад». **Ожидается, но не проверено:** что тот же свайп при открытом окне (`Dialog`) закрывает окно как отмену, а не уводит с экрана под ним, — документация Compose Multiplatform описывает свайп только для навигации, отдельно в коде это не задано и тестом не подтверждено; проверяется в симуляторе (см. `dev_plan.md`). Свайп от правого края не используется (`endEdgePanGestureBehavior` по умолчанию).

## Корень приложения

### `App`

`App.kt`. Точка входа для обеих платформ: Android вызывает `App()` из `MainActivity.setContent`, iOS — из `MainViewController()` (`ComposeUIViewController { App() }`).

1. Берёт `AppViewModel` через `koinViewModel()`.
2. Вычисляет тёмную тему: `ThemeMode.LIGHT` → светлая, `DARK` → тёмная, `SYSTEM` → `isSystemInDarkTheme()`.
3. Через `LaunchedEffect(needsNotificationPermission)` запрашивает системное разрешение на уведомления каждый раз, когда флаг становится `true`.
4. В `ItsTime4ServiceTheme(darkTheme)`: пока `launch` — `Pending`, рисует только фон `bg`; затем `ItsTime4ServiceNavGraph(launchCarId)`.

### `AppViewModel`

Состояние над графом навигации. Зависимости: `ObserveSettingsUseCase`, `ObserveCarsUseCase`, `KeepRemindersInSyncUseCase`, `UpdateSettingsUseCase`, `GetLaunchCarUseCase`. Правила пересборки плана уведомлений — в `KeepRemindersInSyncUseCase` (см. `specs/domain.md`); ViewModel только запускает его в своём scope и сообщает о выданном разрешении.

| Член | Тип | Смысл |
|---|---|---|
| `themeMode` | `StateFlow<ThemeMode>` | тема из настроек; до прихода настроек — `SYSTEM` |
| `needsNotificationPermission` | `StateFlow<Boolean>` | `settings.pushEnabled && cars.isNotEmpty()`; начальное значение `false` |
| `launch` | `StateFlow<Launch>` | `Pending`, затем `Ready(carId)` — машина, экран которой открыть поверх списка, или `null` |
| `onNotificationPermissionResult(granted)` | метод | ответ системы на запрос разрешения |

| Событие | Что происходит | Use case |
|---|---|---|
| создание (`init`) | один раз решается машина запуска | `GetLaunchCarUseCase` |
| создание (`init`) | в `viewModelScope` запускается `KeepRemindersInSyncUseCase(permissionGrants)`; он работает, пока жив ViewModel | `KeepRemindersInSyncUseCase` |
| разрешение выдано | событие в `permissionGrants` (`MutableSharedFlow<Unit>`) → план строится ещё раз | `KeepRemindersInSyncUseCase` |
| в разрешении отказано | `pushEnabled = false` | `UpdateSettingsUseCase` |

- Разрешение запрашивается, когда push включён и есть хотя бы одна машина: при запуске с машинами, после добавления первой, после включения push.
- Отказ выключает push в настройках; повторное включение повторяет запрос.
- Смена темы и открытие экранов машин план уведомлений не перестраивают.

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

Список машин в одном из трёх видов (`CarsView`) и кнопка добавления в конце списка.

Экран (`CarsContent`):

- Слева вверху — переключатель вида `CarsViewToggle`: три круглые иконки `List`, `PanelTop`, `LayoutPanelTop` — «Список», «Карточки с работами», «Карточки с работами и историей»; выбранная подсвечена акцентом. Выбор сохраняется в настройках и переживает перезапуск.
- `ScreenTitle`: заголовок `screen_cars`; в виде «Список» подзаголовок — число машин (`plurals cars_count`) и подсказка `cars_gauge_hint` через « · », в карточках подзаголовка нет.
- **Список** — `LazyColumn` из `ListRowCard`: иконка машины, марка, пробег в выбранных единицах (`distanceText`), справа `GaugeRing`.
- **Карточки** (`CarCard`, `features/cars/CarCards.kt`): сверху марка, пробег крупно и справа `StatusDonut`; ниже колонка «Ближайшие работы» — до 4 самых срочных работ с остатком цветом статуса (просроченный — со знаком «−»); в виде с историей рядом, через вертикальный разделитель, колонка «Последние работы» — до 4 последних записей с датой `дд.мм.гг`. Пусто — «Работ нет» / «Записей пока нет». Внизу по центру госномер (`LicensePlate`, `compact`), огибаемый пологой линией от нижнего края карточки; разделитель колонок упирается в линию; без номера линии нет. Тап по верху и по работам — экран машины, по истории — вкладка «История» с этой машиной.
- В конце списка во всех видах — `AddCarRow`: пунктирная рамка с «+» по центру (66 dp в списке, 54 dp в карточках); прокручивается вместе со списком. FAB на этом экране нет.
- Ключ элемента — `id` машины. Порядок — как отдал `ObserveCarsUseCase`.
- Пустой список после загрузки — текст `cars_empty` по центру и под ним `AddCarRow`. Пока `isLoading = true`, пустое состояние не показывается.

`CarsUiState`:

| Поле | Тип | По умолчанию | Смысл |
|---|---|---|---|
| `isLoading` | `Boolean` | `true` | `true` до первого списка из базы |
| `cars` | `List<CarItem>` | пусто | машины |
| `distanceUnit` | `DistanceUnit` | `KILOMETERS` | единицы показа пробега |
| `view` | `CarsView` | `LIST` | вид списка из настроек |

`CarItem`:

| Поле | Тип | Смысл |
|---|---|---|
| `id` | `String` | идентификатор машины |
| `brand` | `String` | марка |
| `mileageKm` | `Int` | пробег в км |
| `alertCount` | `Int` | `CarAlerts.total` — число работ `SOON` и `DUE`, цифра внутри кольца строки |
| `gaugeValue` | `Float` | заполненная доля кольца строки, 0..1 |
| `status` | `TaskStatus` | `CarAlerts.worstStatus` — цвет кольца и цифры |
| `dueCount`, `soonCount`, `taskCount` | `Int` | дуги `StatusDonut` карточки |
| `upcomingTasks` | `List<UpcomingTask>` | до 4 работ (`name`, `remainingKm`, `status`), самые срочные первыми |
| `recentRecords` | `List<RecentRecord>` | до 4 записей истории (`taskName`, `date`), новые первыми |
| `plateLook` | `PlateLook?` | как рисовать номер; `null` без номера |

| Источник или событие | Что происходит | Use case |
|---|---|---|
| `combine(observeCars(), observeAllHistory(), observeSettings())` | на каждое изменение машин, истории или настроек состояние собирается заново; история раскладывается по машинам | `ObserveCarsUseCase`, `ObserveAllHistoryUseCase`, `ObserveSettingsUseCase` |
| сборка машины | сводка и ближайшие работы на сегодняшнюю дату; вид номера — по региону устройства | `GetTodayUseCase`, `CarAlertsUseCase`, `SortedTaskStatsUseCase`, `DetectPlateStyleUseCase` |
| `onViewSelect(view)` | сохраняет вид | `UpdateSettingsUseCase` |

Тапы по машине, истории машины и `AddCarRow` уходят из экрана напрямую в навигацию.

Правило кольца строки: `gaugeValue = alerts.total / alerts.taskCount`; если работ «скоро» и «просрочено» нет (в том числе у машины без работ) — `0.06`, чтобы кольцо не выглядело пустым.

### `carform` — форма авто

Только добавление машины. `CarFormScreen(onBack)`.

- Поля машины: «Марка», «Пробег» (в выбранных единицах), «Госномер» (свободный текст в верхнем регистре, до 12 символов; не проверяется), «VIN» (верхний регистр, до 17 символов). Марка и пробег обязательны.
- Чипы быстрых работ (`PresetChip`) — по `TaskPreset`; чип добавляет блок с названием, интервалом и окном в выбранных единицах, «Последний раз» не заполняет.
- Блоки работ: название, интервал (единицы / мес), «Последний раз» и дата замены, «Напоминать за». Обязательные — название и интервал. Пустой «Последний раз» сохраняется как 0 — работа ещё не проводилась (например, машина только куплена). Под «Последний раз» и датой — чекбокс `AppCheckbox` «Создать запись в истории», по умолчанию выключен: включённый заполняет пустую дату сегодняшней, делает пробег (`> 0`) и дату обязательными и показывает `ServiceRecordFields` (сервис, стоимость, комментарий); работа сохраняется с записью истории о последнем выполнении (`NewMaintenanceTask.record`). Блок сворачивается стрелкой рядом с корзиной: свёрнутый — название и «каждые N · последний раз M»; тап разворачивает. Новые блоки — развёрнутые; после добавления форма прокручивается к новому блоку.
- Внизу в одну строку: «+ Добавить работу» (`OutlineButton`) и «Сохранить авто» (`PrimaryButton`, всегда активна).
- Сохранение: пустые обязательные поля — красная рамка, свёрнутые блоки с ними разворачиваются, ничего не сохраняется. Без блоков подставляется работа по умолчанию («замена масла» по пресету `OIL`: интервал 7 500 км, окно 1 500 км, «последний раз» = введённый пробег). Повторные нажатия, пока идёт сохранение, игнорируются. Машина сохраняется через `AddCarUseCase`; при успехе `isSaved` закрывает экран.

`CarFormUiState`: `brand`, `mileageKm`, `plate`, `vin`, `tasks: List<TaskBlockState>` (`key`, `fields: TaskFields`, `isCollapsed`), `showErrors`, `isSaved`, `distanceUnit`; вычисляемые `isBrandMissing`, `isMileageMissing`, `isValid`.

### `taskform` — экран одной работы

`TaskFormScreen(carId, taskId?, onBack)`: без `taskId` — «Новая работа», с ним — «Редактирование работы»; под заголовком — марка машины.

- Поля — те же, что у блока работы (`TaskFields`), с теми же обязательными. У новой работы сверху чипы пресетов и под «Последний раз» тот же чекбокс «Создать запись в истории» (`onRecordToggle`, сегодняшняя дата — `GetTodayUseCase`); при правке его нет.
- Правка «Последний раз» по пробегу или дате переносится в связанную запись истории (см. `UpdateTaskUseCase` в `specs/domain.md`).
- **Иконка работы:** слева от поля «Название» — кнопка с текущей иконкой (акцентная рамка и заливка). Нажатие открывает `TaskIconDialog`; тап по иконке выбирает её и закрывает окно. У новой работы — гаечный ключ, в том числе после чипа пресета. У работы без сохранённой иконки кнопка показывает подобранную по названию (`taskIcon(icon, name)`), и работа остаётся без иконки, пока пользователь её не выберет. В форме авто иконка не выбирается.
- Новая работа: «Сохранить» сохраняет и закрывает экран; «Добавить ещё» сохраняет и показывает пустую форму (`formKey` меняется — экран прокручивается вверх). Повторные нажатия, пока идёт сохранение, игнорируются.
- Правка: в топ-баре корзина → `ConfirmDialog` «Удалить работу?»; записи истории остаются.
- Нетронутое поле расстояния сохраняет прежние километры (`keepKm`).
- Use case-ы: `ObserveCarByIdUseCase`, `ObserveSettingsUseCase`, `AddTaskUseCase`, `UpdateTaskUseCase`, `DeleteTaskUseCase`, `GetTodayUseCase`. `isPickingIcon` показывает окно иконок. `isClosed` закрывает экран: работа сохранена или удалена, машины или работы нет.

### `cardetail` — экран машины

`CarDetailScreen(carId, onBack, onOpenHistory, onAddTask, onOpenTask)`.

- **Топ-бар:** «назад», часы (вкладка «История» с этой машиной), корзина (удаление).
- **Шапка:** марка, под ней госномер (`LicensePlate` вида из `DetectPlateStyleUseCase` по региону устройства), затем VIN с пунктирным подчёркиванием. Без номера на его месте строка «Госномер добавить» в стиле VIN.
- **Правка на месте** (`InlineEditField`): марка, госномер (верхний регистр, до 12 символов), VIN и пробег по тапу превращаются в поле с галочкой. Одно поле за раз; сохраняет только галочка; тап мимо — по другому полю, карточке, кнопке или пустому месту — отменяет правку. У марки и пробега галочка неактивна, пока поле пустое.
- **Карточка пробега** закреплена над списком работ: подпись «Пробег», крупное число (тап — ручной ввод), справа кольцо статусов `StatusDonut` (дуги «просрочено», «пора менять», «в порядке», в центре «N/M» цветом худшего статуса); под числом «−», множитель «×1 ×2 ×5 ×10» и «+», подсказка «Шаг N · тап по числу — ввести вручную». Шаг — 100 выбранных единиц × множитель, сохраняется сразу; пробег не опускается ниже нуля. Шаги и ручной ввод применяются по очереди (`Mutex`), каждый — к пробегу, сохранённому на этот момент; пока открыт ручной ввод, кнопки шага неактивны. После любого изменения пробега список работ плавно прокручивается в начало.
- **Карточка работы:** крупный остаток цветом статуса с подписью «осталось» / «просрочено», `StatusChip`, плитка 28 × 28 с иконкой работы (`taskIcon(icon, name)` — выбранная или подобранная по названию) слева от названия, строка «каждые N» и «или M мес» / «время истекло», `ProgressBar` и галочка в кружке. Тап по карточке — экран работы.
- **Порядок работ** — `SortedTaskStatsUseCase`; пересчитывается после любого изменения.
- **Отметка:** пустой кружок открывает `TaskDoneDialog` (сервис, стоимость, комментарий — необязательные); «Сохранить» вызывает `MarkTaskDoneUseCase`. Заполненный зелёный кружок — работа отмечена (`DoneMarksUseCase`); нажатие открывает окно снятия отметки (`ConfirmDialog` с акцентной кнопкой), «Ok» вызывает `UnmarkTaskDoneUseCase`.
- **Удаление машины:** `ConfirmDialog` «Удалить {марка}?», текст — машина и работы удаляются, история остаётся в «Истории» с пометкой «продано», полностью удалить можно в настройках; «Удалить» вызывает `DeleteCarUseCase`, `isMissing` закрывает экран.
- Открытие экрана запоминает машину как последнюю открытую (`RecordCarOpenedUseCase`).

`CarDetailUiState`: `isLoading`, `car: CarDetails?` (`brand`, `vin`, `plate`, `plateLook`, `mileageKm`, `dueCount`, `soonCount`, `taskCount`, `tasks: List<TaskItem>`), `distanceUnit`, `editingField: EditableField?` (`BRAND`, `PLATE`, `VIN`, `MILEAGE`), `isConfirmingDelete`, `taskDoneDialog: TaskItem?`, `isConfirmingUnmark`, `mileageMultiplier`; вычисляемое `isMissing`. `TaskItem` — `id`, `name`, `intervalKm`, `remainingKm`, `remainingMonths`, `status`, `progress`, `isDone`, `icon`.

Источник состояния — `combine(observeCarById(carId), observeHistory(carId), observeSettings(), overlay)`; `isDone` считается `DoneMarksUseCase` по истории машины.

### `history` — история ТО

`HistoryScreen(initialCarId?)` — вкладка верхнего уровня, без «назад». Записи всех машин, в том числе проданных (`ObserveAllHistoryUseCase`).

- **Выбор машины** вверху на всю ширину: «Все авто», затем действующие машины, затем проданные — серым, с пометкой «[продано]». По умолчанию — «Все авто» или `initialCarId`, если экран открыт с машины.
- **Фильтр** — кнопка справа от выбора машины, подсвечена акцентом и точкой, пока фильтр активен. Окно: период «с — по», поиск по названию работы, пробег «с — по» в выбранных единицах; «Сбросить», «Применить»; тап по затемнению закрывает без изменений. Дата, которая не разбирается, не даёт применить фильтр и подсвечивается. Условия объединяются по «и»; поиск — подстрока без учёта регистра.
- **Группировка:** от новых к старым, по годам и месяцам. Год — разделитель с числом по центру и линиями, закреплённый у верха списка (`stickyHeader`). Месяц — рамка фона `surface` с подписью месяца (без года) в разрыве верхней линии; записи месяца внутри рамки разделены линией. Месяцы без записей не показываются.
- **Карточка записи:** название работы и дата; строка «авто · пробег · стоимость · сервис» (машина — только в режиме «Все авто», проданная — серым, с пометкой); под ней комментарий, если есть.
- **Окно записи** по тапу: заголовок — название работы, под ним машина. Поля: «Дата» и «Пробег» (обязательные; несуществующая дата и пробег `≤ 0` — красная рамка), «Где обслуживались», «Сколько стоило, ₽», «Комментарий». «Сохранить» вызывает `UpdateHistoryEntryUseCase`; нетронутый пробег сохраняет прежние километры.
- **Новая запись** — `Fab` с «+» в правом нижнем углу (нет действующих машин — кнопки нет; список снизу с отступом под неё). Окно `NewHistoryEntryDialog` «Новая запись» с пояснением «Работа, сделанная раньше, — например, до установки приложения»: обязательные «Авто» (только действующие; по умолчанию выбранная в фильтре или первая), «Работа» (текст; под полем до 3 чипов с названиями работ этой машины), «Дата» и «Пробег»; кнопка «+ Ещё: сервис, стоимость, комментарий» раскрывает необязательные поля. Поля прокручиваются внутри окна. «Добавить» вызывает `AddHistoryEntryUseCase`; пустое название, несуществующая дата и пробег `≤ 0` — красная рамка. Повторные нажатия, пока запись сохраняется, игнорируются.
- Пустые состояния: записей нет вообще — `history_empty`; под фильтр ничего не попало — «Ничего не найдено — измените фильтр».

`HistoryUiState`: `isLoading`, `cars: List<HistoryCar>` (`id`, `brand`, `isSold`), `selectedCarId`, `isCarMenuOpen`, `filter`, `filterDraft` (открыто окно фильтра), `years: List<HistoryYear>` → `months: List<HistoryMonth>` → `rows: List<HistoryRow>` (`entry`, `car`), `hasEntries`, `entryForm: HistoryEntryForm?`, `newEntry: NewHistoryEntryForm?` (`carId`, `taskName`, `date`, `mileage`, `serviceName`, `cost`, `comment`, `isExpanded`, `isCarMenuOpen`, `showErrors`), `newEntrySuggestions`, `distanceUnit`; вычисляемое `activeCars` — машины без проданных. Источники — `ObserveCarSummariesUseCase`, `ObserveAllHistoryUseCase`, `ObserveSettingsUseCase` и `ObserveCarsUseCase` (названия работ для чипов).

Чистая логика списка — в `HistoryRows.kt` (`internal object HistoryRows`): `ofCar` (записи выбранной машины), `matching(rows, criteria)` (машина и фильтр; границы включительно, пробег сравнивается в выбранных единицах), `grouped` (годы и месяцы в порядке записей — от новых к старым) и `suggestions(car)` (первые три разных названия работ машины). `HistoryViewModel` разбирает поля фильтра в `HistoryCriteria` (даты — через `DateInputFormat`) и держит состояние экрана целиком; сам на части не делится. Логика покрыта `HistoryRowsTest`.

### `settings` — настройки

Каждое изменение сохраняется сразу; кнопки «Сохранить» нет. Изменения идут через `UpdateSettingsUseCase` с функцией изменения — каждое применяется к сохранённым на этот момент настройкам.

- **Тема** — `SegmentedControl` `LIGHT` / `DARK` / `SYSTEM`. **Единицы** — `KILOMETERS` / `MILES`.
- **Запуск** (между единицами и уведомлениями; без действующих машин группы нет): «Открывать по умолчанию» с `AppSwitch` и подписью — выключен: «Сейчас при запуске открывается последнее открытое авто», включён: «При запуске открывается выбранное авто». Включённый показывает список действующих машин (марка, пробег, радиокнопка). Переключение и выбор — `SetDefaultCarUseCase`.
- **Уведомления:** push (`AppSwitch`), `NotificationPreviewCard` (статичный пример), «Напоминать заранее» — `Stepper` в тысячах выбранных единиц, 1–9; «Время напоминаний» — `TimePickerDialog`.
- **Удаление авто** (последний раздел; без машин раздела нет): пояснение — машина удаляется полностью, с работами и всей историей. Список всех машин: действующие (марка, пробег), затем проданные (марка серым, подпись «продано»), у каждой корзина → `ConfirmDialog` «Удалить {марка} полностью?» → `PurgeCarUseCase`.

- **О приложении** (самый последний раздел, есть всегда): строка «Лицензии открытого ПО» (`ScrollText`, справа `ChevronRight`) открывает `Licenses`.

`SettingsUiState`: `settings`, `isPickingReminderTime`, `cars` (действующие), `openDefault`, `defaultCarId` (если выбранной машины нет — первая), `purgeCars` (все, проданные в конце), `purgeCar`; вычисляемое `reminderSteps`.

### `licenses` — лицензии открытого ПО

Статичный экран без ViewModel: `LicensesScreen(onBack)`. Нужен, чтобы выполнять условия лицензий библиотек, которые входят в приложение (Apache 2.0, ISC, MIT); список совпадает с `THIRD_PARTY_NOTICES.md` в корне репозитория.

- `ScreenTopBar` с «назад», заголовок «Лицензии открытого ПО» и подпись.
- Список `openSourceLibraries` (`OpenSourceLibraries.kt`): у каждой библиотеки название, лицензия и адрес проекта. Тап раскрывает полный текст лицензии под карточкой, повторный тап сворачивает; раскрытость хранится в `rememberSaveable`.
- Тексты лицензий лежат в `composeResources/files/licenses/` (`apache-2.0.txt`, `lucide-isc.txt`, `compose-icons-mit.txt`) и читаются `Res.readBytes` при раскрытии. Названия библиотек и лицензий не переводятся.
- При добавлении библиотеки в приложение её нужно внести в `openSourceLibraries`, в `THIRD_PARTY_NOTICES.md` и, если лицензия новая, положить её текст в ресурсы.

## Общий UI

### Тема

`ui/theme/`. `ItsTime4ServiceTheme(darkTheme, content)` кладёт в `CompositionLocal` три набора токенов и оборачивает содержимое в `MaterialTheme`, чья `colorScheme` собрана из тех же цветов (`primary` = `accent`, `background` = `bg`, `surface`, `surfaceVariant` = `surface2`, `outline` = `rule`, `onBackground`/`onSurface` = `ink`). Доступ — `ItsTime4ServiceTheme.colors`, `ItsTime4ServiceTheme.typography`, `ItsTime4ServiceTheme.dimens`.

Цвета (`ItsTime4ServiceColorScheme`, `Color.kt`):

| Токен | Светлая | Тёмная | Назначение |
|---|---|---|---|
| `bg` | `#EEF1F5` | `#0A0E15` | фон экрана |
| `surface` | `#FFFFFF` | `#121823` | карточки, диалоги, таб-бар |
| `surface2` | `#E3E8EE` | `#1A222F` | поля, плитки иконок, второстепенные кнопки |
| `surface3` | `#CCD5DE` | `#242E3D` | подложка кольца, выключенный переключатель |
| `ink` | `#10151D` | `#E9EDF3` | основной текст |
| `inkMuted` | `#5C6774` | `#8D99A8` | вторичный текст, иконки |
| `inkFaint` | `#97A2AE` | `#657185` | подписи, плейсхолдеры |
| `rule` | `#DBE1E8` | `#263140` | рамки и разделители |
| `accent` | `#0EA5B7` | то же | акцент |
| `onAccent` | `#FFFFFF` | то же | текст и иконки на акценте |
| `tierOk` | `#1FAB8C` | то же | статус «в норме» |
| `tierSoon` | `#E2922E` | то же | статус «пора менять» |
| `tierDue` | `#E34B3F` | то же | статус «просрочено», опасные действия |
| `onTier` | `#FFFFFF` | то же | текст на цвете статуса |

`inkFaint` и `rule` тёмной темы подобраны по контрасту: `inkFaint` — не ниже 3 : 1 к `surface` и `surface2` и заметно бледнее `inkMuted`, `rule` — видимая рамка на `surface`, как в светлой теме.

**Системные части экрана** следуют теме приложения, а не системы (`SystemAppearance(themeMode, darkTheme)`, `ui/theme/SystemAppearance.kt`, `expect`; вызывается из `App`): на Android — светлые или тёмные значки статус-бара и панели навигации (`WindowCompat.getInsetsController`), на iOS — `overrideUserInterfaceStyle` у окон приложения, от него зависят статус-бар, клавиатура и системные элементы; при `SYSTEM` стиль не навязывается. Клавиатура Android следует теме системы.

Типографика (`ItsTime4ServiceTypography`, `Type.kt`) — три роли; экраны берут роль и подстраивают размер через `copy(...)`:

| Роль | Шрифт | Базовый стиль | Где |
|---|---|---|---|
| `display` | `FontFamily.Default` | ExtraBold, 28 sp, строка 32 sp, трекинг −0.02 em | заголовки экранов и диалогов, крупные числа |
| `sans` | `FontFamily.Default` | Normal, 15 sp, строка 21 sp | обычный текст, кнопки, подписи |
| `mono` | `FontFamily.Monospace` | SemiBold, 13 sp, строка 17 sp | числа и табличные данные: пробег, VIN, даты |

Своих файлов шрифтов нет — используются системные.

Размеры (`ItsTime4ServiceDimens`, `Dimens.kt`):

| Токен | Значение | Токен | Значение |
|---|---|---|---|
| `radiusS` | 8 dp | `spaceXs` | 4 dp |
| `radiusM` | 10 dp | `spaceS` | 8 dp |
| `radiusL` | 12 dp | `spaceM` | 12 dp |
| `radiusXl` | 14 dp | `spaceL` | 16 dp |
| `radiusXxl` | 24 dp | `spaceXl` | 24 dp |

Горизонтальный отступ экрана — отдельная константа `ScreenHorizontalPadding = 18.dp` в `ui/components/screen/ScreenHeader.kt`.

### Компоненты

`ui/components/`. Компоненты не знают о domain-моделях (кроме `LocalTime` в `TimePickerDialog` и `TaskIcon` в `TaskIconDialog`) и о ViewModel-ах. Каждый публичный компонент имеет пару `@Preview` в светлой и тёмной теме (`ComponentPreview`).

| Группа | Компонент | Назначение |
|---|---|---|
| `buttons` | `AppIconButton` | круглая второстепенная кнопка с иконкой |
| `buttons` | `Fab` | круглая акцентная плавающая кнопка |
| `buttons` | `PresetChip` | пунктирная «пилюля» с «+ название» — быстрая работа |
| `buttons` | `PrimaryButton` | основная акцентная кнопка |
| `buttons` | `OutlineButton` | вторичное действие рядом с `PrimaryButton`: текст и рамка `accent`, фон `bg` |
| `buttons` | `SegmentedControl` | взаимоисключающие варианты: тема, единицы, множитель шага |
| `buttons` | `TabBarItem` | пункт таб-бара: иконка и подпись; выбранный обведён скруглённой рамкой цвета иконки (1,5 dp, `radiusM`, отступы 16 × 5 dp), у остальных рамка прозрачная — переключение ничего не сдвигает; вся ячейка — `selectable` с ролью `Tab` |
| `fields` | `FieldBox` | `internal`; общая «коробка» полей: заливка `surface2`, рамка `rule`, с `isError` — `tierDue` |
| `fields` | `FieldLabel` | подпись поля капсом; у обязательного — красная звёздочка |
| `fields` | `AppTextField`, `AppNumberField`, `LabeledMiniField` | текстовое, числовое и компактное поле с подписью; все с `isError` и `onDone` (клавиша «готово») |
| `fields` | `ServiceRecordFields` | необязательные поля записи истории — сервис, стоимость, комментарий; в `TaskDoneDialog`, окнах записи истории и при «Создать запись в истории» в формах работы |
| `fields` | `InlineEditField` | поле правки на месте с галочкой; галочка неактивна, пока `canSave` ложно |
| `steppers` | `Stepper` | «−  значение  +» с шагом и границами |
| `indicators` | `StatusTier` | `Ok`, `Soon`, `Due` с цветом и подписью |
| `indicators` | `StatusChip` | «пилюля» с названием статуса |
| `indicators` | `GaugeRing` | кольцо на карточке в списке машин: доля и число в центре |
| `indicators` | `StatusDonut` | кольцо статусов на карточке пробега: дуги по статусам, «N/M» в центре |
| `indicators` | `ProgressBar` | тонкая полоса прогресса цвета статуса |
| `indicators` | `AppSwitch` | переключатель |
| `indicators` | `AppCheckbox` | чекбокс с подписью; вся строка переключает, включённый — акцентный квадрат с галочкой |
| `indicators` | `LicensePlate` | номерной знак по `PlateLook`: российский (`RussianPlate`, рисуется на `Canvas` в пропорциях настоящего 520 × 112 мм по образцам `docs/img.png` и `docs/X999AM199.png`: толстая рамка без «болтов», буквы серии меньше цифр — 0,51 и 0,626 высоты — на общей базовой линии, справа за разделителем регион, «RUS» и флаг; все размеры — доли высоты 30 dp, `compact` — 22 dp, системный масштаб шрифта не влияет; секция региона — 28 % внутренней ширины, в неё помещаются три цифры региона (например, 323) в полную высоту; регион, «RUS» и флаг отстоят от разделителя и рамки на 0,07 высоты; строка, которая всё же не помещается, сужается по ширине без потери высоты), европейский (синяя полоса с кодом страны), американский, универсальный; `compact` — уменьшенный для карточки списка. Цвета пластины — в теме; в тёмной теме пластина тёмная со светлыми символами |
| `cards` | `ListRowCard`, `StatCard`, `NotificationPreviewCard` | карточка-строка, блок показателя, имитация уведомления |
| `dialogs` | `DialogCard` | оболочка модального окна: иконка, заголовок, текст, содержимое |
| `dialogs` | `ConfirmDialog` | подтверждение действия; кнопка `tierDue` или акцентная |
| `dialogs` | `TaskDoneDialog` | окно «Работа выполнена»: сервис, стоимость, комментарий (`ServiceRecordFields`) |
| `dialogs` | `TaskIconDialog` | окно выбора иконки работы: 12 иконок сеткой 6 × 2 без подписей на экране (подписи — для диктора), выбранная обведена акцентом |
| `dialogs` | `TimePickerDialog` | время суток двумя `Stepper`-ами: часы шаг 1, минуты шаг 5 |
| `screen` | `ScreenTopBar`, `ScreenTitle` | `internal`; верхняя строка экрана и заголовок с подзаголовком |
| — | `ComponentPreview`, `ScreenPreview` | `internal`; обёртки для `@Preview` |

### Форматирование и ввод

`ui/format/`:

| Что | Где | Что делает |
|---|---|---|
| `Int.grouped()` | `Formatting.kt` | тройки разрядов через неразрывный пробел |
| `Int.kmIn(unit)` / `Int.kmFrom(unit)` | `Formatting.kt` | км → единицы показа (`round(км / 1.609344)` для миль) и обратно (`round(мили × 1.609344)`) |
| `keepKm(originalKm, typed, unit)` | `Formatting.kt` | если введённое число равно показанному, возвращает прежние километры — нетронутое поле не пересчитывается |
| `distanceText(km, unit)`, `unitLabel`, `unitGenitive` | `Formatting.kt` | расстояние с единицей и подписи единиц из ресурсов |
| `LocalDate.toDisplayText()`, `LocalTime.toDisplayText()` | `Formatting.kt` | `дд.мм.гггг`, `ЧЧ:ММ` |
| `DateInputFormat` | `DateInput.kt` | ввод даты только цифрами (до 8), разделители рисует `VisualTransformation`; формат по языку: русский `дд.мм.гггг`, испанский `dd/mm/aaaa`, английский `mm/dd/yyyy`; `parse` — несуществующая дата → `null` |
| `TaskFields` | `TaskFields.kt` | поля работы текстом для формы авто и экрана работы, плюс `icon` (у новой — `WRENCH`); обязательные — название и интервал, пустой «Последний раз» → 0; с `createsRecord` ещё пробег и дата, а `serviceName`, `cost`, `comment` уходят в `record`; `withRecord(on, today)` включает запись и заполняет пустую дату; `toNewTask(unit, original)` с `keepKm` |
| `TaskPreset` | `TaskPreset.kt` | `OIL` 7 500 / 1 500, `PADS` 30 000 / 3 000, `AIR_FILTER` 15 000 / 2 000, `TIMING_BELT` 60 000 / 5 000 км (интервал / окно); название и подпись чипа — из ресурсов |
| `PlateStyle.toLook(plate)` | `PlateLookMapper.kt` | вид номера из domain → `PlateLook` для `LicensePlate` |
| `deviceRegion()` | `DeviceRegion.kt` | `expect`: ISO-код страны устройства (Android — `Locale.getDefault().country`, iOS — `NSLocale.currentLocale.countryCode`) |
| `TaskStatus.toTier()` | `StatusTierMapper.kt` | `OK` → `Ok`, `SOON` → `Soon` («Пора менять»), `DUE` → `Due` |

Клавиша «готово» на клавиатуре (галочка) в любом поле ввода делает то же, что главная кнопка формы: «Сохранить» на экране работы и в форме авто, подтверждение в `TaskDoneDialog`, «Сохранить» и «Добавить» в окнах записи, «Применить» в фильтре истории, сохранение в `InlineEditField`. Для этого у `AppTextField`, `AppNumberField` и `LabeledMiniField` есть `onDone`: с ним поле получает `ImeAction.Done`. Пустые обязательные поля подсвечиваются, как при нажатии кнопки. В многострочном комментарии галочка тоже применяет форму, а не переводит строку. У цифровой клавиатуры iOS такой клавиши нет: там цифровое поле (`KeyboardType.Number`, в том числе ввод даты) в фокусе показывает справа маленькую акцентную галочку (`FieldBox`, `NumericKeyboard.hasDoneKey` — `expect`, на iOS `false`). Видимый значок — 18 dp, а область нажатия — на всю высоту поля и 44 dp в ширину; высота поля при появлении галочки не меняется. Она делает то же, что «готово»: вызывает действие формы, а у поля без действия просто убирает клавиатуру. У `InlineEditField` своя галочка рядом с полем, встроенная отключена (`showDoneButton = false`).

### Единицы расстояния

Все расстояния в моделях и базе — в километрах. При выбранных милях в милях показывается и вводится всё: списки, карточка машины, работы, история, окна, списки в настройках, пример уведомления и сами уведомления. Подписи полей следуют за единицами («Пробег, км» / «Пробег, мили»). Поле открывается со значением в выбранных единицах; нетронутое поле при сохранении не пересчитывается (`keepKm`), иначе сдвиг на километр снимал бы отметку о выполнении. Шаг «напоминать заранее» — тысячи выбранных единиц.

### Иконки

`ui/icons/ItsTime4ServiceIcons.kt` — объект-обёртка над набором Lucide; экраны и компоненты обращаются только к нему. 28 иконок: `Car`, `Gauge`, `Droplet`, `Disc`, `Filter`, `Wrench`, `Gear` (`Lucide.Settings`), `Clock`, `Check`, `ChevronLeft`, `ChevronRight`, `ChevronDown`, `Pencil`, `Trash` (`Lucide.Trash2`), `Bell`, `Plus`, `Minus`, `Sun`, `MoonToggle` (`Lucide.Moon`); виды списка авто — `List`, `PanelTop`, `LayoutPanelTop`; иконки работ — `Zap`, `Battery`, `Thermometer`, `Tire` (`Lucide.CircleDot`), `Wind`; лицензии — `ScrollText`.

Иконка работы (`ui/icons/TaskIcons.kt`, `taskIcon(icon, name)`) — выбранная пользователем, а у работы без неё (`icon == null`, сохранена до этапа 8б) подбирается по подстроке названия без учёта регистра: `масл`/`oil`/`aceite` → `DROPLET`; `колод`/`тормоз`/`brake`/`pad`/`freno`/`pastilla` → `DISC`; `фильтр`/`filter`/`filtro` → `FILTER`; остальное → `WRENCH`. `TaskIcon.vector` даёт картинку из `ItsTime4ServiceIcons`, `TaskIcon.label` — подпись для экранного диктора.

### Локализация

Строки — в ресурсах Compose Multiplatform: `shared/src/commonMain/composeResources/values/strings.xml` (английский, по умолчанию), `values-ru/`, `values-es/`. Набор ключей во всех трёх файлах одинаковый. Язык выбирает система; переключателя языка в приложении нет.

- Множественные формы — `plurals`: `cars_count`, `tasks_added`.
- Названия месяцев для истории — отдельные строки на трёх языках.
- Строки с подстановкой — `%1$s` / `%1$d`: расстояния (`mileage_km`, `distance_mi`), `count_of_total`, `vin_label`, `task_*`, `dialog_delete_car_title`, `cost_rub`, `task_block_title`, `notification_reminder_text`.
- Названия быстрых работ (`task_name_*`) берутся из ресурсов в момент добавления и сохраняются в базу уже текстом на языке устройства.
- Часть строк читает и data-слой — для текста уведомлений (см. `specs/data.md`).
- Название приложения `app_name` — «Пора на ТО» на русском, «It’s Time 4 Service» на английском и испанском; оно же заголовок уведомлений. Метка под иконкой задаётся отдельно: на Android — `androidApp/src/main/res/values*/strings.xml`, на iOS — `CFBundleDisplayName` в `Info.plist` и `ru.lproj/InfoPlist.strings`.

## DI

`di/FeatureModule.kt`, модуль `featureModule`:

| ViewModel | Регистрация | Параметры |
|---|---|---|
| `AppViewModel`, `CarsViewModel`, `CarFormViewModel`, `SettingsViewModel` | `viewModelOf(::…)` | — |
| `CarDetailViewModel` | `viewModelOf(::CarDetailViewModel)` | `carId: String` |
| `HistoryViewModel` | `viewModel { params -> HistoryViewModel(params.values.firstOrNull() as String?, …) }` | `initialCarId: String?` |
| `TaskFormViewModel` | `viewModel { params -> TaskFormViewModel(params.get(0), params.values.getOrNull(1) as String?, …) }` | `carId: String`, `taskId: String?` |

- Экран получает ViewModel значением параметра по умолчанию: `viewModel: XxxViewModel = koinViewModel { parametersOf(...) }`.
- Параметры, которые могут быть `null`, разбираются в фабрике явно.
- Use case-ы приходят из `domainModule` (все — `factoryOf`). Модуль подключается в `initKoin` вместе с `domainModule`, `dataModule`, `platformModule` (см. `specs/data.md`).

## Открытые вопросы

- **FAB перекрывает правый край последней видимой карточки работы** — плашку статуса и галочку, — пока список не прокрутили.
- **Стартовый экран — «Авто»**, хотя вкладка вторая в меню.
- **В окне удаления на карточке машины** про полное удаление в настройках сказано только текстом, перехода туда нет.
- **Нетронутое поле расстояния не пересчитывается** (`keepKm`) — оставить так или сравнивать пробеги с допуском ±1 км.
- **VIN не проверяется**: только верхний регистр и обрезка до 17 символов.
- **Повторный запрос разрешения.** Запрос уходит при каждом переходе `needsNotificationPermission` в `true`, в том числе при каждом запуске с машинами и включённым push. Если система отвечает отказом без диалога, только что включённый push сам выключится — без объяснения.
- **Android ниже 13 всегда отвечает «разрешено»** (`NotificationPermission.android.kt`), даже если уведомления выключены в системе.
- **Тема до прихода настроек.** Начальное значение `themeMode` — `SYSTEM`; первый кадр может быть в системной теме. Выбранная тема в оформление системных панелей не передаётся (`enableEdgeToEdge()` без параметров).
- **«Сегодня» берётся в момент пересчёта состояния.** Если приложение открыто через полночь и данные не меняются, статусы не обновятся.
- **Стоимость всегда в рублях** (`cost_rub`, «₽») на всех трёх языках.
- **Пример уведомления в настройках не совпадает с настоящим текстом**: `notification_sample_full` — «…пора заменить воздушный фильтр — осталось N», а настоящее — «{марка}: {работа} — {состояние}».
- **Подбор иконки работы по подстроке** (для работ без выбранной иконки): `pad` и `oil` встречаются внутри других слов; для названий на другом языке — гаечный ключ.
- **`defaultReminderWindowKm` не предзаполняет окно новых работ**: работе без окна `NormalizeTaskUseCase` ставит 15 % интервала.
- **Устаревший KDoc** у `ItsTime4ServiceTheme` (`ui/theme/Theme.kt`): «будет подключён к настройкам на шаге 6», хотя это давно сделано.

## Что ещё не реализовано

- Анимации индикаторов и остальная полировка этапа 9; сейчас анимированы ползунок `AppSwitch` и прокрутка списка работ.
- Переключатель языка в приложении.
- Юнит-тесты на ViewModel-и, кроме `CarsViewModel` (у `HistoryViewModel` покрыта вынесенная логика списка, `HistoryRowsTest`), и тесты Composable-компонентов. Из `ui/format` покрыт перевод км и миль с `keepKm` (`DistanceConversionTest`); `TaskFields` и ввод дат зависят от `Locale.current` и не покрыты.
