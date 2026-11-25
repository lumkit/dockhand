import AppKit
import Foundation

let arguments = CommandLine.arguments
guard arguments.count >= 3, arguments[1] == "list" else {
    FileHandle.standardError.write(Data("usage: open-with-helper list <file> <icon-dir>\n".utf8))
    exit(2)
}

let fileURL = URL(fileURLWithPath: arguments[2])
let iconDir = URL(fileURLWithPath: arguments.count > 3 ? arguments[3] : NSTemporaryDirectory(), isDirectory: true)
try? FileManager.default.createDirectory(at: iconDir, withIntermediateDirectories: true)

func displayName(for appURL: URL) -> String {
    if let bundle = Bundle(url: appURL) {
        if let name = bundle.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String, !name.isEmpty {
            return name
        }
        if let name = bundle.object(forInfoDictionaryKey: "CFBundleName") as? String, !name.isEmpty {
            return name
        }
    }
    return appURL.deletingPathExtension().lastPathComponent
}

func iconPath(for appURL: URL, index: Int) -> String? {
    let icon = NSWorkspace.shared.icon(forFile: appURL.path)
    icon.size = NSSize(width: 32, height: 32)
    guard let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: 32,
        pixelsHigh: 32,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    ) else { return nil }
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    icon.draw(in: NSRect(x: 0, y: 0, width: 32, height: 32))
    NSGraphicsContext.restoreGraphicsState()
    guard let data = rep.representation(using: .png, properties: [:]) else { return nil }
    let target = iconDir.appendingPathComponent("\(index).png")
    do {
        try data.write(to: target)
        return target.path
    } catch {
        return nil
    }
}

var result: [[String: String]] = []
let apps = NSWorkspace.shared.urlsForApplications(toOpen: fileURL)
for (index, appURL) in apps.enumerated() {
    var item: [String: String] = [
        "name": displayName(for: appURL),
        "path": appURL.path,
    ]
    if let icon = iconPath(for: appURL, index: index) {
        item["icon"] = icon
    }
    result.append(item)
}

let data = try JSONSerialization.data(withJSONObject: result)
FileHandle.standardOutput.write(data)
