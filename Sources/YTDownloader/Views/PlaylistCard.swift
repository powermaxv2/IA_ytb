import SwiftUI

struct PlaylistCard: View {
    let playlist: PlaylistInfo
    @ObservedObject var analyzer: Analyzer
    @EnvironmentObject private var tools: ToolManager
    @EnvironmentObject private var downloads: DownloadManager

    @AppStorage(Prefs.container) private var container: VideoContainer = .mp4
    @AppStorage(Prefs.audioFormat) private var audioFormat: AudioFormat = .m4a

    private let columns = [GridItem(.adaptive(minimum: 120), spacing: 10)]

    private var selectedCount: Int {
        playlist.entries.filter { analyzer.selectedEntries.contains($0.id) }.count
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
                qualityGrid
            } else {
                audioGrid
            }

            entriesList
            footer
        }
        .glassCard(padding: 20)
    }

    private var header: some View {
        HStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Theme.accent.opacity(0.15))
                    .frame(width: 64, height: 64)
                Image(systemName: "list.bullet.rectangle.portrait.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(Theme.gradient)
            }
            VStack(alignment: .leading, spacing: 5) {
                Text(playlist.title)
                    .font(.system(size: 18, weight: .bold))
                    .lineLimit(2)
                HStack(spacing: 12) {
                    if let uploader = playlist.uploader {
                        Label(uploader, systemImage: "person.crop.circle.fill")
                    }
                    Label("\(playlist.entries.count) vidéos", systemImage: "play.rectangle.on.rectangle")
                }
                .font(.system(size: 12.5))
                .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
    }

    private var qualityGrid: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Qualité pour chaque vidéo")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(PlaylistQuality.ladder) { quality in
                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { analyzer.playlistQuality = quality }
                    } label: {
                        SelectableChip(
                            title: quality.title,
                            badge: quality.badge,
                            detail: quality.detail,
                            selected: quality == analyzer.playlistQuality
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var audioGrid: some View {
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
                        SelectableChip(title: format.title, badge: format.badge, detail: format.subtitle, selected: format == audioFormat)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var entriesList: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Vidéos (\(selectedCount)/\(playlist.entries.count))")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .textCase(.uppercase)
                Spacer()
                Button("Tout") {
                    analyzer.selectedEntries = Set(playlist.entries.filter { !$0.isUnavailable }.map(\.id))
                }
                Button("Aucune") { analyzer.selectedEntries = [] }
            }
            .buttonStyle(.plain)
            .font(.system(size: 12, weight: .medium))

            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(Array(playlist.entries.enumerated()), id: \.element.id) { index, entry in
                        PlaylistEntryRow(
                            index: index + 1,
                            entry: entry,
                            selected: analyzer.selectedEntries.contains(entry.id)
                        ) {
                            if analyzer.selectedEntries.contains(entry.id) {
                                analyzer.selectedEntries.remove(entry.id)
                            } else {
                                analyzer.selectedEntries.insert(entry.id)
                            }
                        }
                    }
                }
                .padding(4)
            }
            .frame(maxHeight: 300)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color.black.opacity(0.15)))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Theme.stroke, lineWidth: 1))
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
                startDownloads()
            } label: {
                Label(selectedCount > 1 ? "Télécharger \(selectedCount) vidéos" : "Télécharger", systemImage: "arrow.down.circle.fill")
            }
            .buttonStyle(PrimaryButtonStyle())
            .keyboardShortcut(.return, modifiers: .command)
            .disabled(tools.paths == nil || selectedCount == 0)
        }
    }

    private func startDownloads() {
        guard let paths = tools.paths else { return }
        let quality = analyzer.playlistQuality
        let chosen = playlist.entries.filter { analyzer.selectedEntries.contains($0.id) }
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
            for entry in chosen {
                let request = DownloadRequest.fromPreferences(
                    url: entry.url,
                    mode: analyzer.mode,
                    height: quality.height,
                    qualityLabel: quality.summaryLabel,
                    container: container,
                    audioFormat: audioFormat
                )
                downloads.enqueue(request, title: entry.title, thumbnail: entry.thumbnail, paths: paths)
            }
            analyzer.reset()
        }
    }
}

struct PlaylistEntryRow: View {
    let index: Int
    let entry: PlaylistEntry
    let selected: Bool
    let toggle: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: toggle) {
            HStack(spacing: 10) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 16))
                    .foregroundStyle(selected ? AnyShapeStyle(Theme.gradient) : AnyShapeStyle(Color.secondary))
                Text("\(index)")
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .foregroundStyle(.tertiary)
                    .frame(width: 28, alignment: .trailing)
                Thumbnail(url: entry.thumbnail, width: 64, height: 36, cornerRadius: 6)
                Text(entry.title)
                    .font(.system(size: 12.5))
                    .foregroundStyle(entry.isUnavailable ? Color.secondary : Color.primary)
                    .lineLimit(1)
                Spacer(minLength: 6)
                if let duration = entry.duration {
                    Text(Fmt.duration(duration))
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(Color.white.opacity(hovering ? 0.06 : 0)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
    }
}
