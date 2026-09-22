# Task NNN: <short title>

## Goal
<one sentence: what this task delivers>

## Context
<what the executor must know. Link to DESIGN.md sections. Name the upstream files
or the earlier tasks that this task builds on.>

## Interface (copy exactly)
```
<C-ABI or JNI signatures, Kotlin declarations, or file skeletons. The executor
must produce these exactly. This is the planner's design. Do not change it.>
```

## Files in scope
- `path/to/file`: <create or edit. What changes.>
<Change only these files.>

## Definition of done
- Build: `<exact command>`
- Test: `<exact command>`. The test must pass. It must print `<expected>`.

## Constraints
- Do not change the engine `.cpp` sources.
- Do not add a new dependency.
- <more constraints for this task>
