import UIKit

/// Mirrors Android `RedactedBrowseActivity`.
final class RedactedBrowseViewController: UITableViewController, UISearchBarDelegate {

    var initialQuery: String?

    private let apiKey: String
    private var rows: [(gid: Int, title: String, subtitle: String)] = []
    private var page = 1
    private var totalPages = 1
    private let searchBar = UISearchBar()

    init(apiKey: String) {
        self.apiKey = apiKey
        super.init(style: .plain)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Torrent search"
        tableView.backgroundColor = UIColor(white: 0.1, alpha: 1)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))
        searchBar.delegate = self
        searchBar.text = initialQuery
        searchBar.placeholder = "Search"
        searchBar.sizeToFit()
        tableView.tableHeaderView = searchBar
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Search", style: .done, target: self, action: #selector(runSearch))
        runSearch()
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }

    @objc private func runSearch() {
        let q = searchBar.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let api = RedactedApiClient(apiKey: apiKey)
        let params: [(String, String?)] = [
            ("searchstr", q.isEmpty ? nil : q),
            ("page", "\(page)"),
        ]
        DispatchQueue.global(qos: .userInitiated).async {
            let r = api.browse(params: params)
            DispatchQueue.main.async {
                switch r {
                case .success(let root):
                    let resp = root["response"] as? [String: Any]
                    self.totalPages = (resp?["pages"] as? Int) ?? 1
                    self.page = (resp?["currentPage"] as? Int) ?? 1
                    let arr = resp?["results"] as? [[String: Any]] ?? []
                    self.rows = arr.compactMap { o in
                        let gid = o["groupId"] as? Int ?? 0
                        let name = o["groupName"] as? String ?? ""
                        let artist = o["artist"] as? String ?? ""
                        let year = o["groupYear"] as? Int ?? 0
                        let sub = "\(artist) · \(year) · id \(gid)"
                        return (gid, name.isEmpty ? "(no title)" : name, sub)
                    }
                case .failure(let msg, _, _):
                    self.rows = []
                    let a = UIAlertController(title: "Browse", message: msg, preferredStyle: .alert)
                    a.addAction(UIAlertAction(title: "OK", style: .default))
                    self.present(a, animated: true)
                case .binary:
                    break
                }
                self.tableView.reloadData()
            }
        }
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        rows.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let c = UITableViewCell(style: .subtitle, reuseIdentifier: "c")
        c.backgroundColor = UIColor(white: 0.14, alpha: 1)
        let r = rows[indexPath.row]
        c.textLabel?.text = r.title
        c.textLabel?.textColor = .white
        c.detailTextLabel?.text = r.subtitle
        c.detailTextLabel?.textColor = UIColor(white: 0.65, alpha: 1)
        return c
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let gid = rows[indexPath.row].gid
        navigationController?.pushViewController(RedactedTorrentGroupViewController(apiKey: apiKey, groupId: gid), animated: true)
    }

    func searchBarSearchButtonClicked(_ searchBar: UISearchBar) {
        page = 1
        runSearch()
    }
}
