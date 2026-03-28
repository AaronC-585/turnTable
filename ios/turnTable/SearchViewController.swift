import UIKit

/// Mirrors Android `SearchActivity` (GET opens secondary URL; POST sends body).
final class SearchViewController: UIViewController {

    private let barcode: String
    private let prefs = SearchPrefs()

    private let scroll = UIScrollView()
    private let modeSeg = UISegmentedControl(items: ["Search", "Collage"])
    private let collagePanel = UIStackView()
    private let collageFormContainer = UIView()
    private var embeddedCollageSearch: RedactedCollagesSearchViewController?
    private let barcodeField = UITextField()
    private let secondaryField = UITextField()

    init(barcode: String) {
        self.barcode = barcode
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        title = "Search"
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))

        scroll.translatesAutoresizingMaskIntoConstraints = false
        let outer = UIStackView()
        outer.axis = .vertical
        outer.spacing = 12
        outer.translatesAutoresizingMaskIntoConstraints = false

        let hasRedacted = prefs.redactedApiKey != nil && !(prefs.redactedApiKey ?? "").isEmpty
        modeSeg.selectedSegmentIndex = 0
        modeSeg.addTarget(self, action: #selector(searchModeChanged), for: .valueChanged)
        if #available(iOS 13.0, *) {
            modeSeg.selectedSegmentTintColor = UIColor(white: 0.25, alpha: 1)
        }
        modeSeg.isHidden = !hasRedacted
        if hasRedacted {
            modeSeg.setTitle("Collage (disabled)", forSegmentAt: 1)
            if #available(iOS 14.0, *) {
                modeSeg.setEnabled(false, forSegmentAt: 1)
            }
        }

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false

        barcodeField.placeholder = "Barcode"
        barcodeField.text = barcode
        barcodeField.textColor = .white
        barcodeField.borderStyle = .roundedRect
        barcodeField.keyboardType = .asciiCapable

        secondaryField.placeholder = "Secondary search terms (artist - title)"
        secondaryField.textColor = .white
        secondaryField.borderStyle = .roundedRect

        let submit = UIButton(type: .system)
        submit.setTitle("Submit", for: .normal)
        submit.addTarget(self, action: #selector(submitTapped), for: .touchUpInside)

        let red = UIButton(type: .system)
        red.setTitle("Search Redacted", for: .normal)
        red.addTarget(self, action: #selector(redactedTapped), for: .touchUpInside)
        red.isHidden = prefs.redactedApiKey == nil

        [barcodeField, secondaryField, submit, red].forEach { stack.addArrangedSubview($0) }

        scroll.addSubview(stack)
        scroll.alwaysBounceVertical = true

        collagePanel.axis = .vertical
        collagePanel.spacing = 0
        collagePanel.isHidden = true
        collageFormContainer.translatesAutoresizingMaskIntoConstraints = false
        collagePanel.addArrangedSubview(collageFormContainer)

        if hasRedacted {
            outer.addArrangedSubview(modeSeg)
        }
        outer.addArrangedSubview(scroll)
        outer.addArrangedSubview(collagePanel)

        view.addSubview(outer)
        NSLayoutConstraint.activate([
            outer.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            outer.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
            outer.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
            outer.bottomAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -8),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),
            collageFormContainer.heightAnchor.constraint(equalTo: view.safeAreaLayoutGuide.heightAnchor, multiplier: 0.62),
        ])
        if hasRedacted {
            embedCollageSearchFormIfNeeded()
        }
        searchModeChanged()

        if !barcode.isEmpty, preferRedactedOverBrowser() {
            DispatchQueue.main.async { [weak self] in self?.prefetchRedactedFromScan() }
        }
    }

    @objc private func searchModeChanged() {
        if !modeSeg.isHidden && modeSeg.numberOfSegments > 1 && modeSeg.selectedSegmentIndex == 1 {
            modeSeg.selectedSegmentIndex = 0
        }
        let showCollage = modeSeg.selectedSegmentIndex == 1 && !modeSeg.isHidden
        scroll.isHidden = showCollage
        collagePanel.isHidden = !showCollage
        if showCollage {
            embedCollageSearchFormIfNeeded()
            let fromField = secondaryField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            embeddedCollageSearch?.applySearchPrefillIfEmpty(fromField.nilIfEmptySearchAssist)
        }
    }

    private func embedCollageSearchFormIfNeeded() {
        guard embeddedCollageSearch == nil,
              let key = prefs.redactedApiKey?.trimmingCharacters(in: .whitespacesAndNewlines), !key.isEmpty else { return }
        let vc = RedactedCollagesSearchViewController(apiKey: key)
        vc.isEmbedded = true
        let prefill = secondaryField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        vc.initialSearchTerms = prefill.nilIfEmptySearchAssist
        addChild(vc)
        collageFormContainer.addSubview(vc.view)
        vc.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            vc.view.topAnchor.constraint(equalTo: collageFormContainer.topAnchor),
            vc.view.leadingAnchor.constraint(equalTo: collageFormContainer.leadingAnchor),
            vc.view.trailingAnchor.constraint(equalTo: collageFormContainer.trailingAnchor),
            vc.view.bottomAnchor.constraint(equalTo: collageFormContainer.bottomAnchor),
        ])
        vc.didMove(toParent: self)
        embeddedCollageSearch = vc
    }

    private func preferRedactedOverBrowser() -> Bool {
        guard let k = prefs.redactedApiKey?.trimmingCharacters(in: .whitespacesAndNewlines), !k.isEmpty else { return false }
        return true
    }

    private func prefetchRedactedFromScan() {
        fillFromRedactedBrowse(searchStr: barcode, fallbackCover: nil, quietIfNoHit: true)
    }

    /// Fills secondary field from first Redacted `browse` hit; does not open Safari/browser.
    private func fillFromRedactedBrowse(searchStr: String, fallbackCover: String?, quietIfNoHit: Bool) {
        guard let apiKey = prefs.redactedApiKey?.trimmingCharacters(in: .whitespacesAndNewlines), !apiKey.isEmpty else { return }
        let bc = barcodeField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? barcode
        let q = searchStr.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? bc : searchStr.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else {
            if !quietIfNoHit { showAlert("Enter artist/album or scan a barcode") }
            return
        }
        DispatchQueue.global(qos: .userInitiated).async {
            let hit = RedactedSearchAssistIOS.firstBrowseHit(apiKey: apiKey, searchStr: q)
            DispatchQueue.main.async {
                if let hit = hit {
                    self.secondaryField.text = hit.terms
                    let coverHist = hit.coverRaw.map { RedactedSearchAssistIOS.absoluteCoverURL($0) } ?? fallbackCover
                    SearchHistoryStore.add(barcode: bc, title: hit.terms, coverUrl: coverHist)
                    if !quietIfNoHit {
                        self.showAlert("Filled from Redacted (first match)")
                    }
                } else {
                    if quietIfNoHit { return }
                    self.secondaryField.text = q
                    SearchHistoryStore.add(barcode: bc, title: q, coverUrl: fallbackCover)
                    self.showAlert("No Redacted match; kept your search text")
                }
            }
        }
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }

    @objc private func redactedTapped() {
        guard let key = prefs.redactedApiKey, !key.isEmpty else { return }
        let q = secondaryField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let browse = RedactedBrowseViewController(apiKey: key)
        if !q.isEmpty { browse.initialQuery = q }
        navigationController?.pushViewController(browse, animated: true)
    }

    @objc private func submitTapped() {
        let bc = barcodeField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let terms = secondaryField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        if prefs.method == SearchPrefs.Method.post {
            guard let urlStr = prefs.secondarySearchUrl, let url = URL(string: urlStr) else {
                showAlert("Configure secondary URL in Settings")
                return
            }
            doPost(url: url, barcode: bc, notes: "", category: "")
            return
        }

        let secondaryUrl = prefs.secondarySearchUrl?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if secondaryUrl.isEmpty {
            if preferRedactedOverBrowser(), !bc.isEmpty {
                fillFromRedactedBrowse(searchStr: bc, fallbackCover: nil, quietIfNoHit: false)
            } else {
                showAlert("Configure secondary URL in Settings")
            }
            return
        }

        if prefs.secondarySearchAutoFromMusicBrainz {
            showAlert("MusicBrainz auto-fill is not wired on iOS yet. Enter secondary terms manually.")
            return
        }

        if preferRedactedOverBrowser() {
            let q = terms.isEmpty ? bc : terms
            guard !q.isEmpty else {
                showAlert("Enter secondary search terms (artist - title).")
                return
            }
            fillFromRedactedBrowse(searchStr: q, fallbackCover: nil, quietIfNoHit: false)
            return
        }

        guard !terms.isEmpty else {
            showAlert("Enter secondary search terms (artist - title).")
            return
        }

        let urlBuilt = buildSecondaryUrl(template: secondaryUrl, query: terms)
        SearchHistoryStore.add(barcode: bc, title: terms, coverUrl: nil)
        let browser = prefs.iosSecondaryBrowser
        SecondaryBrowserOpener.open(url: urlBuilt, browser: browser) { ok in
            if !ok { DispatchQueue.main.async { self.showAlert("Could not open URL") } }
        }
    }

    /// Matches Android `SecondarySearchVariables.isPlaceholderCompilationArtist`.
    private func isPlaceholderCompilationArtist(_ name: String) -> Bool {
        let s = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        switch s {
        case "various", "various artists", "various artist", "va": return true
        default: return false
        }
    }

    private func buildSecondaryUrl(template: String, query: String) -> URL {
        let enc: (String) -> String = { q in
            q.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? q
        }
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts: [String] = {
            if let r = trimmed.range(of: " — ") {
                return [String(trimmed[..<r.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines),
                        String(trimmed[r.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)]
            }
            return trimmed.components(separatedBy: " - ")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        }()
        let segArtist = parts.first ?? ""
        let segAlbum = parts.count > 1 ? parts[1] : ""
        let artistToken: String = {
            if isPlaceholderCompilationArtist(segArtist) { return "" }
            return segArtist.isEmpty ? trimmed : segArtist
        }()
        let albumToken: String = {
            if isPlaceholderCompilationArtist(segArtist) { return segAlbum }
            return segAlbum.isEmpty ? trimmed : segAlbum
        }()
        let queryToken: String = {
            if isPlaceholderCompilationArtist(segArtist) { return segAlbum }
            return trimmed
        }()
        var s = template
            .replacingOccurrences(of: "%s", with: enc(queryToken))
            .replacingOccurrences(of: "%query%", with: enc(queryToken))
            .replacingOccurrences(of: "%artist%", with: enc(artistToken))
            .replacingOccurrences(of: "%album%", with: enc(albumToken))
        if s.range(of: "://") == nil { s = "https://" + s }
        return URL(string: s) ?? URL(string: "about:blank")!
    }

    private func doPost(url: URL, barcode: String, notes: String, category: String) {
        var body = (prefs.postBody ?? #"{"code":"%s"}"#)
            .replacingOccurrences(of: "%s", with: barcode)
            .replacingOccurrences(of: "$code", with: barcode)
            .replacingOccurrences(of: "$notes", with: notes)
            .replacingOccurrences(of: "$category", with: category)
        let ct = prefs.postContentType ?? "application/json"

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue(ct, forHTTPHeaderField: "Content-Type")
        if let lines = prefs.postHeaders?.split(separator: "\n") {
            for line in lines {
                let p = line.split(separator: ":", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
                if p.count == 2 { req.setValue(p[1], forHTTPHeaderField: p[0]) }
            }
        }
        req.httpBody = body.data(using: .utf8)

        URLSession.shared.dataTask(with: req) { _, resp, err in
            DispatchQueue.main.async {
                if let err = err {
                    self.showAlert(err.localizedDescription)
                    return
                }
                let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
                self.showAlert(code >= 200 && code < 300 ? "Sent (\(code))" : "Response: \(code)")
            }
        }.resume()
    }

    private func showAlert(_ msg: String) {
        let a = UIAlertController(title: nil, message: msg, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "OK", style: .default))
        present(a, animated: true)
    }
}

// MARK: - Redacted browse → search field (mirrors Android [RedactedSearchAssist])

private enum RedactedSearchAssistIOS {
    struct Hit {
        var terms: String
        var coverRaw: String?
    }

    static func firstBrowseHit(apiKey: String, searchStr: String) -> Hit? {
        let t = searchStr.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return nil }
        let api = RedactedApiClient(apiKey: apiKey)
        let r = api.browse(params: [
            ("searchstr", t),
            ("page", "1"),
            ("group_results", "1"),
        ])
        guard case .success(let root) = r,
            let resp = root["response"] as? [String: Any],
            let arr = resp["results"] as? [[String: Any]],
            let o = arr.first
        else { return nil }
        let artist = (o["artist"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let groupName = (o["groupName"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let terms: String
        if !artist.isEmpty && !groupName.isEmpty {
            terms = "\(artist) - \(groupName)"
        } else if !groupName.isEmpty {
            terms = groupName
        } else if !artist.isEmpty {
            terms = artist
        } else {
            return nil
        }
        let cover = (o["cover"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nilIfEmptySearchAssist
        return Hit(terms: terms, coverRaw: cover)
    }

    static func absoluteCoverURL(_ raw: String) -> String {
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.lowercased().hasPrefix("http") { return t }
        let p = t.hasPrefix("/") ? String(t.dropFirst()) : t
        return "https://redacted.sh/\(p)"
    }
}

private extension String {
    var nilIfEmptySearchAssist: String? {
        let s = trimmingCharacters(in: .whitespacesAndNewlines)
        return s.isEmpty ? nil : s
    }
}
