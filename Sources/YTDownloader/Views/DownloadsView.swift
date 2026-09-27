import AppKit
import SwiftUI

struct DownloadsSection: View {
    @EnvironmentObject private var downloads: DownloadManager

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("Téléchargements")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                if !downloads.items.isEmpty {
                    Text("\(downloads.items.count)")
                        .font(.system(size: 11, weight: .bold))
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(Color.white.opacity(0.1)))
                }
                Spacer()
                if downloads.hasFinished {
                    Button("Effacer les terminés") {
                        withAnimation { downloads.clearFinished() }
                    }
                    .buttonStyle(.plain)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.secondary)
                }
            }

            if downloads.items.isEmpty {
                HStack {
                    Spacer()
                    VStack(spacing: 6) {
                        Image(systemName: "tray")
                            .font(.system(size: 22))
                            .foregroundStyle(.tertiary)
                        Text("Aucun téléchargement pour l'instant")
                            .font(.system(size: 12.5))
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 22)
                    Spacer()
                }
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(Theme.stroke, style: StrokeStyle(lineWidth: 1, dash: [5, 5]))
                )
            } else {
                VStack(spacing: 10) {
                    ForEach(downloads.items) { item in
                        DownloadRow(item: item)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }
                }
            }
        }
        .padding(.top, 6)
    }
}

struct DownloadRow: View {
    @ObservedObject var item: DownloadItem
    @EnvironmentObject private var downloads: DownloadManager

    var body: some View {
        HStack(spacing: 14) {
            Thumbnail(url: item.thumbnail, width: 112, height: 63, cornerRadius: 10)
                .overlay {
                    if item.state == .finished {
                        ZStack {
                            Color.black.opacity(0.35)
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 22))
                                .foregroundStyle(.white, .green)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }

            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Text(item.title)
                        .font(.system(size: 13.5, weight: .semibold))
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    Text(item.request.summary)
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color.white.opacity(0.07)))
                }

                switch item.state {
                case .starting, .downloading:
                    GradientProgressBar(value: item.progress)
                case .processing:
                    ProgressView()
                        .progressViewStyle(.linear)
                        .tint(Theme.accent)
                        .frame(height: 6)
                case .queued, .finished, .failed, .cancelled:
                    EmptyView()
                }

                HStack(spacing: 5) {
                    statusIcon
                    Text(item.statusText)
                        .font(.system(size: 11.5))
                        .foregroundStyle(item.state == .failed ? Color.red.opacity(0.9) : Color.secondary)
                        .lineLimit(2)
                        .textSelection(.enabled)
                }
            }

            actions
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(.ultraThinMaterial))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Theme.stroke, lineWidth: 1))
        .animation(.easeOut(duration: 0.2), value: item.state)
    }

    @ViewBuilder
    private var statusIcon: some View {
        switch item.state {
        case .queued: Image(systemName: "clock").foregroundStyle(.secondary)
        case .finished: Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
        case .failed: Image(systemName: "exclamationmark.circle.fill").foregroundStyle(.red)
        case .cancelled: Image(systemName: "slash.circle").foregroundStyle(.secondary)
        case .starting, .downloading, .processing: EmptyView()
        }
    }

    @ViewBuilder
    private var actions: some View {
        HStack(spacing: 6) {
            switch item.state {
            case .queued, .starting, .downloading, .processing:
                Button { downloads.cancel(item) } label: { Image(systemName: "xmark") }
                    .buttonStyle(IconButtonStyle())
                    .help("Annuler")
            case .finished:
                if let url = item.fileURL {
                    Button { NSWorkspace.shared.open(url) } label: { Image(systemName: "play.fill") }
                        .buttonStyle(IconButtonStyle(tint: Theme.accent))
                        .help("Ouvrir")
                    Button { NSWorkspace.shared.activateFileViewerSelecting([url]) } label: { Image(systemName: "magnifyingglass") }
                        .buttonStyle(IconButtonStyle())
                        .help("Afficher dans le Finder")
                }
                Button { withAnimation { downloads.remove(item) } } label: { Image(systemName: "xmark") }
                    .buttonStyle(IconButtonStyle())
                    .help("Retirer de la liste")
            case .failed, .cancelled:
                Button { downloads.retry(item) } label: { Image(systemName: "arrow.clockwise") }
                    .buttonStyle(IconButtonStyle(tint: Theme.accent))
                    .help("Réessayer")
                Button { withAnimation { downloads.remove(item) } } label: { Image(systemName: "trash") }
                    .buttonStyle(IconButtonStyle())
                    .help("Retirer de la liste")
            }
        }
    }
}
