import XCTest

/**
 * End-to-end flows of the app on the simulator in Russian, each one independent: every test goes back to the car list
 * (the app reopens the last opened car), starts from an empty database and builds the data it needs through the UI. Covers adding a car with required-field errors, in-place
 * edits on the car screen, the task screen, the done mark and its removal, the car history with its record window
 * and filter, settings that survive a restart (miles, theme, "open by default"), deleting a car with a cancelled and
 * a confirmed dialog, the car list views with a record added by hand, and the open source licences.
 * Accepts the system notification prompt that appears after the first car is saved. Buttons below the screen are
 * reached by swiping up; text fields are tapped near their end but left of the iOS done check, so the cursor lands after the text, and
 * text is typed one key at a time and checked, as fast typing drops characters in Compose fields on iOS.
 * Screenshots of the steps are attached to the test report and, when SCREENSHOT_DIR is set, also written there.
 */
final class ScreensWalkthroughUITests: XCTestCase {

    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments = ["-AppleLanguages", "(ru)", "-AppleLocale", "ru_RU"]
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        resetAppState()
    }

    func testCarIsAddedAfterRequiredFieldsAndSurvivesRelaunch() {
        step("cars_empty")
        assertCarCount(0)

        tap("Добавить авто")
        tap("Сохранить авто")
        step("add_errors")
        XCTAssertTrue(app.staticTexts["Добавление авто"].exists)
        type("Kia Rio", into: textField(at: 0))
        type("84300", into: textField(at: 1))
        tap("Сохранить авто")
        allowNotificationsIfAsked()
        step("cars_after_add")
        assertCarCount(1)

        relaunch()
        assertCarCount(1)
        tap("Kia Rio")
        XCTAssertTrue(app.staticTexts["Замена масла и фильтра"].waitForExistence(timeout: 3))
    }

    func testCarScreenEditsInPlace() {
        addCar("Kia Rio", mileage: "84300")
        tap("Kia Rio")
        step("detail")
        XCTAssertFalse(app.buttons["Редактировать авто"].exists)

        tap("Kia Rio")
        append(" X", to: textField(withValue: "Kia Rio"))
        tap("Сохранить")
        XCTAssertTrue(hasText(matching: "Kia Rio X"))

        tap("VIN, добавить")
        type("WRONG", into: textField(at: 0))
        app.staticTexts["ПРОБЕГ"].tap()
        step("detail_vin_cancelled")
        XCTAssertEqual(app.textViews.count, 0)
        XCTAssertFalse(hasText(matching: ".*WRONG.*"))

        staticText(matching: "84\\s300").tap()
        replace(with: "85000", in: textField(withValue: "84300"))
        tap("Сохранить")
        step("detail_mileage_saved")
        XCTAssertTrue(hasText(matching: "85\\s000"))

        relaunch()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Kia Rio X")).firstMatch.exists)
    }

    func testTaskIsEditedAndANewOneAdded() {
        addCar("Kia Rio", mileage: "84300")
        tap("Kia Rio")

        tap("Замена масла и фильтра")
        step("task_edit")
        XCTAssertTrue(app.staticTexts["Редактирование работы"].exists)
        replace(with: "12000", in: textField(withValue: "7500"))
        tap("Сохранить")
        XCTAssertTrue(hasText(matching: "каждые 12\\s000\\sкм"))

        tap("Добавить работу")
        tap("Сохранить")
        step("new_task_errors")
        XCTAssertTrue(app.staticTexts["Новая работа"].exists)
        tap("+ Колодки")
        tap("Сохранить")
        step("detail_two_tasks")
        XCTAssertTrue(app.staticTexts["Тормозные колодки"].waitForExistence(timeout: 3))
    }

    func testDoneMarkIsSetAndRemoved() {
        addCar("Kia Rio", mileage: "84300")
        tap("Kia Rio")

        tap("Отметить выполненной")
        step("done_dialog")
        XCTAssertTrue(app.staticTexts["Работа выполнена"].exists)
        tap("Сохранить")
        step("detail_done")
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Выполнено")).firstMatch.waitForExistence(timeout: 3))

        tap("История ТО")
        XCTAssertTrue(historyRows.firstMatch.waitForExistence(timeout: 3))
        tapTab("Авто")
        tap("Kia Rio")

        tap("Выполнено")
        XCTAssertTrue(app.staticTexts["Снять отметку?"].exists)
        tap("Ok")
        step("detail_unmarked")
        XCTAssertTrue(app.buttons["Отметить выполненной"].waitForExistence(timeout: 3))
    }

    func testCarHistoryRecordAndFilter() {
        addCar("Kia Rio", mileage: "84300")
        tap("Kia Rio")
        tap("Отметить выполненной")
        tap("Сохранить")

        tap("История ТО")
        step("history_of_car")
        XCTAssertTrue(app.staticTexts["История ТО"].exists)
        XCTAssertTrue(app.staticTexts["Kia Rio"].exists)
        historyRows.firstMatch.tap()
        replace(with: "", in: textField(withValue: "84300"))
        tap("Сохранить")
        step("history_record_error")
        XCTAssertTrue(app.buttons["Отмена"].exists)
        if let mileage = emptyTextFields().first {
            type("84400", into: mileage)
        }
        tap("Сохранить")
        XCTAssertTrue(hasText(matching: "84\\s400\\sкм.*"))

        tap("Фильтр")
        let filterFields = emptyTextFields()
        XCTAssertEqual(filterFields.count, 5)
        if filterFields.count == 5 {
            type("масл", into: filterFields[2])
        }
        tap("Применить")
        step("history_filtered")
        XCTAssertTrue(historyRows.firstMatch.waitForExistence(timeout: 3))
        tap("Фильтр")
        replace(with: "zzz", in: textField(withValue: "масл"))
        tap("Применить")
        XCTAssertTrue(app.staticTexts["Ничего не найдено — измените фильтр"].exists)
        tap("Фильтр")
        tap("Сбросить")
        XCTAssertTrue(historyRows.firstMatch.waitForExistence(timeout: 3))
    }

    func testSettingsSurviveRelaunch() {
        addCar("Kia Rio", mileage: "84300")

        tapTab("Настройки")
        step("settings")
        tap("Мили")
        tap("Тёмная")
        app.switches.element(boundBy: 0).tap()
        step("settings_open_default")
        XCTAssertTrue(app.staticTexts["При запуске открывается выбранное авто"].exists)
        tap("Увеличить")

        relaunch()
        step("detail_after_relaunch")
        XCTAssertTrue(app.buttons["Назад"].waitForExistence(timeout: 3))
        XCTAssertTrue(hasText(matching: ".*миль.*"))
        tap("Назад")
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label MATCHES %@", "Kia Rio.*миль.*")).firstMatch.exists)
    }

    func testCarDeletionIsCancelledThenConfirmed() {
        addCar("Kia Rio", mileage: "84300")

        tapTab("Настройки")
        tap("Удалить Kia Rio с историей")
        step("settings_purge_dialog")
        XCTAssertTrue(app.staticTexts["Удалить Kia Rio полностью?"].exists)
        tap("Отмена")
        tapTab("Авто")
        assertCarCount(1)

        tap("Kia Rio")
        tap("Удалить авто")
        step("delete_dialog")
        tap("Отмена")
        XCTAssertTrue(app.buttons["Удалить авто"].exists)
        tap("Удалить авто")
        tap("Удалить")
        step("cars_after_delete")
        assertCarCount(0)

        relaunch()
        assertCarCount(0)
    }

    func testCarViewSurvivesRelaunchAndARecordIsAddedByHand() {
        addCar("Kia Rio", mileage: "84300")

        tap("Карточки с работами и историей")
        step("cars_view_history")
        XCTAssertTrue(hasText(matching: "(?i)последние работы"))

        relaunch()
        XCTAssertTrue(hasText(matching: "(?i)последние работы"))

        tap("Kia Rio")
        tap("История ТО")
        tap("Добавить запись")
        step("new_record_dialog")
        XCTAssertTrue(app.staticTexts["Новая запись"].exists)
        let fields = emptyTextFields()
        XCTAssertEqual(fields.count, 3)
        if fields.count == 3 {
            type("Колодки", into: fields[0])
            type("01102026", into: fields[1])
            type("84000", into: fields[2])
        }
        tap("Добавить")
        step("history_with_manual_record")
        XCTAssertTrue(hasText(matching: "Колодки.*01\\.10\\.2026.*"))
    }

    func testLicencesOpenFromSettings() {
        tapTab("Настройки")
        tap("Лицензии открытого ПО")
        step("licenses")
        XCTAssertTrue(app.staticTexts["Приложение собрано на этих компонентах с открытым кодом"].waitForExistence(timeout: 3))
        tap("Kotlin standard library")
        XCTAssertTrue(hasText(matching: ".*Version 2\\.0, January 2004.*"))
        tap("Назад")
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Лицензии открытого ПО")).firstMatch.waitForExistence(timeout: 3))
    }

    private var carButtons: XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "label MATCHES %@", ".*, \\d+, .*"))
    }

    private var historyRows: XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "label MATCHES %@", ".*\\d{2}\\.\\d{2}\\.\\d{4}.*"))
    }

    private func assertCarCount(_ expected: Int, file: StaticString = #filePath, line: UInt = #line) {
        let deadline = Date().addingTimeInterval(5)
        while carButtons.count != expected && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTAssertEqual(carButtons.count, expected, file: file, line: line)
    }

    private func resetAppState() {
        while app.buttons["Назад"].waitForExistence(timeout: 2) {
            app.buttons["Назад"].firstMatch.tap()
        }
        tapTab("Настройки")
        tap("Системная")
        tap("Километры")
        tapTab("Авто")
        tapIfExists("Список")
        while carButtons.firstMatch.waitForExistence(timeout: 2) {
            carButtons.firstMatch.tap()
            tap("Удалить авто")
            tap("Удалить")
        }
        tapTab("Настройки")
        let purge = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Удалить ")).firstMatch
        for _ in 0..<MAX_SWIPES where !purge.exists {
            app.swipeUp()
        }
        while purge.waitForExistence(timeout: 1) {
            purge.tap()
            tap("Удалить")
        }
        tapTab("Авто")
    }

    private func addCar(_ brand: String, mileage: String) {
        tap("Добавить авто")
        type(brand, into: textField(at: 0))
        type(mileage, into: textField(at: 1))
        tap("Сохранить авто")
        allowNotificationsIfAsked()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", brand)).firstMatch.waitForExistence(timeout: 3))
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
        app.descendants(matching: .any).matching(NSPredicate(format: "label MATCHES %@", pattern)).firstMatch
    }

    private func step(_ name: String) {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = "\(self.name) \(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
        guard let dir = outputDir else { return }
        try? screenshot.pngRepresentation.write(to: dir.appendingPathComponent("\(name).png"))
    }

    private func tap(_ label: String) {
        let exact = app.buttons[label].firstMatch
        let prefixed = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch
        let containing = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", label)).firstMatch
        var swipes = 0
        while !exact.waitForExistence(timeout: 1.5) && !prefixed.exists && !containing.exists && swipes < MAX_SWIPES {
            app.swipeUp()
            swipes += 1
        }
        let target = exact.exists ? exact : (prefixed.exists ? prefixed : containing)
        guard target.waitForExistence(timeout: 1.5) else {
            failWithTree("No button labelled \(label)")
            return
        }
        target.tap()
    }

    private func tapTab(_ label: String) {
        let tab = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@ AND elementType != %d", label, XCUIElement.ElementType.staticText.rawValue))
            .firstMatch
        guard tab.waitForExistence(timeout: 3) else {
            failWithTree("No tab labelled \(label)")
            return
        }
        tab.tap()
    }

    private func failWithTree(_ message: String) {
        print("ACCESSIBILITY TREE BEGIN\n\(app.debugDescription)\nACCESSIBILITY TREE END")
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = "accessibility tree"
        tree.lifetime = .keepAlways
        add(tree)
        XCTFail(message)
    }

    private func tapIfExists(_ label: String) {
        let button = app.buttons[label].firstMatch
        if button.waitForExistence(timeout: 2) {
            button.tap()
        }
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
        let target = boundByIndex(field)
        focus { target.tap() }
        enter(text)
        expectValue(of: target, containing: text)
    }

    private func append(_ text: String, to field: XCUIElement) {
        guard field.waitForExistence(timeout: 3) else {
            XCTFail("No text field to append \(text) to")
            return
        }
        let target = boundByIndex(field)
        focus { tapNearEnd(of: target) }
        enter(text)
        expectValue(of: target, containing: text)
    }

    private func replace(with text: String, in field: XCUIElement) {
        guard field.waitForExistence(timeout: 3) else {
            XCTFail("No text field to replace with \(text)")
            return
        }
        let target = boundByIndex(field)
        focus { tapNearEnd(of: target) }
        for _ in 0..<((target.value as? String)?.count ?? 0) {
            enter(XCUIKeyboardKey.delete.rawValue)
        }
        enter(text)
        expectValue(of: target, equalTo: text)
    }

    private func boundByIndex(_ field: XCUIElement) -> XCUIElement {
        let frame = field.frame
        return app.textViews.allElementsBoundByIndex.first { $0.frame == frame } ?? field
    }

    private func focus(_ tap: () -> Void) {
        tap()
        _ = app.keyboards.firstMatch.waitForExistence(timeout: 3)
        Thread.sleep(forTimeInterval: KEYBOARD_SETTLE)
    }

    private func enter(_ text: String) {
        for character in text {
            app.typeText(String(character))
            Thread.sleep(forTimeInterval: KEY_INTERVAL)
        }
    }

    private func tapNearEnd(of field: XCUIElement) {
        field.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0.5))
            .withOffset(CGVector(dx: field.frame.width - DONE_CHECK_WIDTH, dy: 0))
            .tap()
    }

    private func expectValue(of field: XCUIElement, containing text: String, file: StaticString = #filePath, line: UInt = #line) {
        let value = normalized(field.value as? String ?? "")
        XCTAssertTrue(value.contains(normalized(text)), "Field holds \(value), expected \(text)", file: file, line: line)
    }

    private func expectValue(of field: XCUIElement, equalTo text: String, file: StaticString = #filePath, line: UInt = #line) {
        let value = normalized(field.value as? String ?? "")
        XCTAssertEqual(value, normalized(text), "Field holds \(value), expected \(text)", file: file, line: line)
    }

    private func normalized(_ text: String) -> String {
        text.lowercased().filter { $0.isLetter || $0.isNumber || $0 == " " }
    }

    private var outputDir: URL? {
        guard let path = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] else { return nil }
        let url = URL(fileURLWithPath: path, isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
}

private let MAX_SWIPES = 4
private let DONE_CHECK_WIDTH: CGFloat = 50
private let KEYBOARD_SETTLE: TimeInterval = 0.5
private let KEY_INTERVAL: TimeInterval = 0.08
