# ADR-0003: Package and module structure

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

[ADR-0001](0001-modular-monolith.md) commits us to a modular monolith with
build-time boundary checks. Spring Modulith needs two things the generic company
package standard does not provide:

1. A module must declare which of its packages other modules may use, a
   *named interface*. Without one, every type in the module counts as internal.
2. Shared code has to be either a module that everyone is allowed into (an OPEN
   module) or it fails the boundary checks.

The generic company standard is a single `common` package, plus feature packages
`api/` for the REST layer, `service/`, `repository/` and `entity/`. It has no
concept of a cross-module contract.

## Problem

What are the top-level packages, and which packages does a business module
contain?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| Generic standard as-is: one `common`, feature packages api/service/repository/entity | familiar to anyone who has worked on other company projects; nothing new to learn | `common` mixes dependency-free value types with Spring configuration, so nothing constrains what `common` may import; there is no package to declare as the cross-module contract, so Modulith cannot enforce anything |
| One `common` module, add `contract/` inside each feature | smaller deviation; the contract becomes enforceable | `common` still has no dependency rule of its own, so it eventually imports a business module and the ArchUnit rule has to be dropped |
| `shared` plus `platform` as OPEN modules, module packages contract/api/application/domain/infrastructure/mapper | each foundation package has one rule a test can check; the contract is explicit and enforced; layering inside a module is visible in the tree | two deviations from the generic standard that every new joiner has to learn |

## Decision

Top level under `com.groceryecom`:

| Package | Rule |
|---------|------|
| `shared` | business-agnostic, framework-light code with no dependency on anything else in this project: `Money`, `BaseEntity`, `ApiResponse`, `ApplicationException` and its subclasses. `@ApplicationModule(type = OPEN)`. |
| `platform` | technical framework wiring: `security`, `web`, `cache`. Depends on `shared` only. `@ApplicationModule(type = OPEN)`. |
| `modules.<name>` | one business capability. Depends on `shared`, `platform` and other modules' `contract` packages. |

Inside a business module: `contract/`, `api/` (with `dto/`), `application/`,
`domain/`, `infrastructure/` (only when needed) and `mapper/`. Dependency
direction is `api -> application -> domain`; `mapper` serves `application` and
`api`.

Both deviations from the generic standard are deliberate.

**1. `shared` plus `platform` instead of one `common`.** The split exists so
each half has a rule a test can check. `shared` may import nothing else from
this project, so a value type can never quietly start depending on Spring
Security. `platform` may import `shared` and nothing else from this project.
Both are OPEN modules, so business modules may use any package inside them
without a named interface. The ArchUnit rule in `ModularityTest` enforces that
neither ever depends on `com.groceryecom.modules..`.

**2. `api/` is the REST layer and `contract/` is the cross-module contract.**
The generic standard's `api/` already means "the REST layer", and we kept that
meaning so controllers stay where people look for them. The cross-module public
surface needed a new name, because the generic standard has no such concept, and
`contract` says what it is: the types another module is allowed to compile
against. That package carries `@NamedInterface("contract")`.

## Reason

Every rule in this structure is a rule a build can fail on. "Put shared things
in `common`" is not checkable; "`shared` may not import anything from this
project" is. The naming follows from the same need: Modulith requires one package
per module to be the declared public surface, and calling that package `api`
would collide with the REST layer and invite people to expose controllers as the
contract.

## Consequences

**Positive**

- `ModularityTest` can express every boundary rule, and violations fail the build.
- Reading a module's package tree tells you its layering without opening a class.
- `contract` packages stay small and dependency-free, which is exactly what an
  extracted service would ship as its client library.

**Negative**

- Two deviations to explain to every new joiner, and `api` versus `contract` is
  genuinely easy to confuse at first.
- Copy-paste from other company repositories does not drop in cleanly.
- Six packages is a lot of ceremony for a module with one entity. Create
  `infrastructure/` only when the module actually has an external adapter.

**Revisit when**

- The company standard grows its own cross-module contract concept, at which
  point align the names.
- A module's `contract` package starts needing types from `domain`. That means
  the boundary is in the wrong place, not that this structure is wrong.
