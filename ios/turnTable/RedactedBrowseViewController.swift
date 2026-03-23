import UIKit

// MARK: - Page 1: search form

/// First screen: enter terms, then open results ([RedactedBrowseResultsViewController]).
final class RedactedBrowseViewController: UIViewController, UISearchBarDelegate {

    var initialQuery: String?

    private let apiKey: String
    private let searchBar = UISearchBar()

    init(apiKey: String) {
        self.apiKey = apiKey
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        title = "Torrent search"
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Search", style: .done, target: self, action: #selector(openResults))

        searchBar.delegate = self
        searchBar.text = initialQuery
        searchBar.placeholder = "Search"
        searchBar.translatesAutoresizingMaskIntoConstraints = false

        let hint = UILabel()
        hint.textColor = UIColor(white: 0.55, alpha: 1)
        hint.font = .systemFont(ofSize: 14)
        hint.numberOfLines = 0
        hint.text = "Tap Search to see torrent results on the next screen."
        hint.translatesAutoresizingMaskIntoConstraints = false

        let stack = UIStackView(arrangedSubviews: [searchBar, hint])
        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
        ])

        if let q = initialQuery?.trimmingCharacters(in: .whitespacesAndNewlines), !q.isEmpty {
            DispatchQueue.main.async { [weak self] in self?.openResults() }
        }
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }

    @objc private func openResults() {
        searchBar.resignFirstResponder()
        let q = searchBar.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let params: [(String, String?)] = [
            ("searchstr", q.isEmpty ? nil : q),
            ("page", "1"),
            ("group_results", "1"),
        ]
        let vc = RedactedBrowseResultsViewController(apiKey: apiKey, baseBrowseParams: params)
        navigationController?.pushViewController(vc, animated: true)
    }

    func searchBarSearchButtonClicked(_ searchBar: UISearchBar) {
        openResults()
    }
}

// MARK: - Page 2: results

private struct BrowseResultRow {
    var gid: Int
    var title: String
    var subtitle: String
    var coverRaw: String?
}

final class RedactedBrowseResultsViewController: UITableViewController {

    private let apiKey: String
    /// Template params; `page` is replaced on each request.
    private let baseBrowseParams: [(String, String?)]

    private var rows: [BrowseResultRow] = []
    private var currentPage = 1
    private var totalPages = 1
    private let footerPageLabel = UILabel()
    private weak var footerPrevButton: UIButton?
    private weak var footerNextButton: UIButton?

    init(apiKey: String, baseBrowseParams: [(String, String?)]) {
        self.apiKey = apiKey
        self.baseBrowseParams = baseBrowseParams
        self.currentPage = Int(baseBrowseParams.first { $0.0 == "page" }?.1 ?? "1") ?? 1
        super.init(style: .plain)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Results"
        tableView.backgroundColor = UIColor(white: 0.1, alpha: 1)
        tableView.register(RedactedBrowseResultCell.self, forCellReuseIdentifier: RedactedBrowseResultCell.reuseId)
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 72

        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: "Edit search",
            style: .plain,
            target: self,
            action: #selector(popForm)
        )
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))

        let footer = UIView(frame: CGRect(x: 0, y: 0, width: 320, height: 52))
        footer.backgroundColor = UIColor(white: 0.12, alpha: 1)

        let prev = UIButton(type: .system)
        prev.setTitle("Previous", for: .normal)
        prev.setTitleColor(.white, for: .normal)
        prev.addTarget(self, action: #selector(tapPrev), for: .touchUpInside)
        footerPrevButton = prev

        let next = UIButton(type: .system)
        next.setTitle("Next", for: .normal)
        next.setTitleColor(.white, for: .normal)
        next.addTarget(self, action: #selector(tapNext), for: .touchUpInside)
        footerNextButton = next

        footerPageLabel.textColor = UIColor(white: 0.65, alpha: 1)
        footerPageLabel.font = .systemFont(ofSize: 13)
        footerPageLabel.textAlignment = .center
        footerPageLabel.text = " "

        let h = UIStackView(arrangedSubviews: [prev, footerPageLabel, next])
        h.axis = .horizontal
        h.distribution = .equalSpacing
        h.alignment = .center
        h.translatesAutoresizingMaskIntoConstraints = false
        footer.addSubview(h)
        NSLayoutConstraint.activate([
            h.leadingAnchor.constraint(equalTo: footer.leadingAnchor, constant: 16),
            h.trailingAnchor.constraint(equalTo: footer.trailingAnchor, constant: -16),
            h.topAnchor.constraint(equalTo: footer.topAnchor, constant: 8),
            h.bottomAnchor.constraint(equalTo: footer.bottomAnchor, constant: -8),
            footerPageLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 100),
        ])
        tableView.tableFooterView = footer

        loadResults()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard let footer = tableView.tableFooterView else { return }
        let w = tableView.bounds.width
        if footer.frame.width != w {
            footer.frame = CGRect(x: 0, y: 0, width: w, height: 52)
            tableView.tableFooterView = footer
        }
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }

    @objc private func popForm() {
        navigationController?.popViewController(animated: true)
    }

    @objc private func tapPrev() {
        guard currentPage > 1 else { return }
        currentPage -= 1
        loadResults()
    }

    @objc private func tapNext() {
        guard currentPage < totalPages else { return }
        currentPage += 1
        loadResults()
    }

    private func browseParamsForRequest() -> [(String, String?)] {
        var p = baseBrowseParams.filter { $0.0 != "page" }
        p.append(("page", "\(currentPage)"))
        return p
    }

    private func loadResults() {
        let api = RedactedApiClient(apiKey: apiKey)
        let params = browseParamsForRequest()
        DispatchQueue.global(qos: .userInitiated).async {
            let r = api.browse(params: params)
            DispatchQueue.main.async {
                switch r {
                case .success(let root):
                    let resp = root["response"] as? [String: Any]
                    self.totalPages = (resp?["pages"] as? Int) ?? 1
                    self.currentPage = (resp?["currentPage"] as? Int) ?? self.currentPage
                    let arr = resp?["results"] as? [[String: Any]] ?? []
                    self.rows = arr.compactMap { o in
                        let gid = o["groupId"] as? Int ?? 0
                        let name = o["groupName"] as? String ?? ""
                        let artist = o["artist"] as? String ?? ""
                        let year = o["groupYear"] as? Int ?? 0
                        let sub = "\(artist) · \(year) · id \(gid)"
                        let cover = (o["cover"] as? String)?
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                            .nilIfEmptyBrowse
                        return BrowseResultRow(
                            gid: gid,
                            title: name.isEmpty ? "(no title)" : name,
                            subtitle: sub,
                            coverRaw: cover
                        )
                    }
                case .failure(let msg, _, _):
                    self.rows = []
                    let a = UIAlertController(title: "Browse", message: msg, preferredStyle: .alert)
                    a.addAction(UIAlertAction(title: "OK", style: .default))
                    self.present(a, animated: true)
                case .binary:
                    break
                }
                self.footerPageLabel.text = "Page \(self.currentPage) / \(self.totalPages)"
                self.footerPrevButton?.isEnabled = self.currentPage > 1
                self.footerNextButton?.isEnabled = self.currentPage < self.totalPages
                self.tableView.reloadData()
            }
        }
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        rows.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: RedactedBrowseResultCell.reuseId, for: indexPath) as! RedactedBrowseResultCell
        let r = rows[indexPath.row]
        cell.configure(title: r.title, subtitle: r.subtitle, coverRaw: r.coverRaw, apiKey: apiKey)
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let gid = rows[indexPath.row].gid
        navigationController?.pushViewController(RedactedTorrentGroupViewController(apiKey: apiKey, groupId: gid), animated: true)
    }
}

// MARK: - Cover URL + row cell

private extension String {
    var nilIfEmptyBrowse: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}

private enum RedactedBrowseCoverURL {
    static func absolute(from raw: String) -> URL? {
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return nil }
        if t.lowercased().hasPrefix("http") {
            return URL(string: t)
        }
        let path = t.hasPrefix("/") ? String(t.dropFirst()) : t
        var allowed = CharacterSet.urlPathAllowed
        allowed.insert("/")
        let enc = path.addingPercentEncoding(withAllowedCharacters: allowed) ?? path
        return URL(string: "https://redacted.sh/\(enc)")
    }
}

private final class RedactedBrowseResultCell: UITableViewCell {
    static let reuseId = "RedactedBrowseResultCell"
    private var coverToken: String?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: .subtitle, reuseIdentifier: reuseIdentifier)
        backgroundColor = UIColor(white: 0.14, alpha: 1)
        textLabel?.textColor = .white
        textLabel?.numberOfLines = 2
        detailTextLabel?.textColor = UIColor(white: 0.65, alpha: 1)
        detailTextLabel?.numberOfLines = 2
        imageView?.contentMode = .scaleAspectFill
        imageView?.clipsToBounds = true
        imageView?.layer.cornerRadius = 4
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func prepareForReuse() {
        super.prepareForReuse()
        coverToken = nil
        imageView?.image = nil
    }

    func configure(title: String, subtitle: String, coverRaw: String?, apiKey: String) {
        textLabel?.text = title
        detailTextLabel?.text = subtitle
        coverToken = coverRaw
        imageView?.image = nil

        guard let raw = coverRaw, !raw.isEmpty, let url = RedactedBrowseCoverURL.absolute(from: raw) else {
            return
        }

        let token = raw
        var req = URLRequest(url: url)
        req.setValue("turnTable/1.0 (iOS)", forHTTPHeaderField: "User-Agent")
        if let host = url.host?.lowercased(), host.contains("redacted.sh") {
            req.setValue(apiKey, forHTTPHeaderField: "Authorization")
        }

        URLSession.shared.dataTask(with: req) { [weak self] data, _, _ in
            guard let self = self else { return }
            guard self.coverToken == token else { return }
            let img = data.flatMap { UIImage(data: $0) }
            DispatchQueue.main.async {
                guard self.coverToken == token else { return }
                self.imageView?.image = img
                self.setNeedsLayout()
            }
        }.resume()
    }
}
