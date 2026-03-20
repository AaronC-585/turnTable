import UIKit

/// Hub for additional Redacted endpoints (mirrors Android `Redacted*Activity` graph).
final class RedactedMoreViewController: UITableViewController {

    private let apiKey: String

    private struct Item {
        let title: String
        let loader: (RedactedApiClient) -> RedactedResult
    }

    private lazy var items: [Item] = [
        Item(title: "Top 10") { $0.top10(type: "torrents", limit: 10) },
        Item(title: "Bookmarks (torrents)") { $0.bookmarks(type: "torrents") },
        Item(title: "Inbox") { $0.inbox(page: 1, type: nil, sort: nil, search: nil, searchType: nil) },
        Item(title: "Forum main") { $0.forumMain() },
        Item(title: "Notifications") { $0.notifications(page: 1) },
        Item(title: "Announcements") { $0.announcements(page: 1, perPage: 25, orderWay: nil, orderBy: nil) },
        Item(title: "Subscriptions") { $0.subscriptions(showUnreadOnly: true) },
        Item(title: "Requests") { $0.requests(search: nil, page: 1, tags: nil, tagsType: nil, showFilled: nil, extra: []) },
        Item(title: "Wiki (main)") { $0.wiki(id: nil, name: nil) },
    ]

    init(apiKey: String) {
        self.apiKey = apiKey
        super.init(style: .plain)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "More Redacted"
        tableView.backgroundColor = UIColor(white: 0.1, alpha: 1)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        items.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let c = tableView.dequeueReusableCell(withIdentifier: "c") ?? UITableViewCell(style: .default, reuseIdentifier: "c")
        c.backgroundColor = UIColor(white: 0.14, alpha: 1)
        c.textLabel?.textColor = .white
        c.textLabel?.text = items[indexPath.row].title
        return c
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let it = items[indexPath.row]
        let vc = RedactedJsonLoaderViewController(apiKey: apiKey, title: it.title, load: it.loader)
        navigationController?.pushViewController(vc, animated: true)
    }
}
