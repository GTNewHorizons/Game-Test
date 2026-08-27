# Horizon-QA

Horizon-QA defines how test cases execute interactively or as durable, reported runs against a real Minecraft server.

## Language

**Reported Run**:
One automatic or command-triggered attempt that produces a single durable run outcome. It may contain zero or more Test Batches.
It owns output preflight, batch lifecycle, terminal cleanup, reporting, result publication, and optional process exit while delegating tick execution to the execution kernel.
_Avoid_: Reported batch, batch runner when referring to the whole run

**Test Batch**:
An ordered group of selected test cases that shares a batch name and batch-scoped setup and cleanup within a Reported Run.
_Avoid_: Reported Run

**Interactive Session**:
An authoring-oriented execution context that retains test cells and their latest outcomes for inspection and relaunch.
_Avoid_: Reported Run

**Execution Kernel**:
The shared `GameTestRunner` mechanism that exclusively owns active test instances and dispatches START callbacks before the world tick and END callbacks after it.
_Avoid_: Reported Run
