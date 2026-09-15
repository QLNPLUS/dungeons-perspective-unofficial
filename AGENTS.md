# Project Working Rules

## Branch Matrix

| Branch | Worktree | Loader | Minecraft | Java | Gradle | Role |
| --- | --- | --- | --- | --- | --- | --- |
| `forge-1.20.1` | `D:\projects\dungeons-perspective` | Forge `47.2.19` | `1.20.1` | `17` | `8.12` | Primary Forge port |

The repository currently has one local worktree. The `origin/1.20.1` remote branch is the original mod upstream, not another local worktree.

## Upstream Policy

- `origin/1.20.1` is the source of truth for original Dungeons Perspective updates.
- Never reset or overwrite `forge-1.20.1` with the upstream branch.
- Import upstream work with `git cherry-pick -x` so the original commit remains traceable.
- Resolve Forge, mapping, and Mixin differences explicitly inside the imported change.
- Keep the upstream import, Forge adaptation, and local feature in separate commits.
- Update `UPSTREAM.md` after each completed synchronization.

## Local Feature Policy

- Every new feature must be an independent commit.
- Use a separate feature branch when the work spans multiple commits; use the `codex/` prefix unless the user requests another name.
- Use clear commit prefixes such as `feature:`, `fix:`, and `forge:`.
- Do not squash a local feature into an upstream migration commit.
- Prefer new classes, extension points, or dedicated Mixins for local behavior so future upstream updates remain easy to review.
- When a local feature must touch an upstream file, preserve both behaviors during conflict resolution and test the affected workflow.

## Verification

- Before editing, inspect `git status --short` and preserve unrelated user changes.
- Use the Gradle wrapper for builds.
- For source changes, run `./gradlew compileJava` at minimum; camera, input, or rendering changes also require a client smoke test.
- Do not delete or clean user files as part of synchronization.

## Release Tags

Use the existing project tag format: `v<version>`.

