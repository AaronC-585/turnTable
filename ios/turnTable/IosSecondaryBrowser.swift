import UIKit

/// Browsers shown in **Settings → Open secondary links in** on iOS only.
/// Cross-platform entries mirror [KnownBrowsers] on Android (excluding `androidOnly` and Samsung — no iOS app).
/// App Store numeric ids are kept in sync with `docs/browser-store-ids.md`.
/// iOS-only entries exist only here.
enum IosSecondaryBrowser: String, CaseIterable {
    case systemDefault = "default"
    case chrome = "chrome"
    case firefox = "firefox"
    case firefoxFocus = "firefox_focus"
    case firefoxKlar = "firefox_klar"
    case edge = "edge"
    case opera = "opera"
    case operaMini = "opera_mini"
    case operaGX = "opera_gx"
    case brave = "brave"
    case vivaldi = "vivaldi"
    case duckduckgo = "duckduckgo"
    case ucBrowser = "uc_browser"
    case tor = "tor"
    /// iOS-only (not in Android list)
    case arc = "arc"
    /// iOS-only — Orion Browser (Kagi)
    case orion = "orion"

    /// Order in the settings picker (Default first, then A–Z).
    static var settingsPickerOrder: [IosSecondaryBrowser] {
        let rest = IosSecondaryBrowser.allCases.filter { $0 != .systemDefault }
            .sorted { $0.displayTitle.localizedCaseInsensitiveCompare($1.displayTitle) == .orderedAscending }
        return [.systemDefault] + rest
    }

    var displayTitle: String {
        switch self {
        case .systemDefault: return "Default (Safari)"
        case .chrome: return "Google Chrome"
        case .firefox: return "Firefox"
        case .firefoxFocus: return "Firefox Focus"
        case .firefoxKlar: return "Firefox Klar"
        case .edge: return "Microsoft Edge"
        case .opera: return "Opera"
        case .operaMini: return "Opera Mini"
        case .operaGX: return "Opera GX"
        case .brave: return "Brave"
        case .vivaldi: return "Vivaldi"
        case .duckduckgo: return "DuckDuckGo"
        case .ucBrowser: return "UC Browser"
        case .tor: return "Tor Browser"
        case .arc: return "Arc Search"
        case .orion: return "Orion Browser"
        }
    }

    /// App Store numeric id when the app is not installed (opens product page). Verified against `apps.apple.com`.
    var appStoreId: String? {
        switch self {
        case .systemDefault: return nil
        case .chrome: return "535886823"
        case .firefox: return "989804926"
        case .firefoxFocus: return "1055677337"
        case .firefoxKlar: return "1073435754"
        case .edge: return "1288723196"
        case .opera, .operaMini: return "1411869974"
        case .operaGX: return "1559740799"
        case .brave: return "1052879175"
        case .vivaldi: return "1633234600"
        case .duckduckgo: return "663592361"
        case .ucBrowser: return "1048518592"
        case .tor: return "519296448" // Onion Browser (Tor-capable); see docs
        case .arc: return "6472513080"
        case .orion: return "1484498200"
        }
    }

    /// URL to hand to `UIApplication.open`. If `nil`, use `original` (system default).
    func urlToOpen(original: URL) -> URL? {
        guard self != .systemDefault else { return original }
        let s = original.absoluteString
        switch self {
        case .chrome:
            if s.hasPrefix("https://") {
                let rest = String(s.dropFirst("https://".count))
                return URL(string: "googlechromes://\(rest)")
            }
            if s.hasPrefix("http://") {
                let rest = String(s.dropFirst("http://".count))
                return URL(string: "googlechrome://\(rest)")
            }
            return original
        case .firefox, .firefoxFocus, .firefoxKlar:
            guard let enc = s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return original }
            return URL(string: "firefox://open-url?url=\(enc)")
        case .edge:
            if s.hasPrefix("https://") {
                return URL(string: s.replacingOccurrences(of: "https://", with: "microsoft-edge-https://"))
            }
            if s.hasPrefix("http://") {
                return URL(string: s.replacingOccurrences(of: "http://", with: "microsoft-edge-http://"))
            }
            return original
        case .brave:
            guard let enc = s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return original }
            return URL(string: "brave://open-url?url=\(enc)")
        case .opera, .operaMini:
            guard let enc = s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return original }
            return URL(string: "opera://open-url?url=\(enc)")
        case .operaGX:
            guard let enc = s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return original }
            return URL(string: "opera-gx://open-url?url=\(enc)")
        case .vivaldi:
            guard let enc = s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return original }
            return URL(string: "vivaldi://open-url?url=\(enc)")
        case .duckduckgo, .ucBrowser, .tor, .arc, .orion:
            // No stable public URL scheme documented for all builds — open the HTTPS URL (often lands in Safari).
            return original
        case .systemDefault:
            return original
        }
    }
}

enum SecondaryBrowserOpener {

    static func open(url: URL, browser: IosSecondaryBrowser, completion: @escaping (Bool) -> Void) {
        if browser == .systemDefault {
            UIApplication.shared.open(url, options: [:], completionHandler: completion)
            return
        }
        let target = browser.urlToOpen(original: url) ?? url
        let sch = target.scheme?.lowercased() ?? ""
        // `canOpenURL` is unreliable for http(s); always try system open for web URLs.
        if sch == "http" || sch == "https" {
            UIApplication.shared.open(target, options: [:], completionHandler: completion)
            return
        }
        if UIApplication.shared.canOpenURL(target) {
            UIApplication.shared.open(target, options: [:], completionHandler: completion)
            return
        }
        if let id = browser.appStoreId,
           let store = URL(string: "https://apps.apple.com/app/id\(id)") {
            UIApplication.shared.open(store, options: [:], completionHandler: completion)
            return
        }
        UIApplication.shared.open(url, options: [:], completionHandler: completion)
    }
}

extension SearchPrefs {
    /// Parsed iOS secondary browser (stored under `secondary_browser_package` as a slug, not an Android package name).
    var iosSecondaryBrowser: IosSecondaryBrowser {
        guard let s = secondaryBrowserPackage?.trimmingCharacters(in: .whitespacesAndNewlines), !s.isEmpty else {
            return .systemDefault
        }
        return IosSecondaryBrowser(rawValue: s) ?? .systemDefault
    }
}
