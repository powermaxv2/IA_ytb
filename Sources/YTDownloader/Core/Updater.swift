import AppKit
import Foundation

enum AppInfo {
    static let repo = "powermaxv2/IA_ytb"
    static let defaultBranch = "claude/youtube-downloader-mac-51ojpa"

    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
    }

    /// Branch the updates come from (written into Info.plist at build time).
    static var branch: String {
        let value = Bundle.main.object(forInfoDictionaryKey: "YTDUpdateBranch") as? String ?? ""
        return value.isEmpty ? defaultBranch : value
    }
}

enum SemVer {
    static func isNewer(_ a: String, than b: String) -> Bool {
        let pa = parts(a), pb = parts(b)
        for i in 0..<max(pa.count, pb.count) {
            let x = i < pa.count ? pa[i] : 0
            let y = i < pb.count ? pb[i] : 0
            if x != y { return x > y }
        }
        return false
    }

    private static func parts(_ version: String) -> [Int] {
        version
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: ".")
            .map { Int($0.prefix(while: { $0.isNumber })) ?? 0 }
    }
}

enum UpdateError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        if case .message(let text) = self { return text }
        return nil
    }
}

/// Checks GitHub for a newer version, rebuilds the app locally and relaunches it.
@MainActor
final class Updater: ObservableObject {
    enum State: Equatable {
        case idle
        case checking
        case upToDate
        case available(String)
        case working(String)
        case failed(String)
    }

    @Published var state: State = .idle
    @Published var latestVersion: String?
    @Published var notes: String?
    @Published private(set) var log: [String] = []
    @Published var lastCheck: Date?
    /// Exact commit the available update was read from; the install downloads this commit only.
    @Published private(set) var pinnedCommit: String?

    let currentVersion = AppInfo.version
    private var didAutoCheck = false

    var isWorking: Bool {
        if case .working = state { return true }
        return false
    }

    var isUpdateAvailable: Bool {
        guard let latestVersion else { return false }
        return SemVer.isNewer(latestVersion, than: currentVersion)
    }

    // MARK: Check

    func autoCheck() async {
        guard !didAutoCheck else { return }
        didAutoCheck = true
        guard UserDefaults.standard.bool(forKey: Prefs.autoUpdateCheck) else { return }
        await check(silent: true)
    }

    func check(silent: Bool = false) async {
        guard !isWorking else { return }
        if !silent { state = .checking }
        do {
            let commit = await Self.latestCommit()
            let ref = commit ?? AppInfo.branch
            let version = try await Self.fetchText("VERSION", ref: ref).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !version.isEmpty, version.count < 32 else { throw UpdateError.message("fichier VERSION invalide") }
            latestVersion = version
            lastCheck = Date()
            if SemVer.isNewer(version, than: currentVersion) {
                pinnedCommit = commit
                notes = (try? await Self.fetchText("RELEASE_NOTES.md", ref: ref)).map(Self.notesSummary)
                state = .available(version)
            } else {
                state = silent ? .idle : .upToDate
            }
        } catch {
            state = silent ? .idle : .failed("Impossible de vérifier les mises à jour : \(error.localizedDescription)")
        }
    }

    func dismiss() {
        guard !isWorking else { return }
        state = .idle
    }

    /// SHA of the newest commit on the update branch (nil if GitHub's API is unreachable).
    nonisolated static func latestCommit() async -> String? {
        let branch = AppInfo.branch.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? AppInfo.branch
        var request = URLRequest(url: URL(string: "https://api.github.com/repos/\(AppInfo.repo)/commits/\(branch)")!)
        request.setValue("application/vnd.github.sha", forHTTPHeaderField: "Accept")
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 20
        guard let result = try? await URLSession.shared.data(for: request),
              (result.1 as? HTTPURLResponse)?.statusCode == 200
        else { return nil }
        let sha = String(decoding: result.0, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
        return isCommitSHA(sha) ? sha : nil
    }

    nonisolated static func isCommitSHA(_ value: String) -> Bool {
        value.count == 40 && value.allSatisfy { $0.isHexDigit }
    }

    /// Reads a file of the repository at `ref` (GitHub API first, raw CDN as fallback).
    nonisolated static func fetchText(_ path: String, ref: String) async throws -> String {
        var components = URLComponents(string: "https://api.github.com/repos/\(AppInfo.repo)/contents/\(path)")!
        components.queryItems = [URLQueryItem(name: "ref", value: ref)]
        var request = URLRequest(url: components.url!)
        request.setValue("application/vnd.github.raw", forHTTPHeaderField: "Accept")
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 20
        if let result = try? await URLSession.shared.data(for: request),
           (result.1 as? HTTPURLResponse)?.statusCode == 200 {
            return String(decoding: result.0, as: UTF8.self)
        }

        let raw = URL(string: "https://raw.githubusercontent.com/\(AppInfo.repo)/\(ref)/\(path)")!
        var fallback = URLRequest(url: raw)
        fallback.cachePolicy = .reloadIgnoringLocalCacheData
        fallback.timeoutInterval = 20
        let (data, response) = try await URLSession.shared.data(for: fallback)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard status == 200 else { throw ToolError.http(status) }
        return String(decoding: data, as: UTF8.self)
    }

    /// Keeps the first section of the release notes (the newest version).
    nonisolated static func notesSummary(_ markdown: String) -> String {
        var lines: [String] = []
        var headings = 0
        for line in markdown.components(separatedBy: .newlines) {
            if line.hasPrefix("## ") {
                headings += 1
                if headings > 1 { break }
                continue
            }
            if line.hasPrefix("# ") { continue }
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if !trimmed.isEmpty { lines.append(trimmed) }
        }
        return lines.prefix(8).joined(separator: "\n")
    }

    // MARK: Install

    func install() {
        guard !isWorking else { return }
        log = []
        state = .working("Préparation…")
        Task { await runInstall() }
    }

    func copyLog() {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(log.joined(separator: "\n"), forType: .string)
    }

    private func runInstall() async {
        let fm = FileManager.default
        let work = AppPaths.support.appendingPathComponent("update", isDirectory: true)
        do {
            let destination = URL(fileURLWithPath: Bundle.main.bundlePath)
            guard destination.pathExtension == "app" else {
                throw UpdateError.message("La mise à jour automatique fonctionne seulement depuis l'app installée (YT Downloader.app).")
            }
            guard fm.isWritableFile(atPath: destination.deletingLastPathComponent().path) else {
                throw UpdateError.message("Pas l'autorisation de modifier « \(destination.deletingLastPathComponent().path) ».")
            }

            try? fm.removeItem(at: work)
            try fm.createDirectory(at: work, withIntermediateDirectories: true)

            state = .working("Vérification des outils Apple…")
            let tools = try? await Shell.run(URL(fileURLWithPath: "/usr/bin/xcode-select"), ["-p"])
            guard tools?.status == 0 else {
                _ = try? await Shell.run(URL(fileURLWithPath: "/usr/bin/xcode-select"), ["--install"])
                throw UpdateError.message("Les outils de développement Apple sont nécessaires. Une fenêtre d'installation vient de s'ouvrir : installez-les, puis relancez la mise à jour.")
            }

            state = .working("Téléchargement de la nouvelle version…")
            // Download exactly the commit that was checked (never a later, unchecked one).
            let commit: String
            if let pinned = pinnedCommit {
                commit = pinned
            } else if let latest = await Self.latestCommit() {
                commit = latest
            } else {
                throw UpdateError.message("Impossible d'identifier la version sur GitHub. Réessayez dans quelques minutes.")
            }
            appendLog("Commit installé : \(commit)")
            let archive = work.appendingPathComponent("source.tar.gz")
            try await Installer.download(URL(string: "https://codeload.github.com/\(AppInfo.repo)/tar.gz/\(commit)")!, to: archive)
            let source = work.appendingPathComponent("src", isDirectory: true)
            try fm.createDirectory(at: source, withIntermediateDirectories: true)
            let untar = try await Shell.run(
                URL(fileURLWithPath: "/usr/bin/tar"),
                ["-xzf", archive.path, "-C", source.path, "--strip-components", "1"]
            )
            guard untar.status == 0 else { throw UpdateError.message("Archive du code source illisible.") }

            // Refuse a downgrade even if the branch was rolled back after the check.
            let fetchedVersion = (try? String(contentsOf: source.appendingPathComponent("VERSION"), encoding: .utf8))?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard SemVer.isNewer(fetchedVersion, than: currentVersion) else {
                throw UpdateError.message("La version téléchargée (\(fetchedVersion)) n'est pas plus récente que la version installée : mise à jour annulée.")
            }

            state = .working("Compilation (1 à 3 minutes)…")
            var environment = AppPaths.processEnvironment
            environment["ARCHS"] = "native"
            environment["SKIP_PACKAGES"] = "1"
            environment["UPDATE_BRANCH"] = AppInfo.branch
            let build = StreamingProcess()
            let stream = build.run(
                executable: "/bin/bash",
                arguments: [source.appendingPathComponent("scripts/build_app.sh").path],
                environment: environment
            )
            var exitCode: Int32 = -1
            for await event in stream {
                switch event {
                case .output(let line):
                    appendLog(line)
                    if line.unicodeScalars.first == "\u{25B6}" {
                        let step = String(line.drop(while: { !$0.isLetter }))
                        if !step.isEmpty { state = .working(step) }
                    }
                case .exit(let code):
                    exitCode = code
                case .launchFailed(let message):
                    appendLog(message)
                }
            }
            guard exitCode == 0 else { throw UpdateError.message(buildFailureSummary()) }

            let newApp = source.appendingPathComponent("dist/YT Downloader.app")
            guard fm.fileExists(atPath: newApp.path) else {
                throw UpdateError.message("La compilation n'a pas produit l'application.")
            }

            state = .working("Installation et redémarrage…")
            try relaunch(installing: newApp, at: destination, work: work)
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    private func appendLog(_ line: String) {
        log.append(line)
        if log.count > 400 { log.removeFirst(log.count - 400) }
    }

    private func buildFailureSummary() -> String {
        let errors = log.filter { $0.contains("error:") }
        let relevant = errors.isEmpty ? Array(log.suffix(4)) : Array(errors.prefix(3))
        return "La compilation de la nouvelle version a échoué. L'app actuelle reste inchangée.\n"
            + relevant.joined(separator: "\n")
    }

    /// Starts a helper that waits for the app to quit, swaps the bundle and reopens it.
    private func relaunch(installing newApp: URL, at destination: URL, work: URL) throws {
        let script = work.appendingPathComponent("apply_update.sh")
        try Self.applyScript.write(to: script, atomically: true, encoding: .utf8)
        let helper = Process()
        helper.executableURL = URL(fileURLWithPath: "/bin/bash")
        helper.arguments = [
            script.path,
            String(ProcessInfo.processInfo.processIdentifier),
            newApp.path,
            destination.path,
            work.path,
        ]
        helper.standardInput = FileHandle.nullDevice
        helper.standardOutput = FileHandle.nullDevice
        helper.standardError = FileHandle.nullDevice
        try helper.run()
        ProcessRegistry.shared.terminateAll()
        NSApp.terminate(nil)
    }

    private static let applyScript = """
    #!/bin/bash
    PID="$1"; NEW="$2"; DEST="$3"; WORK="$4"
    for _ in $(seq 1 150); do kill -0 "$PID" 2>/dev/null || break; sleep 0.2; done
    BACKUP="${DEST%.app}.previous.app"
    rm -rf "$BACKUP"
    if mv "$DEST" "$BACKUP" && /usr/bin/ditto "$NEW" "$DEST"; then
      rm -rf "$BACKUP"
    else
      rm -rf "$DEST"
      mv "$BACKUP" "$DEST"
    fi
    /usr/bin/xattr -cr "$DEST" 2>/dev/null
    /usr/bin/open "$DEST"
    rm -rf "$WORK"
    """
}
