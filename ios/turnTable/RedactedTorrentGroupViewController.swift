import UIKit

/// Mirrors Android `RedactedTorrentGroupActivity` (list torrents in group).
final class RedactedTorrentGroupViewController: UITableViewController {

    private let apiKey: String
    private let groupId: Int
    private var torrentIds: [Int] = []
    private var labels: [(String, String)] = []

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
                    let torrents = resp?["torrents"] as? [[String: Any]] ?? []
                    self.torrentIds = torrents.compactMap { $0["id"] as? Int }
                    self.labels = torrents.map { t in
                        let format = t["format"] as? String ?? ""
                        let enc = t["encoding"] as? String ?? ""
                        let title = "\(format) \(enc)".trimmingCharacters(in: .whitespaces)
                        let sub = "id \(t["id"] as? Int ?? 0)"
                        return (title.isEmpty ? "Torrent" : title, sub)
                    }
                default:
                    self.torrentIds = []
                    self.labels = []
                }
                self.tableView.reloadData()
            }
        }
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        labels.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let c = UITableViewCell(style: .subtitle, reuseIdentifier: "c")
        c.backgroundColor = UIColor(white: 0.14, alpha: 1)
        let l = labels[indexPath.row]
        c.textLabel?.text = l.0
        c.textLabel?.textColor = .white
        c.detailTextLabel?.text = l.1
        c.detailTextLabel?.textColor = UIColor(white: 0.65, alpha: 1)
        return c
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let tid = torrentIds[indexPath.row]
        let vc = RedactedJsonLoaderViewController(apiKey: apiKey, title: "Torrent \(tid)") { api in
            api.torrent(torrentId: tid, hash: nil)
        }
        navigationController?.pushViewController(vc, animated: true)
    }
}
