# Git standard

Remote: `github.com/karthickdeveloper0071/groceryecom`.

## Branches

`main` is the default branch and is always deployable. Nobody commits to it
directly; everything arrives through a pull request with green CI.

| Prefix | Use | Example |
|--------|-----|---------|
| `feature/` | new functionality | `feature/vendor-onboarding` |
| `bugfix/` | a defect on `main` | `bugfix/refresh-token-expiry` |
| `hotfix/` | an urgent production fix | `hotfix/login-500` |

Name the rest of the branch in lower-case with hyphens, describing the change,
not the ticket number alone. Branch from up-to-date `main`:

```bash
git switch main && git pull
git switch -c feature/vendor-onboarding
```

Delete the branch after the merge. One branch per logical change: a refactor and
a feature are two branches.

## Commits

Conventional prefixes, and only these:

| Prefix | Use |
|--------|-----|
| `feat` | new user-visible behaviour |
| `fix` | a defect fix |
| `perf` | a change whose point is speed or resource use |
| `refactor` | no behaviour change |
| `test` | tests only |
| `docs` | documentation only |
| `chore` | tooling, config, dependency bumps |
| `build` | build files, Docker, CI |

Format:

```
<prefix>: <imperative summary under ~72 characters>

Optional body: why the change was needed, and anything a reader would
otherwise have to reconstruct from the diff. Wrap at 72 characters.
```

Examples from this repository's history:

```
feat: add refresh token endpoint
refactor: move identity DTOs into api/dto
docs: add ADRs for module structure and persistence
```

Rules:

- Imperative mood: "add", not "added" or "adds".
- No trailing period on the summary.
- One logical change per commit. A commit that renames 40 files *and* changes
  behaviour cannot be reviewed or reverted.
- Never commit a secret, a `.env` file, a build output or an IDE directory.
  Check `git status` before committing; `.gitignore` covers the known cases but
  not a new one.
- Do not commit commented-out code or debug logging.

## Pull requests

Open one as soon as the change is reviewable. The description covers:

- **What** changed, in a sentence.
- **Why**, including the ticket or the reason it came up.
- **Anything a reviewer needs to know:** a new environment variable, a migration,
  a change to `SecurityConfig`, a deviation from a standard.
- **How it was verified** beyond the automated tests.

Then:

1. Wait for CI. A red build is not a review request.
2. Walk through the [definition of done](definition-of-done.md) yourself.
3. One approval is required. A change to `SecurityConfig`, to a migration or to
   module boundaries deserves a reviewer who will actually push back — say so in
   the description.
4. Respond to every comment, in the code or in a reply. "Done" with no commit is
   not a response.
5. Merge to `main` yourself once it is approved and green.

Keep pull requests small. A 2,000-line diff gets a worse review than four
500-line ones, not a better one.

## CI

GitHub Actions, `.github/workflows/ci.yml`, on every pull request and every push
to `main`. Three jobs:

| Job | What it does |
|-----|--------------|
| Build, test and analyse | `./mvnw -B verify` on JDK 21: compiles, runs every test (embedded PostgreSQL, module boundary rules), runs SpotBugs static analysis, packages the jar |
| Dependency vulnerability scan | Trivy filesystem scan in `vuln` and `secret` modes; fails on HIGH or CRITICAL, unfixed findings ignored |
| Build Docker image | builds the image to prove the `Dockerfile` still works; nothing is pushed |

Test reports and the SpotBugs XML are uploaded as artifacts when the build job
fails. Concurrent runs on the same ref are cancelled.

`./mvnw verify` locally runs the same tests and the same static analysis as the
build job, so there is no reason to find out from CI.

If CI fails on something unrelated to your change, say so in the pull request and
fix the cause. Do not re-run until it passes.

## Migrations and rebases

A Flyway migration number is effectively claimed the moment your branch is
pushed. If someone else merges `V3` first, rename yours to `V4` and rerun
`./mvnw verify` — two migrations with the same number will not both apply.

Rebase or merge `main` into your branch to resolve conflicts; either is fine.
Never force-push a branch someone else is reviewing without telling them.

## Not built yet

- No release tags or versioning scheme. The artifact version is still
  `0.0.1-SNAPSHOT`.
- No automated deploy. CI builds the image but does not publish or deploy it;
  see [deployment.md](deployment.md).
- No staging environment, so `main` goes from CI to a developer's manual check.
- No commit-message or branch-name hook. These conventions are enforced by review
  only.
