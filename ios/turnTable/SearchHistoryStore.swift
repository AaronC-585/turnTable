import Foundation

struct SearchHistoryEntry: Codable {
    let timestampMs: Int64
    let barcode: String
    let title: String
    let coverUrl: String?
}

/// Mirrors Android `SearchHistoryStore` — JSON in `search_prefs` under `search_history_json`.
enum SearchHistoryStore {
    private static let key = "search_history_json"
    private static let maxEntries = 100

    private static var prefs: UserDefaults {
        UserDefaults(suiteName: "search_prefs") ?? .standard
    }

    static func add(barcode: String, title: String, coverUrl: String?) {
        let cleanBarcode = barcode.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanCover = coverUrl?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        if cleanBarcode.isEmpty && cleanTitle.isEmpty { return }
        var current = getAll()
        current.insert(
            SearchHistoryEntry(
                timestampMs: Int64(Date().timeIntervalSince1970 * 1000),
                barcode: cleanBarcode,
                title: cleanTitle,
                coverUrl: cleanCover
            ),
            at: 0
        )
        if current.count > maxEntries {
            current = Array(current.prefix(maxEntries))
        }
        save(current)
    }

    static func getAll() -> [SearchHistoryEntry] {
        guard let raw = prefs.string(forKey: key), let data = raw.data(using: .utf8) else { return [] }
        return (try? JSONDecoder().decode([SearchHistoryEntry].self, from: data)) ?? []
    }

    private static func save(_ entries: [SearchHistoryEntry]) {
        guard let data = try? JSONEncoder().encode(entries), let s = String(data: data, encoding: .utf8) else { return }
        prefs.set(s, forKey: key)
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : self
    }
}
