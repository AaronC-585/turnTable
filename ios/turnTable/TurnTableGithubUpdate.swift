import Foundation
import UIKit

/// Fetches **`CurrentVersion.json`** only (no GitHub Releases API for discovery). Same contract as Android [GithubAppUpdateChecker].
/// Keep manifest URL and owner/repo in sync with `android/app/src/main/res/values/github_update.xml`.
enum TurnTableGithubUpdate {

    // MARK: - Config (mirrors github_update.xml)

    /// When non-empty, used as the manifest URL. If empty, URL is built from owner/repo/ref.
    private static let manifestURLString = "https://raw.githubusercontent.com/AaronC-585/turnTable/refs/heads/main/CurrentVersion.json"
    private static let githubOwner = "AaronC-585"
    private static let githubRepo = "turnTable"
    private static let githubRef = "main"

    static var resolvedManifestURL: URL? {
        let direct = manifestURLString.trimmingCharacters(in: .whitespacesAndNewlines)
        if !direct.isEmpty { return URL(string: direct) }
        let o = githubOwner.trimmingCharacters(in: .whitespacesAndNewlines)
        let r = githubRepo.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !o.isEmpty, !r.isEmpty else { return nil }
        let ref = githubRef.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty ?? "main"
        return URL(string: "https://raw.githubusercontent.com/\(o)/\(r)/\(ref)/CurrentVersion.json")
    }

    static var isConfigured: Bool {
        if !manifestURLString.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return true }
        let o = githubOwner.trimmingCharacters(in: .whitespacesAndNewlines)
        let r = githubRepo.trimmingCharacters(in: .whitespacesAndNewlines)
        return !o.isEmpty && !r.isEmpty
    }

    // MARK: - Release payload

    struct ReleaseInfo {
        /// Semantic/compare tag, e.g. `v2026.3.28.116` (from `currentTag` or derived from `version`).
        var tagNameRaw: String
        var versionPlain: String
        var title: String?
        var body: String?
        var htmlUrl: String
        var iosZipUrl: String?
    }

    static func displayVersion(from info: ReleaseInfo) -> String {
        let t = info.tagNameRaw.trimmingCharacters(in: .whitespacesAndNewlines)
        if !t.isEmpty { return t }
        return info.versionPlain
    }

    static func localVersionString() -> String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    /// Strips leading `v`/`V` (Android [DottedVersionCompare.normalizedForCompare]).
    static func normalizedTag(_ raw: String) -> String {
        var t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while t.first == "v" || t.first == "V" {
            t.removeFirst()
        }
        return t.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Dotted numeric compare aligned with Android [DottedVersionCompare.compare].
    static func compareVersions(_ a: String, _ b: String) -> Int {
        func parseParts(_ version: String) -> [Int] {
            let cleaned = normalizedTag(version)
            guard !cleaned.isEmpty else { return [] }
            return cleaned.split(separator: ".").map { seg in
                String(seg.prefix { $0.isNumber }).toIntOrZero()
            }
        }
        let pa = parseParts(a)
        let pb = parseParts(b)
        let n = max(pa.count, pb.count)
        for i in 0..<n {
            let va = i < pa.count ? pa[i] : 0
            let vb = i < pb.count ? pb[i] : 0
            if va != vb { return va < vb ? -1 : 1 }
        }
        return 0
    }

    static func isRemoteNewer(than localVersionName: String, remoteTag: String) -> Bool {
        let remoteNorm = normalizedTag(remoteTag)
        let localNorm = normalizedTag(localVersionName)
        if remoteNorm.isEmpty || localNorm.isEmpty { return false }
        if !remoteNorm.contains(where: { $0.isNumber }) { return false }
        return compareVersions(remoteTag, localVersionName) > 0
    }

    static func plainTextPreview(_ raw: String, maxLength: Int) -> String {
        var s = raw
        if let re = try? NSRegularExpression(pattern: "<[^>]+>", options: []) {
            let r = NSRange(s.startIndex..<s.endIndex, in: s)
            s = re.stringByReplacingMatches(in: s, options: [], range: r, withTemplate: " ")
        }
        s = s.replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
        let t = s.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        if t.count <= maxLength { return t }
        let idx = t.index(t.startIndex, offsetBy: maxLength)
        return String(t[..<idx]) + "…"
    }

    // MARK: - Network

    static func fetchReleaseInfo(completion: @escaping (Result<ReleaseInfo, Error>) -> Void) {
        guard let url = resolvedManifestURL else {
            completion(.failure(NSError(domain: "TurnTableGithubUpdate", code: 10, userInfo: [NSLocalizedDescriptionKey: "Manifest URL not configured"])))
            return
        }
        var req = URLRequest(url: url)
        req.setValue("turnTable/1.0 (iOS)", forHTTPHeaderField: "User-Agent")
        req.timeoutInterval = 25
        URLSession.shared.dataTask(with: req) { data, resp, err in
            if let err = err {
                DispatchQueue.main.async { completion(.failure(err)) }
                return
            }
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
            guard let data = data, !data.isEmpty else {
                DispatchQueue.main.async {
                    completion(.failure(NSError(domain: "TurnTableGithubUpdate", code: 11, userInfo: [NSLocalizedDescriptionKey: "Empty manifest (HTTP \(code))"])))
                }
                return
            }
            guard (200...299).contains(code) else {
                let snippet = String(data: data.prefix(120), encoding: .utf8) ?? ""
                DispatchQueue.main.async {
                    completion(.failure(NSError(domain: "TurnTableGithubUpdate", code: 12, userInfo: [NSLocalizedDescriptionKey: "HTTP \(code): \(snippet)"])))
                }
                return
            }
            do {
                let info = try parseManifest(data: data)
                DispatchQueue.main.async { completion(.success(info)) }
            } catch {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }.resume()
    }

    private static func parseManifest(data: Data) throws -> ReleaseInfo {
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard let root = obj else {
            throw NSError(domain: "TurnTableGithubUpdate", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"])
        }
        let versionPlain = ((root["version"] as? String) ?? (root["versionName"] as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !versionPlain.isEmpty else {
            throw NSError(domain: "TurnTableGithubUpdate", code: 3, userInfo: [NSLocalizedDescriptionKey: "Missing version"])
        }
        let norm = normalizedTag(versionPlain)
        if norm.isEmpty || !norm.contains(where: { $0.isNumber }) {
            throw NSError(domain: "TurnTableGithubUpdate", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid version"])
        }

        let tagFromFile = (root["currentTag"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let tagNameRaw: String = {
            if !tagFromFile.isEmpty { return tagFromFile }
            let t = versionPlain.trimmingCharacters(in: .whitespacesAndNewlines).drop(while: { $0 == "V" || $0 == "v" })
            let s = String(t)
            return s.hasPrefix("v") ? s : "v\(s)"
        }()

        var htmlUrl = (root["releasePageUrl"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if htmlUrl.isEmpty {
            let o = githubOwner.trimmingCharacters(in: .whitespacesAndNewlines)
            let r = githubRepo.trimmingCharacters(in: .whitespacesAndNewlines)
            if !o.isEmpty, !r.isEmpty {
                htmlUrl = "https://github.com/\(o)/\(r)/releases/latest"
            }
        }
        if htmlUrl.isEmpty {
            throw NSError(domain: "TurnTableGithubUpdate", code: 4, userInfo: [NSLocalizedDescriptionKey: "Missing releasePageUrl"])
        }

        var iosZip: String?
        if let assets = root["assets"] as? [String: Any] {
            if let z = assets["iosZip"] as? String, !z.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                iosZip = z.trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }

        let title = (root["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        let body = (root["body"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty

        return ReleaseInfo(
            tagNameRaw: tagNameRaw,
            versionPlain: versionPlain,
            title: title,
            body: body,
            htmlUrl: htmlUrl,
            iosZipUrl: iosZip,
        )
    }

    /// Manual check from UI (delegates to [TurnTableUpdateCoordinator.presentManualCheck] preferred).
    static func presentCheck(from viewController: UIViewController) {
        TurnTableUpdateCoordinator.presentManualCheck(from: viewController)
    }
}

private extension String {
    func toIntOrZero() -> Int {
        Int(self) ?? 0
    }

    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
