---
title: Parameterized tests
description: Execute one game test across named voltage, fluid, item-tier, or other input rows.
---

# Parameterized tests

Use `@MethodSource` when the setup and assertions are identical across several inputs. The source enumerates the rows
to execute; Horizon-QA does not calculate a Cartesian product. Discovery expands every row into a normal test case with
its own placement, timeout, result, and report entry.

```java
@GameTest(timeoutTicks = 200)
@MethodSource("voltageTiers")
public static void acceptsVoltage(GameTestHelper helper, int voltage, String tier) {
    // Configure the real machine with voltage and assert its observable behavior.
    helper.assertTrue(voltage > 0, tier + " voltage must be positive");
    helper.succeed();
}

public static List<GameTestArguments> voltageTiers() {
    return Arrays.asList(
        GameTestArguments.named("lv", 32, "LV"),
        GameTestArguments.named("mv", 128, "MV"),
        GameTestArguments.named("hv", 512, "HV"));
}
```

This produces:

```text
mymod:MachineTests.acceptsVoltage[lv]
mymod:MachineTests.acceptsVoltage[mv]
mymod:MachineTests.acceptsVoltage[hv]
```

Select one row by its full id, or use `runall` with the base id to run every row:

```text
/horizonqa run mymod:MachineTests.acceptsVoltage[lv]
/horizonqa runall mymod:MachineTests.acceptsVoltage
```

## Test and source shape

The test remains `public static void`. Its first parameter must be `GameTestHelper`; source-supplied parameters follow
it:

```java
public static void transfersFluid(GameTestHelper helper, String fluidName, int amount)
```

The method named by `@MethodSource` must be a public static no-argument method in the same holder. It may return a
`Stream`, `Iterable`, `Iterator`, or array. Every element must be a `GameTestArguments` row. The explicit row wrapper
keeps one array-valued argument distinguishable from a row containing several arguments.

`@MethodSource` without a value uses the test method name, so an overloaded no-argument provider is valid:

```java
@GameTest
@MethodSource
public static void fluidExists(GameTestHelper helper, String fluidName) {
    // ...
}

public static GameTestArguments[] fluidExists() {
    return new GameTestArguments[] {
        GameTestArguments.named("water", "water"),
        GameTestArguments.named("lava", "lava")
    };
}
```

Source methods run once during discovery, before any test cell is placed. Keep them deterministic and free of world or
global-registry mutations. Source encounter order controls case placement and report order; rows in the same batch
still run concurrently. A missing source, thrown exception, empty source, duplicate case name, wrong row width,
incompatible argument type, or source with more than 256 rows is a discovery error and excludes the whole
parameterized method.

Base test ids are checked for duplicates before any source is invoked. Overloaded `@GameTest` methods therefore cannot
share a base id, even when their sources would use disjoint case names; every colliding definition is excluded.

## Stable case names

Prefer `GameTestArguments.named(name, firstValue, remainingValues...)`. Names must be at most 128 characters, match
`[A-Za-z0-9_.-]+`, and be unique within the source. Stable domain names such as `lv`, `distilled_water`, or `tier-4`
make selectors and CI history readable.

`GameTestArguments.of(firstValue, remainingValues...)` assigns a zero-based name (`[0]`, `[1]`, ...). Those names are
concise, but inserting or reordering source rows changes case identity. A `null` or array first value is preserved as
one argument. For either type in a later position, use the explicit values-array factory so Java varargs cannot flatten
or reinterpret it:

```java
GameTestArguments.namedValues(
    "mixed",
    new Object[] { 2, new String[] { "water", "lava" }, null });
```

All arguments are resolved and type-checked during discovery. Primitive parameters accept the same boxing and widening
conversions as reflective Java invocation; `null` is valid only for reference parameters. Rows are snapshotted and
array arguments are defensively copied for each launch, but arbitrary object values are not cloned. Prefer immutable
values and registry IDs, and construct mutable game objects such as `ItemStack` inside the test invocation instead of
putting them in a source row.

JUnit XML includes the supplied values in `system-out`, and status JSON includes the same summary as `parameters`.

## Batches, templates, and optional mods

Every row inherits the same `@GameTest` attributes, including `template`, `timeoutTicks`, `batch`, `required`, and
`rotation`. Rows run and report independently, while batch hooks still run once for the containing batch.

When a holder is skipped by `@GameTestHolder(requiredMods = ...)`, Horizon-QA deliberately does not load the holder or
invoke its source. It reports one skipped base test placeholder because the row names cannot be known safely without
loading the optional-mod-dependent class.
