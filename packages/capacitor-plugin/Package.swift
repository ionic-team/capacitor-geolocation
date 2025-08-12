// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorGeolocation",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "CapacitorGeolocation",
            targets: ["GeolocationPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .binaryTarget(
            name: "IONGeolocationLib",
            url: "https://github.com/ionic-team/ion-ios-geolocation/releases/download/1.0.1/IONGeolocationLib.zip",
            checksum: "80e0283964bce3c5d05f61ff4acf4e029305f58d1699a7f16453058ba876bc21" // sha-256
        ),
        .target(
            name: "GeolocationPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                "IONGeolocationLib"
            ],
            path: "ios/Sources/GeolocationPlugin"),
        .testTarget(
            name: "GeolocationPluginTests",
            dependencies: ["GeolocationPlugin"],
            path: "ios/Tests/GeolocationTests")
    ]
)
