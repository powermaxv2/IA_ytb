import AppKit
import SwiftUI

struct SettingsView: View {
    var showsDoneButton = false

    @EnvironmentObject private var tools: ToolManager
    @Environment(\.dismiss) private var dismiss

    @AppStorage(Prefs.folder) private var folder = Prefs.defaultFolder
    @AppStorage(Prefs.embedThumbnail) private var embedThumbnail = true
    @AppStorage(Prefs.embedMetadata) private var embedMetadata = true
    @AppStorage(Prefs.embedChapters) private var embedChapters = true
    @AppStorage(Prefs.subtitles) private var subtitles = false
    @AppStorage(Prefs.subtitleLangs) private var subtitleLangs = "fr,en"
    @AppStorage(Prefs.notify) private var notify = true
    @AppStorage(Prefs.maxConcurrent) private var maxConcurrent = 3
    @AppStorage(Prefs.cookiesBrowser) private var cookiesBrowser: CookieBrowser = .off

    var body: some View {
        ScrollView {
            content
        }
        .frame(width: 580, height: 640)
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Réglages")
                .font(.system(size: 22, weight: .bold, design: .rounded))

            GroupBox {
                HStack(spacing: 10) {
                    Image(systemName: "folder.fill").foregroundStyle(Theme.gradient)
                    Text((folder as NSString).abbreviatingWithTildeInPath)
                        .lineLimit(1)
                        .truncationMode(.middle)
                    Spacer()
                    Button("Choisir…", action: chooseFolder)
                }
                .padding(6)
            } label: {
                Label("Dossier de destination", systemImage: "arrow.down.to.line")
            }

            GroupBox {
                VStack(alignment: .leading, spacing: 8) {
                    Toggle("Intégrer la miniature (pochette)", isOn: $embedThumbnail)
                    Toggle("Intégrer les métadonnées (titre, chaîne, date…)", isOn: $embedMetadata)
                    Toggle("Intégrer les chapitres", isOn: $embedChapters)
                    HStack {
                        Toggle("Sous-titres intégrés", isOn: $subtitles)
                        Spacer()
                        TextField("fr,en", text: $subtitleLangs)
                            .frame(width: 110)
                            .disabled(!subtitles)
                            .help("Codes de langue séparés par des virgules")
                    }
                    Toggle("Notification à la fin d'un téléchargement", isOn: $notify)
                    Stepper("Téléchargements simultanés : \(maxConcurrent)", value: $maxConcurrent, in: 1...6)
                    Divider()
                    Picker("Cookies du navigateur", selection: $cookiesBrowser) {
                        ForEach(CookieBrowser.allCases) { Text($0.title).tag($0) }
                    }
                    .pickerStyle(.menu)
                    .frame(maxWidth: 320)
                    Text("Utile pour les vidéos avec limite d'âge ou si YouTube demande une vérification « not a bot » : l'app utilise votre session YouTube du navigateur choisi. Pour Safari, autorisez « Accès complet au disque » à YT Downloader (Réglages Système → Confidentialité).")
                        .font(.system(size: 11))
                        .foregroundStyle(.tertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(6)
                .frame(maxWidth: .infinity, alignment: .leading)
            } label: {
                Label("Options", systemImage: "slider.horizontal.3")
            }

            GroupBox {
                VStack(alignment: .leading, spacing: 10) {
                    ToolStatusRow(name: "yt-dlp \(tools.ytdlpVersion ?? "")", role: tools.ytdlp.label, status: tools.ytdlp)
                    ToolStatusRow(name: "FFmpeg", role: tools.ffmpeg.label, status: tools.ffmpeg)
                    ToolStatusRow(name: "Deno", role: tools.deno.label, status: tools.deno)
                    HStack {
                        Button {
                            Task { await tools.updateYTDLP() }
                        } label: {
                            if tools.isUpdating {
                                ProgressView().controlSize(.small)
                            } else {
                                Label("Mettre à jour yt-dlp", systemImage: "arrow.triangle.2.circlepath")
                            }
                        }
                        .disabled(tools.isUpdating || tools.isWorking)

                        Button("Réinstaller les outils") {
                            Task { await tools.reinstallAll() }
                        }
                        .disabled(tools.isUpdating || tools.isWorking)

                        Spacer()
                        Button("Ouvrir le dossier") {
                            NSWorkspace.shared.open(AppPaths.bin)
                        }
                    }
                    if let message = tools.updateMessage {
                        Text(message)
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)
                    }
                    Text("Si un téléchargement échoue soudainement, mettez à jour yt-dlp : YouTube change régulièrement son fonctionnement.")
                        .font(.system(size: 11))
                        .foregroundStyle(.tertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(6)
                .frame(maxWidth: .infinity, alignment: .leading)
            } label: {
                Label("Composants", systemImage: "shippingbox")
            }

            SecuritySettingsSection()

            UpdateSettingsSection()

            if showsDoneButton {
                HStack {
                    Spacer()
                    Button("Terminé") { dismiss() }
                        .keyboardShortcut(.defaultAction)
                }
            }
        }
        .padding(24)
    }

    private func chooseFolder() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        panel.canCreateDirectories = true
        panel.allowsMultipleSelection = false
        panel.prompt = "Choisir"
        panel.directoryURL = URL(fileURLWithPath: folder)
        if panel.runModal() == .OK, let url = panel.url {
            folder = url.path
        }
    }
}
