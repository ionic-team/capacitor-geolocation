require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name = 'CapacitorGeolocation'
  s.version = package['version']
  s.summary = package['description']
  s.license = package['license']
  s.homepage = package['repository']['url']
  s.author = package['author']
  s.source = { :git => package['repository']['url'], :tag => s.version.to_s }
  s.source_files = 'ios/Sources/**/*.{swift,h,m,c,cc,mm,cpp}'
  s.vendored_frameworks = 'ios/Sources/GeolocationPlugin/OSGeolocationLib.xcframework' 
  s.ios.deployment_target = '14.0'
  s.dependency 'Capacitor'
  #s.dependency 'OSGeolocationLib'
  s.swift_version = '5.1'
end
