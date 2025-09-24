import StoreKit
import SwiftUI
import Security

enum PurchaseState {
    case notPurchased, loading, purchased, error
}

class IAPManager: ObservableObject {
    @Published var purchaseState: PurchaseState = .loading
    @Published var products: [Product] = []
    
    private var transactionListener: Task<Void, Never>? = nil
    
    static let premiumProductID = "com.example.whisperlive.premium_remove_ads"
    
    init() {
        startTransactionListener()
        loadProducts()
        Task {
            await checkEntitlementsAndKeychain()
        }
    }
    
    private func startTransactionListener() {
        transactionListener = Task {
            for await result in StoreKit.Transaction.updates {
                switch result {
                case .verified(let transaction):
                    await handleVerifiedTransaction(transaction)
                case .unverified(_, let error):
                    print("Unverified transaction: \(error)")
                @unknown default:
                    break
                }
            }
        }
    }
    
    private func loadProducts() {
        Task {
            do {
                let productIDs: Set<String> = [Self.premiumProductID]
                let products = try await Product.products(for: productIDs)
                await MainActor.run {
                    self.products = products
                    self.purchaseState = .notPurchased
                }
            } catch {
                await MainActor.run {
                    self.purchaseState = .error
                }
            }
        }
    }
    
    
    private func handleVerifiedTransaction(_ transaction: StoreKit.Transaction) async {
        if transaction.productID == Self.premiumProductID {
            await MainActor.run {
                self.purchaseState = .purchased
                self.savePremiumStatus(true)
            }
        }
        await transaction.finish()
    }
    
    func purchasePremium() {
        guard let product = products.first(where: { $0.id == Self.premiumProductID }) else { return }
        
        Task {
            do {
                let result = try await product.purchase()
                switch result {
                case .success(let verification):
                    switch verification {
                    case .verified(let transaction):
                        await handleVerifiedTransaction(transaction)
                    case .unverified:
                        await MainActor.run {
                            self.purchaseState = .error
                        }
                    }
                case .userCancelled:
                    break
                case .pending:
                    break
                @unknown default:
                    break
                }
            } catch {
                await MainActor.run {
                    self.purchaseState = .error
                }
            }
        }
    }

    func restorePurchases() async {
        do {
            try await AppStore.sync()
            // Re-check entitlements after sync
            var isPremiumFromEntitlements = false
            for await result in StoreKit.Transaction.currentEntitlements {
                switch result {
                case .verified(let transaction):
                    if transaction.productID == Self.premiumProductID {
                        isPremiumFromEntitlements = true
                        await transaction.finish()
                    }
                case .unverified:
                    break
                @unknown default:
                    break
                }
            }
            await MainActor.run {
                if isPremiumFromEntitlements {
                    self.purchaseState = .purchased
                    self.savePremiumStatus(true)
                } else {
                    self.purchaseState = .notPurchased
                }
            }
        } catch {
            await MainActor.run {
                self.purchaseState = .error
            }
        }
    }
    
    private let premiumKey = "com.example.whisperlive.isPremium"

    private func loadPremiumStatus() -> Bool? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: premiumKey,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess,
              let data = result as? Data,
              data.count == 1 else { return nil }
        return data[0] == 1
    }

    private func savePremiumStatus(_ isPremium: Bool) {
        let data = Data(isPremium ? [1] : [0])
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: premiumKey,
            kSecValueData as String: data
        ]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }

    private func checkEntitlementsAndKeychain() async {
        var isPremiumFromEntitlements = false
        for await result in StoreKit.Transaction.currentEntitlements {
            switch result {
            case .verified(let transaction):
                if transaction.productID == Self.premiumProductID {
                    isPremiumFromEntitlements = true
                    await transaction.finish()
                }
            case .unverified:
                break
            @unknown default:
                break
            }
        }
        await MainActor.run {
            if isPremiumFromEntitlements {
                self.purchaseState = .purchased
                self.savePremiumStatus(true)
            } else if let saved = self.loadPremiumStatus(), saved {
                self.purchaseState = .purchased
            } else {
                self.purchaseState = .notPurchased
            }
        }
    }

    func isPremium() -> Bool {
        purchaseState == .purchased
    }
    
    deinit {
        transactionListener?.cancel()
    }
}
