import Foundation

enum YTDLPError: LocalizedError {
    case failed(String)
    case invalidResponse
    case noFormats

    var errorDescription: String? {
        switch self {
        case .failed(let message): return message
        case .invalidResponse: return "Réponse inattendue de yt-dlp. Essayez de mettre à jour yt-dlp dans les réglages."
        case .noFormats: return "Aucune vidéo téléchargeable trouvée (les playlists et directs ne sont pas pris en charge)."
        }
    }
}

enum YTDLP {
    static func fetchInfo(url: String, tools: ToolPaths) async throws -> VideoInfo {
        var args = ["-J", "--no-playlist", "--no-warnings"]
        if let ffmpeg = tools.ffmpeg { args += ["--ffmpeg-location", ffmpeg] }
        args += ["--", url]

        let result = try await Shell.run(URL(fileURLWithPath: tools.ytdlp), args, environment: tools.environment)
        guard result.status == 0 else {
            throw YTDLPError.failed(friendlyError(from: result.stderrString.components(separatedBy: .newlines)))
        }
        let info: VideoInfo
        do {
            info = try JSONDecoder().decode(VideoInfo.self, from: result.stdout)
        } catch {
            throw YTDLPError.invalidResponse
        }
        if info.type == "playlist" || info.formats.isEmpty { throw YTDLPError.noFormats }
        return info
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
        if lower.contains("confirm your age") { return "Vidéo soumise à une limite d'âge (connexion YouTube requise)." }
        if lower.contains("not a bot") { return "YouTube demande une vérification anti-robot. Réessayez plus tard ou mettez à jour yt-dlp." }
        if lower.contains("video unavailable") { return "Vidéo indisponible." }
        if lower.contains("members-only") || lower.contains("join this channel") { return "Vidéo réservée aux membres de la chaîne." }
        if lower.contains("http error 403") { return "Accès refusé par YouTube (403). Mettez à jour yt-dlp dans les réglages." }
        if lower.contains("unable to download") && lower.contains("urlopen") { return "Connexion impossible. Vérifiez votre réseau." }
        if lower.contains("ffmpeg") && lower.contains("not found") { return "FFmpeg est introuvable. Réinstallez les outils dans les réglages." }
        return message
    }
}
