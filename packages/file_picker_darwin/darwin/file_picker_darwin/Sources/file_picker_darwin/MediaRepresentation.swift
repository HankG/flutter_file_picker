import UniformTypeIdentifiers

/// Chooses which representation of a photo library item to load.
///
/// Kept free of UIKit, AppKit and Flutter so it can be unit tested on its own.
@available(iOS 14.0, macOS 11.0, *)
enum MediaRepresentation {
    /// Whether `type` is an image or a video.
    static func isMedia(_ type: UTType) -> Bool {
        type.conforms(to: .image) || type.conforms(to: .movie)
    }

    /// The first of an item's `registeredTypeIdentifiers` that is an image or
    /// a video, or `nil` if none is.
    ///
    /// A Live Photo registers its `.pvt` bundle type first, which is neither,
    /// so this picks its still image instead of the whole bundle with the
    /// paired video.
    static func mediaType(in registeredTypeIdentifiers: [String]) -> UTType? {
        registeredTypeIdentifiers.compactMap { UTType($0) }.first(where: isMedia)
    }

    /// The type identifier to load an item with: its first image or video
    /// representation, or any representation if it has neither.
    static func typeIdentifier(for registeredTypeIdentifiers: [String]) -> String {
        (mediaType(in: registeredTypeIdentifiers) ?? .item).identifier
    }
}
