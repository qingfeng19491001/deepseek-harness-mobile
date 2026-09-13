import AVFoundation
import PhotosUI
import UIKit

@objc(DshImagePicker)
final class DshImagePicker: NSObject, PHPickerViewControllerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    @objc static let shared = DshImagePicker()

    private var completion: (([String: Any]) -> Void)?
    private var maxCount = 20
    private var maxImageBytes: Int = 20 * 1024 * 1024
    private var maxImagePixels: Int = 64_000_000
    private var maxImageDimension = 8192
    private var longEdge = 2048
    private var picker: UIViewController?

    @objc func pickImages(from presenter: UIViewController?, params: [String: Any]?, completion: @escaping ([String: Any]) -> Void) {
        guard let presenter else {
            completion(Self.error("internal", "无法打开相册"))
            return
        }
        apply(params)
        self.completion = completion
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = maxCount
        let controller = PHPickerViewController(configuration: configuration)
        controller.delegate = self
        picker = controller
        presenter.present(controller, animated: true)
    }

    @objc func captureImage(from presenter: UIViewController?, params: [String: Any]?, completion: @escaping ([String: Any]) -> Void) {
        guard let presenter else {
            completion(Self.error("internal", "无法打开相机"))
            return
        }
        apply(params)
        self.completion = completion
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        let start = { [weak self] in
            guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
                self?.finish(Self.error("internal", "此设备没有可用相机"))
                return
            }
            DispatchQueue.main.async {
                let controller = UIImagePickerController()
                controller.sourceType = .camera
                controller.delegate = self
                self?.picker = controller
                presenter.present(controller, animated: true)
            }
        }
        switch status {
        case .authorized:
            start()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if granted {
                    start()
                } else {
                    self.finish(Self.error("permission-denied", "未获得相机权限，请在系统设置中允许后重试"))
                }
            }
        default:
            finish(Self.error("permission-denied", "未获得相机权限，请在系统设置中允许后重试"))
        }
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        self.picker = nil
        if results.isEmpty {
            finish(Self.cancelled())
            return
        }
        encode(results.prefix(maxCount).map { $0.itemProvider })
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        self.picker = nil
        finish(Self.cancelled())
    }

    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        self.picker = nil
        guard let image = info[.originalImage] as? UIImage else {
            finish(Self.cancelled())
            return
        }
        DispatchQueue.global(qos: .userInitiated).async {
            let prepared = self.prepare(image: image, name: "camera.jpg", sourceType: "image/jpeg")
            DispatchQueue.main.async {
                if let prepared {
                    self.finish(["ok": true, "imagesJson": "[\(prepared)]", "rejectedJson": "[]"])
                } else {
                    self.finish(Self.error("IMAGES_TOO_LARGE", "图片体积或总大小超过上限"))
                }
            }
        }
    }

    private func encode(_ providers: [NSItemProvider]) {
        let group = DispatchGroup()
        var images: [String] = []
        let lock = NSLock()
        for provider in providers {
            group.enter()
            let type = Self.typeIdentifier(for: provider)
            provider.loadDataRepresentation(forTypeIdentifier: type) { data, _ in
                defer { group.leave() }
                guard let data else { return }
                let name = provider.suggestedName ?? "image.jpg"
                if let json = self.prepare(data: data, name: name, typeIdentifier: type) {
                    lock.lock()
                    images.append(json)
                    lock.unlock()
                }
            }
        }
        group.notify(queue: .main) {
            let joined = images.joined(separator: ",")
            self.finish(["ok": true, "imagesJson": "[\(joined)]", "rejectedJson": "[]"])
        }
    }

    private func prepare(data: Data, name: String, typeIdentifier: String) -> String? {
        if typeIdentifier == "public.gif" || name.lowercased().hasSuffix(".gif"), data.count <= maxImageBytes {
            return json(name: name, mime: "image/gif", data: data, width: 0, height: 0, compressed: false)
        }
        guard let image = UIImage(data: data) else { return nil }
        return prepare(image: image, name: name, sourceType: Self.mime(for: typeIdentifier, name: name))
    }

    private func prepare(image: UIImage, name: String, sourceType: String) -> String? {
        let normalized = image.normalizedOrientation()
        let scaled = normalized.scaled(longEdge: min(longEdge, maxImageDimension), maxPixels: maxImagePixels)
        let png = sourceType == "image/png"
        if png, let data = scaled.pngData(), data.count <= maxImageBytes {
            return json(name: name, mime: "image/png", data: data, width: Int(scaled.size.width), height: Int(scaled.size.height), compressed: data.count < (image.pngData()?.count ?? Int.max))
        }
        for quality in [0.85, 0.7, 0.55, 0.4, 0.28] as [CGFloat] {
            guard let data = scaled.jpegData(compressionQuality: quality), data.count <= maxImageBytes else { continue }
            let outName = name.lowercased().hasSuffix(".jpg") || name.lowercased().hasSuffix(".jpeg") ? name : name.replacingOccurrences(of: "\\.[^.]+$", with: ".jpg", options: .regularExpression)
            return json(name: outName.isEmpty ? "image.jpg" : outName, mime: "image/jpeg", data: data, width: Int(scaled.size.width), height: Int(scaled.size.height), compressed: true)
        }
        return nil
    }

    private func json(name: String, mime: String, data: Data, width: Int, height: Int, compressed: Bool) -> String {
        let payload: [String: Any] = [
            "localId": UUID().uuidString,
            "name": (name as NSString).lastPathComponent,
            "mediaType": mime,
            "data": data.base64EncodedString(),
            "bytes": data.count,
            "width": width,
            "height": height,
            "compressed": compressed,
        ]
        guard let encoded = try? JSONSerialization.data(withJSONObject: payload),
              let text = String(data: encoded, encoding: .utf8) else { return "{}" }
        return text
    }

    private func apply(_ params: [String: Any]?) {
        maxCount = (params?["maxCount"] as? NSNumber)?.intValue.takeIfPositive() ?? 20
        maxImageBytes = (params?["maxImageBytes"] as? NSNumber)?.intValue.takeIfPositive() ?? (20 * 1024 * 1024)
        maxImagePixels = (params?["maxImagePixels"] as? NSNumber)?.intValue.takeIfPositive() ?? 64_000_000
        maxImageDimension = (params?["maxImageDimension"] as? NSNumber)?.intValue.takeIfPositive() ?? 8192
        longEdge = (params?["longEdge"] as? NSNumber)?.intValue.takeIfPositive() ?? 2048
    }

    private func finish(_ value: [String: Any]) {
        let callback = completion
        completion = nil
        DispatchQueue.main.async { callback?(value) }
    }

    private static func cancelled() -> [String: Any] { ["ok": true, "cancelled": true, "imagesJson": "[]"] }
    private static func error(_ code: String, _ message: String) -> [String: Any] {
        ["ok": false, "code": code, "message": message, "imagesJson": "[]"]
    }

    private static func typeIdentifier(for provider: NSItemProvider) -> String {
        if provider.hasItemConformingToTypeIdentifier("public.png") { return "public.png" }
        if provider.hasItemConformingToTypeIdentifier("public.jpeg") { return "public.jpeg" }
        if provider.hasItemConformingToTypeIdentifier("public.gif") { return "public.gif" }
        if provider.hasItemConformingToTypeIdentifier("org.webmproject.webp") { return "org.webmproject.webp" }
        return "public.image"
    }

    private static func mime(for typeIdentifier: String, name: String) -> String {
        switch typeIdentifier {
        case "public.png": return "image/png"
        case "public.jpeg": return "image/jpeg"
        case "public.gif": return "image/gif"
        case "org.webmproject.webp": return "image/webp"
        default:
            switch (name as NSString).pathExtension.lowercased() {
            case "png": return "image/png"
            case "gif": return "image/gif"
            case "webp": return "image/webp"
            default: return "image/jpeg"
            }
        }
    }
}

private extension Int {
    func takeIfPositive() -> Int? { self > 0 ? self : nil }
}

private extension UIImage {
    func normalizedOrientation() -> UIImage {
        if imageOrientation == .up { return self }
        UIGraphicsBeginImageContextWithOptions(size, false, scale)
        draw(in: CGRect(origin: .zero, size: size))
        let image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return image ?? self
    }

    func scaled(longEdge: Int, maxPixels: Int) -> UIImage {
        let width = max(size.width, 1)
        let height = max(size.height, 1)
        let longest = max(width, height)
        var scale: CGFloat = 1
        if Int(longest) > longEdge {
            scale = CGFloat(longEdge) / longest
        }
        let pixels = width * height
        if Int(pixels) > maxPixels {
            scale = min(scale, CGFloat(sqrt(Double(maxPixels) / Double(pixels))))
        }
        if scale >= 0.999 { return self }
        let next = CGSize(width: max(1, width * scale), height: max(1, height * scale))
        UIGraphicsBeginImageContextWithOptions(next, false, 1)
        draw(in: CGRect(origin: .zero, size: next))
        let image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return image ?? self
    }
}
