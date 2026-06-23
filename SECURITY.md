# Security Policy

Project EKA handles enterprise document ingestion, authentication, and AI-powered retrieval. Security is treated as a first-class concern. We take all vulnerability reports seriously and are committed to responding promptly and transparently.

---

## Supported Versions

| Version | Supported |
|---|---|
| `main` branch (latest) | ✅ Active security fixes |
| Tagged releases (current minor) | ✅ Active security fixes |
| Tagged releases (previous minor) | ⚠️ Critical fixes only |
| Older releases | ❌ Not supported |

We strongly recommend always running the latest release. Security backports to older versions are made on a case-by-case basis for critical vulnerabilities only.

---

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues, pull requests, or discussions.**

Public disclosure of an unpatched vulnerability puts all users of the project at risk.

### Preferred reporting channel

Report vulnerabilities privately via **GitHub Security Advisories**:

1. Go to the repository on GitHub
2. Click the **Security** tab
3. Click **Report a vulnerability**
4. Complete the advisory form with as much detail as possible

This creates a private, encrypted thread between you and the maintainers, with no public visibility until a patch is ready.

### Alternative reporting channel

If you are unable to use GitHub Security Advisories, email the maintainer directly:

**mudassir.shahzad111@gmail.com**

Use the subject line: `[SECURITY] Project EKA — <brief description>`

If the information is sensitive, request a PGP public key before sending.

---

## What to Include in Your Report

A high-quality report helps us triage and fix the vulnerability faster. Please include as many of the following as possible:

| Field | Description |
|---|---|
| **Vulnerability type** | e.g. Injection, Authentication bypass, Information disclosure, Insecure deserialisation |
| **Affected component** | e.g. JWT filter, document ingestion pipeline, Weaviate adapter |
| **Affected version(s)** | Branch name or release tag |
| **Severity assessment** | Your assessment of impact (Critical / High / Medium / Low) |
| **Attack vector** | How is the vulnerability triggered? Network, local, or requires authentication? |
| **Steps to reproduce** | Minimal, precise steps to demonstrate the vulnerability |
| **Proof of concept** | Code or payload demonstrating exploitability (if safe to share) |
| **Impact** | What can an attacker achieve? Data exfiltration, privilege escalation, denial of service? |
| **Suggested fix** | If you have one — not required, but appreciated |

The more detail you provide, the faster we can validate and patch.

---

## Response Timeline

We commit to the following response times:

| Milestone | Target timeline |
|---|---|
| Acknowledgement of receipt | Within **48 hours** |
| Initial severity assessment | Within **5 business days** |
| Status update to reporter | Every **7 days** until resolved |
| Patch for Critical / High severity | Within **14 days** of confirmed reproduction |
| Patch for Medium severity | Within **30 days** of confirmed reproduction |
| Patch for Low severity | Addressed in the next regular release |
| Public disclosure | After patch is released and users have had time to update |

These are targets, not guarantees. Complex vulnerabilities — particularly those requiring architectural changes — may take longer. We will communicate openly with the reporter about any delays.

---

## Responsible Disclosure Policy

We follow the principle of **coordinated vulnerability disclosure**:

1. **You report privately.** The vulnerability is reported through one of the private channels above, giving us the opportunity to investigate and patch before public knowledge.

2. **We investigate.** We reproduce the vulnerability, assess its severity, and develop a fix. We keep you informed throughout.

3. **We patch and release.** A security patch is released. Release notes describe the vulnerability type and affected versions without providing exploitation details.

4. **We disclose publicly.** After the patch is released and a reasonable period has passed for users to update (typically 7–14 days for critical issues, 30 days for lower severity), we publish a GitHub Security Advisory with full details.

5. **We credit you.** Unless you request anonymity, your name or handle is included in the security advisory as the reporter.

### Disclosure timeline negotiation

If you have a specific disclosure deadline (e.g. a conference talk or academic publication), please state it in your initial report. We will work with you to align our patch and disclosure timeline with your deadline wherever possible.

---

## Scope

The following are in scope for this security policy:

- Authentication and authorisation (JWT handling, RBAC enforcement, refresh token management)
- Tenant isolation and data segregation (multi-tenancy leakage between tenants)
- Document ingestion pipeline (path traversal, file upload bypass, Tika sandbox escape)
- API input validation and injection vulnerabilities (SQL injection, prompt injection, SSRF)
- Secrets and credential handling (JWT keys, database passwords, API tokens)
- Dependency vulnerabilities with a direct exploitation path in this project

### Out of scope

The following are **not** in scope:

- Vulnerabilities in third-party services (Weaviate, Ollama, PostgreSQL) — report those directly to their maintainers
- Denial of service attacks requiring sustained high traffic volume
- Self-XSS or attacks requiring the victim to already have admin access
- Vulnerabilities only reproducible on end-of-life operating systems or JVMs
- Issues in development-only configuration that cannot be triggered in production

---

## Security Design Principles

For contributors and users reviewing the security posture of Project EKA:

| Principle | Implementation |
|---|---|
| **Defence in depth** | Tenant isolation enforced at application layer, database RLS, and Weaviate native tenancy — three independent layers |
| **Least privilege** | RBAC with four roles; method-level `@PreAuthorize`; database user has minimal permissions |
| **No raw secrets in storage** | Refresh tokens stored as SHA-256 hashes only; raw token never persisted |
| **Asymmetric JWT** | RS256 signing — private key on the issuer only; verification services use public key |
| **Append-only audit log** | PostgreSQL RULE statements reject any UPDATE or DELETE on `audit_logs` at the database level |
| **Prompt isolation** | System prompt is structurally separated from user input; user query text never enters the system section |
| **Dependency pinning** | All dependency versions pinned via Gradle version catalogue or BOM |

---

## Acknowledgements

We are grateful to all security researchers who report vulnerabilities responsibly. Contributors who report valid, confirmed vulnerabilities will be acknowledged in:

- The GitHub Security Advisory for the issue
- The release notes for the patch release

Thank you for helping keep Project EKA and its users secure.
