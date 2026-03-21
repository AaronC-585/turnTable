import UIKit

/// Mirrors Android `RedactedTorrentGroupActivity` — torrents grouped by **pressing / edition** like the site.
final class RedactedTorrentGroupViewController: UITableViewController {

    private let apiKey: String
    private let groupId: Int
    private var torrentIds: [Int] = []
    private var rows: [GroupRow] = []

    private enum GroupRow {
        case editionHeader(String)
        case torrent(listIndex: Int, title: String, subtitle: String)
    }

    init(apiKey: String, groupId: Int) {
        self.apiKey = apiKey
        self.groupId = groupId
        super.init(style: .plain)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Group \(groupId)"
        tableView.backgroundColor = UIColor(white: 0.1, alpha: 1)
        tableView.register(TorrentGroupTableCell.self, forCellReuseIdentifier: TorrentGroupTableCell.reuseId)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))
        load()
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }

    private func load() {
        DispatchQueue.global(qos: .userInitiated).async {
            let api = RedactedApiClient(apiKey: self.apiKey)
            let r = api.torrentGroup(groupId: self.groupId, hash: nil)
            DispatchQueue.main.async {
                switch r {
                case .success(let root):
                    let resp = root["response"] as? [String: Any]
                    let group = resp?["group"] as? [String: Any] ?? [:]
                    let torrents = resp?["torrents"] as? [[String: Any]] ?? []
                    self.rebuildRows(group: group, torrents: torrents)
                default:
                    self.torrentIds = []
                    self.rows = []
                }
                self.tableView.reloadData()
            }
        }
    }

    private func rebuildRows(group: [String: Any], torrents: [[String: Any]]) {
        torrentIds = []
        var out: [GroupRow] = []
        let buckets = RedactedGazelleEditionIOS.groupTorrentsByEdition(group: group, torrents: torrents)
        for bucket in buckets {
            guard let first = bucket.first else { continue }
            out.append(.editionHeader(RedactedGazelleEditionIOS.buildEditionHeaderTitle(group: group, torrent: first)))
            for t in bucket {
                guard let tid = t["id"] as? Int else { continue }
                let idx = torrentIds.count
                torrentIds.append(tid)
                let titleLine = Self.buildTorrentTitleLine(t)
                let sub = Self.buildTorrentSubtitle(t, tid: tid)
                out.append(.torrent(listIndex: idx, title: titleLine, subtitle: sub))
            }
        }
        rows = out
    }

    private static func buildTorrentTitleLine(_ t: [String: Any]) -> String {
        let base = "\(t["format"] as? String ?? "") / \(t["encoding"] as? String ?? "") / \(t["media"] as? String ?? "")"
        let status = (t["userStatus"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !status.isEmpty { return "\(base) — \(status)" }
        return base
    }

    private static func buildTorrentSubtitle(_ t: [String: Any], tid: Int) -> String {
        let seed = t["seeders"] as? Int ?? 0
        let leech = t["leechers"] as? Int ?? 0
        return "↑\(seed) ↓\(leech) · id \(tid)"
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        rows.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: TorrentGroupTableCell.reuseId, for: indexPath) as! TorrentGroupTableCell
        cell.selectionStyle = .default
        switch rows[indexPath.row] {
        case .editionHeader(let title):
            cell.textLabel?.text = title
            cell.textLabel?.numberOfLines = 0
            cell.textLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
            cell.textLabel?.textColor = .white
            cell.detailTextLabel?.text = nil
            cell.backgroundColor = UIColor(white: 0.18, alpha: 1)
            cell.selectionStyle = .none
        case .torrent(_, let title, let sub):
            cell.textLabel?.text = title
            cell.textLabel?.numberOfLines = 2
            cell.textLabel?.font = .systemFont(ofSize: 16, weight: .regular)
            cell.textLabel?.textColor = .white
            cell.detailTextLabel?.text = sub
            cell.detailTextLabel?.textColor = UIColor(white: 0.65, alpha: 1)
            cell.backgroundColor = UIColor(white: 0.14, alpha: 1)
        }
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard case .torrent(let listIndex, _, _) = rows[indexPath.row] else { return }
        let tid = torrentIds[listIndex]
        let vc = RedactedJsonLoaderViewController(apiKey: apiKey, title: "Torrent \(tid)") { api in
            api.torrent(torrentId: tid, hash: nil)
        }
        navigationController?.pushViewController(vc, animated: true)
    }
}

// MARK: - Edition grouping (Gazelle remaster + media)

private enum RedactedGazelleEditionIOS {

    static func groupKey(group: [String: Any], torrent: [String: Any]) -> String {
        let rem = (torrent["remastered"] as? Bool) ?? false
        let parts = [
            rem ? "1" : "0",
            "\(torrent["remasterYear"] as? Int ?? 0)",
            (torrent["remasterTitle"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
            (torrent["remasterRecordLabel"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
            (torrent["remasterCatalogueNumber"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
            (torrent["media"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
        ]
        return parts.joined(separator: "\u{01}")
    }

    static func sortYear(group: [String: Any], torrent: [String: Any]) -> Int {
        let gy = group["year"] as? Int ?? 0
        let rem = (torrent["remastered"] as? Bool) ?? false
        let ry = torrent["remasterYear"] as? Int ?? 0
        if rem && ry > 0 { return ry }
        return gy
    }

    static func buildEditionHeaderTitle(group: [String: Any], torrent: [String: Any]) -> String {
        let gy = group["year"] as? Int ?? 0
        let gLabel = (group["recordLabel"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let gCat = (group["catalogueNumber"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let rem = (torrent["remastered"] as? Bool) ?? false
        let ry = torrent["remasterYear"] as? Int ?? 0
        let y: Int = (rem && ry > 0) ? ry : gy
        let remLabel = (torrent["remasterRecordLabel"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let label = remLabel.isEmpty ? gLabel : remLabel
        let remCat = (torrent["remasterCatalogueNumber"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let cat = remCat.isEmpty ? gCat : remCat
        let remTitle = (torrent["remasterTitle"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let media = (torrent["media"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        var parts: [String] = []
        if y > 0 { parts.append("\(y)") }
        if !label.isEmpty { parts.append(label) }
        if !cat.isEmpty { parts.append(cat) }
        if !remTitle.isEmpty { parts.append(remTitle) }
        if !media.isEmpty { parts.append(media) }
        let s = parts.joined(separator: " / ")
        return s.isEmpty ? "—" : s
    }

    static func groupTorrentsByEdition(group: [String: Any], torrents: [[String: Any]]) -> [[[String: Any]]] {
        if torrents.isEmpty { return [] }
        var map: [String: [[String: Any]]] = [:]
        var order: [String] = []
        for t in torrents {
            let k = groupKey(group: group, torrent: t)
            if map[k] == nil { order.append(k) }
            map[k, default: []].append(t)
        }
        var buckets: [[[String: Any]]] = order.compactMap { map[$0] }
        buckets.sort { a, b in
            guard let fa = a.first, let fb = b.first else { return false }
            let ya = sortYear(group: group, torrent: fa)
            let yb = sortYear(group: group, torrent: fb)
            if ya != yb { return ya < yb }
            return groupKey(group: group, torrent: fa) < groupKey(group: group, torrent: fb)
        }
        for i in buckets.indices {
            buckets[i].sort { a, b in
                let fa = (a["format"] as? String ?? "").compare(b["format"] as? String ?? "")
                if fa != .orderedSame { return fa == .orderedAscending }
                let ea = (a["encoding"] as? String ?? "").compare(b["encoding"] as? String ?? "")
                if ea != .orderedSame { return ea == .orderedAscending }
                return torrentSize(a) < torrentSize(b)
            }
        }
        return buckets
    }

    private static func torrentSize(_ t: [String: Any]) -> Int64 {
        if let n = t["size"] as? Int64 { return n }
        if let n = t["size"] as? Int { return Int64(n) }
        if let n = t["size"] as? Double { return Int64(n) }
        return 0
    }
}

private final class TorrentGroupTableCell: UITableViewCell {
    static let reuseId = "TorrentGroupTableCell"

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: .subtitle, reuseIdentifier: reuseIdentifier)
        textLabel?.numberOfLines = 0
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }
}
