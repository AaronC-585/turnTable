import UIKit

// MARK: - Search form (mirrors Android RedactedCollagesSearchActivity + form)

final class RedactedCollagesSearchViewController: UIViewController, UIPickerViewDataSource, UIPickerViewDelegate {

    private let apiKey: String
    var initialSearchTerms: String?
    /// When true, hides nav items and pins the form to the top (embedded under another screen).
    var isEmbedded: Bool = false

    private static let orderByLabels = ["(default)", "Time", "Name", "Subscribers", "Entries", "Updated"]
    private static let orderByValues = ["", "time", "name", "subscribers", "torrents", "updated"]
    private static let orderWayLabels = ["(default)", "Descending", "Ascending"]
    private static let orderWayValues = ["", "desc", "asc"]
    private static let categoryLabels = [
        "Personal", "Theme", "Genre introduction", "Discography", "Label", "Staff picks", "Charts", "Artists",
    ]

    private let scroll = UIScrollView()
    private let searchField = UITextField()
    private let tagsField = UITextField()
    private let tagsMode = UISegmentedControl(items: ["All tags", "Any tag"])
    private let searchIn = UISegmentedControl(items: ["Names", "Descriptions"])
    private var categorySwitches: [UISwitch] = []
    private let orderPicker = UIPickerView()
    private let wayPicker = UIPickerView()

    init(apiKey: String) {
        self.apiKey = apiKey
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        if !isEmbedded {
            title = "Collages"
            navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Back", style: .plain, target: self, action: #selector(goBack))
            navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))
        }

        scroll.translatesAutoresizingMaskIntoConstraints = false
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false

        func label(_ t: String) -> UILabel {
            let l = UILabel()
            l.text = t
            l.textColor = UIColor(white: 0.65, alpha: 1)
            l.font = .systemFont(ofSize: 13)
            return l
        }

        styleField(searchField, placeholder: "Search terms")
        styleField(tagsField, placeholder: "Tags (comma-separated)")
        searchField.text = initialSearchTerms
        tagsMode.selectedSegmentIndex = 0
        searchIn.selectedSegmentIndex = 0

        stack.addArrangedSubview(label("Search terms"))
        stack.addArrangedSubview(searchField)
        stack.addArrangedSubview(label("Tags"))
        stack.addArrangedSubview(tagsField)
        stack.addArrangedSubview(label("Tag matching"))
        stack.addArrangedSubview(tagsMode)
        stack.addArrangedSubview(label("Search in"))
        stack.addArrangedSubview(searchIn)
        stack.addArrangedSubview(label("Categories"))
        let catStack = UIStackView()
        catStack.axis = .vertical
        catStack.spacing = 8
        for (i, name) in Self.categoryLabels.enumerated() {
            let row = UIStackView()
            row.axis = .horizontal
            row.distribution = .fill
            let sw = UISwitch()
            sw.tag = i
            sw.isOn = false
            categorySwitches.append(sw)
            let lab = UILabel()
            lab.text = name
            lab.textColor = .white
            lab.font = .systemFont(ofSize: 15)
            row.addArrangedSubview(lab)
            row.addArrangedSubview(sw)
            catStack.addArrangedSubview(row)
        }
        stack.addArrangedSubview(catStack)

        stack.addArrangedSubview(label("Order by"))
        orderPicker.dataSource = self
        orderPicker.delegate = self
        orderPicker.selectRow(1, inComponent: 0, animated: false)
        orderPicker.heightAnchor.constraint(equalToConstant: 120).isActive = true
        stack.addArrangedSubview(orderPicker)

        stack.addArrangedSubview(label("Order direction"))
        wayPicker.dataSource = self
        wayPicker.delegate = self
        wayPicker.selectRow(1, inComponent: 0, animated: false)
        wayPicker.heightAnchor.constraint(equalToConstant: 100).isActive = true
        stack.addArrangedSubview(wayPicker)

        let searchBtn = UIButton(type: .system)
        searchBtn.setTitle("Search", for: .normal)
        searchBtn.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        searchBtn.backgroundColor = UIColor(red: 0.45, green: 0.35, blue: 0.75, alpha: 1)
        searchBtn.setTitleColor(.white, for: .normal)
        searchBtn.layer.cornerRadius = 10
        searchBtn.heightAnchor.constraint(equalToConstant: 48).isActive = true
        searchBtn.addTarget(self, action: #selector(runSearch), for: .touchUpInside)
        stack.addArrangedSubview(searchBtn)

        scroll.addSubview(stack)
        view.addSubview(scroll)
        let topAnchor = isEmbedded
            ? scroll.topAnchor.constraint(equalTo: view.topAnchor)
            : scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8)
        NSLayoutConstraint.activate([
            topAnchor,
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -24),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -32),
        ])
    }

    /// Copies `raw` into the search field when it is empty (e.g. secondary terms from [SearchViewController]).
    func applySearchPrefillIfEmpty(_ raw: String?) {
        let t = raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !t.isEmpty else { return }
        let cur = searchField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if cur.isEmpty { searchField.text = t }
    }

    private func styleField(_ f: UITextField, placeholder: String) {
        f.placeholder = placeholder
        f.textColor = .white
        f.borderStyle = .roundedRect
        f.autocapitalizationType = .none
        f.autocorrectionType = .no
    }

    @objc private func goBack() {
        navigationController?.popViewController(animated: true)
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }

    @objc private func runSearch() {
        let json = RedactedBrowseParamsCodec.encode(buildParams(page: 1))
        let vc = RedactedCollagesSearchResultsViewController(apiKey: apiKey, paramsJson: json, allowAutoOpenSingle: true)
        navigationController?.pushViewController(vc, animated: true)
    }

    private func buildParams(page: Int) -> [(String, String?)] {
        var p: [(String, String?)] = [("page", "\(page)"))]
        let s = searchField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !s.isEmpty { p.append(("search", s)) }
        let tags = tagsField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !tags.isEmpty {
            p.append(("tags", tags))
            p.append(("tags_type", tagsMode.selectedSegmentIndex == 0 ? "1" : "0"))
        }
        p.append(("type", searchIn.selectedSegmentIndex == 0 ? "name" : "description"))
        for (i, sw) in categorySwitches.enumerated() where sw.isOn {
            p.append(("cats[\(i)]", "1"))
        }
        let ob = Self.orderByValues[orderPicker.selectedRow(inComponent: 0)]
        if !ob.isEmpty { p.append(("order", ob)) }
        let ow = Self.orderWayValues[wayPicker.selectedRow(inComponent: 0)]
        if !ow.isEmpty { p.append(("sort", ow)) }
        return p
    }

    func numberOfComponents(in pickerView: UIPickerView) -> Int { 1 }

    func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
        if pickerView === orderPicker { return Self.orderByLabels.count }
        return Self.orderWayLabels.count
    }

    func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? {
        if pickerView === orderPicker { return Self.orderByLabels[row] }
        return Self.orderWayLabels[row]
    }
}

// MARK: - Results list

private struct CollageResultRow {
    var collageId: Int
    var title: String
    var subtitle: String
    var coverRaw: String?
    var usePinPlaceholder: Bool
}

final class RedactedCollagesSearchResultsViewController: UITableViewController {

    private let apiKey: String
    private var baseParams: [(String, String?)] = []
    private var allowAutoOpenSingle: Bool

    private var rows: [CollageResultRow] = []
    private var collageIds: [Int] = []
    private var currentPage = 1
    private var totalPages = 1
    private let footerPageLabel = UILabel()
    private weak var footerPrevButton: UIButton?
    private weak var footerNextButton: UIButton?

    init(apiKey: String, paramsJson: String, allowAutoOpenSingle: Bool) {
        self.apiKey = apiKey
        self.allowAutoOpenSingle = allowAutoOpenSingle
        super.init(style: .plain)
        if let decoded = try? RedactedBrowseParamsCodec.decode(paramsJson) {
            baseParams = decoded
            currentPage = Int(baseParams.first { $0.0 == "page" }?.1 ?? "1") ?? 1
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Results"
        tableView.backgroundColor = UIColor(white: 0.1, alpha: 1)
        tableView.register(CollageResultCell.self, forCellReuseIdentifier: CollageResultCell.reuseId)
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 72

        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Edit search", style: .plain, target: self, action: #selector(popForm))
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

        if baseParams.isEmpty {
            let a = UIAlertController(title: "Collages", message: "Missing search parameters.", preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "OK", style: .default) { [weak self] _ in self?.navigationController?.popViewController(animated: true) })
            present(a, animated: true)
            return
        }
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

    private func collagesParamsForRequest() -> [(String, String?)] {
        RedactedBrowseParamsCodec.withPage(baseParams, page: currentPage)
    }

    private func unwrapCollagesBody(_ root: [String: Any]) -> [String: Any] {
        if let inner = root["response"] as? [String: Any],
           inner["results"] != nil || inner["pages"] != nil {
            return inner
        }
        return root
    }

    private func loadResults() {
        let api = RedactedApiClient(apiKey: apiKey)
        let params = collagesParamsForRequest()
        let categoryLabels = [
            "Personal", "Theme", "Genre introduction", "Discography", "Label", "Staff picks", "Charts", "Artists",
        ]
        DispatchQueue.global(qos: .userInitiated).async {
            let r = api.collagesSearch(params: params)
            DispatchQueue.main.async {
                switch r {
                case .success(let root):
                    let body = self.unwrapCollagesBody(root)
                    self.totalPages = (body["pages"] as? Int) ?? 1
                    self.currentPage = (body["currentPage"] as? Int) ?? self.currentPage
                    let arr = body["results"] as? [[String: Any]] ?? []
                    var newRows: [CollageResultRow] = []
                    var ids: [Int] = []
                    for o in arr {
                        let cid = (o["collageId"] as? Int)
                            .flatMap { $0 > 0 ? $0 : nil }
                            ?? (o["id"] as? Int).flatMap { $0 > 0 ? $0 : nil }
                            ?? 0
                        if cid == 0 { continue }
                        let name = (o["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                        let title = name.isEmpty ? "(no title)" : name
                        let catId = (o["category_id"] as? Int) ?? (o["categoryId"] as? Int) ?? 0
                        var catLabel = (0..<categoryLabels.count).contains(catId) ? categoryLabels[catId] : ""
                        if catLabel.isEmpty {
                            catLabel = (o["categoryName"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                        }
                        let subs = (o["subscriber_total"] as? Int) ?? (o["subscribers"] as? Int) ?? 0
                        var sub = ""
                        if !catLabel.isEmpty { sub = catLabel }
                        if subs > 0 {
                            if !sub.isEmpty { sub += " · " }
                            sub += "\(subs) subscribers"
                        }
                        let cover = self.collageListCoverUrl(o)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmptyCollage
                        newRows.append(CollageResultRow(
                            collageId: cid,
                            title: title,
                            subtitle: sub,
                            coverRaw: cover,
                            usePinPlaceholder: cover == nil,
                        ))
                        ids.append(cid)
                    }
                    self.rows = newRows
                    self.collageIds = ids
                case .failure(let msg, _, _):
                    self.rows = []
                    self.collageIds = []
                    let a = UIAlertController(title: "Collages", message: msg, preferredStyle: .alert)
                    a.addAction(UIAlertAction(title: "OK", style: .default))
                    self.present(a, animated: true)
                case .binary:
                    break
                }
                self.footerPageLabel.text = "Page \(self.currentPage) / \(self.totalPages)"
                self.footerPrevButton?.isEnabled = self.currentPage > 1
                self.footerNextButton?.isEnabled = self.currentPage < self.totalPages
                self.tableView.reloadData()

                let mayAuto = self.allowAutoOpenSingle
                self.allowAutoOpenSingle = false
                if mayAuto, self.rows.count == 1, let only = self.collageIds.first, only > 0,
                   let nav = self.navigationController {
                    let detail = RedactedCollageDetailViewController(apiKey: self.apiKey, collageId: only)
                    nav.pushViewController(detail, animated: true)
                    nav.setViewControllers(nav.viewControllers.filter { $0 !== self }, animated: false)
                }
            }
        }
    }

    private func collageListCoverUrl(_ o: [String: Any]) -> String? {
        for k in ["cover", "image", "wikiImage", "picture", "thumb", "coverUrl"] {
            if let s = o[k] as? String, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return s
            }
        }
        return nil
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        rows.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: CollageResultCell.reuseId, for: indexPath) as! CollageResultCell
        let r = rows[indexPath.row]
        cell.configure(title: r.title, subtitle: r.subtitle, coverRaw: r.coverRaw, usePinPlaceholder: r.usePinPlaceholder, apiKey: apiKey)
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let id = collageIds[indexPath.row]
        navigationController?.pushViewController(RedactedCollageDetailViewController(apiKey: apiKey, collageId: id), animated: true)
    }
}

private extension String {
    var nilIfEmptyCollage: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}

// MARK: - Collage detail (torrent groups in collage)

final class RedactedCollageDetailViewController: UITableViewController {

    private let apiKey: String
    private let collageId: Int
    private var rows: [BrowseGroupRow] = []
    private var groupIds: [Int] = []

    private struct BrowseGroupRow {
        var gid: Int
        var title: String
        var subtitle: String
        var coverRaw: String?
    }

    init(apiKey: String, collageId: Int) {
        self.apiKey = apiKey
        self.collageId = collageId
        super.init(style: .plain)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Collage"
        tableView.backgroundColor = UIColor(white: 0.1, alpha: 1)
        tableView.register(CollageGroupCell.self, forCellReuseIdentifier: CollageGroupCell.reuseId)
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 72
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))
        loadCollage()
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }

    private func torrentGroupsArray(_ body: [String: Any]) -> [[String: Any]]? {
        if let a = body["torrentGroup"] as? [[String: Any]] { return a }
        if let a = body["torrentgroup"] as? [[String: Any]] { return a }
        if let a = body["torrentgroups"] as? [[String: Any]] { return a }
        if let a = body["torrentGroups"] as? [[String: Any]] { return a }
        return nil
    }

    private func torrentGroupIdListKeys(_ body: [String: Any]) -> [String] {
        let raw = (body["torrentGroupIDList"] as? [Any]) ?? (body["torrentGroupIdList"] as? [Any]) ?? []
        return raw.compactMap { el -> String? in
            if let s = el as? String, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return s.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            if let n = el as? NSNumber { return "\(n.intValue)" }
            if let i = el as? Int { return "\(i)" }
            return nil
        }
    }

    private static func jsonIntCollage(_ v: Any?) -> Int? {
        if let i = v as? Int { return i }
        if let n = v as? NSNumber { return n.intValue }
        if let s = v as? String { return Int(s.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return nil
    }

    private func indexTorrentGroupsById(_ arr: [[String: Any]]) -> [String: [String: Any]] {
        var m: [String: [String: Any]] = [:]
        for o in arr {
            let sid = (o["id"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !sid.isEmpty {
                m[sid] = o
                continue
            }
            if let idn = Self.jsonIntCollage(o["id"]), idn > 0 {
                m["\(idn)"] = o
            } else if let gid = Self.jsonIntCollage(o["groupId"]), gid > 0 {
                m["\(gid)"] = o
            }
        }
        return m
    }

    private func coverRawFromCollageGroup(_ o: [String: Any]) -> String? {
        for k in ["wikiImage", "cover", "image", "picture", "thumb", "coverUrl"] {
            if let s = o[k] as? String, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return s.trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return nil
    }

    private func primaryArtistLineFromCollageGroup(_ o: [String: Any]) -> String {
        if let a = o["artist"] as? String, !a.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return a.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        guard let mi = o["musicInfo"] as? [String: Any] else { return "" }
        func names(_ key: String) -> [String] {
            guard let arr = mi[key] as? [[String: Any]] else { return [] }
            return arr.compactMap { ($0["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmptyCollage }
        }
        var seen = Set<String>()
        var parts: [String] = []
        for key in ["artists", "dj", "composers", "conductor", "with", "remixedBy", "producer"] {
            for n in names(key) where seen.insert(n).inserted {
                parts.append(n)
            }
        }
        return parts.joined(separator: ", ")
    }

    private func browseRowFromCollageGroup(_ o: [String: Any], listGid: Int) -> BrowseGroupRow? {
        let gid = listGid > 0 ? listGid : (Self.jsonIntCollage(o["id"]) ?? Self.jsonIntCollage(o["groupId"]) ?? 0)
        guard gid > 0 else { return nil }
        let nameRaw = ((o["name"] as? String) ?? (o["groupName"] as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let title = nameRaw.isEmpty ? "(no title)" : nameRaw
        let artistLine = primaryArtistLineFromCollageGroup(o)
        let year = Self.jsonIntCollage(o["year"]) ?? Self.jsonIntCollage(o["groupYear"]) ?? 0
        var sub = artistLine
        if year > 0 {
            if !sub.isEmpty { sub += " · " }
            sub += "\(year)"
        }
        let cover = coverRawFromCollageGroup(o)?.nilIfEmptyCollage
        return BrowseGroupRow(gid: gid, title: title, subtitle: sub, coverRaw: cover)
    }

    private func loadCollage() {
        let api = RedactedApiClient(apiKey: apiKey)
        DispatchQueue.global(qos: .userInitiated).async {
            let r = api.collage(collageId: self.collageId, showOnlyGroups: false)
            DispatchQueue.main.async {
                switch r {
                case .success(let root):
                    let payload = (root["response"] as? [String: Any]) ?? root
                    let name = (payload["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                    if !name.isEmpty { self.title = name }
                    let arr = self.torrentGroupsArray(payload) ?? []
                    let byId = self.indexTorrentGroupsById(arr)
                    let idKeys = self.torrentGroupIdListKeys(payload)
                    var out: [BrowseGroupRow] = []
                    var gids: [Int] = []
                    if !idKeys.isEmpty {
                        for key in idKeys {
                            guard let gid = Int(key), gid > 0 else { continue }
                            if let o = byId[key] {
                                if let row = self.browseRowFromCollageGroup(o, listGid: gid) {
                                    out.append(row)
                                    gids.append(gid)
                                }
                            } else {
                                out.append(BrowseGroupRow(gid: gid, title: "Torrent group \(gid)", subtitle: "", coverRaw: nil))
                                gids.append(gid)
                            }
                        }
                    } else {
                        for o in arr {
                            guard let row = self.browseRowFromCollageGroup(o, listGid: 0) else { continue }
                            out.append(row)
                            gids.append(row.gid)
                        }
                    }
                    self.rows = out
                    self.groupIds = gids
                case .failure(let msg, _, _):
                    self.rows = []
                    let a = UIAlertController(title: "Collage", message: msg, preferredStyle: .alert)
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
        let cell = tableView.dequeueReusableCell(withIdentifier: CollageGroupCell.reuseId, for: indexPath) as! CollageGroupCell
        let r = rows[indexPath.row]
        cell.configure(title: r.title, subtitle: r.subtitle, coverRaw: r.coverRaw, apiKey: apiKey)
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let gid = groupIds[indexPath.row]
        guard gid > 0 else { return }
        navigationController?.pushViewController(RedactedTorrentGroupViewController(apiKey: apiKey, groupId: gid), animated: true)
    }
}

// MARK: - Cells + cover URL

private enum RedactedCollageCoverURL {
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

private final class CollageResultCell: UITableViewCell {
    static let reuseId = "CollageResultCell"
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
        imageView?.alpha = 1
        imageView?.tintColor = nil
    }

    func configure(title: String, subtitle: String, coverRaw: String?, usePinPlaceholder: Bool, apiKey: String) {
        textLabel?.text = title
        detailTextLabel?.text = subtitle
        coverToken = coverRaw
        imageView?.image = nil
        imageView?.alpha = 1

        if usePinPlaceholder, coverRaw == nil {
            let img = UIImage(systemName: "pin.fill")
            imageView?.image = img
            imageView?.tintColor = UIColor(white: 0.45, alpha: 1)
            imageView?.alpha = 0.55
            return
        }

        guard let raw = coverRaw, !raw.isEmpty, let url = RedactedCollageCoverURL.absolute(from: raw) else {
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
                self.imageView?.tintColor = nil
                self.imageView?.alpha = 1
                self.imageView?.image = img
                self.setNeedsLayout()
            }
        }.resume()
    }
}

private final class CollageGroupCell: UITableViewCell {
    static let reuseId = "CollageGroupCell"
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
        guard let raw = coverRaw, !raw.isEmpty, let url = RedactedCollageCoverURL.absolute(from: raw) else {
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
