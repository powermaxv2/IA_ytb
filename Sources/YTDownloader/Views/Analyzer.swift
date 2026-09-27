import AppKit
import SwiftUI

/// State of the "paste a link → pick a quality" flow.
@MainActor
final class Analyzer: ObservableObject {
    enum Phase {
        case idle
        case loading
        case video(VideoInfo)
        case playlist(PlaylistInfo)
        case failed(String)
    }

    @Published var urlText = ""
    @Published var phase: Phase = .idle
    @Published var mode: MediaMode = .video

    // Single video
    @Published var options: [QualityOption] = []
    @Published var selectedHeight: Int?

    // Playlist
    @Published var playlistQuality = PlaylistQuality.ladder[0]
    @Published var selectedEntries: Set<String> = []

    /// A YouTube link found in the clipboard, offered to the user.
    @Published var clipboardSuggestion: String?

    private var task: Task<Void, Never>?
    private var lastPasteboardChange = -1

    var isLoading: Bool {
        if case .loading = phase { return true }
        return false
    }

    func analyze(with paths: ToolPaths?) {
        let url = urlText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !url.isEmpty, let paths else { return }
        urlText = url
        // Only plain web links are handed to yt-dlp (no file://, no options, no multi-line input).
        guard Self.isWebLink(url) else {
            withAnimation { phase = .failed("Lien invalide : seules les adresses web (https://…) sont acceptées.") }
            return
        }
        if clipboardSuggestion == url { clipboardSuggestion = nil }
        task?.cancel()
        phase = .loading
        task = Task {
            do {
                let media = try await YTDLP.fetch(url: url, tools: paths)
                guard !Task.isCancelled else { return }
                withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
                    switch media {
                    case .video(let info):
                        let options = info.qualityOptions
                        self.options = options
                        self.selectedHeight = options.first?.height
                        self.phase = .video(info)
                    case .playlist(let playlist):
                        self.selectedEntries = Set(playlist.entries.filter { !$0.isUnavailable }.map(\.id))
                        self.phase = .playlist(playlist)
                    }
                }
            } catch {
                guard !Task.isCancelled else { return }
                withAnimation { self.phase = .failed(error.localizedDescription) }
            }
        }
    }

    func reset() {
        task?.cancel()
        urlText = ""
        options = []
        selectedHeight = nil
        selectedEntries = []
        phase = .idle
    }

    func pasteFromClipboard(andAnalyzeWith paths: ToolPaths?) {
        guard let text = NSPasteboard.general.string(forType: .string)?
            .trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { return }
        urlText = text
        analyze(with: paths)
    }

    func useSuggestion(with paths: ToolPaths?) {
        guard let suggestion = clipboardSuggestion else { return }
        clipboardSuggestion = nil
        urlText = suggestion
        analyze(with: paths)
    }

    /// Looks for a new YouTube link in the clipboard (called when the app becomes active).
    func checkClipboard() {
        let pasteboard = NSPasteboard.general
        guard pasteboard.changeCount != lastPasteboardChange else { return }
        lastPasteboardChange = pasteboard.changeCount
        guard let text = pasteboard.string(forType: .string)?.trimmingCharacters(in: .whitespacesAndNewlines),
              Self.isYouTubeLink(text),
              text != urlText
        else { return }
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { clipboardSuggestion = text }
    }

    static func isWebLink(_ text: String) -> Bool {
        guard text.count < 2048,
              !text.contains(where: { $0.isWhitespace || $0.isNewline }),
              let url = URL(string: text),
              let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https",
              let host = url.host, !host.isEmpty
        else { return false }
        return true
    }

    static func isYouTubeLink(_ text: String) -> Bool {
        guard text.count < 2048, !text.contains(" "), let url = URL(string: text),
              let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https",
              let host = url.host?.lowercased()
        else { return false }
        return host == "youtu.be" || host == "youtube.com" || host.hasSuffix(".youtube.com")
    }
}
