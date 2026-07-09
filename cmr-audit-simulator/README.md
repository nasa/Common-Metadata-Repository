# CMR Audit Simulator

The CMR Audit Simulator is a deliberately flawed Clojure/Leiningen mini app placed at the root of
the Common Metadata Repository checkout. It is designed as a safe target for a future auditor that
will scan CMR and related applications for security, build, architecture, metadata, and reliability
problems.

This app is not part of the normal top-level `lein modules` build. It behaves as a separate entity,
the same way the future auditor can live at the repository root without changing the production CMR
module graph.

## What It Simulates

The code is organized around the four audit categories:

| Category | Namespace | Simulated issues |
| --- | --- | --- |
| Security and build health | `cmr.audit-simulator.security-build` | hardcoded secrets, unsafe reader usage, shell command injection risk, stale dependencies, flaky build signals |
| Architecture and code health | `cmr.audit-simulator.architecture` | unused code, unbounded cache growth, duplicated scoring logic, high-churn module metadata |
| Metadata quality | `cmr.audit-simulator.metadata` | missing required fields, invalid temporal ranges, malformed spatial values, broken distribution URLs |
| Reliability and performance | `cmr.audit-simulator.reliability` | p99 latency pressure, serial query fan-out, retry storms, low cache-hit rates, uptime incident hints |

Each namespace exposes two kinds of functions:

1. intentionally weak code or data patterns that static and runtime auditors can detect
2. structured findings with suggested fixes that let you test report generation and remediation flows

## Running It

From this directory:

```sh
lein run-simulation
```

Or:

```sh
lein run -m cmr.audit-simulator.runner
```

The runner prints a JSON report containing simulated findings, severity levels, evidence, and
candidate fixes.

## Useful Audit Targets

Start with these files when testing an auditor:

- `project.clj` contains normal CMR-style Leiningen configuration plus dependencies and lint aliases.
- `src/cmr/audit_simulator/security_build.clj` contains intentionally unsafe patterns.
- `src/cmr/audit_simulator/architecture.clj` contains code-health and maintainability smells.
- `src/cmr/audit_simulator/metadata.clj` validates sample metadata from `resources/`.
- `src/cmr/audit_simulator/reliability.clj` contains latency and uptime simulation data.
- `src/cmr/audit_simulator/runner.clj` aggregates all findings into a single report.

## Safety Notes

The simulator does not call production CMR services, mutate real provider metadata, or run dangerous
commands during normal execution. Some risky functions are present on purpose so an auditor can find
them, but the runner only reports their presence and recommended fixes.

## Expected Auditor Behavior

A useful auditor should be able to:

- identify the intentionally risky functions and stale dependency signals
- connect slow simulated endpoints to reliability findings
- detect invalid metadata records and propose JSON/EDN patch-like corrections
- rank findings by severity and blast radius
- generate focused pull request patches without modifying unrelated CMR modules
