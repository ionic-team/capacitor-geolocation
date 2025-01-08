// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "GeolocationCapacitor",
    platforms: [.iOS(.v13)],
    products: [
        .library(
            name: "GeolocationCapacitor",
            targets: ["GeolocationPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", branch: "main")
    ],
    targets: [
        .target(
            name: "GeolocationPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/GeolocationPlugin"),
        .testTarget(
            name: "GeolocationPluginTests",
            dependencies: ["GeolocationPlugin"],
            path: "ios/Tests/GeolocationTests")
    ]
)
