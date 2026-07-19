import Foundation

/// Minimal Redacted `browse` client for barcode → artist/album (mirrors Android `ScannerRedactedAssist`).
enum ScannerRedactedAssist {
    struct Hit {
        var artist: String
        var album: String
    }

    static func firstHit(apiKey: String, barcode: String) -> Hit? {
        let q = barcode.trimmingCharacters(in: .whitespacesAndNewlines)
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty, !key.isEmpty else { return nil }

        var comps = URLComponents(string: "https://redacted.sh/ajax.php")!
        comps.queryItems = [
            URLQueryItem(name: "action", value: "browse"),
            URLQueryItem(name: "searchstr", value: q),
            URLQueryItem(name: "page", value: "1"),
            URLQueryItem(name: "group_results", value: "1"),
        ]
        guard let url = comps.url else { return nil }

        var req = URLRequest(url: url, timeoutInterval: 60)
        req.httpMethod = "GET"
        req.setValue(key, forHTTPHeaderField: "Authorization")
        req.setValue("turnTableScanner/1.0 (iOS)", forHTTPHeaderField: "User-Agent")

        let sem = DispatchSemaphore(value: 0)
        var hit: Hit?
        URLSession.shared.dataTask(with: req) { data, _, _ in
            defer { sem.signal() }
            guard let data,
                  let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  (root["status"] as? String) == "success",
                  let resp = root["response"] as? [String: Any],
                  let arr = resp["results"] as? [[String: Any]],
                  let o = arr.first
            else { return }
            let artist = (o["artist"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let album = (o["groupName"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if artist.isEmpty && album.isEmpty { return }
            hit = Hit(artist: artist, album: album)
        }.resume()
        _ = sem.wait(timeout: .now() + 65)
        return hit
    }

    static func turnTableSearchURL(barcode: String, artist: String?, album: String?) -> URL? {
        var comps = URLComponents()
        comps.scheme = "turntable"
        comps.host = "search"
        var items = [URLQueryItem(name: "barcode", value: barcode)]
        let a = artist?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let al = album?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !a.isEmpty { items.append(URLQueryItem(name: "artist", value: a)) }
        if !al.isEmpty { items.append(URLQueryItem(name: "album", value: al)) }
        comps.queryItems = items
        return comps.url
    }
}
