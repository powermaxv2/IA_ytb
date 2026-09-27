import SwiftUI

/// Red banner shown when an installed tool was modified after installation.
struct SecurityAlertBanner: View {
    @EnvironmentObject private var tools: ToolManager

    var body: some View {
        if let alert = tools.securityAlert {
            HStack(spacing: 12) {
                Image(systemName: "exclamationmark.shield.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(.red)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Alerte de sécurité")
                        .font(.system(size: 13, weight: .semibold))
                    Text(alert)
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer()
                Button { withAnimation { tools.securityAlert = nil } } label: { Image(systemName: "xmark") }
                    .buttonStyle(IconButtonStyle(size: 28))
                    .help("Fermer")
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 14)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.red.opacity(0.12)))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.red.opacity(0.4), lineWidth: 1))
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

/// "Sécurité" section of the settings.
struct SecuritySettingsSection: View {
    @EnvironmentObject private var tools: ToolManager
    @State private var checking = false

    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 10) {
                trustRow("yt-dlp", tools.trust["yt-dlp"] ?? .unknown)
                trustRow("FFmpeg", tools.trust["ffmpeg"] ?? .unknown)
                trustRow("Deno", tools.trust["deno"] ?? .unknown)

                Divider()

                VStack(alignment: .leading, spacing: 5) {
                    protection("Outils vérifiés par empreinte SHA-256 à l'installation, puis contrôlés à chaque lancement : un fichier modifié est supprimé et réinstallé.")
                    protection("yt-dlp ignore tout fichier de configuration et tout plugin externe ; environnement des processus nettoyé.")
                    protection("Seuls les liens web (https://…) sont acceptés.")
                    protection("App signée avec le « runtime renforcé » d'Apple (pas d'injection de code).")
                    protection("Mises à jour : code d'un commit GitHub précis, en HTTPS, jamais de retour à une version antérieure.")
                }

                HStack {
                    Button {
                        checking = true
                        Task {
                            await tools.verifyNow()
                            checking = false
                        }
                    } label: {
                        if checking {
                            ProgressView().controlSize(.small)
                        } else {
                            Label("Vérifier les fichiers maintenant", systemImage: "checkmark.shield")
                        }
                    }
                    .disabled(checking || tools.isWorking)
                    Spacer()
                }
            }
            .padding(6)
            .frame(maxWidth: .infinity, alignment: .leading)
        } label: {
            Label("Sécurité", systemImage: "lock.shield")
        }
    }

    private func trustRow(_ name: String, _ trust: ToolTrust) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Group {
                switch trust {
                case .verified:
                    Image(systemName: "checkmark.shield.fill").foregroundStyle(.green)
                case .unverified:
                    Image(systemName: "exclamationmark.shield.fill").foregroundStyle(.orange)
                case .external:
                    Image(systemName: "shield.lefthalf.filled").foregroundStyle(.secondary)
                case .unknown:
                    Image(systemName: "shield").foregroundStyle(.tertiary)
                }
            }
            .frame(width: 18)
            VStack(alignment: .leading, spacing: 1) {
                Text(name).font(.system(size: 12.5, weight: .semibold))
                Text(trust.label)
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .textSelection(.enabled)
            }
        }
    }

    private func protection(_ text: String) -> some View {
        Label {
            Text(text)
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: "checkmark")
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(.green)
        }
    }
}
