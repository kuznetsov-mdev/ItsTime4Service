import XCTest

final class TransferFilesCheckUITests: XCTestCase {
    private let app = XCUIApplication(bundleIdentifier:"ru.kuznetsov.itstime4service.filescheck")
    private let runToken = "-" + String(format:"%06llu", 999999 - (UInt64(Date().timeIntervalSince1970) % 1000000)) + String(UUID().uuidString.prefix(8))

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US", "-FileCheckRun", runToken]
        app.launch()
        XCTAssertTrue(app.buttons["Save"].waitForExistence(timeout:30))

    }

    private func status(_ value: String) {
        let predicate = NSPredicate(format:"label == %@",value)
        let result = XCTWaiter.wait(for: [expectationsForStatus(predicate)], timeout: 30)
        if result != .completed { diagnoseDialog() }
        XCTAssertEqual(result, .completed, "Harness status: " + app.staticTexts["status"].label)
    }

    private func expectationsForStatus(_ predicate: NSPredicate) -> XCTestExpectation {
        XCTNSPredicateExpectation(predicate: predicate, object: app.staticTexts["status"])
    }

    private func waitForPicker(exporting: Bool = false) {
        let ready = XCTNSPredicateExpectation(predicate:NSPredicate { _,_ in
            let bar = self.app.navigationBars.firstMatch
            guard bar.exists && bar.isHittable else { return false }
            if !exporting { return true }
            let action = self.app.navigationBars.buttons.matching(NSPredicate(format:"label IN %@",["Save","Export","Сохранить","Экспорт"])).firstMatch
            return action.exists && action.isHittable
        },object:nil)
        let result = XCTWaiter.wait(for:[ready],timeout:30)
        if result != .completed { diagnoseDialog() }
        XCTAssertEqual(result,.completed,"Native picker is not ready")
    }

    private func cancelPicker(preferButton: Bool = true) {
        waitForPicker(exporting: !preferButton)
        let cancel = app.buttons.matching(NSPredicate(format:"label IN %@",["Cancel","Отменить","Отмена"])).firstMatch
        if preferButton && cancel.exists && cancel.isHittable { cancel.tap(); return }
        let bar = app.navigationBars.firstMatch
        let otherCancel = app.otherElements["Cancel"].firstMatch
        print("FILECHECK_DIAGNOSTIC cancelBar="+String(describing:bar.frame)+" otherCancel="+(otherCancel.exists ? String(describing:otherCancel.frame) : "none"))
        let start = bar.coordinate(withNormalizedOffset:CGVector(dx:0.95,dy:0.1))
        let end = app.coordinate(withNormalizedOffset:CGVector(dx:0.95,dy:0.9))
        start.press(forDuration:0,thenDragTo:end)
        let dismissed = XCTNSPredicateExpectation(predicate:NSPredicate { _,_ in !self.app.navigationBars.firstMatch.exists },object:nil)
        if XCTWaiter.wait(for:[dismissed],timeout:5) != .completed {
            let middle = bar.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.05))
            let bottom = app.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.9))
            middle.press(forDuration:0,thenDragTo:bottom)
        }
    }

    private func diagnoseDialog() {
        let labels = ["Cancel", "Отменить", "Отмена", "Save", "Export", "Browse", "Locations", "Done", "Close", "On My iPhone", "TransferFilesCheck", "Recents", "iCloud Drive", "Save to Files", "Сохранить в Файлы", "Сохранить в файлы", "Сохранить в Файлах", "More", "New Folder", "View Options", "List", "Icons", "Files", "Файлы", "AirDrop", "Messages", "Mail", "Notes", "Copy", "Copy Link", "Print", "Markup", "Options", "Edit Actions…"]
        let snapshot = app.debugDescription.replacingOccurrences(of:"\u{00a0}",with:" ")
        let visible = labels.filter { snapshot.contains("label: '"+$0+"'") }
        let matches = snapshot.components(separatedBy:"\n").compactMap { line -> String? in
            guard let label = labels.first(where:{line.contains("label: '"+$0+"'")}) else { return nil }
            let identifier = line.components(separatedBy:"identifier: '").dropFirst().first?.components(separatedBy:"'").first ?? "none"
            return line.trimmingCharacters(in:.whitespaces).components(separatedBy:",")[0]+":"+label+":"+identifier
        }
        print("FILECHECK_DIAGNOSTIC labels="+visible.joined(separator:",")+" types="+matches.joined(separator:",")+" status="+(app.staticTexts["status"].exists ? app.staticTexts["status"].label : "hidden"))
        let structure = snapshot.components(separatedBy:"\n").filter { line in
            ["Sheet,", "Popover,", "ScrollView,", "CollectionView,", "Table,"].contains(where:{line.trimmingCharacters(in:.whitespaces).hasPrefix($0)})
        }.map { $0.trimmingCharacters(in:.whitespaces).components(separatedBy:"identifier:")[0].components(separatedBy:"label:")[0] }
        let ownRows = snapshot.components(separatedBy:"\n").filter { $0.contains("Run-") || $0.contains("SourcePlain") || $0.contains("SourceProtected") }.map { String($0.trimmingCharacters(in:.whitespaces).prefix(220)) }
        print("FILECHECK_DIAGNOSTIC ownRows="+ownRows.prefix(20).joined(separator:";"))
        print("FILECHECK_DIAGNOSTIC structure="+structure.prefix(10).joined(separator:";"))
        let systemSnapshot = XCUIApplication(bundleIdentifier:"com.apple.springboard").debugDescription
        print("FILECHECK_DIAGNOSTIC systemLabels="+labels.filter { systemSnapshot.contains("label: '"+$0+"'") }.joined(separator:","))
    }

    private func tapIfPresent(_ labels: [String]) -> Bool {
        let navigationAction = app.navigationBars.buttons.matching(NSPredicate(format:"label IN %@",labels)).firstMatch
        if navigationAction.exists && navigationAction.isEnabled && navigationAction.isHittable { navigationAction.tap(); return true }
        let action = app.buttons["DOCPicker.actionButton"]
        if labels.contains("Save") && action.exists {
            print("FILECHECK_DIAGNOSTIC saveAction enabled="+String(action.isEnabled)+" hittable="+String(action.isHittable))
            if action.isEnabled && action.isHittable { action.tap(); return true }
        }
        for label in labels {
            let button = app.descendants(matching:.any).matching(NSPredicate(format:"label == %@",label)).firstMatch
            if button.exists && button.isHittable { button.tap(); return true }
        }
        if labels.contains("Save") { diagnoseDialog() }
        return false
    }

    private func isRunFolder() -> Bool {
        let name = "Run-"+runToken
        return app.navigationBars[name].exists || app.navigationBars.buttons.matching(NSPredicate(format:"label BEGINSWITH %@",name)).firstMatch.exists
    }

    private func localFolder() {
        if isRunFolder() { return }
        let rootName = "TransferFilesCheck"
        let rootBar = app.navigationBars.buttons.matching(NSPredicate(format:"label == %@",rootName)).firstMatch
        if !app.navigationBars[rootName].exists && rootBar.exists && rootBar.isHittable { rootBar.tap() }
        let run = app.cells.matching(NSPredicate(format:"label CONTAINS %@ OR identifier CONTAINS %@","Run-"+runToken,"Run-"+runToken)).firstMatch
        if !run.waitForExistence(timeout:8) {
            let local = app.descendants(matching:.any).matching(NSPredicate(format:"label MATCHES[c] %@", "^(?:On\\s+My\\s+iPhone|На\\s+(?:мо[её]м\\s+)?iPhone)$")).firstMatch
            if !local.exists { _ = tapIfPresent(["Browse", "Locations", "Обзор", "Места"]) }
            if local.waitForExistence(timeout:8) && local.isHittable { local.tap() }
            let folder = app.cells.matching(NSPredicate(format:"label CONTAINS %@ OR identifier CONTAINS %@",rootName,rootName)).firstMatch
            if folder.waitForExistence(timeout:8) && folder.isHittable { folder.tap() }
        }
        if !run.waitForExistence(timeout:10) { diagnoseDialog() }
        XCTAssertTrue(run.exists,"Own run directory is not exposed")
        run.tap()
        let ready = XCTNSPredicateExpectation(predicate:NSPredicate { _,_ in self.isRunFolder() },object:nil)
        XCTAssertEqual(XCTWaiter.wait(for:[ready],timeout:10),.completed,"Picker did not enter the own run directory")
    }

    func testNativeSaveControl() {
        app.terminate()
        app.launchArguments += ["-NativeControl"]
        app.launch()
        app.buttons["Save"].tap()
        cancelPicker(preferButton:false)
        status("Native cancelled")
    }

    func testCancelPick() {
        app.buttons["Pick"].tap()
        cancelPicker()
        status("Cancelled")
    }

    func testCancelSave() {
        app.buttons["Save"].tap()
        cancelPicker(preferButton:false)
        status("Cancelled")
    }

    func testCancelShare() {
        app.buttons["Share"].tap()
        let close = app.descendants(matching:.any).matching(NSPredicate(format:"label IN %@", ["Close", "Закрыть"])).firstMatch
        if close.waitForExistence(timeout:5) && close.isHittable { close.tap() }
        else {
            let popover = app.popovers.firstMatch
            if popover.exists {
                app.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.3)).tap()
            } else {
                app.swipeDown()
            }
        }
        status("Cancelled")
    }

    func testSaveAndReadPlain() { saveAndRead("Plain", "c1b00ee9eb5b929ae970aa8033788e96189e69a1463f42c92a3035ab64696ead") }
    func testSaveAndReadProtected() { saveAndRead("Protected", "4a91be64406bffac75c5fc887bd3126672f6adab17d774d58c4c5f75667a0e1d") }

    private func saveAndRead(_ kind: String, _ expected: String) {
        app.buttons[kind].tap()
        app.buttons["Save"].tap()
        waitForPicker(exporting:true)
        localFolder()
        waitForPicker(exporting:true)
        XCTAssertTrue(tapIfPresent(["Export", "Save", "Move", "Экспорт", "Сохранить", "Переместить"]))
        _ = tapIfPresent(["Replace", "Заменить"])
        status("Saved")
        pickAndRead("Saved"+kind+"-"+runToken, expected)
    }

    private func pickAndRead(_ name: String, _ expected: String) {
        app.buttons["Pick"].tap()
        waitForPicker()
        localFolder()
        let file = app.cells.matching(NSPredicate(format:"label CONTAINS %@ OR identifier CONTAINS %@", name, name)).firstMatch
        XCTAssertTrue(file.waitForExistence(timeout:30))
        print("FILECHECK_DIAGNOSTIC selectedFile enabled="+String(file.isEnabled)+" hittable="+String(file.isHittable))
        XCTAssertTrue(file.isEnabled)
        file.tap()
        let open = app.navigationBars.buttons.matching(NSPredicate(format:"label IN %@",["Open", "Открыть"])).firstMatch
        if open.exists && open.isEnabled { open.tap() }
        status("Read "+expected)
    }

    func testShareToFilesHasConfirmedCompletion() {
        app.buttons["Protected"].tap()
        app.buttons["Share"].tap()
        let predicate = NSPredicate(format:"label MATCHES[c] %@ OR identifier CONTAINS %@", "^(?:Save\\s+to\\s+Files|Сохранить\\s+в\\s+[«\"]?Файл(?:ы|ах)[»\"]?).*$", "com.apple.CloudDocsUI.AddToiCloudDrive")
        var save = app.descendants(matching:.any).matching(predicate).firstMatch
        for attempt in 0..<6 {
            if save.waitForExistence(timeout:5) && save.isHittable { break }
            let systemSave = XCUIApplication(bundleIdentifier:"com.apple.springboard").descendants(matching:.any).matching(predicate).firstMatch
            if systemSave.exists && systemSave.isHittable { save = systemSave; break }
            if attempt == 0 { diagnoseDialog() }
            let start = app.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.85))
            let end = app.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.2))
            start.press(forDuration:0.1,thenDragTo:end)
        }
        if !save.exists || !save.isHittable { diagnoseDialog() }
        XCTAssertTrue(save.exists && save.isHittable)
        save.tap()
        waitForPicker(exporting:true)
        localFolder()
        waitForPicker(exporting:true)
        XCTAssertTrue(tapIfPresent(["Save", "Export", "Сохранить", "Экспорт"]))
        _ = tapIfPresent(["Replace", "Заменить"])
        status("Shared")
        pickAndRead("SharedProtected-"+runToken, "4a91be64406bffac75c5fc887bd3126672f6adab17d774d58c4c5f75667a0e1d")
    }
}
