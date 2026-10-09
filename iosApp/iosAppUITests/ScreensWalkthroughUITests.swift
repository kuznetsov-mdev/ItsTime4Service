import XCTest

/**
 * Walks through every screen of the app on fake data and saves a screenshot of each step.
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
        step("01_cars")

        tap("Добавить авто")
        step("02_add_empty")
        XCTAssertTrue(app.staticTexts["0 добавлено"].exists)
        XCTAssertFalse(app.buttons["Сохранить авто"].isEnabled)
        tap("+ Масло")
        step("03_add_preset")
        tap("Добавить произвольную работу")
        step("04_add_custom")
        tap("Удалить работу")
        tap("Удалить работу")
        step("05_add_blocks_removed")
        XCTAssertTrue(app.staticTexts["0 добавлено"].exists)
        type("Lada Vesta", into: 0)
        type("12000", into: 1)
        step("06_add_filled")
        XCTAssertTrue(app.buttons["Сохранить авто"].isEnabled)
        tap("Сохранить авто")
        step("07_cars_after_add")

        tap("Lada Vesta")
        step("08_detail_new_car")
        XCTAssertTrue(app.staticTexts["Замена масла и фильтра"].exists)
        tap("Назад")

        tap("Kia Rio")
        step("09_detail")
        tap("Отметить выполненной")
        step("10_detail_marked_done")
        tap("Изменить пробег")
        step("11_detail_edit_mileage")
        type(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 5) + "85000", into: 0)
        tap("Сохранить")
        step("12_detail_mileage_saved")
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "85\\s000")).firstMatch.exists)
        tap("История ТО")
        step("13_history")
        tap("Назад")

        tap("Удалить авто")
        step("14_delete_dialog")
        tap("Отмена")
        step("15_delete_cancelled")
        XCTAssertFalse(app.buttons["Отмена"].exists)
        tap("Удалить авто")
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9)).tap()
        XCTAssertFalse(app.buttons["Отмена"].exists)
        XCTAssertTrue(app.staticTexts["Kia Rio"].exists)
        tap("Удалить авто")
        tap("Удалить")
        step("16_cars_after_delete")
        XCTAssertFalse(app.staticTexts["Kia Rio"].exists)

        tap("Настройки, Настройки")
        step("17_settings")
        tap("Мили")
        app.switches.firstMatch.tap()
        tap("Увеличить")
        step("18_settings_changed")
        tap("Авто, Авто")
        step("19_cars_miles")
        tap("Настройки, Настройки")
        tap("Километры")
        tap("Тёмная")
        step("20_settings_dark")

        tap("Авто, Авто")
        step("21_cars_dark")
        tap("Toyota Camry")
        step("22_detail_dark")
        tap("История ТО")
        step("23_history_dark")
        tap("Назад")
        tap("Удалить авто")
        step("24_delete_dialog_dark")
        tap("Отмена")
        tap("Назад")
        tap("Добавить авто")
        tap("+ ГРМ")
        step("25_add_dark")
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
