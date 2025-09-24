//
//  WhisperLive_iOS_ClientApp.swift
//  WhisperLive_iOS_Client
//
//  Created by 바견규 on 6/17/25.
//

import SwiftUI
import UIKit
import GoogleMobileAds

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        GADMobileAds.sharedInstance().start(completionHandler: nil)
        return true
    }
}

@main
struct WhisperLive_iOS_ClientApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            RecordingView {
                // Handle dismiss action here, or leave it empty for now
                print("RecordingView dismissed")
            }
        }
    }
}
