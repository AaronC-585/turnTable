import UIKit

/// Mirrors Android `HomeActivity`: shortcuts, optional Redacted profile when API key set, pull-to-refresh.
final class HomeViewController: UIViewController {

    private let scroll = UIScrollView()
    private let refresh = UIRefreshControl()
    private let stack = UIStackView()
    private let statusLabel = UILabel()
    private let profileLabel = UILabel()
    private let prefs = SearchPrefs()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        navigationItem.title = "Home"
        navigationController?.navigationBar.prefersLargeTitles = false

        scroll.translatesAutoresizingMaskIntoConstraints = false
        refresh.addTarget(self, action: #selector(refreshPulled), for: .valueChanged)
        scroll.addSubview(refresh)
        scroll.alwaysBounceVertical = true

        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false

        statusLabel.numberOfLines = 0
        statusLabel.textColor = UIColor(white: 0.7, alpha: 1)
        statusLabel.font = .systemFont(ofSize: 14)
        profileLabel.numberOfLines = 0
        profileLabel.textColor = .white
        profileLabel.font = .systemFont(ofSize: 15)

        let shortcuts = makeShortcutRow()
        stack.addArrangedSubview(statusLabel)
        stack.addArrangedSubview(profileLabel)
        stack.addArrangedSubview(shortcuts)

        scroll.addSubview(stack)
        view.addSubview(scroll)

        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -24),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -32),
        ])

        if prefs.redactedApiKey == nil || prefs.redactedApiKey!.isEmpty {
            promptApiKeyIfNeeded()
        } else {
            loadProfile()
        }
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }

    private func makeShortcutRow() -> UIStackView {
        let row = UIStackView()
        row.axis = .vertical
        row.spacing = 12
        row.addArrangedSubview(labeledButton("Scan", action: #selector(openScan)))
        row.addArrangedSubview(labeledButton("Redacted torrent search", action: #selector(openRedacted)))
        row.addArrangedSubview(labeledButton("History", action: #selector(openHistory)))
        row.addArrangedSubview(labeledButton("Settings", action: #selector(openSettings)))
        row.addArrangedSubview(labeledButton("Redacted account (index)", action: #selector(openAccount)))
        row.addArrangedSubview(labeledButton("More Redacted…", action: #selector(openMoreRedacted)))
        return row
    }

    private func labeledButton(_ title: String, action: Selector) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(title, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 17, weight: .medium)
        b.contentHorizontalAlignment = .leading
        b.addTarget(self, action: action, for: .touchUpInside)
        return b
    }

    @objc private func refreshPulled() {
        loadProfile()
    }

    private func promptApiKeyIfNeeded() {
        statusLabel.text = "No Redacted API key. Add one in Settings to load profile, or use Scan / History / Settings below."
        let alert = UIAlertController(title: "Redacted API key", message: "Optional. Add your API key for profile and torrent features.", preferredStyle: .alert)
        alert.addTextField { $0.placeholder = "API key"; $0.isSecureTextEntry = true }
        alert.addAction(UIAlertAction(title: "Save", style: .default) { [weak self] _ in
            let v = alert.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            self?.prefs.redactedApiKey = v.isEmpty ? nil : v
            self?.loadProfile()
        })
        alert.addAction(UIAlertAction(title: "Skip", style: .cancel) { _ in })
        present(alert, animated: true)
    }

    private func loadProfile() {
        guard let key = prefs.redactedApiKey, !key.isEmpty else {
            refresh.endRefreshing()
            navigationItem.title = "Home"
            profileLabel.text = ""
            return
        }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let api = RedactedApiClient(apiKey: key)
            let idx = api.index()
            let name: String
            let body: String
            switch idx {
            case .success(let root):
                let resp = root["response"] as? [String: Any]
                let u = (resp?["username"] as? String) ?? ""
                name = u.isEmpty ? "User" : u
                if let data = try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted]),
                   let s = String(data: data, encoding: .utf8) {
                    body = String(s.prefix(4000))
                } else {
                    body = ""
                }
            case .failure(let msg, _, _):
                name = "Home"
                body = "Index error: \(msg)"
            case .binary:
                name = "Home"
                body = "Unexpected binary"
            }
            DispatchQueue.main.async {
                self?.refresh.endRefreshing()
                self?.navigationItem.title = name
                self?.profileLabel.text = body
                self?.statusLabel.text = ""
            }
        }
    }

    @objc private func openScan() {
        let scan = ScannerViewController()
        scan.delegate = self
        navigationController?.pushViewController(scan, animated: true)
    }

    @objc private func openHistory() {
        navigationController?.pushViewController(SearchHistoryViewController(), animated: true)
    }

    @objc private func openSettings() {
        navigationController?.pushViewController(SettingsViewController(), animated: true)
    }

    @objc private func openRedacted() {
        guard let key = prefs.redactedApiKey, !key.isEmpty else {
            let a = UIAlertController(title: "API key required", message: "Add your Redacted API key in Settings.", preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "OK", style: .default))
            present(a, animated: true)
            return
        }
        navigationController?.pushViewController(RedactedBrowseViewController(apiKey: key), animated: true)
    }

    @objc private func openAccount() {
        guard let key = prefs.redactedApiKey, !key.isEmpty else {
            openSettings()
            return
        }
        navigationController?.pushViewController(RedactedAccountViewController(apiKey: key), animated: true)
    }

    @objc private func openMoreRedacted() {
        guard let key = prefs.redactedApiKey, !key.isEmpty else {
            openSettings()
            return
        }
        navigationController?.pushViewController(RedactedMoreViewController(apiKey: key), animated: true)
    }
}

extension HomeViewController: ScannerDelegate {
    func scanner(_ scanner: ScannerViewController, didScan code: String) {
        navigationController?.popViewController(animated: false)
        navigationController?.pushViewController(SearchViewController(barcode: code), animated: true)
    }
}
