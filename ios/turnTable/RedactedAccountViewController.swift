import UIKit

/// Mirrors Android `RedactedAccountActivity` (`index` JSON).
final class RedactedAccountViewController: UIViewController {

    private let apiKey: String
    private let textView = UITextView()

    init(apiKey: String) {
        self.apiKey = apiKey
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        title = "Account (index)"
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
            let r = RedactedApiClient(apiKey: self.apiKey).index()
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
            case .binary:
                s = "<binary>"
            }
            DispatchQueue.main.async { self.textView.text = s }
        }
    }

    @objc private func goHome() {
        AppNavigation.navigateToHome(from: self)
    }
}
