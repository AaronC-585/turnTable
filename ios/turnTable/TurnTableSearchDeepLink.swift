import Foundation

/// Parses `turntable://search?barcode=&artist=&album=` from the Scanner companion.
enum TurnTableSearchDeepLink {
    struct Parsed {
        var barcode: String
        var artist: String
        var album: String
        var hasResolvedRelease: Bool {
            !artist.isEmpty || !album.isEmpty
        }
        var secondaryTerms: String {
            switch (artist.isEmpty, album.isEmpty) {
            case (false, false): return "\(artist) - \(album)"
            case (true, false): return album
            case (false, true): return artist
            default: return ""
            }
        }
    }

    static func parse(_ url: URL?) -> Parsed? {
        guard let url = url,
              url.scheme?.lowercased() == "turntable",
              url.host?.lowercased() == "search" else { return nil }
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func q(_ name: String) -> String {
            items.first { $0.name == name }?.value?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        }
        let barcode = q("barcode")
        let artist = q("artist")
        let album = q("album")
        if barcode.isEmpty && artist.isEmpty && album.isEmpty { return nil }
        return Parsed(barcode: barcode, artist: artist, album: album)
    }
}
