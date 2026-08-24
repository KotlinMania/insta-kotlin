import Testing
import Insta

@Suite("Insta Export Smoke Tests")
struct InstaExportTests {
    @Test("Swift module loads cleanly")
    func testSwiftModuleLoads() throws {
        #expect(true)
    }
}
