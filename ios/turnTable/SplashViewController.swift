import UIKit

/// Mirrors Android `SplashActivity` (~2.5s then Home or onboarding).
final class SplashViewController: UIViewController {

    private static let splashMs: TimeInterval = 2.5
    private static let onboardingSuite = "permission_onboarding"
    private static let onboardingDoneKey = "onboarding_done"

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.1, alpha: 1)
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = "turnTable"
        label.textColor = .white
        label.font = .systemFont(ofSize: 28, weight: .semibold)
        label.textAlignment = .center
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        TurnTableUpdateCoordinator.requestSplashLaunchUpdateCheckIfDue()
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.splashMs) { [weak self] in
            self?.goNext()
        }
    }

    private func goNext() {
        let done = UserDefaults(suiteName: Self.onboardingSuite)?.bool(forKey: Self.onboardingDoneKey) ?? false
        let nav = navigationController
        if done {
            nav?.setViewControllers([HomeViewController()], animated: true)
        } else {
            nav?.setViewControllers([OnboardingViewController()], animated: true)
        }
    }
}
