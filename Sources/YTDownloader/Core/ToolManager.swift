import Foundation

enum ToolStatus: Equatable {
    case checking
    case missing
    case installing(String)
    case ready(String)
    case failed(String)

    var path: String? {
        if case .ready(let p) = self { return p }
        return nil
    }

    var isWorking: Bool {
        switch self {
        case .checking, .installing: return true
        default: return false
        }
    }

    var isFailed: Bool {
        if case .failed = self { return true }
        return false
    }

    var label: String {
        switch self {
        case .checking: return "Vérification…"
        case .missing: return "Non installé"
        case .installing(let message): return message
        case .ready(let path): return path
        case .failed(let message): return "Échec : \(message)"
        }
    }
}

struct ToolPaths: Sendable {
    let ytdlp: String
    let ffmpeg: String?
    let environment: [String: String]
}

enum ToolError: LocalizedError {
    case http(Int)
    case unzip
    case notFound(String)

    var errorDescription: String? {
        switch self {
        case .http(let code): return "erreur réseau (HTTP \(code))"
        case .unzip: return "impossible d'extraire l'archive"
        case .notFound(let name): return "\(name) introuvable dans l'archive"
        }
    }
}

enum AppPaths {
    static let support: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("YTDownloader", isDirectory: true)
    }()

    static let bin: URL = {
        let dir = support.appendingPathComponent("bin", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }()

    static let systemBinDirs: [String] = [
        "/opt/homebrew/bin",
        "/usr/local/bin",
        NSHomeDirectory() + "/.deno/bin",
    ]

    static var processEnvironment: [String: String] {
        var env = ProcessInfo.processInfo.environment
        env["PATH"] = ([bin.path] + systemBinDirs + ["/usr/bin", "/bin", "/usr/sbin", "/sbin"]).joined(separator: ":")
        env["PYTHONIOENCODING"] = "utf-8"
        env["PYTHONUTF8"] = "1"
        return env
    }
}

/// Finds or installs yt-dlp, FFmpeg and Deno (JS runtime required by yt-dlp for YouTube).
@MainActor
final class ToolManager: ObservableObject {
    @Published var ytdlp: ToolStatus = .checking
    @Published var ffmpeg: ToolStatus = .checking
    @Published var deno: ToolStatus = .checking
    @Published var ytdlpVersion: String?
    @Published var isUpdating = false
    @Published var updateMessage: String?

    private var bootstrapped = false

    var isReady: Bool { ytdlp.path != nil && ffmpeg.path != nil }
    var hasFailure: Bool { ytdlp.isFailed || ffmpeg.isFailed || deno.isFailed }
    var isWorking: Bool { ytdlp.isWorking || ffmpeg.isWorking || deno.isWorking }

    var paths: ToolPaths? {
        guard let y = ytdlp.path else { return nil }
        return ToolPaths(ytdlp: y, ffmpeg: ffmpeg.path, environment: AppPaths.processEnvironment)
    }

    func bootstrap() async {
        guard !bootstrapped else { return }
        bootstrapped = true
        refresh()
        await installMissing()
    }

    func refresh() {
        ytdlp = Self.locate("yt-dlp", preferManaged: true).map { ToolStatus.ready($0) } ?? .missing
        ffmpeg = Self.locate("ffmpeg", preferManaged: false).map { ToolStatus.ready($0) } ?? .missing
        deno = Self.locate("deno", preferManaged: false).map { ToolStatus.ready($0) } ?? .missing
    }

    func installMissing() async {
        if ytdlp.path == nil { await installYTDLP() }
        if ffmpeg.path == nil { await installFFmpeg() }
        if deno.path == nil { await installDeno() }
        await loadVersion()
    }

    func updateYTDLP() async {
        isUpdating = true
        updateMessage = nil
        let before = ytdlpVersion
        await installYTDLP()
        if case .failed(let message) = ytdlp {
            updateMessage = "Échec de la mise à jour : \(message)"
            refresh()
        }
        await loadVersion()
        if updateMessage == nil {
            let now = ytdlpVersion ?? "?"
            updateMessage = before == ytdlpVersion ? "yt-dlp est déjà à jour (\(now))." : "yt-dlp mis à jour : \(now)"
        }
        isUpdating = false
    }

    func reinstallAll() async {
        let fm = FileManager.default
        if let files = try? fm.contentsOfDirectory(at: AppPaths.bin, includingPropertiesForKeys: nil) {
            files.forEach { try? fm.removeItem(at: $0) }
        }
        refresh()
        await installMissing()
    }

    // MARK: Install

    private func installYTDLP() async {
        ytdlp = .installing("Téléchargement de yt-dlp…")
        do {
            let dest = AppPaths.bin.appendingPathComponent("yt-dlp")
            let url = URL(string: "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos")!
            try await Installer.download(url, to: dest)
            try await Installer.makeExecutable(dest)
            ytdlp = .ready(dest.path)
        } catch {
            ytdlp = .failed(error.localizedDescription)
        }
    }

    private func installFFmpeg() async {
        ffmpeg = .installing("Téléchargement de FFmpeg…")
        #if arch(arm64)
        let arch = "arm64"
        #else
        let arch = "amd64"
        #endif
        do {
            for name in ["ffmpeg", "ffprobe"] {
                let url = URL(string: "https://ffmpeg.martin-riedl.de/redirect/latest/macos/\(arch)/release/\(name).zip")!
                try await Installer.installZip(url, binary: name)
            }
            ffmpeg = .ready(AppPaths.bin.appendingPathComponent("ffmpeg").path)
        } catch {
            ffmpeg = .failed("\(error.localizedDescription) — installez-le avec « brew install ffmpeg »")
        }
    }

    private func installDeno() async {
        deno = .installing("Téléchargement de Deno…")
        #if arch(arm64)
        let arch = "aarch64"
        #else
        let arch = "x86_64"
        #endif
        do {
            let url = URL(string: "https://github.com/denoland/deno/releases/latest/download/deno-\(arch)-apple-darwin.zip")!
            try await Installer.installZip(url, binary: "deno")
            deno = .ready(AppPaths.bin.appendingPathComponent("deno").path)
        } catch {
            deno = .failed(error.localizedDescription)
        }
    }

    private func loadVersion() async {
        guard let path = ytdlp.path else { ytdlpVersion = nil; return }
        let result = try? await Shell.run(URL(fileURLWithPath: path), ["--version"], environment: AppPaths.processEnvironment)
        let version = result?.stdoutString.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        ytdlpVersion = version.isEmpty ? nil : version
    }

    private static func locate(_ name: String, preferManaged: Bool) -> String? {
        let managed = AppPaths.bin.appendingPathComponent(name).path
        let system = AppPaths.systemBinDirs.map { "\($0)/\(name)" }
        let candidates = preferManaged ? [managed] + system : system + [managed]
        return candidates.first { FileManager.default.isExecutableFile(atPath: $0) }
    }
}

enum Installer {
    static func download(_ url: URL, to dest: URL) async throws {
        let (tmp, response) = try await URLSession.shared.download(from: url)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            try? FileManager.default.removeItem(at: tmp)
            throw ToolError.http(http.statusCode)
        }
        let fm = FileManager.default
        if fm.fileExists(atPath: dest.path) { try fm.removeItem(at: dest) }
        try fm.moveItem(at: tmp, to: dest)
    }

    static func installZip(_ url: URL, binary: String) async throws {
        let fm = FileManager.default
        let work = fm.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try fm.createDirectory(at: work, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: work) }

        let zip = work.appendingPathComponent("archive.zip")
        try await download(url, to: zip)
        let out = work.appendingPathComponent("out", isDirectory: true)
        let result = try await Shell.run(URL(fileURLWithPath: "/usr/bin/ditto"), ["-x", "-k", zip.path, out.path])
        guard result.status == 0 else { throw ToolError.unzip }
        guard let found = findFile(named: binary, in: out) else { throw ToolError.notFound(binary) }

        let dest = AppPaths.bin.appendingPathComponent(binary)
        if fm.fileExists(atPath: dest.path) { try fm.removeItem(at: dest) }
        try fm.moveItem(at: found, to: dest)
        try await makeExecutable(dest)
    }

    static func makeExecutable(_ url: URL) async throws {
        try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: url.path)
        _ = try? await Shell.run(URL(fileURLWithPath: "/usr/bin/xattr"), ["-d", "com.apple.quarantine", url.path])
        // Apple Silicon refuses to run unsigned binaries: ad-hoc sign when needed.
        let verify = try? await Shell.run(URL(fileURLWithPath: "/usr/bin/codesign"), ["-v", url.path])
        if verify?.status != 0 {
            _ = try? await Shell.run(URL(fileURLWithPath: "/usr/bin/codesign"), ["--force", "--sign", "-", url.path])
        }
    }

    private static func findFile(named name: String, in dir: URL) -> URL? {
        guard let enumerator = FileManager.default.enumerator(at: dir, includingPropertiesForKeys: nil) else { return nil }
        for case let url as URL in enumerator where url.lastPathComponent == name {
            return url
        }
        return nil
    }
}
