# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 10/31 (32.3%)
- **Function parity:** 138/629 matched (target 225) — 21.9%
- **Class/type parity:** 30/128 matched (target 87) — 23.4%
- **Combined symbol parity:** 168/757 matched (target 312) — 22.2%
- **Average inline-code cosine:** 0.39 (function body across 9 matched files)
- **Average documentation cosine:** 0.51 (doc text across 9 matched files)
- **Cheat-zeroed Files:** 2
- **Critical Issues:** 6 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. insta.settings

- **Target:** `insta.Settings`
- **Similarity:** 0.63
- **Dependents:** 3
- **Priority Score:** 3085503.8
- **Functions:** 43/49 matched (target 60)
- **Missing functions:** `description`, `set_info`, `add_redaction_impl`, `bind_async`, `poll`, `drop`
- **Types:** 4/6 matched (target 4)
- **Missing types:** `BindingFuture`, `Output`

### 2. insta.env

- **Target:** `insta.Env`
- **Similarity:** 0.00
- **Dependents:** 2
- **Priority Score:** 2354010.0
- **Functions:** 0/31 matched (target 1)
- **Missing functions:** `get_tool_config`, `resolve_fallback`, `fmt`, `source`, `from_workspace`, `require_full_match`, `force_pass`, `output_behavior`, `snapshot_update`, `glob_fail_fast`, `test_runner`, `test_runner_fallback`, `test_unreferenced`, `auto_review`, `auto_accept_unseen`, `review_include_hidden`, `review_include_ignored`, `review_warn_undiscovered`, `disable_nextest_doctest`, `snapshot_update_behavior`, `get_cargo_workspace`, `get_cargo_workspace_from_metadata`, `test_get_cargo_workspace_manifest_dir`, `test_get_cargo_workspace_insta_workspace`, `from_str`, `memoize_snapshot_file`, `memoize_warning`, `get_pending_dir`, `strip_prefix_with_fallback`, `pending_snapshot_path`, `resolve`
- **Types:** 5/9 matched (target 5)
- **Missing types:** `Error`, `SnapshotUpdateBehavior`, `Workspace`, `Err`
- **Tests:** 0/2 matched

### 3. insta.filters

- **Target:** `insta.Filters`
- **Similarity:** 0.63
- **Dependents:** 2
- **Priority Score:** 2000903.8
- **Functions:** 8/8 matched (target 10)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_
- **Tests:** 3/3 matched

### 4. insta.snapshot

- **Target:** `insta.Snapshot [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 476410.0
- **Functions:** 11/56 matched (target 13)
- **Missing functions:** `new`, `load_batch`, `save_batch`, `save`, `source`, `assertion_line`, `expression`, `description`, `get_relative_source`, `input_file`, `from_file`, `module_name`, `snapshot_name`, `metadata`, `save_with_metadata`, `build_binary_path`, `save_new`, `from`, `matches_legacy`, `as_str_legacy`, `needs_escaped_format`, `from_inline_literal`, `normalize`, `to_inline`, `fmt`, `eq`, `required_hashes`, `test_required_hashes`, `leading_space`, `min_indentation`, `normalize_inline`, `test_normalize_inline_snapshot`, `normalized_of_literal`, `names_of_path`, `test_names_of_path`, `legacy_inline_normalize`, `test_snapshot_contents_to_inline`, `test_escaped_format_preserves_content`, `test_snapshot_contents_hashes`, `test_min_indentation`, `test_min_indentation_additional`, `test_inline_snapshot_value_newline`, `test_parse_yaml_error`, `test_ownership`, `test_empty_lines`
- **Types:** 6/8 matched
- **Missing types:** `PendingInlineSnapshot`, `TmpSnapshotKind`
- **Tests:** 0/12 matched

### 5. insta.runtime

- **Target:** `insta.Runtime`
- **Similarity:** 0.02
- **Dependents:** 0
- **Priority Score:** 212509.8
- **Functions:** 2/19 matched (target 5)
- **Missing functions:** `from`, `is_doctest`, `detect_snapshot_name`, `add_suffix_to_snapshot_name`, `get_snapshot_filename`, `prepare`, `localize_path`, `new_snapshot`, `cleanup_passing`, `cleanup_previous_pending_binary_snapshots`, `update_snapshot`, `print_snapshot_info`, `finalize`, `path_relative_from`, `prevent_inline_duplicate`, `record_snapshot_duplicate`, `allow_duplicates`
- **Types:** 2/6 matched (target 5)
- **Missing types:** `InlineValue`, `SnapshotName`, `BinarySnapshotValue`, `SnapshotAssertionContext`
- **Lint issues:** 1

### 6. insta.output

- **Target:** `insta.Output`
- **Similarity:** 0.36
- **Dependents:** 0
- **Priority Score:** 102206.4
- **Functions:** 11/21 matched (target 11)
- **Missing functions:** `new`, `print_info`, `print_line`, `trailing_newline`, `detect_newlines`, `newlines_matter`, `render_invisible`, `encode_file_link_escape`, `invoke_external_diff_tool`, `test_invisible`
- **Types:** 1/1 matched
- **Missing types:** _none_
- **Tests:** 0/1 matched

### 7. insta.redaction

- **Target:** `insta.Redaction`
- **Similarity:** 0.66
- **Dependents:** 0
- **Priority Score:** 22803.4
- **Functions:** 20/21 matched (target 29)
- **Missing functions:** `fmt`
- **Types:** 6/7 matched (target 16)
- **Missing types:** `SelectParser`
- **Tests:** 1/1 matched

### 8. insta.utils

- **Target:** `insta.Utils`
- **Similarity:** 0.38
- **Dependents:** 0
- **Priority Score:** 10906.2
- **Functions:** 7/8 matched (target 16)
- **Missing functions:** `fmt`
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 9. content.json

- **Target:** `content.Json`
- **Similarity:** 0.83
- **Dependents:** 0
- **Priority Score:** 2301.7
- **Functions:** 21/21 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_
- **Tests:** 4/4 matched

### 10. content.mod

- **Target:** `content.Mod [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 1710.0
- **Functions:** 15/15 matched (target 59)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 41)
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

