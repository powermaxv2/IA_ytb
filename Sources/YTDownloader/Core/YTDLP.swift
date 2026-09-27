import Foundation

enum YTDLPError: LocalizedError {
    case failed(String)
    case invalidResponse
    case noFormats

    var errorDescription: String? {
        switch self {
        case .failed(let message): return message
        case .invalidResponse: return "Réponse inattendue de yt-dlp. Essayez de mettre à jour yt-dlp dans les réglages."
        case .noFormats: return "Aucune vidéo téléchargeable trouvée à ce lien (les directs en cours ne sont pas pris en charge)."
        }
    }
}

enum YTDLP {
    /// Analyses a link: a single video (with all its formats) or a playlist (flat list of videos).
    static func fetch(url: String, tools: ToolPaths) async throws -> MediaInfo {
        var args = tools.commonArguments + ["-J", "--no-playlist", "--flat-playlist", "--no-warnings"]
        if let ffmpeg = tools.ffmpeg { args += ["--ffmpeg-location", ffmpeg] }
        args += ["--", url]

        let result = try await Shell.run(URL(fileURLWithPath: tools.ytdlp), args, environment: tools.environment)
        guard result.status == 0 else {
            throw YTDLPError.failed(friendlyError(from: result.stderrString.components(separatedBy: .newlines)))
        }

        let decoder = JSONDecoder()
        let probe = try? decoder.decode(TypeProbe.self, from: result.stdout)
        if probe?.type == "playlist" {
            guard let playlist = try? decoder.decode(PlaylistInfo.self, from: result.stdout) else {
                throw YTDLPError.invalidResponse
            }
            guard !playlist.entries.isEmpty else { throw YTDLPError.noFormats }
            return .playlist(playlist)
        }

        guard let info = try? decoder.decode(VideoInfo.self, from: result.stdout) else {
            throw YTDLPError.invalidResponse
        }
        guard !info.formats.isEmpty else { throw YTDLPError.noFormats }
        return .video(info)
    }

    private struct TypeProbe: Decodable {
        let type: String?
        enum CodingKeys: String, CodingKey { case type = "_type" }
    }

    /// Turns yt-dlp stderr output into a short, readable French message.
    static func friendlyError(from lines: [String]) -> String {
        let cleaned = lines.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        guard let raw = cleaned.last(where: { $0.hasPrefix("ERROR:") }) ?? cleaned.last else {
            return "Erreur inconnue."
        }
        var message = raw.replacingOccurrences(of: "ERROR: ", with: "")
        if let range = message.range(of: #"^\[[^\]]+\] [^:]+: "#, options: .regularExpression) {
            message.removeSubrange(range)
        }
        let lower = message.lowercased()
        if lower.contains("unsupported url") || lower.contains("is not a valid url") { return "Ce lien n'est pas pris en charge." }
        if lower.contains("private video") { return "Cette vidéo est privée." }
        if lower.contains("confirm your age") { return "Vidéo soumise à une limite d'âge : activez « Cookies du navigateur » dans les réglages (en étant connecté à YouTube)." }
        if lower.contains("not a bot") { return "YouTube demande une vérification anti-robot : mettez à jour yt-dlp ou activez « Cookies du navigateur » dans les réglages." }
        if lower.contains("could not copy") && lower.contains("cookie") { return "Impossible de lire les cookies du navigateur. Fermez le navigateur ou choisissez-en un autre dans les réglages." }
        if lower.contains("operation not permitted") && lower.contains("cookies") { return "Accès aux cookies Safari refusé : autorisez « Accès complet au disque » pour YT Downloader, ou choisissez un autre navigateur." }
        if lower.contains("video unavailable") { return "Vidéo indisponible." }
        if lower.contains("members-only") || lower.contains("join this channel") { return "Vidéo réservée aux membres de la chaîne." }
        if lower.contains("http error 403") { return "Accès refusé par YouTube (403). Mettez à jour yt-dlp dans les réglages." }
        if lower.contains("unable to download") && lower.contains("urlopen") { return "Connexion impossible. Vérifiez votre réseau." }
        if lower.contains("ffmpeg") && lower.contains("not found") { return "FFmpeg est introuvable. Réinstallez les outils dans les réglages." }
        return message
    }
}
