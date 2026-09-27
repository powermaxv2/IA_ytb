import AppKit
import Foundation
import UserNotifications

@MainActor
final class DownloadItem: ObservableObject, Identifiable {
    enum State: Equatable {
        case queued, starting, downloading, processing, finished, failed, cancelled
    }

    let id = UUID()
    let title: String
    let thumbnail: URL?
    let request: DownloadRequest
    let paths: ToolPaths

    @Published var state: State = .queued
    @Published var progress: Double = 0
    @Published var statusText = "En attente…"
    @Published var fileURL: URL?

    fileprivate var process: StreamingProcess?
    private var cancelled = false
    private var currentFormatID: String?
    private var phaseIndex = 0
    private var logTail: [String] = []

    init(title: String, thumbnail: URL?, request: DownloadRequest, paths: ToolPaths) {
        self.title = title
        self.thumbnail = thumbnail
        self.request = request
        self.paths = paths
    }

    var isActive: Bool { [.starting, .downloading, .processing].contains(state) }

    fileprivate func reset() {
        state = .queued
        progress = 0
        statusText = "En attente…"
        fileURL = nil
        cancelled = false
        currentFormatID = nil
        phaseIndex = 0
        logTail = []
    }

    fileprivate func cancel() {
        cancelled = true
        if state == .queued {
            state = .cancelled
            statusText = "Annulé"
        }
        process?.terminate()
    }

    fileprivate func handle(_ event: ProcessEvent) {
        switch event {
        case .output(let line):
            parse(line.trimmingCharacters(in: .whitespacesAndNewlines))
        case .exit(let code):
            complete(code)
        case .launchFailed(let message):
            process = nil
            state = .failed
            statusText = "Impossible de lancer yt-dlp : \(message)"
        }
    }

    private func parse(_ line: String) {
        if line.hasPrefix("[PROG]") {
            parseProgress(String(line.dropFirst(6)))
        } else if line.hasPrefix("[POST]") {
            let parts = line.dropFirst(6).components(separatedBy: "|")
            guard parts.count >= 2, parts[1] != "finished" else { return }
            state = .processing
            statusText = Self.postprocessorLabel(parts[0])
        } else if line.hasPrefix("[FILE]") {
            let path = String(line.dropFirst(6))
            if !path.isEmpty { fileURL = URL(fileURLWithPath: path) }
        } else if !line.isEmpty {
            logTail.append(line)
            if logTail.count > 50 { logTail.removeFirst(logTail.count - 50) }
        }
    }

    private func parseProgress(_ payload: String) {
        let parts = payload.components(separatedBy: "|")
        guard parts.count >= 8 else { return }
        func number(_ s: String) -> Double? { Double(s.trimmingCharacters(in: .whitespaces)) }

        let downloaded = number(parts[0]) ?? 0
        let total = number(parts[1]) ?? number(parts[2])
        let speed = number(parts[3])
        let eta = number(parts[4])
        let formatID = parts[5]
        let hasVideo = !["none", "NA", ""].contains(parts[6])
        let hasAudio = !["none", "NA", ""].contains(parts[7])

        if let current = currentFormatID, current != formatID { phaseIndex += 1 }
        currentFormatID = formatID

        let expected = (request.mode == .audio || (hasVideo && hasAudio)) ? 1 : 2
        let fraction = total.map { $0 > 0 ? min(1, downloaded / $0) : 0 } ?? 0
        let phase = min(phaseIndex, expected - 1)
        let overall = (Double(phase) + fraction) / Double(expected)
        progress = max(progress, min(1, overall))

        if fraction >= 1 && phase == expected - 1 {
            state = .processing
            statusText = "Finalisation…"
            return
        }

        state = .downloading
        var pieces: [String] = ["\(Int(progress * 100)) %"]
        if expected == 2 { pieces.append(phase == 0 ? "vidéo" : "audio") }
        if let total, total > 0 { pieces.append("\(Fmt.bytes(downloaded)) / \(Fmt.bytes(total))") }
        if let speed, speed > 0 { pieces.append("\(Fmt.bytes(speed))/s") }
        if let eta, eta > 0 { pieces.append("reste \(Fmt.duration(eta))") }
        statusText = pieces.joined(separator: " · ")
    }

    private func complete(_ code: Int32) {
        process = nil
        if cancelled {
            state = .cancelled
            statusText = "Annulé"
            return
        }
        if code == 0 {
            state = .finished
            progress = 1
            var text = "Terminé"
            if let fileURL,
               let size = (try? FileManager.default.attributesOfItem(atPath: fileURL.path))?[.size] as? NSNumber {
                text += " · \(Fmt.bytes(size.doubleValue))"
            }
            statusText = text
        } else {
            state = .failed
            statusText = YTDLP.friendlyError(from: logTail)
        }
    }

    private static func postprocessorLabel(_ name: String) -> String {
        switch name {
        case "Merger": return "Fusion de l'audio et de la vidéo…"
        case "FFmpegExtractAudio": return "Conversion audio…"
        case "EmbedThumbnail": return "Ajout de la miniature…"
        case "FFmpegMetadata": return "Écriture des métadonnées…"
        case "FFmpegEmbedSubtitle": return "Ajout des sous-titres…"
        case "MoveFiles", "MoveFilesAfterDownload": return "Finalisation…"
        default: return "Traitement…"
        }
    }
}

@MainActor
final class DownloadManager: ObservableObject {
    @Published private(set) var items: [DownloadItem] = []

    var activeCount: Int { items.filter(\.isActive).count }
    var hasFinished: Bool { items.contains { [.finished, .cancelled, .failed].contains($0.state) } }

    func enqueue(_ request: DownloadRequest, title: String, thumbnail: URL?, paths: ToolPaths) {
        let item = DownloadItem(title: title, thumbnail: thumbnail, request: request, paths: paths)
        items.insert(item, at: 0)
        pump()
    }

    func cancel(_ item: DownloadItem) {
        item.cancel()
        pump()
    }

    func retry(_ item: DownloadItem) {
        item.reset()
        pump()
    }

    func remove(_ item: DownloadItem) {
        item.cancel()
        items.removeAll { $0.id == item.id }
        pump()
    }

    func clearFinished() {
        items.removeAll { [.finished, .cancelled, .failed].contains($0.state) }
    }

    private func pump() {
        let limit = max(1, UserDefaults.standard.integer(forKey: Prefs.maxConcurrent))
        while activeCount < limit, let next = items.last(where: { $0.state == .queued }) {
            start(next)
        }
        updateDockBadge()
    }

    /// Shows the number of pending downloads on the Dock icon.
    private func updateDockBadge() {
        let pending = items.filter { $0.isActive || $0.state == .queued }.count
        NSApp?.dockTile.badgeLabel = pending > 0 ? "\(pending)" : nil
    }

    private func start(_ item: DownloadItem) {
        item.state = .starting
        item.statusText = "Préparation…"
        try? FileManager.default.createDirectory(atPath: item.request.folder, withIntermediateDirectories: true)

        let process = StreamingProcess()
        item.process = process
        let stream = process.run(
            executable: item.paths.ytdlp,
            arguments: item.request.arguments(paths: item.paths),
            environment: item.paths.environment
        )
        Task { [weak self] in
            for await event in stream {
                item.handle(event)
            }
            self?.didFinish(item)
        }
    }

    private func didFinish(_ item: DownloadItem) {
        if item.state == .finished { notify(item) }
        pump()
    }

    private func notify(_ item: DownloadItem) {
        guard UserDefaults.standard.bool(forKey: Prefs.notify), Bundle.main.bundleIdentifier != nil else { return }
        let content = UNMutableNotificationContent()
        content.title = "Téléchargement terminé"
        content.body = item.title
        content.sound = .default
        let request = UNNotificationRequest(identifier: item.id.uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
