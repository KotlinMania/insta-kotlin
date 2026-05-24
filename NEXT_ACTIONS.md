# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 1/22 (4.5%)
- **Function parity:** 7/527 matched (target 46) — 1.3%
- **Class/type parity:** 2/85 matched (target 38) — 2.4%
- **Combined symbol parity:** 9/612 matched (target 84) — 1.5%
- **Average inline-code cosine:** 0.00 (function body across 0 matched files)
- **Average documentation cosine:** 0.00 (doc text across 0 matched files)
- **Cheat-zeroed Files:** 1
- **Critical Issues:** 1 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. content.mod

- **Target:** `content.Mod [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 81710.0
- **Functions:** 7/15 matched (target 46)
- **Missing functions:** `fmt`, `resolve_inner_mut`, `as_str`, `as_u64`, `as_u128`, `as_i64`, `as_i128`, `as_f64`
- **Types:** 2/2 matched (target 38)
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `vendored.mod` | `content.yaml.vendored.Mod` | 0 | `content/yaml/vendored/mod.rs` | `content/yaml/vendored/Mod.kt` |
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |
