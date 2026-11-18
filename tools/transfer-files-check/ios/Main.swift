import UIKit
import CryptoKit
import Shared

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool { true }
}

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene:scene)
        window.rootViewController = FileCheckController()
        window.makeKeyAndVisible()
        self.window = window
    }
}

final class NativeControlDelegate: NSObject, UIDocumentPickerDelegate, UIAdaptivePresentationControllerDelegate {
    private let cancelled: () -> Void
    init(cancelled: @escaping () -> Void) { self.cancelled = cancelled }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { cancelled() }
    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) { cancelled() }
}

final class FileCheckController: UIViewController {
    private let files = IosTransferFiles()
    private let status = UILabel()
    private var selected = "Plain"
    private var attached = false
    private var nativeControlDelegate: NativeControlDelegate?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        for title in ["Plain", "Protected", "Save", "Pick", "Share"] {
            let button = UIButton(type: .system)
            button.setTitle(title, for: .normal)
            button.accessibilityIdentifier = title
            button.addAction(UIAction { [weak self] _ in self?.perform(title) }, for: .touchUpInside)
            stack.addArrangedSubview(button)
        }
        status.text = "Ready"
        status.accessibilityIdentifier = "status"
        status.numberOfLines = 0
        stack.addArrangedSubview(status)
        view.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.leadingAnchor,constant:20),stack.trailingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.trailingAnchor,constant:-20),stack.topAnchor.constraint(equalTo:view.safeAreaLayoutGuide.topAnchor,constant:20)])
        let docs = FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0]
        for name in ["Plain", "Protected"] {
            let destination = docs.appendingPathComponent("Source"+name+".it4s")
            if !FileManager.default.fileExists(atPath:destination.path), let source = Bundle.main.url(forResource:name,withExtension:"it4s"), let data = try? Data(contentsOf:source) {
                try? data.write(to:destination)
            }
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if !attached { files.attach(controller:self); attached = true }
    }

    private func perform(_ title: String) {
        if title == "Save" && ProcessInfo.processInfo.arguments.contains("-NativeControl") {
            let source = Bundle.main.url(forResource:"Plain",withExtension:"it4s")!
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent("NativeControl-"+UUID().uuidString,isDirectory:true)
            let url = directory.appendingPathComponent("Plain.it4s")
            do {
                try FileManager.default.createDirectory(at:directory,withIntermediateDirectories:true)
                try Data(contentsOf:source).write(to:url)
            } catch { show("Native fixture failed"); return }
            let picker = UIDocumentPickerViewController(forExporting:[url],asCopy:true)
            let listener = NativeControlDelegate { [weak self] in self?.show("Native cancelled") }
            nativeControlDelegate = listener
            picker.delegate = listener
            picker.modalPresentationStyle = .pageSheet
            picker.allowsMultipleSelection = false
            status.text = "Native presenting"
            present(picker,animated:true) { [weak self, weak picker] in
                guard let self = self, let picker = picker else { return }
                picker.presentationController?.delegate = listener
                self.status.text = "Native presented style="+String(picker.modalPresentationStyle.rawValue)+" locked="+String(picker.isModalInPresentation)
            }
            return
        }
        if title == "Plain" || title == "Protected" { selected = title; status.text = "Ready "+title; return }
        status.text = "Working"
        if title == "Pick" {
            files.pick { [weak self] result, error in
                guard let self = self else { return }
                if let handle = (result as? TransferFileResultSuccess<AnyObject>)?.value as? PickedFileHandle {
                    self.files.read(handle:handle) { read, error in
                        defer { self.files.release(handle:handle) { _ in } }
                        if let bytes = (read as? TransferFileResultSuccess<AnyObject>)?.value as? KotlinByteArray {
                            var data = Data()
                            for i in 0..<bytes.size { data.append(UInt8(bitPattern:bytes.get(index:i))) }
                            self.show("Read "+SHA256.hash(data:data).map { String(format:"%02x",$0) }.joined())
                        } else { self.complete(read,error) }
                    }
                } else { self.complete(result,error) }
            }
            return
        }
        guard let url = Bundle.main.url(forResource:selected,withExtension:"it4s"), let data = try? Data(contentsOf:url) else { show("FailedFixture"); return }
        let bytes = KotlinByteArray(size:Int32(data.count))
        for (index,value) in data.enumerated() { bytes.set(index:Int32(index),value:Int8(bitPattern:value)) }
        let arguments = ProcessInfo.processInfo.arguments
        let marker = arguments.firstIndex(of:"-FileCheckRun")
        let suffix = marker.flatMap { $0+1 < arguments.count ? "-"+arguments[$0+1] : nil } ?? ""
        if title == "Save" { files.save(bytes:bytes,fileName:"Saved"+selected+suffix+".it4s") { [weak self] result,error in self?.complete(result,error) } }
        if title == "Share" { files.share(bytes:bytes,fileName:"Shared"+selected+suffix+".it4s") { [weak self] result,error in self?.complete(result,error) } }
    }

    private func complete(_ result: TransferFileResult?, _ error: Error?) {
        if error != nil { show("CoroutineCancelled"); return }
        if result is TransferFileResultCancelled { show("Cancelled"); return }
        if let success = result as? TransferFileResultSuccess<AnyObject> {
            if success.value is SavedFile { show("Saved"); return }
            if success.value is SharedFile { show("Shared"); return }
        }
        if let failure = result as? TransferFileResultFailure { show("Failed "+String(describing:type(of:failure.reason))); return }
        show("Unexpected "+String(describing:type(of:result)))
    }

    private func show(_ value: String) { DispatchQueue.main.async { self.status.text = value } }
}
