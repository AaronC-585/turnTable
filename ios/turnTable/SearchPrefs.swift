import Foundation

/// Mirrors Android `SearchPrefs` — uses suite `search_prefs` like SharedPreferences name.
final class SearchPrefs {
    private let d: UserDefaults = UserDefaults(suiteName: Keys.prefsName) ?? .standard

    var primaryApiListText: String? {
        get { str(Key.primaryApiList) }
        set { d.set(newValue, forKey: Key.primaryApiList) }
    }
    var secondaryListText: String? {
        get { str(Key.secondaryListText) }
        set { d.set(newValue, forKey: Key.secondaryListText) }
    }
    var method: String {
        get { d.string(forKey: Key.method) ?? Method.get }
        set { d.set(newValue, forKey: Key.method) }
    }
    var postContentType: String? {
        get { str(Key.postContentType) }
        set { d.set(newValue, forKey: Key.postContentType) }
    }
    var postBody: String? {
        get { str(Key.postBody) }
        set { d.set(newValue, forKey: Key.postBody) }
    }
    var postHeaders: String? {
        get { str(Key.postHeaders) }
        set { d.set(newValue, forKey: Key.postHeaders) }
    }
    /// Android: package name. iOS: [IosSecondaryBrowser] slug (e.g. `chrome`, `default` cleared as nil).
    var secondaryBrowserPackage: String? {
        get { str(Key.secondaryBrowserPackage) }
        set {
            guard let raw = newValue?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
                d.removeObject(forKey: Key.secondaryBrowserPackage)
                return
            }
            d.set(raw, forKey: Key.secondaryBrowserPackage)
        }
    }
    var secondarySearchUrl: String? {
        get { str(Key.secondaryUrl) }
        set { d.set(newValue, forKey: Key.secondaryUrl) }
    }
    var secondarySearchAutoFromMusicBrainz: Bool {
        get { d.bool(forKey: Key.secondaryAutoMb) }
        set { d.set(newValue, forKey: Key.secondaryAutoMb) }
    }
    var beepOnScan: Bool {
        get { d.object(forKey: Key.beepOnScan) as? Bool ?? true }
        set { d.set(newValue, forKey: Key.beepOnScan) }
    }
    var hapticOnScan: Bool {
        get { d.object(forKey: Key.hapticOnScan) as? Bool ?? false }
        set { d.set(newValue, forKey: Key.hapticOnScan) }
    }
    var theAudioDbApiKey: String? {
        get { str(Key.theAudioDb) }
        set { d.set(newValue, forKey: Key.theAudioDb) }
    }
    var redactedApiKey: String? {
        get { str(Key.redactedKey) }
        set { d.set(newValue, forKey: Key.redactedKey) }
    }
    var lastRedactedNotificationsSnapshot: String {
        get { d.string(forKey: Key.redactedNotifSnap) ?? "" }
        set { d.set(newValue, forKey: Key.redactedNotifSnap) }
    }

    private func str(_ k: String) -> String? {
        guard let s = d.string(forKey: k) else { return nil }
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }

    enum Method {
        static let get = "GET"
        static let post = "POST"
    }

    enum Keys {
        static let prefsName = "search_prefs"
    }

    private enum Key {
        static let primaryApiList = "primary_api_list"
        static let method = "method"
        static let postContentType = "post_content_type"
        static let postBody = "post_body"
        static let postHeaders = "post_headers"
        static let secondaryUrl = "secondary_search_url"
        static let secondaryListText = "secondary_list_text"
        static let secondaryBrowserPackage = "secondary_browser_package"
        static let secondaryAutoMb = "secondary_auto_musicbrainz"
        static let beepOnScan = "beep_on_scan"
        static let hapticOnScan = "haptic_on_scan"
        static let theAudioDb = "theaudiodb_api_key"
        static let redactedKey = "redacted_api_key"
        static let redactedNotifSnap = "redacted_notifications_snapshot"
    }
}
