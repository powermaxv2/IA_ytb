import Foundation

enum Prefs {
    static let folder = "downloadFolder"
    static let container = "videoContainer"
    static let audioFormat = "audioFormat"
    static let embedThumbnail = "embedThumbnail"
    static let embedMetadata = "embedMetadata"
    static let embedChapters = "embedChapters"
    static let subtitles = "downloadSubtitles"
    static let subtitleLangs = "subtitleLangs"
    static let notify = "notifyOnFinish"
    static let maxConcurrent = "maxConcurrent"
    static let cookiesBrowser = "cookiesBrowser"

    static var defaultFolder: String {
        FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first?.path
            ?? NSHomeDirectory() + "/Downloads"
    }

    static func register() {
        UserDefaults.standard.register(defaults: [
            folder: defaultFolder,
            container: VideoContainer.mp4.rawValue,
            audioFormat: AudioFormat.m4a.rawValue,
            embedThumbnail: true,
            embedMetadata: true,
            embedChapters: true,
            subtitles: false,
            subtitleLangs: "fr,en",
            notify: true,
            maxConcurrent: 3,
            cookiesBrowser: "",
        ])
    }

    static var currentFolder: String {
        let value = UserDefaults.standard.string(forKey: folder) ?? ""
        return value.isEmpty ? defaultFolder : value
    }
}
