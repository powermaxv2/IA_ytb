import AppKit
import SwiftUI

/// State of the "paste a link → pick a quality" flow.
@MainActor
final class Analyzer: ObservableObject {
    enum Phase {
        case idle
        case loading
        case loaded(VideoInfo)
        case failed(String)
    }

    @Published var urlText = ""
    @Published var phase: Phase = .idle
    @Published var options: [QualityOption] = []
    @Published var selectedHeight: Int?
    @Published var mode: MediaMode = .video

    private var task: Task<Void, Never>?

    var isLoading: Bool {
        if case .loading = phase { return true }
        return false
    }

    func analyze(with paths: ToolPaths?) {
        let url = urlText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !url.isEmpty, let paths else { return }
        urlText = url
        task?.cancel()
        phase = .loading
        task = Task {
            do {
                let info = try await YTDLP.fetchInfo(url: url, tools: paths)
                guard !Task.isCancelled else { return }
                let options = info.qualityOptions
                self.options = options
                self.selectedHeight = options.first?.height
                withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
                    self.phase = .loaded(info)
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
        phase = .idle
    }

    func pasteFromClipboard(andAnalyzeWith paths: ToolPaths?) {
        guard let text = NSPasteboard.general.string(forType: .string)?
            .trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { return }
        urlText = text
        analyze(with: paths)
    }
}
