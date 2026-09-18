# Definition of done

For the author, before asking for a review. The reviewer works from the
[code review checklist](../engineering/code-review-checklist.md); this list is
what you check yourself so their time goes on the design, not on the basics.

"Done" means merged to `main` with everything below true. Not "works on my
machine".

## The change itself

- [ ] It does what the ticket asked, and nothing extra. Unrelated improvements
      are a separate branch.
- [ ] Every class is in the right package per the
      [module layout](../architecture/module-architecture.md).
- [ ] No module reaches past another module's `contract` package.
- [ ] Nothing was added to a `contract` package that did not have to be public.
- [ ] No commented-out code, no debug logging, no `TODO` without a name and a
      reason.
- [ ] No unrelated reformatting in the diff.

## Build and tests

- [ ] `./mvnw verify` passes locally — tests, module boundary rules and SpotBugs.
- [ ] New behaviour has a test. A bug fix has a test that fails without the fix.
- [ ] Error paths assert the HTTP status **and** the `code` field.
- [ ] No test was disabled, deleted or narrowed to get a green build.
- [ ] A new public endpoint has its rule in `SecurityRulesTest`.
- [ ] A new module is listed in `ModularityTest.detectsExpectedModules`.
- [ ] Tests create and clean up their own data; nothing assumes an empty table.

## API changes

- [ ] Path is versioned, plural, lower-case, hyphenated; path variables use the
      public UUID.
- [ ] Response is `ApiResponse`, built with a factory method; status matches the
      outcome.
- [ ] Request and response types are records in `api/dto` with Bean Validation.
- [ ] New error codes are in the table in the
      [API standard](../engineering/api-standard.md), added in this branch.
- [ ] `@Operation`, `@Tag` and — for authenticated endpoints —
      `@SecurityRequirement` are present.
- [ ] Checked by hand in `/api/swagger-ui.html`: the success case, a validation
      failure and at least one business failure.
- [ ] No response field exposes a hash, an internal id or anything the client
      has no use for.

## Database changes

- [ ] The schema change is a **new** migration named
      `V<n>__<module>_<change>.sql`; no existing migration was edited.
- [ ] The migration number is still free after the latest `main` — rebase and
      renumber if not.
- [ ] `./mvnw verify` passes, so the migration applied and `ddl-auto=validate`
      agreed with the entities.
- [ ] The migration is backward compatible with the deployed version: nothing
      dropped or renamed that the running code still uses.
- [ ] `NOT NULL`, `UNIQUE` and `CHECK` constraints match the entity's rules; a
      new enum value updated its `CHECK`.
- [ ] `TIMESTAMPTZ` and `Instant`; money as `BIGINT` minor units plus a currency
      code and `Money` in Java.
- [ ] A vendor-owned table has `vendor_id NOT NULL` and indexes starting with it.
- [ ] Every new index answers a query in this change.

## Security

- [ ] Nothing was added to the public matcher lists in `SecurityConfig`, or it
      was and the pull request explains why.
- [ ] The caller comes from `@AuthenticationPrincipal`, never from client input.
- [ ] Ownership is checked, not just the role.
- [ ] No password, hash, token, secret or request body reaches a log line.
- [ ] No error message reveals whether an account or resource exists.
- [ ] A new secret has no usable default in `application.yml` and is documented
      in `.env.example`.

## Configuration

- [ ] Any new setting is in `application.yml` with an environment-variable
      override, and its local default matches `docker-compose.yml`.
- [ ] A new environment variable is documented in `.env.example` and in the
      table in [deployment.md](deployment.md).
- [ ] The application still starts with only the documented variables set.

## Documentation

- [ ] Every standard your change makes wrong has been updated in the same branch.
- [ ] A decision that is hard to reverse, or a deliberate deviation from a
      standard, has an [ADR](../architecture/adr/README.md) with an index row.
- [ ] A new gap is written down as "not built yet", not left implied.
- [ ] Javadoc on new public types says what they are for.

## Git and review

- [ ] Branch and commit prefixes follow the [git standard](git-standard.md).
- [ ] The pull request says what changed, why, and how it was verified.
- [ ] CI is green: build and tests, dependency scan, image build.
- [ ] One approval, and every comment answered in code or in a reply.
- [ ] Merged to `main` and the branch deleted.

## What "done" does not mean yet

There is no staging environment and no automated deploy. Merged to `main` means
the change is built, tested and packaged; it does not mean it has run anywhere
other than a developer machine and CI. Until that changes, a risky change needs a
plan for verifying it after deployment, in the pull request description.
