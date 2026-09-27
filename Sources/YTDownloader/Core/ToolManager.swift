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
    /// Options shared by every yt-dlp call (e.g. browser cookies).
    let commonArguments: [String]
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
        let dir = base.appendingPathComponent("YTDownloader", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try? FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: dir.path)
        return dir
    }()

    static let bin: URL = {
        let dir = support.appendingPathComponent("bin", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        // Only this user may add or replace the executables the app runs.
        try? FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: dir.path)
        return dir
    }()

    static let systemBinDirs: [String] = [
        "/opt/homebrew/bin",
        "/usr/local/bin",
        NSHomeDirectory() + "/.deno/bin",
    ]

    /// Minimal environment for child processes: nothing that could inject code
    /// (PYTHONPATH, DYLD_*, …) is passed through.
    static var processEnvironment: [String: String] {
        let allowed: Set<String> = [
            "HOME", "USER", "LOGNAME", "TMPDIR", "LANG", "__CF_USER_TEXT_ENCODING", "DEVELOPER_DIR",
            "http_proxy", "https_proxy", "no_proxy", "all_proxy",
            "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY", "ALL_PROXY",
        ]
        var env = ProcessInfo.processInfo.environment.filter { allowed.contains($0.key) || $0.key.hasPrefix("LC_") }
        env["PATH"] = ([bin.path] + systemBinDirs + ["/usr/bin", "/bin", "/usr/sbin", "/sbin"]).joined(separator: ":")
        if env["LANG"] == nil { env["LANG"] = "en_US.UTF-8" }
        env["PYTHONIOENCODING"] = "utf-8"
        env["PYTHONUTF8"] = "1"
        env["PYTHONNOUSERSITE"] = "1"
        return env
    }
}

/// Finds or installs yt-dlp, FFmpeg and Deno (JS runtime required by yt-dlp for YouTube),
/// verifying official checksums and detecting any later modification of the files.
@MainActor
final class ToolManager: ObservableObject {
    @Published var ytdlp: ToolStatus = .checking
    @Published var ffmpeg: ToolStatus = .checking
    @Published var deno: ToolStatus = .checking
    @Published var ytdlpVersion: String?
    @Published var isUpdating = false
    @Published var updateMessage: String?
    @Published private(set) var trust: [String: ToolTrust] = [:]
    /// Set when a tool file was modified after installation (it is then removed and reinstalled).
    @Published var securityAlert: String?

    private var bootstrapped = false
    private var manifest = ToolManifest.load()

    var isReady: Bool { ytdlp.path != nil && ffmpeg.path != nil }
    var hasFailure: Bool { ytdlp.isFailed || ffmpeg.isFailed || deno.isFailed }
    var isWorking: Bool { ytdlp.isWorking || ffmpeg.isWorking || deno.isWorking }

    var paths: ToolPaths? {
        guard let y = ytdlp.path else { return nil }
        // Never read user/system yt-dlp config files or plugins: they could inject options or code.
        var common: [String] = ["--ignore-config"]
        if let version = ytdlpVersion, version >= "2025.02.19" { common.append("--no-plugin-dirs") }
        let browser = UserDefaults.standard.string(forKey: Prefs.cookiesBrowser) ?? ""
        if !browser.isEmpty { common += ["--cookies-from-browser", browser] }
        return ToolPaths(ytdlp: y, ffmpeg: ffmpeg.path, environment: AppPaths.processEnvironment, commonArguments: common)
    }

    func bootstrap() async {
        guard !bootstrapped else { return }
        bootstrapped = true
        await verifyInstalledTools()
        refresh()
        await installMissing()
    }

    /// Re-checks the integrity of the installed tools on demand.
    func verifyNow() async {
        securityAlert = nil
        await verifyInstalledTools()
        refresh()
        await installMissing()
    }

    func refresh() {
        ytdlp = Self.locate("yt-dlp", preferManaged: true).map { ToolStatus.ready($0) } ?? .missing
        ffmpeg = Self.locate("ffmpeg", preferManaged: false).map { ToolStatus.ready($0) } ?? .missing
        deno = Self.locate("deno", preferManaged: false).map { ToolStatus.ready($0) } ?? .missing
        updateTrust()
    }

    func installMissing() async {
        if ytdlp.path == nil { await installYTDLP() }
        if ffmpeg.path == nil { await installFFmpeg() }
        if deno.path == nil { await installDeno() }
        updateTrust()
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
        updateTrust()
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
        manifest.entries = [:]
        manifest.save()
        securityAlert = nil
        refresh()
        await installMissing()
    }

    // MARK: Integrity

    /// Re-hashes every tool installed by the app and removes any file that changed since
    /// installation (or that predates integrity tracking), so a verified copy is reinstalled.
    private func verifyInstalledTools() async {
        let fm = FileManager.default
        var tampered: [String] = []
        for name in ["yt-dlp", "ffmpeg", "ffprobe", "deno"] {
            let file = AppPaths.bin.appendingPathComponent(name)
            guard fm.fileExists(atPath: file.path) else {
                manifest.entries[name] = nil
                continue
            }
            guard let entry = manifest.entries[name] else {
                // Installed by an older version without a manifest: replace with a verified copy.
                try? fm.removeItem(at: file)
                continue
            }
            let hash = try? await Integrity.sha256Async(of: file)
            if hash != entry.sha256 {
                try? fm.removeItem(at: file)
                manifest.entries[name] = nil
                tampered.append(name)
            }
        }
        manifest.save()
        if !tampered.isEmpty {
            securityAlert = "Fichier modifié depuis son installation : \(tampered.joined(separator: ", ")). "
                + "Il a été supprimé et une copie vérifiée est réinstallée."
        }
    }

    private func record(_ name: String, source: String, verified: Bool) async {
        let file = AppPaths.bin.appendingPathComponent(name)
        guard let hash = try? await Integrity.sha256Async(of: file) else { return }
        manifest.entries[name] = ToolManifest.Entry(sha256: hash, source: source, verified: verified, installedAt: Date())
        manifest.save()
    }

    private func updateTrust() {
        let binPath = AppPaths.bin.path
        var result: [String: ToolTrust] = [:]
        for (name, status) in [("yt-dlp", ytdlp), ("ffmpeg", ffmpeg), ("deno", deno)] {
            guard let path = status.path else {
                result[name] = .unknown
                continue
            }
            if path.hasPrefix(binPath), let entry = manifest.entries[name] {
                result[name] = entry.verified ? .verified(source: entry.source) : .unverified(source: entry.source)
            } else {
                result[name] = .external(path: path)
            }
        }
        trust = result
    }

    // MARK: Install

    private func installYTDLP() async {
        ytdlp = .installing("Téléchargement de yt-dlp…")
        do {
            let tag = await Installer.latestReleaseTag("yt-dlp/yt-dlp")
            let base = Installer.releaseBase("yt-dlp/yt-dlp", tag: tag)
            let sums = try await Installer.fetchText(URL(string: base + "SHA2-256SUMS")!)
            guard let expected = Integrity.checksum(in: sums, for: "yt-dlp_macos") else {
                throw IntegrityError.missingChecksum("yt-dlp")
            }
            ytdlp = .installing("Vérification de yt-dlp…")
            let dest = AppPaths.bin.appendingPathComponent("yt-dlp")
            try await Installer.downloadVerified(URL(string: base + "yt-dlp_macos")!, to: dest, sha256: expected, name: "yt-dlp")
            try await Installer.makeExecutable(dest)
            await record("yt-dlp", source: "GitHub yt-dlp \(tag ?? "latest")", verified: true)
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
                // The build site may publish a .sha256 next to the archive: use it when present.
                let checksumText = try? await Installer.fetchText(URL(string: url.absoluteString + ".sha256")!)
                let expected = checksumText.flatMap { Integrity.checksum(in: $0, for: nil) }
                try await Installer.installZip(url, binary: name, sha256: expected)
                await record(name, source: "ffmpeg.martin-riedl.de", verified: expected != nil)
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
            let asset = "deno-\(arch)-apple-darwin.zip"
            let tag = await Installer.latestReleaseTag("denoland/deno")
            let base = Installer.releaseBase("denoland/deno", tag: tag)
            let sums = try await Installer.fetchText(URL(string: base + asset + ".sha256sum")!)
            guard let expected = Integrity.checksum(in: sums, for: asset) ?? Integrity.checksum(in: sums, for: nil) else {
                throw IntegrityError.missingChecksum("Deno")
            }
            deno = .installing("Vérification de Deno…")
            try await Installer.installZip(URL(string: base + asset)!, binary: "deno", sha256: expected)
            await record("deno", source: "GitHub Deno \(tag ?? "latest")", verified: true)
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
    /// Latest release tag, so the binary and its checksum file come from the same release.
    static func latestReleaseTag(_ repo: String) async -> String? {
        struct Release: Decodable { let tag_name: String }
        var request = URLRequest(url: URL(string: "https://api.github.com/repos/\(repo)/releases/latest")!)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 20
        guard let result = try? await URLSession.shared.data(for: request),
              (result.1 as? HTTPURLResponse)?.statusCode == 200,
              let release = try? JSONDecoder().decode(Release.self, from: result.0),
              !release.tag_name.isEmpty
        else { return nil }
        return release.tag_name
    }

    static func releaseBase(_ repo: String, tag: String?) -> String {
        guard let tag, let encoded = tag.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) else {
            return "https://github.com/\(repo)/releases/latest/download/"
        }
        return "https://github.com/\(repo)/releases/download/\(encoded)/"
    }

    static func fetchText(_ url: URL) async throws -> String {
        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 30
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else { throw ToolError.http(status) }
        return String(decoding: data, as: UTF8.self)
    }

    static func download(_ url: URL, to dest: URL) async throws {
        guard url.scheme == "https" else { throw ToolError.http(0) }
        let (tmp, response) = try await URLSession.shared.download(from: url)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            try? FileManager.default.removeItem(at: tmp)
            throw ToolError.http(http.statusCode)
        }
        let fm = FileManager.default
        if fm.fileExists(atPath: dest.path) { try fm.removeItem(at: dest) }
        try fm.moveItem(at: tmp, to: dest)
    }

    /// Downloads to a temporary name, checks the SHA-256, then moves into place.
    static func downloadVerified(_ url: URL, to dest: URL, sha256 expected: String, name: String) async throws {
        let fm = FileManager.default
        let partial = dest.deletingLastPathComponent().appendingPathComponent(".\(dest.lastPathComponent).download")
        try await download(url, to: partial)
        let actual = try await Integrity.sha256Async(of: partial)
        guard actual == expected.lowercased() else {
            try? fm.removeItem(at: partial)
            throw IntegrityError.mismatch(name)
        }
        if fm.fileExists(atPath: dest.path) { try fm.removeItem(at: dest) }
        try fm.moveItem(at: partial, to: dest)
    }

    static func installZip(_ url: URL, binary: String, sha256 expected: String?) async throws {
        let fm = FileManager.default
        let work = fm.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try fm.createDirectory(at: work, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: work) }

        let zip = work.appendingPathComponent("archive.zip")
        if let expected {
            try await downloadVerified(url, to: zip, sha256: expected, name: binary)
        } else {
            try await download(url, to: zip)
        }
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
        // Apple Silicon refuses to run unsigned binaries: ad-hoc sign only when no valid signature exists.
        let verify = try? await Shell.run(URL(fileURLWithPath: "/usr/bin/codesign"), ["-v", url.path])
        if verify?.status != 0 {
            _ = try? await Shell.run(URL(fileURLWithPath: "/usr/bin/codesign"), ["--force", "--sign", "-", url.path])
        }
    }

    private static func findFile(named name: String, in dir: URL) -> URL? {
        guard let enumerator = FileManager.default.enumerator(at: dir, includingPropertiesForKeys: nil) else { return nil }
        for case let url as URL in enumerator where url.lastPathComponent == name {
            // Never follow a symlink out of the extracted archive.
            if (try? url.resourceValues(forKeys: [.isSymbolicLinkKey]))?.isSymbolicLink == true { continue }
            return url
        }
        return nil
    }
}
