import AuthenticationServices
import ComposeApp
import Security
import UIKit

/// #187: the parts of sign-in Kotlin cannot reach on its own — the system browser for Google,
/// Sign in with Apple, and the Keychain that holds the refresh token. Kotlin owns the rest.
final class AuthBridge: NSObject, IosAuthBridge {
    /// Kotlin's word for a sign-in the person closed themselves; it shows no error for it.
    private static let cancelled = "cancelled"
    private static let service = "com.ptut.expense"

    // ASWebAuthenticationSession and the Apple controller must outlive the call that starts them.
    private var webSession: ASWebAuthenticationSession?
    private var appleRequest: AppleRequest?

    func openWebAuth(url: String, callbackScheme: String, completion: @escaping (String?, String?) -> Void) {
        guard let start = URL(string: url) else {
            completion(nil, "The sign-in address was not valid")
            return
        }
        let session = ASWebAuthenticationSession(url: start, callbackURLScheme: callbackScheme) { [weak self] callback, error in
            self?.webSession = nil
            if let callback {
                completion(callback.absoluteString, nil)
            } else if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
                completion(nil, Self.cancelled)
            } else {
                completion(nil, error?.localizedDescription ?? "Google sign-in stopped")
            }
        }
        session.presentationContextProvider = self
        webSession = session
        session.start()
    }

    func signInWithApple(hashedNonce: String, completion: @escaping (String?, String?, String?) -> Void) {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = hashedNonce
        let pending = AppleRequest { [weak self] token, name, error in
            self?.appleRequest = nil
            completion(token, name, error)
        }
        appleRequest = pending
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = pending
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func keychainLoad(key: String) -> String? {
        var query = baseQuery(key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func keychainSave(key: String, value: String) {
        let data = Data(value.utf8)
        let query = baseQuery(key)
        let update: [String: Any] = [kSecValueData as String: data]
        if SecItemUpdate(query as CFDictionary, update as CFDictionary) == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(insert as CFDictionary, nil)
        }
    }

    func keychainDelete(key: String) {
        SecItemDelete(baseQuery(key) as CFDictionary)
    }

    private func baseQuery(_ key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: key,
        ]
    }

    fileprivate static func keyWindow() -> UIWindow {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.flatMap(\.windows).first(where: \.isKeyWindow) ?? scenes.first?.windows.first ?? UIWindow()
    }
}

extension AuthBridge: ASWebAuthenticationPresentationContextProviding, ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        Self.keyWindow()
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        Self.keyWindow()
    }
}

/// One Sign in with Apple request. Apple sends the name only the first time, so it goes up with the token.
private final class AppleRequest: NSObject, ASAuthorizationControllerDelegate {
    private let finish: (String?, String?, String?) -> Void

    init(finish: @escaping (String?, String?, String?) -> Void) {
        self.finish = finish
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let tokenData = credential.identityToken,
            let token = String(data: tokenData, encoding: .utf8)
        else {
            finish(nil, nil, "Apple sent no sign-in token")
            return
        }
        finish(token, credential.fullName?.givenName, nil)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        if let error = error as? ASAuthorizationError, error.code == .canceled {
            finish(nil, nil, "cancelled")
        } else {
            finish(nil, nil, error.localizedDescription)
        }
    }
}
