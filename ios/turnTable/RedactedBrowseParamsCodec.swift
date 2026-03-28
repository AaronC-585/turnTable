import Foundation

/// Same shape as Android [RedactedBrowseParamsCodec]: JSON object of string keys/values for Redacted query params.
enum RedactedBrowseParamsCodec {

    static func encode(_ params: [(String, String?)]) -> String {
        var o: [String: String] = [:]
        for (k, v) in params {
            if let v = v { o[k] = v }
        }
        guard let data = try? JSONSerialization.data(withJSONObject: o, options: []) else { return "{}" }
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    static func decode(_ json: String) throws -> [(String, String?)] {
        guard let data = json.data(using: .utf8),
              let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            throw NSError(domain: "RedactedBrowseParamsCodec", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"])
        }
        return obj.keys.sorted().map { k in
            let v = obj[k]
            let s: String? = {
                if v is NSNull { return nil }
                if let str = v as? String { return str }
                if let n = v as? NSNumber { return n.stringValue }
                return nil
            }()
            return (k, s)
        }
    }

    static func withPage(_ base: [(String, String?)], page: Int) -> [(String, String?)] {
        base.filter { $0.0 != "page" } + [("page", "\(page)")]
    }
}
