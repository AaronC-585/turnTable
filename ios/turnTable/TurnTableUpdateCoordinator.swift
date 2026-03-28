import UIKit

/// Mirrors Android [UpdateCheckCoordinator]: splash + 24h-throttled background check, pending delivery after splash, manual check from About/Settings.
enum TurnTableUpdateCoordinator {

    private static let intervalMs: Double = 24 * 60 * 60 * 1000
    private static let ud = UserDefaults(suiteName: "app_update_prefs")!
    private static let keyLastBg = "last_bg_check_wall_ms"
    private static let keySkipped = "skipped_release_tag"

    private static let lock = NSLock()
    private static var pending: (info: TurnTableGithubUpdate.ReleaseInfo, local: String)?

    // MARK: - Splash / background (throttled)

    static func requestSplashLaunchUpdateCheckIfDue() {
        guard TurnTableGithubUpdate.isConfigured else { return }
        let now = Date().timeIntervalSince1970 * 1000
        let last = ud.double(forKey: keyLastBg)
        if last > 0, now - last < intervalMs { return }
        ud.set(now, forKey: keyLastBg)

        TurnTableGithubUpdate.fetchReleaseInfo { result in
            DispatchQueue.main.async {
                guard case .success(let info) = result else { return }
                let local = TurnTableGithubUpdate.localVersionString()
                guard TurnTableGithubUpdate.isRemoteNewer(than: local, remoteTag: info.tagNameRaw) else { return }
                let norm = TurnTableGithubUpdate.normalizedTag(info.tagNameRaw)
                if norm == ud.string(forKey: keySkipped) { return }
                lock.lock()
                pending = (info, local)
                lock.unlock()
                tryDeliverPendingIfForeground()
            }
        }
    }

    static func consumePendingUpdateIfAny(from viewController: UIViewController) {
        if viewController is SplashViewController { return }
        lock.lock()
        let p = pending
        pending = nil
        lock.unlock()
        guard let p = p else { return }
        if viewController.isBeingDismissed { return }
        let norm = TurnTableGithubUpdate.normalizedTag(p.info.tagNameRaw)
        if norm == ud.string(forKey: keySkipped) { return }
        showUpdateAlert(from: viewController, info: p.info, localVersion: p.local, onSkipThisRelease: {
            ud.set(norm, forKey: keySkipped)
        })
    }

    static func requestBackgroundCheckIfDue(from viewController: UIViewController) {
        guard TurnTableGithubUpdate.isConfigured else { return }
        let now = Date().timeIntervalSince1970 * 1000
        let last = ud.double(forKey: keyLastBg)
        if last > 0, now - last < intervalMs { return }
        ud.set(now, forKey: keyLastBg)

        TurnTableGithubUpdate.fetchReleaseInfo { result in
            DispatchQueue.main.async {
                guard viewController.viewIfLoaded?.window != nil else { return }
                guard case .success(let info) = result else { return }
                let local = TurnTableGithubUpdate.localVersionString()
                guard TurnTableGithubUpdate.isRemoteNewer(than: local, remoteTag: info.tagNameRaw) else { return }
                let norm = TurnTableGithubUpdate.normalizedTag(info.tagNameRaw)
                if norm == ud.string(forKey: keySkipped) { return }
                showUpdateAlert(from: viewController, info: info, localVersion: local, onSkipThisRelease: {
                    ud.set(norm, forKey: keySkipped)
                })
            }
        }
    }

    private static func tryDeliverPendingIfForeground() {
        guard let top = UIApplication.shared.topMostViewController() else { return }
        if top is SplashViewController { return }
        lock.lock()
        let p = pending
        pending = nil
        lock.unlock()
        guard let p = p else { return }
        let norm = TurnTableGithubUpdate.normalizedTag(p.info.tagNameRaw)
        if norm == ud.string(forKey: keySkipped) { return }
        showUpdateAlert(from: top, info: p.info, localVersion: p.local, onSkipThisRelease: {
            ud.set(norm, forKey: keySkipped)
        })
    }

    // MARK: - Manual (About / Settings; no throttle)

    static func presentManualCheck(from viewController: UIViewController) {
        guard TurnTableGithubUpdate.isConfigured else {
            let a = UIAlertController(
                title: "Updates",
                message: "GitHub update manifest is not configured for this build.",
                preferredStyle: .alert
            )
            a.addAction(UIAlertAction(title: "OK", style: .default))
            viewController.present(a, animated: true)
            return
        }
        let wait = UIAlertController(title: "Check for updates", message: "Loading…", preferredStyle: .alert)
        viewController.present(wait, animated: true)
        TurnTableGithubUpdate.fetchReleaseInfo { result in
            wait.dismiss(animated: true) {
                let local = TurnTableGithubUpdate.localVersionString()
                switch result {
                case .failure(let e):
                    let a = UIAlertController(title: "Update check failed", message: e.localizedDescription, preferredStyle: .alert)
                    a.addAction(UIAlertAction(title: "OK", style: .default))
                    viewController.present(a, animated: true)
                case .success(let info):
                    if !TurnTableGithubUpdate.isRemoteNewer(than: local, remoteTag: info.tagNameRaw) {
                        let a = UIAlertController(
                            title: "Up to date",
                            message: "You have version \(local). The latest listed release is \(TurnTableGithubUpdate.displayVersion(from: info)).",
                            preferredStyle: .alert
                        )
                        a.addAction(UIAlertAction(title: "OK", style: .default))
                        viewController.present(a, animated: true)
                        return
                    }
                    showUpdateAlert(from: viewController, info: info, localVersion: local, onSkipThisRelease: {
                        ud.set(TurnTableGithubUpdate.normalizedTag(info.tagNameRaw), forKey: keySkipped)
                    })
                }
            }
        }
    }

    private static func showUpdateAlert(
        from viewController: UIViewController,
        info: TurnTableGithubUpdate.ReleaseInfo,
        localVersion: String,
        onSkipThisRelease: @escaping () -> Void,
    ) {
        let remoteLabel = TurnTableGithubUpdate.displayVersion(from: info)
        var message = "You have \(localVersion). Latest: \(remoteLabel)."
        if let body = info.body?.nilIfEmpty {
            let plain = TurnTableGithubUpdate.plainTextPreview(body, maxLength: 400)
            if !plain.isEmpty {
                message += "\n\n\(plain)"
            }
        }
        let a = UIAlertController(title: "Update available", message: message, preferredStyle: .alert)
        if let zip = info.iosZipUrl, let u = URL(string: zip) {
            a.addAction(UIAlertAction(title: "Open iOS artifact (.zip)", style: .default) { _ in
                UIApplication.shared.open(u, options: [:], completionHandler: nil)
            })
        }
        if let u = URL(string: info.htmlUrl) {
            a.addAction(UIAlertAction(title: "Open release page", style: .default) { _ in
                UIApplication.shared.open(u, options: [:], completionHandler: nil)
            })
        }
        a.addAction(UIAlertAction(title: "Skip this version", style: .destructive) { _ in
            onSkipThisRelease()
        })
        a.addAction(UIAlertAction(title: "Later", style: .cancel))
        viewController.present(a, animated: true)
    }
}

// MARK: - Top VC

private extension UIApplication {
    func topMostViewController() -> UIViewController? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .rootViewController?
            .topPresented()
    }
}

private extension UIViewController {
    func topPresented() -> UIViewController {
        if let p = presentedViewController { return p.topPresented() }
        if let nav = self as? UINavigationController, let vis = nav.visibleViewController {
            return vis.topPresented()
        }
        if let tab = self as? UITabBarController, let sel = tab.selectedViewController {
            return sel.topPresented()
        }
        return self
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
