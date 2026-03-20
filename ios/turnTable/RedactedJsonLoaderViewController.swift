import UIKit

/// Generic JSON viewer for Redacted API calls (mirrors many `Redacted*Activity` detail screens).
final class RedactedJsonLoaderViewController: UIViewController {

    private let apiKey: String
    private let loadBlock: (RedactedApiClient) -> RedactedResult
    private let textView = UITextView()

    init(apiKey: String, title: String, load: @escaping (RedactedApiClient) -> RedactedResult) {
        self.apiKey = apiKey
        self.loadBlock = load
        super.init(nibName: nil, bundle: nil)
        self.title = title
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Home", style: .plain, target: self, action: #selector(goHome))

        textView.translatesAutoresizingMaskIntoConstraints = false
        textView.backgroundColor = UIColor(white: 0.14, alpha: 1)
        textView.textColor = .white
        textView.font = .monospacedSystemFont(ofSize: 12, weight: .regular)
        textView.isEditable = false
        view.addSubview(textView)
        NSLayoutConstraint.activate([
            textView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8),
            textView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 8),
            textView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -8),
            textView.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -8),
        ])

        DispatchQueue.global(qos: .userInitiated).async {
            let r = self.loadBlock(RedactedApiClient(apiKey: self.apiKey))
            let s: String
            switch r {
            case .success(let root):
                if let data = try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted]),
                   let str = String(data: data, encoding: .utf8) {
                    s = str
                } else {
                    s = "\(root)"
                }
            case .failure(let msg, _, _):
                s = msg
            case .binary(let data, let ct):
                s = "Binary \(data.count) bytes, \(ct ?? "?")"
            }
            DispatchQueue.main.async { self.textView.text = s }
        }
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }
}
