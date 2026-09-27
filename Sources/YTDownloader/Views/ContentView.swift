import AppKit
import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var tools: ToolManager
    @EnvironmentObject private var downloads: DownloadManager
    @StateObject private var analyzer = Analyzer()
    @State private var showSettings = false
    @State private var dropTargeted = false

    var body: some View {
        ZStack {
            BackgroundView()
            ScrollView {
                VStack(spacing: 18) {
                    HeaderView(showSettings: $showSettings)
                    ToolsBanner()
                    URLBar(analyzer: analyzer)
                    mainCard
                    DownloadsSection()
                }
                .padding(.horizontal, 28)
                .padding(.top, 34)
                .padding(.bottom, 28)
                .frame(maxWidth: 920)
                .frame(maxWidth: .infinity)
            }
        }
        .preferredColorScheme(.dark)
        .overlay {
            if dropTargeted {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(Theme.gradient, style: StrokeStyle(lineWidth: 3, dash: [10, 8]))
                    .padding(10)
                    .allowsHitTesting(false)
            }
        }
        .dropDestination(for: URL.self) { urls, _ in
            guard let url = urls.first else { return false }
            analyzer.urlText = url.absoluteString
            analyzer.analyze(with: tools.paths)
            return true
        } isTargeted: { dropTargeted = $0 }
        .sheet(isPresented: $showSettings) {
            SettingsView(showsDoneButton: true)
                .environmentObject(tools)
                .environmentObject(downloads)
        }
    }

    @ViewBuilder
    private var mainCard: some View {
        switch analyzer.phase {
        case .idle:
            EmptyStateView()
                .transition(.opacity)
        case .loading:
            LoadingCard()
                .transition(.opacity)
        case .failed(let message):
            ErrorCard(message: message) { analyzer.analyze(with: tools.paths) }
                .transition(.opacity)
        case .loaded(let info):
            VideoCard(info: info, analyzer: analyzer)
                .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
}

// MARK: - Header

struct HeaderView: View {
    @EnvironmentObject private var tools: ToolManager
    @Binding var showSettings: Bool

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .fill(Theme.gradient)
                    .frame(width: 46, height: 46)
                    .shadow(color: Theme.accent.opacity(0.5), radius: 12, y: 4)
                Image(systemName: "arrow.down")
                    .font(.system(size: 22, weight: .heavy))
                    .foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("YT Downloader")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                Text("Vidéos avec le son, jusqu'en 8K — ou juste la musique.")
                    .font(.system(size: 12.5))
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button {
                NSWorkspace.shared.open(URL(fileURLWithPath: Prefs.currentFolder))
            } label: {
                Image(systemName: "folder")
            }
            .buttonStyle(IconButtonStyle(size: 36))
            .help("Ouvrir le dossier de téléchargement")

            Button {
                showSettings = true
            } label: {
                Image(systemName: "gearshape")
            }
            .buttonStyle(IconButtonStyle(size: 36))
            .keyboardShortcut(",", modifiers: .command)
            .help("Réglages")
        }
    }
}

// MARK: - URL bar

struct URLBar: View {
    @EnvironmentObject private var tools: ToolManager
    @ObservedObject var analyzer: Analyzer
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "link")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(focused ? AnyShapeStyle(Theme.gradient) : AnyShapeStyle(Color.secondary))
            TextField("Collez un lien YouTube…", text: $analyzer.urlText)
                .textFieldStyle(.plain)
                .font(.system(size: 15))
                .focused($focused)
                .onSubmit { analyzer.analyze(with: tools.paths) }
            if !analyzer.urlText.isEmpty {
                Button {
                    withAnimation { analyzer.reset() }
                } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help("Effacer")
            }
            Button {
                analyzer.pasteFromClipboard(andAnalyzeWith: tools.paths)
            } label: {
                Label("Coller", systemImage: "doc.on.clipboard")
                    .font(.system(size: 13, weight: .medium))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(Color.white.opacity(0.08)))
            }
            .buttonStyle(.plain)
            .disabled(!tools.isReady)
            .help("Coller le lien du presse-papiers et l'analyser")

            Button {
                analyzer.analyze(with: tools.paths)
            } label: {
                HStack(spacing: 6) {
                    if analyzer.isLoading {
                        ProgressView().controlSize(.small).tint(.white)
                    } else {
                        Image(systemName: "magnifyingglass")
                    }
                    Text("Analyser")
                }
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(!tools.isReady || analyzer.urlText.trimmingCharacters(in: .whitespaces).isEmpty || analyzer.isLoading)
        }
        .padding(.leading, 18)
        .padding(.trailing, 7)
        .padding(.vertical, 7)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.ultraThinMaterial))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(focused ? AnyShapeStyle(Theme.gradient) : AnyShapeStyle(Theme.stroke), lineWidth: focused ? 1.5 : 1)
        )
        .animation(.easeOut(duration: 0.15), value: focused)
        .onAppear { focused = true }
    }
}

// MARK: - Placeholder cards

struct EmptyStateView: View {
    var body: some View {
        VStack(spacing: 14) {
            ZStack {
                Circle().fill(Theme.accent.opacity(0.12)).frame(width: 74, height: 74)
                Image(systemName: "play.rectangle.on.rectangle.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(Theme.gradient)
            }
            Text("Collez un lien ou glissez-le dans la fenêtre")
                .font(.system(size: 15, weight: .semibold))
            Text("Choisissez ensuite la qualité : de 144p jusqu'au maximum disponible (4K, 8K, HDR, 60 fps).")
                .font(.system(size: 12.5))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            HStack(spacing: 8) {
                FeaturePill(icon: "sparkles.tv", text: "Jusqu'en 8K")
                FeaturePill(icon: "speaker.wave.2.fill", text: "Son inclus")
                FeaturePill(icon: "music.note", text: "MP3 · M4A · FLAC")
                FeaturePill(icon: "square.stack.3d.down.right", text: "File d'attente")
            }
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 34)
        .glassCard()
    }
}

struct FeaturePill: View {
    let icon: String
    let text: String

    var body: some View {
        Label(text, systemImage: icon)
            .font(.system(size: 11.5, weight: .medium))
            .foregroundStyle(.secondary)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Capsule().fill(Color.white.opacity(0.06)))
            .overlay(Capsule().strokeBorder(Theme.stroke, lineWidth: 1))
    }
}

struct LoadingCard: View {
    @State private var pulse = false

    var body: some View {
        HStack(spacing: 16) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.white.opacity(pulse ? 0.1 : 0.04))
                .frame(width: 240, height: 135)
            VStack(alignment: .leading, spacing: 10) {
                RoundedRectangle(cornerRadius: 5).fill(Color.white.opacity(pulse ? 0.1 : 0.04)).frame(height: 16)
                RoundedRectangle(cornerRadius: 5).fill(Color.white.opacity(pulse ? 0.1 : 0.04)).frame(width: 180, height: 12)
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text("Analyse de la vidéo et des qualités disponibles…")
                        .font(.system(size: 12.5))
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 6)
            }
            Spacer(minLength: 0)
        }
        .glassCard(padding: 20)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true }
        }
    }
}

struct ErrorCard: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 22))
                .foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 4) {
                Text("Impossible d'analyser ce lien")
                    .font(.system(size: 14, weight: .semibold))
                Text(message)
                    .font(.system(size: 12.5))
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }
            Spacer()
            Button("Réessayer", action: retry)
                .buttonStyle(PrimaryButtonStyle())
        }
        .glassCard()
    }
}

// MARK: - Tools banner

struct ToolsBanner: View {
    @EnvironmentObject private var tools: ToolManager

    var body: some View {
        if !tools.isReady || tools.hasFailure {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Image(systemName: "shippingbox.fill").foregroundStyle(Theme.gradient)
                    Text(tools.isReady ? "Un composant optionnel est manquant" : "Préparation des composants (première ouverture)")
                        .font(.system(size: 14, weight: .semibold))
                    Spacer()
                    if tools.hasFailure && !tools.isWorking {
                        Button("Réessayer") { Task { await tools.installMissing() } }
                            .buttonStyle(PrimaryButtonStyle())
                    }
                }
                ToolStatusRow(name: "yt-dlp", role: "Moteur de téléchargement", status: tools.ytdlp)
                ToolStatusRow(name: "FFmpeg", role: "Fusion audio + vidéo, conversion", status: tools.ffmpeg)
                ToolStatusRow(name: "Deno", role: "Requis par YouTube pour débloquer toutes les qualités", status: tools.deno)
            }
            .glassCard()
            .transition(.opacity)
        }
    }
}

struct ToolStatusRow: View {
    let name: String
    let role: String
    let status: ToolStatus

    var body: some View {
        HStack(spacing: 10) {
            Group {
                switch status {
                case .checking, .installing:
                    ProgressView().controlSize(.small)
                case .ready:
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                case .missing, .failed:
                    Image(systemName: "xmark.octagon.fill").foregroundStyle(.red)
                }
            }
            .frame(width: 18)
            VStack(alignment: .leading, spacing: 1) {
                Text(name).font(.system(size: 12.5, weight: .semibold))
                Text(status.path == nil ? status.label : role)
                    .font(.system(size: 11))
                    .foregroundStyle(status.isFailed ? Color.red.opacity(0.9) : Color.secondary)
                    .lineLimit(2)
                    .textSelection(.enabled)
            }
            Spacer()
        }
    }
}
