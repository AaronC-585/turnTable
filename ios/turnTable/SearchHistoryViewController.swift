import UIKit

/// Mirrors Android `SearchHistoryActivity`.
final class SearchHistoryViewController: UITableViewController {

    private var entries: [SearchHistoryEntry] = []

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "History"
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "c")
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Scan", style: .plain, target: self, action: #selector(openScan))
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        entries = SearchHistoryStore.getAll()
        tableView.reloadData()
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }

    @objc private func openScan() {
        let scan = ScannerViewController()
        scan.delegate = self
        navigationController?.pushViewController(scan, animated: true)
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        max(entries.count, 1)
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let c = tableView.dequeueReusableCell(withIdentifier: "c", for: indexPath)
        c.backgroundColor = UIColor(white: 0.14, alpha: 1)
        c.textLabel?.numberOfLines = 0
        c.textLabel?.textColor = .white
        if entries.isEmpty {
            c.textLabel?.text = "No search history yet."
            return c
        }
        let e = entries[indexPath.row]
        c.textLabel?.text = "\(e.barcode)\n\(e.title)"
        return c
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard !entries.isEmpty else { return }
        let e = entries[indexPath.row]
        navigationController?.pushViewController(SearchViewController(barcode: e.barcode), animated: true)
    }
}

extension SearchHistoryViewController: ScannerDelegate {
    func scanner(_ scanner: ScannerViewController, didScan code: String) {
        navigationController?.popViewController(animated: false)
        navigationController?.pushViewController(SearchViewController(barcode: code), animated: true)
    }
}
