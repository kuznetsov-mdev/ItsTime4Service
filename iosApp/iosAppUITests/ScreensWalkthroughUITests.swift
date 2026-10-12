import XCTest

/**
 * Walks through every screen of the app starting from an empty database and saves a screenshot of each step.
 * Relaunches the app along the way to check that cars, tasks, history and settings survive a restart.
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
        step("02_add_empty")
        XCTAssertTrue(app.staticTexts["0 добавлено"].exists)
        XCTAssertFalse(app.buttons["Сохранить авто"].isEnabled)
        tap("+ Масло")
        tap("+ Воздушный фильтр")
        step("03_add_presets")
        type("Kia Rio", into: 0)
        type("84300", into: 1)
        step("04_add_filled")
        XCTAssertTrue(app.buttons["Сохранить авто"].isEnabled)
        tap("Сохранить авто")

        tap("Добавить авто")
        tap("+ ГРМ")
        tap("Добавить произвольную работу")
        tap("Удалить работу")
        tap("Удалить работу")
        step("05_add_blocks_removed")
        XCTAssertTrue(app.staticTexts["0 добавлено"].exists)
        type("Lada Vesta", into: 0)
        type("12000", into: 1)
        tap("Сохранить авто")
        step("06_cars_after_add")
        XCTAssertEqual(carButtons.count, 2)

        tap("Lada Vesta")
        step("07_detail_default_task")
        XCTAssertTrue(app.staticTexts["Замена масла и фильтра"].exists)
        tap("Назад")

        tap("Kia Rio")
        step("08_detail")
        tap("Отметить выполненной")
        step("09_detail_marked_done")
        tap("Изменить пробег")
        step("10_detail_edit_mileage")
        type(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 5) + "85000", into: 0)
        tap("Сохранить")
        step("11_detail_mileage_saved")
        XCTAssertTrue(hasText(matching: "85\\s000"))
        tap("История ТО")
        step("12_history")
        XCTAssertTrue(hasText(matching: "84\\s300\\sкм.*"))
        tap("Назад")
        tap("Назад")

        tap("Настройки, Настройки")
        step("13_settings")
        tap("Мили")
        tap("Тёмная")
        app.switches.firstMatch.tap()
        tap("Увеличить")
        step("14_settings_changed")

        relaunch()
        step("15_cars_after_relaunch")
        XCTAssertEqual(carButtons.count, 2)
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label MATCHES %@", "Kia Rio.*миль")).firstMatch.exists)
        tap("Настройки, Настройки")
        step("16_settings_after_relaunch")
        XCTAssertTrue(app.staticTexts["3"].exists)
        tap("Километры")
        tap("Авто, Авто")
        tap("Kia Rio")
        step("17_detail_after_relaunch")
        XCTAssertTrue(hasText(matching: "85\\s000"))
        tap("История ТО")
        step("18_history_after_relaunch")
        XCTAssertTrue(hasText(matching: "84\\s300\\sкм.*"))
        tap("Назад")

        tap("Удалить авто")
        step("19_delete_dialog")
        tap("Отмена")
        XCTAssertFalse(app.buttons["Отмена"].exists)
        tap("Удалить авто")
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9)).tap()
        XCTAssertFalse(app.buttons["Отмена"].exists)
        XCTAssertTrue(app.staticTexts["Kia Rio"].exists)
        tap("Удалить авто")
        tap("Удалить")
        step("20_cars_after_delete")
        XCTAssertEqual(carButtons.count, 1)

        relaunch()
        step("21_cars_after_delete_relaunch")
        XCTAssertEqual(carButtons.count, 1)
        XCTAssertFalse(app.staticTexts["Kia Rio"].exists)
        tap("Добавить авто")
        tap("+ Колодки")
        step("22_add_dark")
    }

    private var carButtons: XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "label MATCHES %@", ".*, \\d+, .*"))
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

    private func relaunch() {
        app.terminate()
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
    }

    private func hasText(matching pattern: String) -> Bool {
        app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", pattern)).firstMatch.waitForExistence(timeout: 3)
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

    private func type(_ text: String, into fieldIndex: Int) {
        let field = app.textViews.element(boundBy: fieldIndex)
        guard field.waitForExistence(timeout: 3) else {
            XCTFail("No text field #\(fieldIndex)")
            return
        }
        field.tap()
        field.typeText(text)
    }

    private var outputDir: URL? {
        guard let path = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] else { return nil }
        let url = URL(fileURLWithPath: path, isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
}
