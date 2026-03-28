import Foundation

/// Swift port of Android `RedactedApiClient` — same `ajax.php` contract and actions.
final class RedactedApiClient {
    static let baseURL = "https://redacted.sh/ajax.php"

    private let apiKey: String
    private let session: URLSession

    init(apiKey: String) {
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 45
        cfg.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: cfg)
    }

    private func authorizedRequest(url: URL) -> URLRequest {
        var r = URLRequest(url: url)
        r.setValue(apiKey, forHTTPHeaderField: "Authorization")
        r.setValue("turnTable/1.0 (iOS)", forHTTPHeaderField: "User-Agent")
        return r
    }

    private func buildURL(action: String, params: [(String, String?)] = []) -> URL {
        var comp = URLComponents(string: RedactedApiClient.baseURL)!
        var items = [URLQueryItem(name: "action", value: action)]
        for (k, v) in params {
            guard let v = v, !v.isEmpty else { continue }
            items.append(URLQueryItem(name: k, value: v))
        }
        comp.queryItems = items
        return comp.url!
    }

    private func syncData(_ request: URLRequest) -> (Data?, HTTPURLResponse?, Error?) {
        let sem = DispatchSemaphore(value: 0)
        var out: (Data?, HTTPURLResponse?, Error?) = (nil, nil, nil)
        session.dataTask(with: request) { data, resp, err in
            out = (data, resp as? HTTPURLResponse, err)
            sem.signal()
        }.resume()
        sem.wait()
        return out
    }

    private func handleJsonResponse(data: Data?, response: HTTPURLResponse?) -> RedactedResult {
        let code = response?.statusCode ?? 0
        let retryAfter = response?.value(forHTTPHeaderField: "Retry-After").flatMap { Int($0) }
        if code == 429 {
            return .failure("Rate limited (429). Wait before retrying.", code, retryAfter)
        }
        guard let data = data, !data.isEmpty else {
            return .failure("Empty response (HTTP \(code))", code, retryAfter)
        }
        if !(200...299).contains(code) {
            let s = String(data: data, encoding: .utf8) ?? ""
            return .failure("HTTP \(code): \(String(s.prefix(500)))", code, retryAfter)
        }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return .failure("Invalid JSON", code, retryAfter)
        }
        let status = obj["status"] as? String ?? ""
        switch status {
        case "success":
            return .success(root: obj)
        case "failure":
            let err = (obj["error"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                ?? (obj["message"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                ?? "Request failed"
            return .failure(err, code, retryAfter)
        default:
            if String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("{") == true {
                return .success(root: obj)
            }
            return .failure("Unexpected response", code, retryAfter)
        }
    }

    private func executeJson(_ request: URLRequest) -> RedactedResult {
        let (data, resp, err) = syncData(request)
        if err != nil { return .failure(err!.localizedDescription, 0, nil) }
        return handleJsonResponse(data: data, response: resp)
    }

    private func executeBinary(_ request: URLRequest) -> RedactedResult {
        let (data, resp, err) = syncData(request)
        let code = resp?.statusCode ?? 0
        let retryAfter = resp?.value(forHTTPHeaderField: "Retry-After").flatMap { Int($0) }
        if err != nil { return .failure(err!.localizedDescription, 0, nil) }
        if code == 429 { return .failure("Rate limited (429)", code, retryAfter) }
        guard let data = data else { return .failure("Empty body", code, retryAfter) }
        let ct = resp?.mimeType ?? resp?.value(forHTTPHeaderField: "Content-Type")
        if ct?.contains("json") == true || (data.first == UInt8(ascii: "{")) {
            return handleJsonResponse(data: data, response: resp)
        }
        if !(200...299).contains(code) { return .failure("HTTP \(code)", code, retryAfter) }
        return .binary(data, ct)
    }

    private func postForm(url: URL, fields: [(String, String)]) -> RedactedResult {
        var r = authorizedRequest(url: url)
        r.httpMethod = "POST"
        r.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
        var qc = URLComponents()
        qc.queryItems = fields.map { URLQueryItem(name: $0.0, value: $0.1) }
        r.httpBody = qc.percentEncodedQuery?.data(using: .utf8)
        return executeJson(r)
    }

    private func postMultipart(url: URL, fields: [(String, String)]) -> RedactedResult {
        let b = "Boundary-\(UUID().uuidString)"
        var data = Data()
        let nl = "\r\n"
        for (name, value) in fields {
            data.append("--\(b)\(nl)".data(using: .utf8)!)
            data.append("Content-Disposition: form-data; name=\"\(name)\"\(nl)\(nl)".data(using: .utf8)!)
            data.append(value.data(using: .utf8)!)
            data.append(nl.data(using: .utf8)!)
        }
        data.append("--\(b)--\(nl)".data(using: .utf8)!)
        var r = authorizedRequest(url: url)
        r.httpMethod = "POST"
        r.setValue("multipart/form-data; boundary=\(b)", forHTTPHeaderField: "Content-Type")
        r.httpBody = data
        return executeJson(r)
    }

    // MARK: - API

    func index() -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "index"))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func communityStats(userId: Int) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "community_stats", params: [("userid", "\(userId)")]))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func browse(params: [(String, String?)]) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "browse", params: params))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    /// Collage list search (same query shape as `collages.php` on site).
    func collagesSearch(params: [(String, String?)]) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "collages", params: params))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func logcheckerPaste(pasteLog: String) -> RedactedResult {
        let url = buildURL(action: "logchecker")
        return postMultipart(url: url, fields: [("pastelog", pasteLog)])
    }

    func similarArtists(artistId: Int, limit: Int?) -> RedactedResult {
        var p: [(String, String?)] = [("id", "\(artistId)")]
        if let l = limit { p.append(("limit", "\(l)")) }
        var r = authorizedRequest(url: buildURL(action: "similar_artists", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func announcements(page: Int?, perPage: Int?, orderWay: String?, orderBy: String?) -> RedactedResult {
        var p: [(String, String?)] = []
        if let page = page { p.append(("page", "\(page)")) }
        if let perPage = perPage { p.append(("perpage", "\(perPage)")) }
        if let orderWay = orderWay, !orderWay.isEmpty { p.append(("order_way", orderWay)) }
        if let orderBy = orderBy, !orderBy.isEmpty { p.append(("order_by", orderBy)) }
        var r = authorizedRequest(url: buildURL(action: "announcements", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func user(userId: Int) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "user", params: [("id", "\(userId)")]))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func inbox(page: Int?, type: String?, sort: String?, search: String?, searchType: String?) -> RedactedResult {
        var p: [(String, String?)] = []
        if let page = page { p.append(("page", "\(page)")) }
        if let type = type, !type.isEmpty { p.append(("type", type)) }
        if let sort = sort, !sort.isEmpty { p.append(("sort", sort)) }
        if let search = search, !search.isEmpty { p.append(("search", search)) }
        if let searchType = searchType, !searchType.isEmpty { p.append(("searchtype", searchType)) }
        var r = authorizedRequest(url: buildURL(action: "inbox", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func inboxConversation(convId: Int) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "inbox", params: [("type", "viewconv"), ("id", "\(convId)")]))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func sendPm(toUserId: Int, subject: String?, body: String, convId: Int?) -> RedactedResult {
        let url = buildURL(action: "send_pm")
        var fields: [(String, String)] = [("toid", "\(toUserId)"), ("body", body)]
        if let convId = convId { fields.append(("convid", "\(convId)")) }
        if let s = subject, !s.isEmpty { fields.append(("subject", s)) }
        return postForm(url: url, fields: fields)
    }

    func userSearch(search: String, page: Int?) -> RedactedResult {
        var p: [(String, String?)] = [("search", search)]
        if let page = page { p.append(("page", "\(page)")) }
        var r = authorizedRequest(url: buildURL(action: "usersearch", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func bookmarks(type: String) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "bookmarks", params: [("type", type)]))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func subscriptions(showUnreadOnly: Bool) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "subscriptions", params: [("showunread", showUnreadOnly ? "1" : "0")]))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func userTorrents(userId: Int, type: String, limit: Int?, offset: Int?) -> RedactedResult {
        var p: [(String, String?)] = [("id", "\(userId)"), ("type", type)]
        if let limit = limit { p.append(("limit", "\(limit)")) }
        if let offset = offset { p.append(("offset", "\(offset)")) }
        var r = authorizedRequest(url: buildURL(action: "user_torrents", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func notifications(page: Int?) -> RedactedResult {
        let p: [(String, String?)] = page.map { [("page", "\($0)")] } ?? []
        var r = authorizedRequest(url: buildURL(action: "notifications", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func top10(type: String?, limit: Int?) -> RedactedResult {
        var p: [(String, String?)] = []
        if let type = type, !type.isEmpty { p.append(("type", type)) }
        if let limit = limit { p.append(("limit", "\(limit)")) }
        var r = authorizedRequest(url: buildURL(action: "top10", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func artist(artistId: Int) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "artist", params: [("id", "\(artistId)")]))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func torrentGroup(groupId: Int, hash: String?) -> RedactedResult {
        var p: [(String, String?)] = [("id", "\(groupId)")]
        if let hash = hash, !hash.isEmpty { p.append(("hash", hash)) }
        var r = authorizedRequest(url: buildURL(action: "torrentgroup", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func torrent(torrentId: Int, hash: String?) -> RedactedResult {
        var p: [(String, String?)] = [("id", "\(torrentId)")]
        if let hash = hash, !hash.isEmpty { p.append(("hash", hash)) }
        var r = authorizedRequest(url: buildURL(action: "torrent", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func ripLog(torrentId: Int, logId: Int) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "riplog", params: [("id", "\(torrentId)"), ("logid", "\(logId)")]))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func downloadTorrent(torrentId: Int, useToken: Bool) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "download", params: [
            ("id", "\(torrentId)"),
            ("usetoken", useToken ? "1" : "0"),
        ]))
        r.httpMethod = "GET"
        return executeBinary(r)
    }

    func groupEditGet(groupId: Int) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "groupedit", params: [("id", "\(groupId)")]))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func groupEditPost(groupId: Int, summary: String, body: String?, image: String?, releaseType: Int?, groupEditNotes: String?) -> RedactedResult {
        let url = buildURL(action: "groupedit", params: [("id", "\(groupId)")])
        var fields: [(String, String)] = [("summary", summary)]
        if let body = body { fields.append(("body", body)) }
        if let image = image { fields.append(("image", image)) }
        if let releaseType = releaseType { fields.append(("releasetype", "\(releaseType)")) }
        if let n = groupEditNotes { fields.append(("groupeditnotes", n)) }
        return postForm(url: url, fields: fields)
    }

    func addTag(groupId: Int, tagNamesCsv: String) -> RedactedResult {
        postForm(url: buildURL(action: "addtag"), fields: [("groupid", "\(groupId)"), ("tagname", tagNamesCsv)])
    }

    func torrentEditGet(torrentId: Int) -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "torrentedit", params: [("id", "\(torrentId)")]))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func torrentEditPost(torrentId: Int, fields: [(String, String)]) -> RedactedResult {
        let url = buildURL(action: "torrentedit", params: [("id", "\(torrentId)")])
        return postForm(url: url, fields: fields)
    }

    func collage(collageId: Int, showOnlyGroups: Bool) -> RedactedResult {
        var p: [(String, String?)] = [("id", "\(collageId)")]
        if showOnlyGroups { p.append(("showonlygroups", "1")) }
        var r = authorizedRequest(url: buildURL(action: "collage", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func addToCollage(collageId: Int, groupIdsCsv: String) -> RedactedResult {
        let url = buildURL(action: "addtocollage", params: [("collageid", "\(collageId)")])
        return postForm(url: url, fields: [("groupids", groupIdsCsv)])
    }

    func requests(search: String?, page: Int?, tags: String?, tagsType: Int?, showFilled: Bool?, extra: [(String, String?)] = []) -> RedactedResult {
        var p: [(String, String?)] = []
        if let search = search, !search.isEmpty { p.append(("search", search)) }
        if let page = page { p.append(("page", "\(page)")) }
        if let tags = tags, !tags.isEmpty { p.append(("tags", tags)) }
        if let tagsType = tagsType { p.append(("tags_type", "\(tagsType)")) }
        if let showFilled = showFilled { p.append(("show_filled", showFilled ? "true" : "false")) }
        p.append(contentsOf: extra)
        var r = authorizedRequest(url: buildURL(action: "requests", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func request(requestId: Int, page: Int?) -> RedactedResult {
        var p: [(String, String?)] = [("id", "\(requestId)")]
        if let page = page { p.append(("page", "\(page)")) }
        var r = authorizedRequest(url: buildURL(action: "request", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func requestFill(requestId: Int, torrentId: Int?, link: String?) -> RedactedResult {
        var f: [(String, String)] = [("requestid", "\(requestId)")]
        if let t = torrentId { f.append(("torrentid", "\(t)")) }
        if let link = link { f.append(("link", link)) }
        return postForm(url: buildURL(action: "requestfill"), fields: f)
    }

    func forumMain() -> RedactedResult {
        var r = authorizedRequest(url: buildURL(action: "forum", params: [("type", "main")]))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func forumViewForum(forumId: Int, page: Int?) -> RedactedResult {
        var p: [(String, String?)] = [("type", "viewforum"), ("forumid", "\(forumId)")]
        if let page = page { p.append(("page", "\(page)")) }
        var r = authorizedRequest(url: buildURL(action: "forum", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func forumThread(threadId: Int, postId: Int?, page: Int?, skipUpdateLastRead: Bool) -> RedactedResult {
        var p: [(String, String?)] = [("type", "viewthread"), ("threadid", "\(threadId)")]
        if let postId = postId { p.append(("postid", "\(postId)")) }
        if let page = page { p.append(("page", "\(page)")) }
        if skipUpdateLastRead { p.append(("updatelastread", "1")) }
        var r = authorizedRequest(url: buildURL(action: "forum", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }

    func wiki(id: Int?, name: String?) -> RedactedResult {
        var p: [(String, String?)] = []
        if let id = id { p.append(("id", "\(id)")) }
        if let name = name, !name.isEmpty { p.append(("name", name)) }
        var r = authorizedRequest(url: buildURL(action: "wiki", params: p))
        r.httpMethod = "GET"
        return executeJson(r)
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
