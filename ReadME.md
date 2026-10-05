
# Job Intelligence Platform

An intelligent job-search and job-application platform designed to maximize high-quality interview opportunities per unit of user effort.

The system continuously discovers relevant software engineering jobs, normalizes and deduplicates listings from multiple sources, evaluates them against a structured candidate profile, ranks opportunities, and eventually automates application workflows while keeping the user in control whenever human judgment is required.

**Primary Optimization Target:**  
`Expected Interview Opportunities / User Time Required`

---

## 🚀 Project Status: Phase 1 (Job Intelligence)

### Completed
- Spring Boot 4.1 project setup (Java 25 LTS)
- PostgreSQL development environment with Docker Compose
- Flyway database migrations & Spring Data JPA
- Testcontainers integration (Unit/Integration test separation)
- Initial Company and Job domain implementation

### Currently Building
- Job source abstraction & ingestion pipeline
- Job normalization and deduplication
- Job requirement extraction
- Candidate/job matching and ranking

### Planned
- Resume intelligence & application automation
- Automated application-question answering
- Human approval workflow & application tracking
- Recruiter outreach assistance & interview preparation
- Outcome-based ranking optimization

---

## 🏗 Architecture

This project is built as a **serious backend system**, not a minimal proof of concept. The platform is developed as a **modular monolith**, organized around feature boundaries (business capabilities) rather than global technical layers. Microservices are intentionally avoided until concrete scaling needs arise.

### High-Level Pipeline


Job Sources (Greenhouse, Lever, LinkedIn, Email, etc.)
      ↓
Job Ingestion & Normalization
      ↓
Deduplication (Canonical Job Model)
      ↓
Deterministic Qualification Filtering
      ↓
LLM Semantic Analysis & Job Ranking
      ↓
PostgreSQL (System of Record)
      ↓
Daily Recommendations


---

## 💻 Technology Stack

| **Category**       | **Technologies**                                                                                |
| ------------------ | ----------------------------------------------------------------------------------------------- |
| **Backend**        | Java 25 LTS, Spring Boot 4.1, Maven, Spring MVC, Spring Security (JWT), Spring Data JPA, Hibernate, Jakarta Validation |
| **Database**       | PostgreSQL, Flyway                                                                              |
| **Testing**        | JUnit, Spring Boot Test, Testcontainers                                                         |
| **Infrastructure** | Docker, Docker Compose, Spring Boot Actuator                                                    |
| **Future/Planned** | Redis, Playwright, TypeScript browser workers, LLM APIs, n8n, Gmail API                         |

---

## 🧠 Core Systems

### 1. Canonical Job Model & Deduplication

The same job often appears across multiple platforms (e.g., LinkedIn, Greenhouse, Indeed). The system separates the **Canonical Job** from the **Job Source Listing** to maintain accurate provenance without cluttering the pipeline.

Deduplication uses multiple signals:

* Source + External ID uniqueness.
* Canonical application URL matching.
* Canonical fingerprinting (Company + Normalized Title + Location + Employment Type + Experience).
* Normalized description similarity.
* *Conservative merging strategy:* False positives are more damaging than temporary duplicate records.

### 2. Matching Architecture

* **Stage 1 — Deterministic Qualification:** Fast filtering based on hard constraints (role, seniority, location, impossible experience requirements).
* **Stage 2 — Semantic Analysis (LLM):** The strongest candidates pass to an LLM to evaluate semantic skill overlap, project relevance, and transferable experience.

    * *Note:* LLM outputs are structured, stored with reasoning/model metadata for reproducibility, and **never** override hard qualification rules.

### 3. Database Design Principles

* **Flyway owns the schema:** `ddl-auto: validate` is used. Applied migrations are immutable.
* **Human Truth Over Model Guessing:** The system must never invent candidate information simply to automate an application or improve a score.

---

## 🗺 Long-Term Roadmap

* **Phase 1 — Job Intelligence (Current):** Discovery → Normalization → Deduplication → Qualification → Ranking.
* **Phase 2 — Candidate & Resume Intelligence:** Structured profiles → Resume knowledge base → Role-specific customization.
* **Phase 3 — Application Automation:** Application prep → Form automation → Question answering → Human approval → Tracking.
* **Phase 4 — Learning & Optimization:** Track outcomes (Interviews, Offers, Rejections) → Optimize ranking probabilities.

---

## 🛠 Local Development

### Prerequisites

* Java 25 LTS
* Maven
* Docker & Docker Compose

### Quick Start

1. **Start the Database:**

   Bash

   ```bash
   docker compose up -d


2. **Run the Application** (the JWT signing secret is required, see [Authentication](#-authentication)):

   Bash

   ```bash
   export JWT_SECRET="$(openssl rand -base64 48)"
   ./mvnw spring-boot:run
   ```

3. **Run Tests:**

    * Fast unit tests (isolated): `./mvnw test`
    * Unit + Integration tests (requires Docker): `./mvnw clean verify`

4. **Health Check:**

   Bash

   ```bash
   curl http://localhost:8080/actuator/health
   ```

> **Note:** To reset the local database volume and wipe development data, run `docker compose down -v`.

---

## 🔐 Authentication

Candidates authenticate with stateless **JWT bearer tokens**. The existing `CandidateProfile` is the authenticated principal; there is no separate user table. Passwords are stored only as **BCrypt** hashes.

### Configuration

| Property (env var)                                      | Default           | Description                                                              |
| ------------------------------------------------------- | ----------------- | ------------------------------------------------------------------------ |
| `security.jwt.secret` (`JWT_SECRET`)                    | **none, required** | HS256 signing key, at least 32 bytes. The application refuses to start without it. Never commit it. |
| `security.jwt.access-token-expiration` (`JWT_ACCESS_TOKEN_EXPIRATION`)   | `15m` | Access token lifetime (Spring `Duration` format)  |
| `security.jwt.refresh-token-expiration` (`JWT_REFRESH_TOKEN_EXPIRATION`) | `7d`  | Refresh token lifetime                            |
| `security.jwt.issuer`                                   | `job-intelligence` | `iss` claim that is issued and required                                  |

Generate a secret with `openssl rand -base64 48`. Automated tests use a fake secret from `src/test/resources/config/application.yml`.

### Endpoints

| Method & path                      | Auth   | Description |
| ---------------------------------- | ------ | ----------- |
| `POST /api/v1/auth/register`       | public | Creates a candidate (same profile fields and validation as before, plus `password` and `confirmPassword`; password 8-72 bytes). Email is trimmed and lower-cased. Returns `201` with the candidate and an access/refresh token pair; `409` if the email is already registered. |
| `POST /api/v1/auth/login`          | public | `{email, password}` returns the same response as register. Unknown email and wrong password both return an identical `401`. |
| `POST /api/v1/auth/refresh`        | public | `{refreshToken}` rotates the refresh token and returns a new pair. Expired, malformed, revoked or reused tokens return `401`. |
| `POST /api/v1/auth/logout`         | bearer | Revokes all of the caller's refresh tokens. Returns `204`. |
| `GET /api/v1/auth/me`              | bearer | Returns the authenticated candidate. |
| `GET` / `PATCH /api/v1/candidates/me` | bearer | Read / update the authenticated candidate's own profile. |

`GET /actuator/health` is public. **Every other route under `/api/v1/**` requires a valid access token** (`Authorization: Bearer <accessToken>`).

Tokens carry the candidate id (`sub`, `cid`), `email` and a `type` claim (`access` or `refresh`); a refresh token is rejected as an access token and vice versa.

### Authorization

Routes scoped to a candidate (`PATCH /api/v1/candidates/{candidateProfileId}`, `.../skills`, `.../skills/bulk`, `.../daily-digest`, `.../matching/refresh`) only allow the candidate that owns the id in the path; other candidates get `403`. Ownership is decided solely from the path id and the token, never from request bodies or query parameters.

Errors are returned as JSON (`timestamp`, `status`, `error`, `message`, `path`, optional `fieldErrors`) without internal details.

### Behaviour worth knowing

* **Refresh token rotation and reuse detection:** every refresh revokes the used token and issues a new one. Presenting an already revoked refresh token is treated as theft and revokes *all* of that candidate's refresh tokens. Refresh tokens are stored only as SHA-256 hashes in `candidate_refresh_tokens`.
* **Logout** revokes refresh tokens only. Access tokens are stateless and stay valid until they expire (15 minutes by default).
* **Changing the email** invalidates access tokens issued for the old email; the client can obtain a new one with its refresh token.
* **Existing candidates (created before authentication existed)** are migrated (`V26`) with an unusable password hash and cannot log in until a password is set. There is no password-reset flow yet.
* **`POST /api/v1/candidates` was removed.** Create candidates with `POST /api/v1/auth/register`.
* There are no roles yet: routes that are not candidate-scoped (companies, source configurations, ingestion, admin repair endpoints) only require *an authenticated candidate*.
