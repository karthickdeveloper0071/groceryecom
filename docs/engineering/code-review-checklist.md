# Code review checklist

For the reviewer. Work top to bottom; stop and ask rather than guessing. The
author should have run through the
[definition of done](../development/definition-of-done.md) first.

Leave one of three outcomes: approve, approve with comments the author can apply
alone, or request changes. Say which. "Looks good to me" on a change you did not
read is worse than no review.

## Before reading the diff

- [ ] The pull request says **what** changed and **why**, not just what.
- [ ] CI is green: build, tests, SpotBugs, Trivy dependency scan.
- [ ] The branch name and commit prefixes follow the
      [git standard](../development/git-standard.md).
- [ ] The diff is one logical change. A refactor mixed into a feature is a
      request-changes.

## Boundaries and structure

- [ ] Every new class is in the right package per the
      [module layout](../architecture/module-architecture.md).
- [ ] No import of another module's `api`, `application`, `domain`,
      `infrastructure` or `mapper` package — only its `contract`.
- [ ] Dependency direction inside the module holds: `api -> application ->
      domain`. No controller touching a repository.
- [ ] `shared` imports nothing else from this project; `platform` imports only
      `shared`.
- [ ] A new module has `package-info.java` with `@ApplicationModule` and a
      `contract/package-info.java` with `@NamedInterface("contract")`.
- [ ] Nothing was added to `contract` that did not need to be public.
- [ ] A new cross-module interaction is an event where it could be, not a call.

## API

- [ ] Path is versioned (`/api/v1/...`), plural, lower-case, hyphenated.
- [ ] Path variables use the public UUID, never the numeric id.
- [ ] The response is `ApiResponse`, built with a factory method.
- [ ] HTTP status matches the outcome; a create returns 201.
- [ ] New error codes are `SCREAMING_SNAKE_CASE` and were added to the table in
      the [API standard](api-standard.md) in this pull request.
- [ ] Request and response types are records in `api/dto`, never entities.
- [ ] Bean Validation on every request field that has a rule, and `@Valid` on
      the parameter.
- [ ] No response field the client has no use for; no internal id, no hash.
- [ ] `@Operation` and `@Tag` present; `@SecurityRequirement` on every
      authenticated endpoint.

## Security

- [ ] Nothing new in the public matcher lists in `SecurityConfig`. If there is,
      the pull request explains why, and `SecurityRulesTest` covers it.
- [ ] The caller comes from `@AuthenticationPrincipal`, not from a path, body or
      query parameter.
- [ ] No role, permission or ownership decision taken from client input.
- [ ] Authorization is actually checked: a vendor endpoint verifies that the
      caller owns that vendor, not just that the caller is a `VENDOR_OWNER`.
- [ ] No password, hash, token, secret or full request body reaches a log line.
- [ ] No exception message exposes internals to a client.
- [ ] Error responses do not reveal whether an account or resource exists.
- [ ] The login path still compares against the dummy hash when no account is
      found, and still checks the password before account state.
- [ ] No new secret has a usable default in `application.yml`; new variables are
      in `.env.example`.

## Database

- [ ] A schema change is a **new** migration file, correctly named
      `V<n>__<module>_<change>.sql`. No existing migration was edited.
- [ ] The migration is backward compatible with the currently deployed version.
- [ ] `NOT NULL`, `UNIQUE` and `CHECK` constraints match the entity's rules; a
      new enum value updated its `CHECK`.
- [ ] `TIMESTAMPTZ` and `Instant`; money is `BIGINT` minor units plus a currency
      code and `Money` in Java.
- [ ] A vendor-owned table has `vendor_id NOT NULL` and indexes that start with
      it.
- [ ] Every new index answers a query that exists in this change.
- [ ] `@Transactional` is on the service method, `readOnly = true` for reads.
- [ ] No N+1: no lazy association dereferenced in a loop.
- [ ] Nothing relies on a lazy association outside the transaction
      (`open-in-view` is off).

## Code

- [ ] Constructor injection, `private final` fields, no `@Autowired` field.
- [ ] Classes are package-private unless something outside the package needs
      them.
- [ ] One use case per service class; the class name says which.
- [ ] Business failures throw an `ApplicationException` subclass with the right
      status and code. No `catch` that swallows or returns null.
- [ ] Lombok limited to `@Getter`, `@Setter`, `@NoArgsConstructor`, `@Slf4j`. No
      `@Data` on an entity.
- [ ] `Instant` for time, `Money` for money, `Optional` only as a return type.
- [ ] Log levels are right, messages parameterised, nothing sensitive logged.
- [ ] No commented-out code, no `TODO` without a name and a reason, no debug
      print.
- [ ] No unrelated reformatting in the diff.

## Tests

- [ ] New behaviour has a test. A bug fix has a test that fails without the fix.
- [ ] The right kind: unit by default, context test when the wiring is the point.
- [ ] Error paths assert the status **and** the `code` field.
- [ ] Test names describe behaviour.
- [ ] AssertJ, no sleeping, no conditional assertions.
- [ ] Tests create and clean up their own data; nothing assumes an empty table.
- [ ] No test was disabled, `@Disabled`, or narrowed to make the build pass.
- [ ] A new public endpoint added its rule to `SecurityRulesTest`.

## Documentation

- [ ] A decision that is hard to reverse has an
      [ADR](../architecture/adr/README.md), with the index row added.
- [ ] A new error code, environment variable, endpoint or migration convention is
      reflected in the matching standard.
- [ ] A new gap is written down as "not built yet" rather than left implied.
- [ ] Javadoc on new public types says what they are for.

## Things worth saying out loud

If a change is fine but you would have done it differently, say so and approve.
If a change works but makes the next change harder, say that specifically — name
the next change. If you do not understand a piece of the diff, that is a review
comment, not something to wave through.
