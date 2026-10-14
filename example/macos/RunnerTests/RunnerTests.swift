import XCTest

@testable import file_picker_darwin

/// Unit tests for the parts of the native implementation that do not depend
/// on a system picker, which tests cannot drive.
final class PickOutcomeTests: XCTestCase {
    func testNothingPickedIsACancellation() {
        let outcome = PickOutcome<String>.resolve(pickedCount: 0, loaded: [])
        guard case .cancelled = outcome else {
            return XCTFail("Expected cancelled, got \(outcome)")
        }
    }

    func testEveryPickedItemLoaded() {
        let outcome = PickOutcome.resolve(pickedCount: 2, loaded: ["a", "b"])
        guard case .loaded(let items) = outcome else {
            return XCTFail("Expected loaded, got \(outcome)")
        }
        XCTAssertEqual(items, ["a", "b"])
    }

    func testNothingLoadedIsAFailureNotACancellation() {
        // Picking items that cannot be loaded, such as iCloud only photos
        // while offline, must not look like a cancellation (#2248).
        let outcome = PickOutcome<String>.resolve(pickedCount: 2, loaded: [])
        guard case .loadFailed = outcome else {
            return XCTFail("Expected loadFailed, got \(outcome)")
        }
    }

    func testPartialFailuresKeepTheLoadedItems() {
        let outcome = PickOutcome.resolve(pickedCount: 3, loaded: ["a"])
        guard case .loaded(let items) = outcome else {
            return XCTFail("Expected loaded, got \(outcome)")
        }
        XCTAssertEqual(items, ["a"])
    }
}
