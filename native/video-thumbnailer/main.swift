import AVFoundation
import Foundation
import ImageIO
import UniformTypeIdentifiers

let arguments = CommandLine.arguments
guard arguments.count == 5,
      let frameMillis = Int64(arguments[3]),
      let maxSize = Int(arguments[4]) else {
    FileHandle.standardError.write(Data("usage: video-thumbnailer <input> <output> <frame-ms> <max-size>\n".utf8))
    exit(2)
}

let inputURL = URL(fileURLWithPath: arguments[1])
let outputURL = URL(fileURLWithPath: arguments[2])
let asset = AVURLAsset(url: inputURL)
let generator = AVAssetImageGenerator(asset: asset)
generator.appliesPreferredTrackTransform = true
generator.maximumSize = CGSize(width: maxSize, height: maxSize)

func frame(at milliseconds: Int64) throws -> CGImage {
    let time = CMTime(value: milliseconds, timescale: 1000)
    return try generator.copyCGImage(at: time, actualTime: nil)
}

let image: CGImage
do {
    image = try frame(at: frameMillis)
} catch {
    image = try frame(at: 0)
}

guard let destination = CGImageDestinationCreateWithURL(
    outputURL as CFURL,
    UTType.png.identifier as CFString,
    1,
    nil
) else {
    FileHandle.standardError.write(Data("cannot create image destination\n".utf8))
    exit(3)
}
CGImageDestinationAddImage(destination, image, nil)
guard CGImageDestinationFinalize(destination) else {
    FileHandle.standardError.write(Data("cannot encode thumbnail\n".utf8))
    exit(4)
}
