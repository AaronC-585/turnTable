import UIKit

/// Mirrors Android `PermissionOnboardingActivity` (camera + continue → Home).
final class OnboardingViewController: UIViewController {

    private static let onboardingSuite = "permission_onboarding"
    private static let onboardingDoneKey = "onboarding_done"

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        title = "Welcome"

        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        let body = UILabel()
        body.translatesAutoresizingMaskIntoConstraints = false
        body.numberOfLines = 0
        body.textColor = UIColor(white: 0.85, alpha: 1)
        body.font = .systemFont(ofSize: 15)
        body.text = """
        turnTable uses the camera to scan 1D barcodes and can open configured search URLs.

        Camera permission is requested when you open the scanner. You can change access later in Settings.

        Internet access is used for optional music APIs and Redacted features when you add an API key.
        """

        let btn = UIButton(type: .system)
        btn.translatesAutoresizingMaskIntoConstraints = false
        btn.setTitle("Continue", for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        btn.addTarget(self, action: #selector(continueTapped), for: .touchUpInside)

        view.addSubview(scroll)
        scroll.addSubview(body)
        view.addSubview(btn)
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            body.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            body.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor),
            body.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor),
            body.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            scroll.bottomAnchor.constraint(equalTo: btn.topAnchor, constant: -16),
            btn.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            btn.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            btn.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
            btn.heightAnchor.constraint(equalToConstant: 48),
        ])
    }

    @objc private func continueTapped() {
        UserDefaults(suiteName: Self.onboardingSuite)?.set(true, forKey: Self.onboardingDoneKey)
        navigationController?.setViewControllers([HomeViewController()], animated: true)
    }
}
