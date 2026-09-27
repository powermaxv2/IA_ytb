import Foundation
#if canImport(CryptoKit)
import CryptoKit
#endif

/// SHA-256 helpers used to verify downloaded tools and detect later tampering.
enum Integrity {
    static func sha256(of url: URL) throws -> String {
        #if canImport(CryptoKit)
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hasher = SHA256()
        while let chunk = try handle.read(upToCount: 1 << 20), !chunk.isEmpty {
            hasher.update(data: chunk)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
        #else
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/sha256sum")
        process.arguments = [url.path]
        let pipe = Pipe()
        process.standardOutput = pipe
        try process.run()
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        process.waitUntilExit()
        return String(String(decoding: data, as: UTF8.self).prefix(64))
        #endif
    }

    /// Hashes a file off the main thread.
    static func sha256Async(of url: URL) async throws -> String {
        try await Task.detached(priority: .utility) { try sha256(of: url) }.value
    }

    /// Extracts the hash for `fileName` from a checksum file ("<hex>  name" lines, or a bare hash).
    static func checksum(in text: String, for fileName: String?) -> String? {
        for line in text.components(separatedBy: .newlines) {
            let parts = line.split(whereSeparator: { $0 == " " || $0 == "\t" }).map(String.init)
            guard let hash = parts.first?.lowercased(), isSHA256(hash) else { continue }
            guard let fileName else { return hash }
            let name = parts.dropFirst().first.map { $0.hasPrefix("*") ? String($0.dropFirst()) : $0 }
            if name == nil || name == fileName { return hash }
        }
        return nil
    }

    static func isSHA256(_ value: String) -> Bool {
        value.count == 64 && value.allSatisfy { $0.isHexDigit }
    }
}

enum IntegrityError: LocalizedError {
    case mismatch(String)
    case missingChecksum(String)

    var errorDescription: String? {
        switch self {
        case .mismatch(let name):
            return "l'empreinte de \(name) ne correspond pas à l'empreinte officielle attendue : fichier refusé"
        case .missingChecksum(let name):
            return "empreinte officielle de \(name) introuvable : fichier refusé"
        }
    }
}

/// Record of the tools installed by the app, with their SHA-256, used to detect tampering.
struct ToolManifest: Codable {
    struct Entry: Codable, Equatable {
        var sha256: String
        var source: String
        /// true when the download matched an official checksum.
        var verified: Bool
        var installedAt: Date
    }

    var entries: [String: Entry] = [:]

    static var url: URL { AppPaths.support.appendingPathComponent("manifest.json") }

    static func load() -> ToolManifest {
        guard let data = try? Data(contentsOf: url),
              let manifest = try? JSONDecoder().decode(ToolManifest.self, from: data)
        else { return ToolManifest() }
        return manifest
    }

    func save() {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(self) else { return }
        try? data.write(to: Self.url, options: .atomic)
        try? FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: Self.url.path)
    }
}

/// What the security panel shows for each tool.
enum ToolTrust: Equatable {
    case unknown
    /// Downloaded by the app and matching the official checksum.
    case verified(source: String)
    /// Downloaded by the app, no official checksum available (HTTPS only).
    case unverified(source: String)
    /// Provided by the system / Homebrew, not managed by the app.
    case external(path: String)

    var label: String {
        switch self {
        case .unknown: return "—"
        case .verified(let source): return "Empreinte SHA-256 vérifiée · \(source)"
        case .unverified(let source): return "Téléchargé en HTTPS, sans empreinte officielle · \(source)"
        case .external(let path): return "Installé hors de l'app · \(path)"
        }
    }

    var isVerified: Bool {
        if case .verified = self { return true }
        return false
    }
}
