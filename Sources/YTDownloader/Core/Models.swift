import Foundation

// MARK: - Lenient decoding (yt-dlp JSON is not always strictly typed)

extension KeyedDecodingContainer {
    func lenientString(_ key: Key) -> String? {
        if let s = try? decodeIfPresent(String.self, forKey: key) { return s }
        if let d = try? decodeIfPresent(Double.self, forKey: key) { return String(d) }
        return nil
    }

    func lenientDouble(_ key: Key) -> Double? {
        if let d = try? decodeIfPresent(Double.self, forKey: key) { return d }
        if let s = try? decodeIfPresent(String.self, forKey: key) { return Double(s) }
        return nil
    }
}

// MARK: - yt-dlp info

struct VideoFormat: Decodable, Sendable {
    let formatID: String
    let ext: String?
    let vcodec: String?
    let acodec: String?
    let width: Int?
    let height: Int?
    let fps: Double?
    let tbr: Double?
    let filesize: Double?
    let dynamicRange: String?

    enum CodingKeys: String, CodingKey {
        case formatID = "format_id"
        case ext, vcodec, acodec, width, height, fps, tbr, filesize
        case filesizeApprox = "filesize_approx"
        case dynamicRange = "dynamic_range"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        formatID = c.lenientString(.formatID) ?? UUID().uuidString
        ext = c.lenientString(.ext)
        vcodec = c.lenientString(.vcodec)
        acodec = c.lenientString(.acodec)
        width = c.lenientDouble(.width).map { Int($0) }
        height = c.lenientDouble(.height).map { Int($0) }
        fps = c.lenientDouble(.fps)
        tbr = c.lenientDouble(.tbr)
        filesize = c.lenientDouble(.filesize) ?? c.lenientDouble(.filesizeApprox)
        dynamicRange = c.lenientString(.dynamicRange)
    }

    var hasVideo: Bool {
        guard ext != "mhtml" else { return false }
        if let vcodec { return vcodec != "none" }
        return height != nil
    }

    var hasAudio: Bool {
        if let acodec { return acodec != "none" }
        return false
    }

    var isHDR: Bool {
        guard let dynamicRange else { return false }
        return dynamicRange.uppercased() != "SDR"
    }
}

struct VideoInfo: Decodable, Sendable {
    let id: String
    let title: String
    let uploader: String?
    let duration: Double?
    let thumbnail: String?
    let viewCount: Double?
    let webpageURL: String?
    let type: String?
    let formats: [VideoFormat]

    enum CodingKeys: String, CodingKey {
        case id, title, uploader, channel, duration, thumbnail, formats
        case viewCount = "view_count"
        case webpageURL = "webpage_url"
        case type = "_type"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.lenientString(.id) ?? ""
        title = c.lenientString(.title) ?? "Sans titre"
        uploader = c.lenientString(.channel) ?? c.lenientString(.uploader)
        duration = c.lenientDouble(.duration)
        thumbnail = c.lenientString(.thumbnail)
        viewCount = c.lenientDouble(.viewCount)
        webpageURL = c.lenientString(.webpageURL)
        type = c.lenientString(.type)
        formats = (try? c.decodeIfPresent([VideoFormat].self, forKey: .formats)) ?? []
    }

    var thumbnailURL: URL? { thumbnail.flatMap(URL.init(string:)) }

    var bestAudio: VideoFormat? {
        formats.filter { $0.hasAudio && !$0.hasVideo }.max { ($0.tbr ?? 0) < ($1.tbr ?? 0) }
    }

    func estimatedSize(of format: VideoFormat) -> Double? {
        if let size = format.filesize, size > 0 { return size }
        if let tbr = format.tbr, let duration { return tbr * 1000 / 8 * duration }
        return nil
    }

    var audioEstimatedSize: Double? { bestAudio.flatMap(estimatedSize(of:)) }

    /// One option per available video height, best first.
    var qualityOptions: [QualityOption] {
        let video = formats.filter { $0.hasVideo && $0.height != nil }
        let groups = Dictionary(grouping: video) { $0.height ?? 0 }
        let audioSize = audioEstimatedSize ?? 0
        return groups.map { height, formats in
            let best = formats.max { ($0.tbr ?? 0) < ($1.tbr ?? 0) }
            let width = formats.compactMap(\.width).max() ?? height
            let fps = Int((formats.compactMap(\.fps).max() ?? 0).rounded())
            var size: Double?
            if let best, let videoSize = estimatedSize(of: best) {
                size = videoSize + (best.hasAudio ? 0 : audioSize)
            }
            return QualityOption(
                height: height,
                displayP: min(width, height),
                maxFPS: fps,
                hdr: formats.contains { $0.isHDR },
                estimatedSize: size
            )
        }
        .sorted { $0.height > $1.height }
    }
}

struct QualityOption: Identifiable, Hashable, Sendable {
    /// Value used in the yt-dlp `height<=` filter.
    let height: Int
    /// Short side of the frame — what people call "1080p" (handles vertical videos).
    let displayP: Int
    let maxFPS: Int
    let hdr: Bool
    let estimatedSize: Double?

    var id: Int { height }
    var label: String { "\(displayP)p" }

    var badge: String {
        switch displayP {
        case 4320...: return "8K"
        case 2160...: return "4K"
        case 1440...: return "2K"
        case 1080...: return "Full HD"
        case 720...: return "HD"
        default: return "SD"
        }
    }

    var detail: String {
        var parts: [String] = []
        if maxFPS > 0 { parts.append("\(maxFPS) fps") }
        if hdr { parts.append("HDR") }
        if let estimatedSize { parts.append("~\(Fmt.bytes(estimatedSize))") }
        return parts.isEmpty ? "—" : parts.joined(separator: " · ")
    }
}

// MARK: - Download options

enum MediaMode: String, CaseIterable, Identifiable {
    case video, audio
    var id: String { rawValue }
    var title: String { self == .video ? "Vidéo + son" : "Audio seul" }
    var icon: String { self == .video ? "film" : "music.note" }
}

enum VideoContainer: String, CaseIterable, Identifiable {
    case mp4, mkv, appleCompat
    var id: String { rawValue }

    var title: String {
        switch self {
        case .mp4: return "MP4"
        case .mkv: return "MKV"
        case .appleCompat: return "MP4 · QuickTime"
        }
    }

    var subtitle: String {
        switch self {
        case .mp4: return "Qualité maximale — lisible avec IINA, VLC, navigateurs."
        case .mkv: return "Qualité maximale — conteneur le plus flexible."
        case .appleCompat: return "H.264 prioritaire pour QuickTime / iMovie (souvent ≤ 1080p sur YouTube)."
        }
    }

    var fileExtension: String { self == .mkv ? "mkv" : "mp4" }
}

enum AudioFormat: String, CaseIterable, Identifiable {
    case m4a, mp3, opus, flac
    var id: String { rawValue }

    var title: String {
        switch self {
        case .m4a: return "M4A"
        case .mp3: return "MP3"
        case .opus: return "Opus"
        case .flac: return "FLAC"
        }
    }

    var badge: String {
        switch self {
        case .m4a: return "AAC"
        case .mp3: return "V0"
        case .opus: return "Original"
        case .flac: return "Lossless"
        }
    }

    var subtitle: String {
        switch self {
        case .m4a: return "Compatible Apple (Musique, iPhone)"
        case .mp3: return "Compatible partout, haute qualité"
        case .opus: return "Flux d'origine, sans réencodage"
        case .flac: return "Sans perte, fichier plus lourd"
        }
    }
}

struct DownloadRequest: Sendable {
    let url: String
    let mode: MediaMode
    let height: Int?
    let qualityLabel: String
    let container: VideoContainer
    let audioFormat: AudioFormat
    let folder: String
    let embedThumbnail: Bool
    let embedMetadata: Bool
    let embedChapters: Bool
    let subtitles: Bool
    let subtitleLangs: String

    var summary: String {
        switch mode {
        case .video: return "\(qualityLabel) · \(container.title)"
        case .audio: return "Audio · \(audioFormat.title)"
        }
    }

    func arguments(ffmpegPath: String?) -> [String] {
        var args: [String] = [
            "--newline", "--progress", "--no-colors", "--no-playlist", "--no-mtime", "--no-simulate",
            "--concurrent-fragments", "4",
            "--progress-template",
            "download:[PROG]%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s|%(info.format_id)s|%(info.vcodec)s|%(info.acodec)s",
            "--progress-template",
            "postprocess:[POST]%(progress.postprocessor)s|%(progress.status)s",
            "--print", "after_move:[FILE]%(filepath)s",
        ]

        switch mode {
        case .audio:
            let selector = audioFormat == .m4a ? "ba[ext=m4a]/ba/b" : "ba/b"
            args += ["-f", selector, "-x", "--audio-format", audioFormat.rawValue, "--audio-quality", "0"]
            args += ["-o", "%(title).180B.%(ext)s"]
        case .video:
            let h = height.map { "[height<=\($0)]" } ?? ""
            let selector: String
            switch container {
            case .mp4:
                selector = "bv*\(h)+ba[ext=m4a]/bv*\(h)+ba/b\(h)/bv*+ba/b"
            case .mkv:
                selector = "bv*\(h)+ba/b\(h)/bv*+ba/b"
            case .appleCompat:
                selector = "bv*[vcodec^=avc]\(h)+ba[ext=m4a]/b[ext=mp4]\(h)/bv*\(h)+ba[ext=m4a]/bv*\(h)+ba/b\(h)/bv*+ba/b"
            }
            args += ["-f", selector, "--merge-output-format", container.fileExtension]
            args += ["-o", "%(title).180B [%(height)sp].%(ext)s"]
            if embedChapters { args.append("--embed-chapters") }
            if subtitles {
                let langs = subtitleLangs.trimmingCharacters(in: .whitespaces)
                args += ["--write-subs", "--sub-langs", langs.isEmpty ? "fr,en" : langs, "--embed-subs"]
            }
        }

        if embedMetadata { args.append("--embed-metadata") }
        if embedThumbnail { args.append("--embed-thumbnail") }
        if let ffmpegPath { args += ["--ffmpeg-location", ffmpegPath] }
        args += ["-P", folder, "--", url]
        return args
    }
}

// MARK: - Formatting helpers

enum Fmt {
    static func bytes(_ value: Double) -> String {
        ByteCountFormatter.string(fromByteCount: Int64(value), countStyle: .file)
    }

    static func duration(_ seconds: Double) -> String {
        let t = Int(seconds.rounded())
        let h = t / 3600, m = (t % 3600) / 60, s = t % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }

    static func views(_ n: Double) -> String {
        switch n {
        case 1_000_000_000...: return String(format: "%.1f Md de vues", n / 1_000_000_000)
        case 1_000_000...: return String(format: "%.1f M de vues", n / 1_000_000)
        case 1_000...: return String(format: "%.0f k vues", n / 1_000)
        default: return "\(Int(n)) vues"
        }
    }
}
