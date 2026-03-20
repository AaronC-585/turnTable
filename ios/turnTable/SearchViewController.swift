import UIKit

/// Mirrors Android `SearchActivity` (GET opens secondary URL; POST sends body).
final class SearchViewController: UIViewController {

    private let barcode: String
    private let prefs = SearchPrefs()

    private let scroll = UIScrollView()
    private let barcodeField = UITextField()
    private let secondaryField = UITextField()
    private let notesField = UITextField()
    private let categoryField = UITextField()

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

        notesField.placeholder = "Notes (optional)"
        notesField.textColor = .white
        notesField.borderStyle = .roundedRect

        categoryField.placeholder = "Category (optional)"
        categoryField.textColor = .white
        categoryField.borderStyle = .roundedRect

        let submit = UIButton(type: .system)
        submit.setTitle("Submit", for: .normal)
        submit.addTarget(self, action: #selector(submitTapped), for: .touchUpInside)

        let red = UIButton(type: .system)
        red.setTitle("Search Redacted", for: .normal)
        red.addTarget(self, action: #selector(redactedTapped), for: .touchUpInside)
        red.isHidden = prefs.redactedApiKey == nil

        [barcodeField, secondaryField, notesField, categoryField, submit, red].forEach { stack.addArrangedSubview($0) }

        scroll.addSubview(stack)
        view.addSubview(scroll)
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -24),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -32),
        ])
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
        let notes = notesField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let category = categoryField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        if prefs.method == SearchPrefs.Method.post {
            guard let urlStr = prefs.secondarySearchUrl, let url = URL(string: urlStr) else {
                showAlert("Configure secondary URL in Settings")
                return
            }
            doPost(url: url, barcode: bc, notes: notes, category: category)
            return
        }

        guard let secondaryUrl = prefs.secondarySearchUrl, !secondaryUrl.isEmpty else {
            showAlert("Configure secondary URL in Settings")
            return
        }

        if prefs.secondarySearchAutoFromMusicBrainz {
            showAlert("MusicBrainz auto-fill is not wired on iOS yet. Enter secondary terms manually.")
            return
        }

        guard !terms.isEmpty else {
            showAlert("Enter secondary search terms (artist - title).")
            return
        }

        let urlBuilt = buildSecondaryUrl(template: secondaryUrl, query: terms)
        SearchHistoryStore.add(barcode: bc, title: terms, coverUrl: nil)
        UIApplication.shared.open(urlBuilt, options: [:]) { ok in
            if !ok { DispatchQueue.main.async { self.showAlert("Could not open URL") } }
        }
    }

    private func buildSecondaryUrl(template: String, query: String) -> URL {
        let enc: (String) -> String = { q in
            q.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? q
        }
        let parts = query.components(separatedBy: " - ")
        let artist = parts.first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let album = parts.count > 1 ? parts[1].trimmingCharacters(in: .whitespacesAndNewlines) : ""
        var s = template
            .replacingOccurrences(of: "%s", with: enc(query))
            .replacingOccurrences(of: "%artist%", with: enc(artist.isEmpty ? query : artist))
            .replacingOccurrences(of: "%album%", with: enc(album.isEmpty ? query : album))
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
