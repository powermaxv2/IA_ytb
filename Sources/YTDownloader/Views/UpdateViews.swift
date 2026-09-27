import SwiftUI

/// Banner shown at the top of the window while checking, when an update is available,
/// while it installs, or when something went wrong.
struct UpdateBanner: View {
    @EnvironmentObject private var updater: Updater
    @EnvironmentObject private var downloads: DownloadManager
    @State private var confirmInstall = false

    var body: some View {
        switch updater.state {
        case .idle:
            EmptyView()
        case .checking:
            banner(icon: "arrow.triangle.2.circlepath", title: "Recherche de mise à jour…", spinner: true)
        case .upToDate:
            banner(icon: "checkmark.seal.fill", title: "YT Downloader est à jour (version \(updater.currentVersion)).") {
                closeButton
            }
        case .available(let version):
            banner(
                icon: "sparkles",
                title: "Nouvelle version \(version) disponible",
                subtitle: updater.notes ?? "Vous avez la version \(updater.currentVersion)."
            ) {
                Button("Plus tard") { withAnimation { updater.dismiss() } }
                    .buttonStyle(.plain)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.secondary)
                Button {
                    if downloads.activeCount > 0 {
                        confirmInstall = true
                    } else {
                        updater.install()
                    }
                } label: {
                    Label("Mettre à jour", systemImage: "arrow.down.app.fill")
                }
                .buttonStyle(PrimaryButtonStyle())
            }
            .confirmationDialog(
                "Des téléchargements sont en cours",
                isPresented: $confirmInstall
            ) {
                Button("Mettre à jour quand même", role: .destructive) { updater.install() }
                Button("Annuler", role: .cancel) {}
            } message: {
                Text("L'app redémarrera à la fin de la mise à jour et les téléchargements en cours seront interrompus.")
            }
        case .working(let step):
            banner(
                icon: "hammer.fill",
                title: "Mise à jour en cours — \(step)",
                subtitle: "L'app redémarrera automatiquement. Vous pouvez continuer à l'utiliser en attendant.",
                spinner: true
            )
        case .failed(let message):
            banner(icon: "exclamationmark.triangle.fill", title: "La mise à jour n'a pas abouti", subtitle: message, tint: .orange) {
                if !updater.log.isEmpty {
                    Button("Copier le journal") { updater.copyLog() }
                        .buttonStyle(.plain)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.secondary)
                }
                Button("Réessayer") {
                    if updater.isUpdateAvailable {
                        updater.install()
                    } else {
                        Task { await updater.check() }
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                closeButton
            }
        }
    }

    private var closeButton: some View {
        Button { withAnimation { updater.dismiss() } } label: { Image(systemName: "xmark") }
            .buttonStyle(IconButtonStyle(size: 28))
            .help("Fermer")
    }

    private func banner(
        icon: String,
        title: String,
        subtitle: String? = nil,
        spinner: Bool = false,
        tint: Color = Theme.accent
    ) -> some View {
        banner(icon: icon, title: title, subtitle: subtitle, spinner: spinner, tint: tint) { EmptyView() }
    }

    private func banner<Actions: View>(
        icon: String,
        title: String,
        subtitle: String? = nil,
        spinner: Bool = false,
        tint: Color = Theme.accent,
        @ViewBuilder actions: () -> Actions
    ) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .textSelection(.enabled)
                }
                if spinner {
                    ProgressView()
                        .progressViewStyle(.linear)
                        .tint(tint)
                        .padding(.top, 2)
                }
            }
            Spacer(minLength: 8)
            actions()
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 14)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(tint.opacity(0.12)))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(tint.opacity(0.35), lineWidth: 1))
        .transition(.move(edge: .top).combined(with: .opacity))
    }
}

/// "Mises à jour" section of the settings.
struct UpdateSettingsSection: View {
    @EnvironmentObject private var updater: Updater
    @AppStorage(Prefs.autoUpdateCheck) private var autoCheck = true

    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Version installée : \(updater.currentVersion)")
                        .font(.system(size: 13, weight: .semibold))
                    Spacer()
                    Text(statusText)
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                }
                Toggle("Rechercher les mises à jour au lancement", isOn: $autoCheck)
                HStack {
                    Button {
                        Task { await updater.check() }
                    } label: {
                        Label("Rechercher maintenant", systemImage: "arrow.triangle.2.circlepath")
                    }
                    .disabled(updater.isWorking || updater.state == .checking)

                    if case .available(let version) = updater.state {
                        Button("Installer la version \(version)") { updater.install() }
                            .keyboardShortcut(.defaultAction)
                    }
                    Spacer()
                }
                Text("Les mises à jour viennent de github.com/\(AppInfo.repo). L'app télécharge le code, le compile sur ce Mac puis redémarre (1 à 3 minutes).")
                    .font(.system(size: 11))
                    .foregroundStyle(.tertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(6)
            .frame(maxWidth: .infinity, alignment: .leading)
        } label: {
            Label("Mises à jour", systemImage: "arrow.down.app")
        }
    }

    private var statusText: String {
        switch updater.state {
        case .idle:
            if let latest = updater.latestVersion, !updater.isUpdateAvailable { return "À jour (\(latest))" }
            return ""
        case .checking: return "Recherche…"
        case .upToDate: return "À jour"
        case .available(let version): return "Version \(version) disponible"
        case .working(let step): return step
        case .failed: return "Échec"
        }
    }
}
