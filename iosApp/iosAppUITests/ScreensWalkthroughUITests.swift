import XCTest

/**
 * Walks through every screen of the app starting from an empty database and saves a screenshot of each step.
 * Covers required-field errors in the car form, in-place editing on the car screen, the task screen,
 * the done mark and its removal, the shared history with its filter and record window, and settings —
 * including miles, "open by default" and deleting a car.
 * Accepts the system notification prompt that appears after the first car is saved.
 * Relaunches the app along the way to check that cars, edits and settings survive a restart.
 * Screenshots are attached to the test report and, when TEST_RUNNER_SCREENSHOT_DIR is set,
 * also written to that directory.
 */
final class ScreensWalkthroughUITests: XCTestCase {

    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = true
        app.launchArguments = ["-AppleLanguages", "(ru)", "-AppleLocale", "ru_RU"]
        app.launch()
    }

    func testWalkthrough() {
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        resetAppState()
        step("01_cars_empty")
        XCTAssertEqual(carButtons.count, 0)

        tap("Добавить авто")
        tap("Сохранить авто")
        step("02_add_errors")
        XCTAssertTrue(app.staticTexts["Добавление авто"].exists)
        type("Kia Rio", into: textField(at: 0))
        type("84300", into: textField(at: 1))
        tap("Сохранить авто")
        allowNotificationsIfAsked()
        step("03_cars_after_add")
        XCTAssertEqual(carButtons.count, 1)

        tap("Kia Rio")
        step("04_detail")
        XCTAssertTrue(app.staticTexts["Замена масла и фильтра"].exists)
        XCTAssertFalse(app.buttons["Редактировать авто"].exists)

        app.staticTexts["Kia Rio"].tap()
        append(" X", to: textField(withValue: "Kia Rio"))
        tap("Сохранить")
        step("05_detail_brand_saved")
        XCTAssertTrue(app.staticTexts["Kia Rio X"].waitForExistence(timeout: 3))

        app.staticTexts["добавить"].tap()
        type("WRONG", into: textField(at: 0))
        app.staticTexts["ПРОБЕГ"].tap()
        step("06_detail_vin_cancelled")
        XCTAssertEqual(app.textViews.count, 0)
        XCTAssertFalse(hasText(matching: ".*WRONG.*"))

        staticText(matching: "84\\s300").tap()
        replace(with: "85000", in: textField(withValue: "84300"))
        tap("Сохранить")
        step("07_detail_mileage_saved")
        XCTAssertTrue(hasText(matching: "85\\s000"))

        tap("Замена масла и фильтра")
        step("08_task_edit")
        XCTAssertTrue(app.staticTexts["Редактирование работы"].exists)
        replace(with: "12000", in: textField(withValue: "10000"))
        tap("Сохранить")
        XCTAssertTrue(hasText(matching: "каждые 12\\s000\\sкм"))

        tap("Добавить работу")
        tap("+ Колодки")
        tap("Сохранить")
        step("09_new_task_errors")
        XCTAssertTrue(app.staticTexts["Новая работа"].exists)
        let empty = emptyTextFields()
        XCTAssertGreaterThanOrEqual(empty.count, 2)
        if empty.count >= 2 {
            type("60000", into: empty[1])
        }
        tap("Сохранить")
        step("10_detail_two_tasks")
        XCTAssertTrue(app.staticTexts["Тормозные колодки"].waitForExistence(timeout: 3))

        tap("Отметить выполненной")
        step("11_done_dialog")
        XCTAssertTrue(app.staticTexts["Работа выполнена"].exists)
        tap("Сохранить")
        step("12_detail_done")
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Выполнено")).firstMatch.waitForExistence(timeout: 3))
        tap("Выполнено")
        XCTAssertTrue(app.staticTexts["Снять отметку?"].exists)
        tap("Ok")
        step("13_detail_unmarked")

        tap("История ТО")
        step("14_history_of_car")
        XCTAssertTrue(app.staticTexts["История ТО"].exists)
        XCTAssertTrue(app.staticTexts["Kia Rio X"].exists)
        historyRows.firstMatch.tap()
        replace(with: "", in: textField(withValue: "85000"))
        tap("Сохранить")
        step("15_history_record_error")
        XCTAssertTrue(app.buttons["Отмена"].exists)
        if let mileage = emptyTextFields().first {
            type("85100", into: mileage)
        }
        tap("Сохранить")
        XCTAssertTrue(hasText(matching: "85\\s100\\sкм.*"))

        tap("Фильтр")
        let filterFields = emptyTextFields()
        XCTAssertEqual(filterFields.count, 5)
        if filterFields.count == 5 {
            type("масл", into: filterFields[2])
        }
        tap("Применить")
        step("16_history_filtered")
        XCTAssertTrue(historyRows.firstMatch.waitForExistence(timeout: 3))
        tap("Фильтр")
        replace(with: "zzz", in: textField(withValue: "масл"))
        tap("Применить")
        XCTAssertTrue(app.staticTexts["Ничего не найдено — измените фильтр"].exists)
        tap("Фильтр")
        tap("Сбросить")

        tap("Настройки, Настройки")
        step("17_settings")
        tap("Мили")
        tap("Тёмная")
        app.switches.element(boundBy: 0).tap()
        step("18_settings_open_default")
        XCTAssertTrue(app.staticTexts["При запуске открывается выбранное авто"].exists)
        tap("Увеличить")

        relaunch()
        step("19_cars_after_relaunch")
        XCTAssertEqual(carButtons.count, 1)
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label MATCHES %@", "Kia Rio X.*миль.*")).firstMatch.exists)
        tap("Kia Rio X")
        step("20_detail_in_miles")
        XCTAssertTrue(hasText(matching: ".*миль.*"))
        staticText(matching: "52\\s817").tap()
        XCTAssertTrue(textField(withValue: "52817").waitForExistence(timeout: 3))
        tap("Сохранить")
        XCTAssertTrue(hasText(matching: "52\\s817"))
        tap("Назад")

        tap("Настройки, Настройки")
        tap("Километры")
        tap("Удалить Kia Rio X с историей")
        step("21_settings_purge_dialog")
        XCTAssertTrue(app.staticTexts["Удалить Kia Rio X полностью?"].exists)
        tap("Отмена")
        tap("Авто, Авто")
        tap("Kia Rio X")
        tap("Удалить авто")
        step("22_delete_dialog")
        tap("Удалить")
        step("23_cars_after_delete")
        XCTAssertEqual(carButtons.count, 0)

        relaunch()
        step("24_cars_after_delete_relaunch")
        XCTAssertEqual(carButtons.count, 0)
    }

    private var carButtons: XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "label MATCHES %@", ".*, \\d+, .*"))
    }

    private var historyRows: XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "label MATCHES %@", ".*\\d{2}\\.\\d{2}\\.\\d{4}.*"))
    }

    private func resetAppState() {
        tap("Настройки, Настройки")
        tap("Системная")
        tap("Километры")
        tap("Авто, Авто")
        while carButtons.firstMatch.waitForExistence(timeout: 2) {
            carButtons.firstMatch.tap()
            tap("Удалить авто")
            tap("Удалить")
        }
    }

    private func allowNotificationsIfAsked() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let allow = springboard.buttons.matching(NSPredicate(format: "label IN %@", ["Allow", "Разрешить"])).firstMatch
        if allow.waitForExistence(timeout: 3) {
            allow.tap()
        }
    }

    private func relaunch() {
        app.terminate()
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
    }

    private func hasText(matching pattern: String) -> Bool {
        staticText(matching: pattern).waitForExistence(timeout: 3)
    }

    private func staticText(matching pattern: String) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", pattern)).firstMatch
    }

    private func step(_ name: String) {
        sleep(1)
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        guard let dir = outputDir else { return }
        try? screenshot.pngRepresentation.write(to: dir.appendingPathComponent("\(name).png"))
    }

    private func tap(_ label: String) {
        let exact = app.buttons[label].firstMatch
        let target = exact.waitForExistence(timeout: 3)
            ? exact
            : app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch
        guard target.waitForExistence(timeout: 3) else {
            XCTFail("No button labelled \(label)")
            return
        }
        target.tap()
    }

    private func textField(at index: Int) -> XCUIElement {
        app.textViews.element(boundBy: index)
    }

    private func textField(withValue value: String) -> XCUIElement {
        app.textViews.matching(NSPredicate(format: "value == %@", value)).firstMatch
    }

    private func emptyTextFields() -> [XCUIElement] {
        app.textViews.allElementsBoundByIndex
            .filter { ($0.value as? String ?? "").isEmpty }
            .sorted { lhs, rhs in
                lhs.frame.minY == rhs.frame.minY ? lhs.frame.minX < rhs.frame.minX : lhs.frame.minY < rhs.frame.minY
            }
    }

    private func type(_ text: String, into field: XCUIElement) {
        guard field.waitForExistence(timeout: 3) else {
            XCTFail("No text field to type \(text) into")
            return
        }
        field.tap()
        field.typeText(text)
    }

    private func append(_ text: String, to field: XCUIElement) {
        guard field.waitForExistence(timeout: 3) else {
            XCTFail("No text field to append \(text) to")
            return
        }
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        field.typeText(text)
    }

    private func replace(with text: String, in field: XCUIElement) {
        guard field.waitForExistence(timeout: 3) else {
            XCTFail("No text field to replace with \(text)")
            return
        }
        let length = (field.value as? String)?.count ?? 0
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: length) + text)
    }

    private var outputDir: URL? {
        guard let path = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] else { return nil }
        let url = URL(fileURLWithPath: path, isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
}
