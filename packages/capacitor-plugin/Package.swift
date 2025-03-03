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
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .binaryTarget(
            name: "IONGeolocationLib",
            url: "https://github.com/ionic-team/ion-ios-geolocation/releases/download/1.0.0/IONGeolocationLib.zip",
            checksum: "b117d3681a947f5d367e79abdb3bfc9abf7ab070ea5279d7da634ddd2d54ffdb" // sha-256
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
