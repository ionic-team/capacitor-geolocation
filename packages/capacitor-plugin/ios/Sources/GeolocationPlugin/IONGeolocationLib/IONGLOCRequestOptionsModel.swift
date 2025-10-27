import Foundation

public struct IONGLOCRequestOptionsModel {
    let timeout: Int

    public init(timeout: Int? = nil) {
        self.timeout = timeout ?? 5000
    }
}
