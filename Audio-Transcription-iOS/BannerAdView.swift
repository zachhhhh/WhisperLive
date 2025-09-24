import SwiftUI
import UIKit
import GoogleMobileAds
import AppTrackingTransparency

struct BannerAdView: UIViewRepresentable {
    let adUnitID: String

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> GADBannerView {
        let bannerView = GADBannerView(adSize: GADAdSizeBanner)
        bannerView.adUnitID = adUnitID
        bannerView.delegate = context.coordinator
        context.coordinator.bannerView = bannerView

        // Try to set a rootViewController immediately if available
        if let root = context.coordinator.rootViewController() {
            bannerView.rootViewController = root
        }

        // Request ATT (optional) and then load an ad. Do not block ad loading on ATT.
        DispatchQueue.main.async {
            if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
                ATTrackingManager.requestTrackingAuthorization { _ in
                    DispatchQueue.main.async {
                        context.coordinator.loadAdIfNeeded()
                    }
                }
            } else {
                context.coordinator.loadAdIfNeeded()
            }
        }

        return bannerView
    }

    func updateUIView(_ uiView: GADBannerView, context: Context) {
        context.coordinator.bannerView = uiView
        if uiView.rootViewController == nil,
           let root = context.coordinator.rootViewController() {
            uiView.rootViewController = root
        }
        context.coordinator.loadAdIfNeeded()
    }

    class Coordinator: NSObject, GADBannerViewDelegate {
        weak var bannerView: GADBannerView?
        private var didLoadAd = false

        func rootViewController() -> UIViewController? {
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first(where: { $0.isKeyWindow })?.rootViewController
        }

        func loadAdIfNeeded() {
            guard let banner = bannerView else { return }
            guard !didLoadAd else { return }
            guard banner.rootViewController != nil else { return }

            banner.load(GADRequest())
            didLoadAd = true
        }
    }
}
