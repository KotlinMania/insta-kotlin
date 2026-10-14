import Testing
import Insta

@Suite("Insta Export Smoke Tests")
struct InstaExportTests {
    @Test("Content creation, accessors, and tree walk")
    func testContentAndWalk() throws {
        let userContent = content.Content.Struct(
            name: "User",
            fields: [
                content.Content.Field(
                    name: "id",
                    value: content.Content.Companion.shared.from(value: Int64(42))
                ),
                content.Content.Field(
                    name: "name",
                    value: content.Content.Companion.shared.from(value: "Alice")
                ),
                content.Content.Field(
                    name: "bytes",
                    value: content.Content.Companion.shared.from(value: [Int8(1), Int8(2), Int8(3)])
                )
            ]
        )

        var visitedStrings: [String] = []
        let visitor = content.contentVisitor { node in
            if let str = node.asString() {
                visitedStrings.append(str)
            }
            return true
        }
        userContent.walk(visit: visitor)
        #expect(visitedStrings == ["Alice"])

        let json = serializeContent(content: userContent, format: .Json)
        #expect(json.contains("\"id\": 42"))
        #expect(json.contains("\"name\": \"Alice\""))
    }

    @Test("Filters and FilterRule replacement")
    func testFilters() throws {
        let filters = Filters.Companion.shared.from(
            rules: [
                FilterRule(pattern: "\\d+", replacement: "[NUM]"),
                FilterRule(pattern: "secret", replacement: "[REDACTED]")
            ]
        )
        let filtered = filters.applyTo(s: "user 123 has secret token")
        #expect(filtered == "user [NUM] has [REDACTED] token")
    }

    @Test("Selector, DynamicRedactionCallback, and Settings withSettings")
    func testRedactionsAndSettings() throws {
        let item = content.Content.Struct(
            name: "Record",
            fields: [
                content.Content.Field(
                    name: "id",
                    value: content.Content.Companion.shared.from(value: Int64(999))
                ),
                content.Content.Field(
                    name: "role",
                    value: content.Content.Companion.shared.from(value: "admin")
                )
            ]
        )

        let dynRedaction = dynamicRedaction(
            callback: dynamicRedactionCallback { _, path in
                content.Content.Companion.shared.from(value: "redacted:\(path.toString())")
            }
        )
        let redactions = Redactions.Companion.shared.from(
            entries: [
                RedactionEntry(selector: ".id", redaction: dynRedaction)
            ]
        )
        let redacted = redactions.applyToContent(content: item)
        let rendered = content.toStringPretty(value: redacted)
        #expect(rendered.contains("\"id\": \"redacted:.id\""))
        #expect(rendered.contains("\"role\": \"admin\""))

        withSettings(
            configure: settingsConfigurer { settings in
                settings.addRedaction(
                    selector: ".id",
                    replacement: Redaction.Companion.shared.from(value: "[ID]")
                )
            },
            block: snapshotBlock {
                assertJsonSnapshot(
                    value: item,
                    reference: "{\n  \"id\": \"[ID]\",\n  \"role\": \"admin\"\n}",
                    name: "swift_record",
                    expr: "item"
                )
            }
        )
    }

    @Test("Snapshot and ContentError round-trip")
    func testSnapshotAndContentError() throws {
        let meta = MetaData(
            source: "InstaExportTests.swift",
            assertionLine: 10,
            description: "Swift export test",
            expression: "value",
            info: nil,
            inputFile: nil,
            snapshotKind: SnapshotKind.Text.shared
        )
        let textContents = TextSnapshotContents(
            contents: "hello from swift",
            kind: .Inline
        )
        let snapshot = Snapshot.Companion.shared.fromComponents(
            moduleName: "SwiftTestHarness",
            snapshotName: "hello_snap",
            metadata: meta,
            snapshot: SnapshotContents.Text(value: textContents)
        )
        #expect(snapshot.moduleName() == "SwiftTestHarness")
        #expect(snapshot.snapshotName() == "hello_snap")
        #expect(snapshot.metadata().description() == "Swift export test")

        let error = content.ContentError.MissingField.shared
        #expect(error.fmt().contains("missing"))
    }
}

