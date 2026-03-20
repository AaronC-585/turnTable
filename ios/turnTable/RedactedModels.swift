import Foundation

/// Mirrors Android `redacted.RedactedResult`.
enum RedactedResult {
    case success(root: [String: Any])
    case failure(String, Int, Int?)
    case binary(Data, String?)

    var responseObject: [String: Any]? {
        guard case .success(let root) = self else { return nil }
        return root["response"] as? [String: Any]
    }

    var responseArray: [Any]? {
        guard case .success(let root) = self else { return nil }
        return root["response"] as? [Any]
    }
}

func redactedResponseOrNull(_ r: RedactedResult) -> [String: Any]? {
    if case .success(let root) = r { return root["response"] as? [String: Any] }
    return nil
}
