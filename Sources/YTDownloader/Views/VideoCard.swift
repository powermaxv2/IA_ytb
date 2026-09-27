import SwiftUI

struct VideoCard: View {
    let info: VideoInfo
    @ObservedObject var analyzer: Analyzer
    @EnvironmentObject private var tools: ToolManager
    @EnvironmentObject private var downloads: DownloadManager

    @AppStorage(Prefs.container) private var container: VideoContainer = .mp4
    @AppStorage(Prefs.audioFormat) private var audioFormat: AudioFormat = .m4a
    @AppStorage(Prefs.embedThumbnail) private var embedThumbnail = true
    @AppStorage(Prefs.embedMetadata) private var embedMetadata = true
    @AppStorage(Prefs.embedChapters) private var embedChapters = true
    @AppStorage(Prefs.subtitles) private var subtitles = false
    @AppStorage(Prefs.subtitleLangs) private var subtitleLangs = "fr,en"

    private let columns = [GridItem(.adaptive(minimum: 150), spacing: 10)]

    private var selectedOption: QualityOption? {
        analyzer.options.first { $0.height == analyzer.selectedHeight }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            header
            Divider().overlay(Theme.stroke)
            HStack {
                ModePicker(mode: $analyzer.mode)
                Spacer()
                if analyzer.mode == .video {
                    Picker("Format", selection: $container) {
                        ForEach(VideoContainer.allCases) { Text($0.title).tag($0) }
                    }
                    .labelsHidden()
                    .pickerStyle(.menu)
                    .fixedSize()
                }
            }

            if analyzer.mode == .video {
                videoOptions
            } else {
                audioOptions
            }

            footer
        }
        .glassCard(padding: 20)
    }

    // MARK: Sections

    private var header: some View {
        HStack(alignment: .top, spacing: 18) {
            Thumbnail(url: info.thumbnailURL, width: 240, height: 135)
                .overlay(alignment: .bottomTrailing) {
                    if let duration = info.duration {
                        Text(Fmt.duration(duration))
                            .font(.system(size: 11, weight: .semibold, design: .monospaced))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(Capsule().fill(Color.black.opacity(0.7)))
                            .padding(8)
                    }
                }
            VStack(alignment: .leading, spacing: 8) {
                Text(info.title)
                    .font(.system(size: 18, weight: .bold))
                    .lineLimit(3)
                    .textSelection(.enabled)
                if let uploader = info.uploader {
                    Label(uploader, systemImage: "person.crop.circle.fill")
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                }
                HStack(spacing: 14) {
                    if let views = info.viewCount {
                        Label(Fmt.views(views), systemImage: "eye")
                    }
                    if let best = analyzer.options.first {
                        Label("\(best.label) max · \(best.badge)", systemImage: "sparkles")
                    }
                }
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
    }

    private var videoOptions: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Qualité")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(analyzer.options) { option in
                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            analyzer.selectedHeight = option.height
                        }
                    } label: {
                        SelectableChip(
                            title: option.label,
                            badge: option.badge,
                            detail: option.detail,
                            selected: option.height == analyzer.selectedHeight,
                            highlight: option.height == analyzer.options.first?.height
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            Text(container.subtitle)
                .font(.system(size: 11.5))
                .foregroundStyle(.secondary)
        }
    }

    private var audioOptions: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Format audio")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(AudioFormat.allCases) { format in
                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { audioFormat = format }
                    } label: {
                        SelectableChip(
                            title: format.title,
                            badge: format.badge,
                            detail: format.subtitle,
                            selected: format == audioFormat
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            if let size = info.audioEstimatedSize {
                Text("Meilleure piste audio disponible · ~\(Fmt.bytes(size))")
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var footer: some View {
        HStack(spacing: 12) {
            Image(systemName: "folder.fill").foregroundStyle(.secondary)
            Text((Prefs.currentFolder as NSString).abbreviatingWithTildeInPath)
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .truncationMode(.middle)
            Spacer()
            Button {
                startDownload()
            } label: {
                Label(downloadTitle, systemImage: "arrow.down.circle.fill")
            }
            .buttonStyle(PrimaryButtonStyle())
            .keyboardShortcut(.return, modifiers: .command)
            .disabled(tools.paths == nil)
        }
    }

    private var downloadTitle: String {
        switch analyzer.mode {
        case .video: return "Télécharger \(selectedOption?.label ?? "")".trimmingCharacters(in: .whitespaces)
        case .audio: return "Télécharger en \(audioFormat.title)"
        }
    }

    private func startDownload() {
        guard let paths = tools.paths else { return }
        let option = selectedOption
        let request = DownloadRequest(
            url: info.webpageURL ?? analyzer.urlText,
            mode: analyzer.mode,
            height: option?.height,
            qualityLabel: option.map { "\($0.label) (\($0.badge))" } ?? "Meilleure",
            container: container,
            audioFormat: audioFormat,
            folder: Prefs.currentFolder,
            embedThumbnail: embedThumbnail,
            embedMetadata: embedMetadata,
            embedChapters: embedChapters,
            subtitles: subtitles,
            subtitleLangs: subtitleLangs
        )
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
            downloads.enqueue(request, title: info.title, thumbnail: info.thumbnailURL, paths: paths)
            analyzer.reset()
        }
    }
}

struct ModePicker: View {
    @Binding var mode: MediaMode
    @Namespace private var namespace

    var body: some View {
        HStack(spacing: 4) {
            ForEach(MediaMode.allCases) { item in
                Button {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { mode = item }
                } label: {
                    Label(item.title, systemImage: item.icon)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(mode == item ? Color.white : Color.secondary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background {
                            if mode == item {
                                Capsule()
                                    .fill(Theme.gradient)
                                    .matchedGeometryEffect(id: "pill", in: namespace)
                            }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Capsule().fill(Color.white.opacity(0.06)))
        .overlay(Capsule().strokeBorder(Theme.stroke, lineWidth: 1))
    }
}
