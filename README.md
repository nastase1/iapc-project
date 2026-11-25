# EduPulse AI – Intelligent Multi-Tenant Compliance & Audit Assistant

EduPulse AI is an intelligent compliance and internal-audit assistant built for strictly regulated industries (Fintech, Healthcare, Higher Education). It lets users upload large, heterogeneous policy and regulation documents and query them in natural language for automated compliance Gap Analysis, policy conformity checks, and audit-ready reports with strict source citations.

---

## 🏛️ Microservices Architecture

The project is a multi-module Maven build with **exactly 3 microservices**, one per team member, using **Spring Boot 4 / Java 21**, **Spring Cloud 2025.1** (Eureka, Gateway Server MVC, LoadBalancer) and **Spring AI 2.0+** backed by **PostgreSQL + pgvector**:

```
                                [ Frontend / Client ]
                                          │  JWT (Bearer)
                                          ▼
        ┌──────────────────────────────────────────────────────────────────┐
        │  [1] gateway-ingestion-service (Port 8080)          — Member 1   │
        │  ┌──────────────┐ ┌──────────────────┐ ┌──────────────────────┐  │
        │  │ Eureka Server│ │ API Gateway      │ │ Document Ingestion   │  │
        │  │ /eureka      │ │ JWT + RBAC,      │ │ /api/ingestion/**    │  │
        │  │              │ │ lb:// routing    │ │ async + hierarchical │  │
        │  └──────────────┘ └────────┬─────────┘ │ chunking             │  │
        │                            │           └──────────┬───────────┘  │
        └────────────────────────────┼──────────────────────┼──────────────┘
                 X-Tenant-Id,        │                      │
                 X-Org-Unit, ...     │                      │
                  ┌──────────────────┴─────┐                │
                  ▼                        ▼                │
        ┌──────────────────┐    ┌──────────────────┐        │
        │ [2] rag-query-   │◄───│ [3] audit-       │        │
        │ service          │    │ analytics-service│        │
        │ (Port 8081)      │    │ (Port 8082)      │        │
        │ — Member 2       │    │ — Member 3       │        │
        └─────────┬────────┘    └─────────┬────────┘        │
                  │                       │                 │
                  └───────────────────────┼─────────────────┘
                                          ▼
                            ┌───────────────────────────┐
                            │   PostgreSQL + pgvector   │
                            │    (Relational + HNSW)    │
                            └───────────────────────────┘
```

* **Single entry point:** clients only call port `8080`. The gateway validates the JWT, enforces RBAC and routes through Eureka (`lb://rag-query-service`, `lb://audit-analytics-service`). Ingestion endpoints are served locally.
* **Tenancy context propagation:** the gateway overwrites the `X-Tenant-Id`, `X-Org-Unit`, `X-User-Id` and `X-User-Roles` headers with values taken from the JWT (any values sent by the client are ignored).
* **Inter-service communication:** `audit-analytics-service` calls `rag-query-service` through `RagQueryClient` (a `@LoadBalanced` `RestClient`, resolved via Eureka).

---

## 👥 Roles and Responsibilities (3 Team Members)

To balance complexity, the work is split among the 3 team members as follows:

### 🔹 Member 1: Cloud Infrastructure, Security & Asynchronous Ingestion
* **Assigned microservice:** `gateway-ingestion-service` (Eureka Server + API Gateway + Document Ingestion in a single service)
* **Main responsibilities:**
  1. **Service Discovery & Gateway:**
     - Configure the Eureka Server (embedded in `gateway-ingestion-service`) for clustering and resilience.
     - Configure the API Gateway (Spring Cloud Gateway Server MVC) with dynamic routing based on Service ID (`lb://...`).
  2. **Security & Tenancy:**
     - Stateless authentication and authorization via JWT tokens at the API Gateway level.
     - Role-based access control (RBAC): `ROLE_ADMIN`, `ROLE_COMPLIANCE_OFFICER`.
  3. **Document Ingestion Pipeline:**
     - Accept multi-format files: PDF, DOCX, TXT.
     - Asynchronous processing for large files (> 10MB) using `@Async` / JobRunr / a dedicated thread pool, so API calls are never blocked.
     - Implement **Hierarchical Chunking**: child chunks (e.g. 200 tokens for vector matching) mapped to parent chunks (e.g. 1000 tokens for rich context).
     - Store embeddings in PostgreSQL (`pgvector`) together with multi-tenant metadata (`tenant_id`, `org_unit`, `access_level`).

---

### 🔹 Member 2: Core Advanced RAG & AI Orchestration
* **Assigned microservice:** `rag-query-service`
* **Main responsibilities:**
  1. **Pre-Retrieval:**
     - Implement `MultiQueryExpander`: automatically rewrite the user prompt into 3-4 phrasings from different technical angles to maximize recall.
  2. **Hybrid Retrieval & Metadata Filtering:**
     - **Hybrid Search:** combine `pgvector` semantic search (HNSW index) with classic `tsvector` full-text search for legal codes or exact terms.
     - Strict metadata-based filtering (`Filter.Expression`) to isolate data per organization / security level.
  3. **Post-Retrieval:**
     - **Reranking:** reorder the top retrieved results using a Cross-Encoder / Cohere Reranker.
     - **Context Compression:** `CompressionQueryTransformer` to remove noise and reduce token usage.
  4. **Generation, Guardrails & Citations:**
     - Integrate `RetrievalAugmentationAdvisor` into the `ChatClient`.
     - **Strict Source Citations:** generate answers with exact references to the source document, page and text fragment.
     - **Safety Guardrails:** implement `SafeguardAdvisor` and `TopicBoundaryAdvisor` to prevent hallucinations and drift away from the compliance domain.

---

### 🔹 Member 3: Agentic Gap Analysis, Audit Trail & Analytics
* **Assigned microservice:** `audit-analytics-service`
* **Main responsibilities:**
  1. **Automated Gap Analysis (Agentic Workflow):**
     - An autonomous flow that takes a new regulation document, breaks it down chunk by chunk, queries the existing policy base (`rag-query-service`) and produces a structured report of the identified compliance gaps.
  2. **Audit Trail & History:**
     - Log every compliance query together with the actual context used by the LLM for each answer.
     - Keep the audit trail for transparency and regulatory compliance.
  3. **Reports & Export:**
     - Generate and export compliance assessments and reports in structured formats (JSON / PDF / CSV).
     - Monitor performance indicators (NFR: response latency < 2.0s per semantic search).

---

## 📁 Project Directory Structure

```text
├── pom.xml                           # Root Maven POM (multi-module, 3 modules)
├── mvnw / mvnw.cmd / .mvn/           # Maven Wrapper
├── gateway-ingestion-service/        # [1] Port 8080 - Eureka + API Gateway (JWT/RBAC) + async ingestion
│   └── src/main/java/com/edupulse/gateway/
│       ├── security/                 #     JWT (HS256), RBAC, demo token issuing
│       ├── routing/                  #     lb:// routes + tenant propagation from JWT
│       └── ingestion/                #     Upload & Hierarchical Chunking
├── rag-query-service/                # [2] Port 8081 - Advanced RAG & Hybrid Search (pgvector + tsvector)
├── audit-analytics-service/          # [3] Port 8082 - Gap Analysis Agent & Audit Trail
└── docker/
    └── postgres/
        ├── pgvector.yml              # PostgreSQL with the pgvector extension (port 5434, DB edupulse_db)
        └── init.sql                  # vector / hstore / uuid-ossp extensions
```

---

## 🔌 Ports and Main Endpoints

All client calls go through `http://localhost:8080`.

| Microservice | Port | Key Endpoint | Roles | Description |
| :--- | :--- | :--- | :--- | :--- |
| **[1] Gateway & Ingestion** | `8080` | `POST /api/auth/token` | public | JWT issuing (demo users `admin`/`admin`, `officer`/`officer`) |
| | | `POST /api/ingestion/upload` | ADMIN, COMPLIANCE_OFFICER | Asynchronous document upload & ingestion |
| | | `/eureka-ui`, `/eureka/**` | public (internal network) | Eureka dashboard and registry |
| **[2] RAG Query Engine** | `8081` | `POST /api/rag/query` | ADMIN, COMPLIANCE_OFFICER | Advanced RAG query with citations |
| **[3] Audit & Gap Analysis** | `8082` | `POST /api/gap-analysis/run` | ADMIN, COMPLIANCE_OFFICER | Start an asynchronous gap analysis (202) |
| | | `GET /api/gap-analysis/reports[/{id}]` | ADMIN, COMPLIANCE_OFFICER | List reports / poll one report |
| | | `GET /api/gap-analysis/reports/{id}/export` | ADMIN, COMPLIANCE_OFFICER | Export a finished report (json, csv, pdf) |
| | | `POST /api/audit/events` | ADMIN, COMPLIANCE_OFFICER | Record an audit event (called by other services) |
| | | `GET /api/audit/events`, `/export/json`, `/export/csv`, `/stats/latency` | ADMIN | Audit trail, export, latency NFR |

---

## Safe metadata filtering (S3 T1, `rag-query-service`)

`POST /api/rag/query` accepts an optional `filters` JSON object. Every supplied field is an equality condition, and all conditions are combined with **AND**. An omitted or empty object yields `"appliedFilters": {}`.

| Filter | JSON type and allowed values |
| --- | --- |
| `doc_id`, `source`, `version` | Nonblank string |
| `page` | Integer from 1 to 2,147,483,647 |
| `is_latest` | Boolean |
| `valid_from` | ISO-8601 timestamp string with a UTC offset, such as `2026-11-25T09:00:00Z` |

The endpoint accepts up to six fields and limits each string to 256 UTF-16 code units. Nulls, blank strings, incorrect JSON types, nested objects, arrays, and unknown fields return HTTP 400. The allowlist excludes `tenant_id`, `org_unit`, and `access_level`. Tenant and access constraints belong to the retrieval security context. AND is the only supported grouping.

In Postman, send a POST to `http://localhost:8081/api/rag/query` with **Body → raw → JSON** while the RAG service is running locally.

```json
{
  "query": "What does the regulation say about exams?",
  "filters": {
    "source": "regulation.pdf",
    "version": "2.0",
    "is_latest": true
  }
}
```

The expected HTTP 200 response uses the default tenant for a direct local request without an `X-Tenant-Id` header. The service validates and prepares the filters. Applying them to pgvector and enforcing tenant and access restrictions during retrieval are future integration work. The answer and citations still come from the demo response. Timestamps in `appliedFilters` are formatted from the parsed value.

```json
{
  "query": "What does the regulation say about exams",
  "expandedQuery": "What does the regulation say about exams",
  "tenantId": "default-org",
  "answer": "Sample compliance response with citation.",
  "citations": [
    {"source": "FERPA_Policy.pdf", "page": 4, "snippet": "Student record data privacy..."}
  ],
  "appliedFilters": {"source": "regulation.pdf", "version": "2.0", "is_latest": true}
}
```

This invalid request sends a string where the endpoint expects an integer.

```json
{"query": "What does the regulation say about exams?", "filters": {"page": "4"}}
```

The endpoint returns HTTP 400 with this body.

```json
{"error": "invalid_filters", "message": "page must be an integer from 1 to 2147483647."}
```

## Query normalization (S3 T2, `rag-query-service`)

`POST /api/rag/query` returns the normalized text in `query`. The response also includes the tenant and the demo answer and citations. The service uses the `X-Tenant-Id` header or defaults to `default-org` when called directly without it.

`QueryNormalizer` collapses Unicode whitespace to single spaces, removes trailing question marks (including runs separated by spaces), and strips consecutive leading `te rog`, `vă rog`, `va rog`, and `please` prefixes. It recognizes the prefixes regardless of case and accepts an optional comma or colon after each prefix. The rest of the text keeps its case, diacritics, acronyms, and punctuation. An omitted, null, empty, or whitespace-only query becomes an empty string. Reapplying normalization yields the same text.

The original query is logged only at `TRACE` for `com.edupulse.rag.controller.ComplianceRagController`, with control characters escaped. To inspect the original text locally, set `--logging.level.com.edupulse.rag.controller.ComplianceRagController=TRACE` when starting the service and use a sample without personal data.

### Postman example

1. Use `POST http://localhost:8080/api/auth/token` to get an access token with the local credentials from **Quick Test** below.
2. Send `POST http://localhost:8080/api/rag/query`. In **Authorization**, select **Bearer Token** and paste the access token. In **Body**, select **raw** and **JSON**, then enter the following.

```json
{"query":"  Vă rog,   ce spune GDPR despre păstrarea datelor???  "}
```

Expect HTTP `200 OK` and `"query": "ce spune GDPR despre păstrarea datelor"`. The gateway supplies the tenant from the token.

---

## Acronym and synonym expansion (S3 T3, `rag-query-service`)

The service normalizes the input first and returns it in `query`. It then runs `AcronymExpander.expand()` and returns the result in the additional `expandedQuery` field. The tenant, demo answer and citations are served as before.

Expansion matches whole Unicode words regardless of case. It keeps the original acronym and appends its full form and any configured synonyms in parentheses. It annotates only the first eligible occurrence of each acronym and skips it if its full form already appears in the query. Queries without matches keep their text.

The editable [sample dictionary](rag-query-service/config/acronyms.txt) is outside the JAR. Copy it to an accessible location or use it directly. In a PowerShell terminal at the repository root, configure and start the service as follows.

```powershell
$env:RAG_ACRONYMS_PATH = (Resolve-Path .\rag-query-service\config\acronyms.txt).Path
.\mvnw.cmd -pl rag-query-service spring-boot:run
```

You can also set the Spring property `rag.acronyms.path` to an absolute file path. Use UTF-8 with one `ACRONYM = Full form` entry per line. Add optional synonyms after semicolons, for example `ÎS = Învățământ Secundar; studii secundare`. Blank lines and lines starting with `#` are ignored. Edit the file and restart the service to load the changes. A missing or invalid configured file, including a case-insensitive duplicate acronym, logs a startup warning and leaves expansion disabled. With no path configured, `expandedQuery` contains the normalized text.

### Postman example for expansion

1. With the sample dictionary configured, get an access token from `POST http://localhost:8080/api/auth/token` using the local credentials from **Quick Test** below.
2. Send `POST http://localhost:8080/api/rag/query` with the token in **Authorization → Bearer Token**. Select **Body → raw → JSON** and send this request.

```json
{"query":"  Vă rog,  ce spun GDPR și FERPA???  "}
```

Expect HTTP `200 OK`, with `query` equal to `ce spun GDPR și FERPA` and `expandedQuery` equal to `ce spun GDPR (General Data Protection Regulation) și FERPA (Family Educational Rights and Privacy Act)`.

---

## 🧵 Neighbor stitching (`rag-query-service`, `com.edupulse.rag.postretrieval`)

`NeighborStitcher.stitch(hits, window, tokenBudget)` expands each retrieved chunk with its adjacent chunks (by `chunk_index`), merges runs of adjacent chunks into one `Passage` and removes the text overlap between them.

* **Overlap dedup:** the longest suffix of the previous chunk that is also a prefix of the next one is dropped, only if it has at least `minOverlapChars` characters (shorter coincidences are kept so real text is never cut). Set `minOverlapChars` below the chunker's overlap size.
* **Token budget:** retrieved chunks are taken first (rank order), then neighbors, nearest first, alternating before/after. A chunk that does not fit is skipped. `Passage.tokens` is the sum of the chunk tokens before overlap removal, so the real size is never above the budget. The default counter `NeighborStitcher.APPROX_TOKENS` is a rough 4 chars/token; pass the real tokenizer when available.
* **Boundaries:** a neighbor must have the same `docId` and `version` as the hit and the requested `chunk_index`, whatever the lookup returns, so stitching never crosses documents or mixes versions.
* **Edges:** first chunk (no previous), last chunk (no next) and single-chunk documents are covered by tests.
* **Integration (TODO):** implement `ChunkNeighborLookup` on top of pgvector (`doc_id`, `version`, `chunk_index` metadata, from S1 T1) and call the stitcher after reranking. Tests use an in-memory lookup.

```powershell
.\mvnw.cmd -pl rag-query-service test
```

---

## 🛡️ Ingestion source security (SSRF controls)

Package `com.edupulse.gateway.ingestion.security` in `gateway-ingestion-service`. Every external source (web page, remote PDF, repository docs) must be fetched through `SafeFetcher` and its text extracted through `ContentSanitizer`.

| Control | Where | Proven by (negative test) |
| :--- | :--- | :--- |
| HTTPS only (`file:`, `ftp:`, `gopher:`, `javascript:`, scheme-relative rejected) | `UrlValidator` | `UrlValidatorTest.rejectsNonHttpsSchemes` |
| Domain allowlist (subdomains allowed, lookalikes such as `notexample.com` rejected; empty list rejects all) | `UrlValidator` | `rejectsDomainOutsideAllowlist`, `rejectsLookalikeDomains`, `emptyAllowlistRejectsEverything` |
| No credentials in the URL | `UrlValidator` | `rejectsCredentialsInUrl` |
| Block localhost, private ranges (10/8, 172.16/12, 192.168/16), link-local, CGNAT, IPv6 loopback/ULA, IPv4-mapped IPv6; every resolved address is checked | `UrlValidator` | `blocksHostResolvingTo*`, `blocksIpv4MappedIpv6Loopback`, `blocksWhenAnyResolvedAddressIsPrivate` |
| Block cloud metadata (`169.254.169.254`, `fd00:ec2::254`) | `UrlValidator` | `blocksCloudMetadataAddress`, `blocksLiteralInternalHostsEvenWhenListedInAllowlist` |
| Timeout | `SafeFetcher` | `SafeFetcherTest.enforcesTimeout` |
| Size limit (streamed read) | `SafeFetcher` | `rejectsResponseOverSizeLimit` |
| Content-type allowlist | `SafeFetcher` | `rejectsDisallowedContentType` |
| Redirects re-validated (max 3) | `SafeFetcher` | `revalidatesRedirectTarget`, `stopsRedirectLoops` |
| Scripts and active content stripped (`script`, `style`, `iframe`, `object`, `embed`, `form`, `on*` handlers, `javascript:` URLs) | `ContentSanitizer` | `ContentSanitizerTest` |

Blocked sources are logged at `WARN` (`Blocked ingestion source ...`) with the reason. A blocked source raises `UrlSafetyException`; the batch caller is expected to catch it per source so one failure does not stop the rest.

Configuration (`edupulse.ingestion.fetch.*`):

| Property / env var | Default | Meaning |
| :--- | :--- | :--- |
| `allowed-domains` / `INGESTION_ALLOWED_DOMAINS` | empty (nothing allowed) | Comma-separated domains, subdomains included |
| `https-only` | `true` | Reject plain HTTP |
| `timeout` | `10s` | Connect and response timeout |
| `max-bytes` | `5242880` | Maximum response size |
| `allowed-content-types` | `text/html,application/pdf,text/plain,text/markdown` | Accepted `Content-Type` values |
| `max-redirects` | `3` | Redirects followed, each re-validated |

Known limitation: DNS is resolved once for validation and again for the connection (DNS rebinding window); the domain allowlist limits the exposure.

```powershell
.\mvnw.cmd -pl gateway-ingestion-service test
```

---

## 🔎 audit-analytics-service (Member 3)

### Gap analysis (agentic workflow)
`POST /api/gap-analysis/run` with `{"targetFramework": "SOX", "regulationText": "..."}` returns **202 Accepted** immediately with a `PENDING` report and a `Location` header. In the background the service:

1. splits the regulation into structure-aware chunks (headings such as `Article`, `Section`, `1.2`, `#`);
2. queries `rag-query-service` (through Eureka) for the existing internal policies matching each chunk;
3. asks the LLM whether the policies cover the requirement (the regulation text is passed as data, never as instructions);
4. stores the report and writes one audit event per chunk plus one for the final report.

Poll `GET /api/gap-analysis/reports/{id}`: `PENDING` → `RUNNING` (with `analyzedChunks` growing) → `COMPLETED`, `PARTIAL` or `FAILED`.
The report only lists requirements that are not covered (`GAP`) and requirements that could not be evaluated (`NOT_ASSESSED`, e.g. RAG or LLM unavailable): a failed evaluation is never reported as compliant or as a gap. A report is `PARTIAL` when some chunks were `NOT_ASSESSED` or the `GAP_MAX_CHUNKS` cap was reached.

Export a `COMPLETED`/`PARTIAL` report with `GET /api/gap-analysis/reports/{id}/export?format=json|csv|pdf` (409 while the report is still running). Limitation: the PDF uses a standard font, so characters outside Latin-1 (e.g. Romanian diacritics) may not render.

Backpressure: at most `GAP_CONCURRENCY` analyses run at once and `GAP_QUEUE_CAPACITY` wait; beyond that the API answers `429`. Texts above `GAP_MAX_INPUT_CHARS` are rejected with `413`. Analyses left unfinished by a restart are marked `FAILED` on startup.

### Audit trail and latency NFR
Every compliance query should be recorded so the context given to the LLM can be reviewed later. Other services call:

```http
POST /api/audit/events
Authorization: Bearer <the user's JWT>
Content-Type: application/json

{"eventType": "RAG_QUERY", "userId": "...", "query": "...", "context": "chunks used", "answer": "...", "latencyMs": 420}
```

* The tenant comes from the JWT, never from the request: **forward the user's `Authorization` header**, otherwise the call is rejected with 401.
* `GET /api/audit/stats/latency` returns p50 / p95 / max and whether p95 meets the 2.0 s NFR.
* `GET /api/audit/export/json|csv` exports the tenant's audit trail (CSV cells are protected against formula injection).

### Security of this service
The service validates the gateway's JWT itself (HS256, same `JWT_SECRET` and issuer as the gateway) and enforces RBAC, so calling port 8082 directly gives no way around the gateway. `X-Tenant-Id` / `X-User-Id` are always overwritten with the values from the token. Unknown paths are denied, and `/actuator/**` except `health`/`info` is ADMIN-only.

### Configuration (environment variables)

| Variable | Default | Meaning |
| :--- | :--- | :--- |
| `JWT_SECRET` | dev-only value | HMAC secret (>= 32 bytes). **Must be identical to the gateway's.** Set a real one outside development. |
| `JWT_ISSUER` | `edupulse-gateway` | Expected token issuer |
| `OPENAI_API_KEY` | `demo-key` | Without a real key every chunk ends up `NOT_ASSESSED` |
| `GAP_CHUNK_SIZE` | `1500` | Max characters per regulation chunk |
| `GAP_MAX_CHUNKS` | `100` | Cost cap: chunks analysed per run |
| `GAP_MAX_INPUT_CHARS` | `500000` | Max accepted regulation size |
| `GAP_CONCURRENCY` / `GAP_QUEUE_CAPACITY` | `4` / `20` | Parallel analyses / waiting analyses |

Tables (`audit_event`, `gap_report`, `gap_finding`) are created on startup from `schema.sql`. After starting the services, wait about 40 seconds for the instance to appear in Eureka before calling it through the gateway (otherwise: 503/500 `Unable to find instance`).

### Evaluation harness (`com.edupulse.audit.eval`)

Measures retrieval quality (recall@k, MRR) of the RAG pipeline on a golden set of question → expected-source pairs (unanswerable questions have `"answerable": false`). One command runs it:

```powershell
# against the live system (gateway on 8080); the token is read from the environment
$env:EVAL_TOKEN = "<jwt from POST /api/auth/token>"
.\mvnw.cmd -q -pl audit-analytics-service compile exec:java "-Deval.args=--golden audit-analytics-service/eval/golden-set.json --url http://localhost:8080 --k 5 --out audit-analytics-service/eval/reports"

# offline, from recorded results (CI, demos)
.\mvnw.cmd -q -pl audit-analytics-service compile exec:java "-Deval.args=--golden audit-analytics-service/eval/golden-set.example.json --results audit-analytics-service/eval/recorded-results.example.json --out audit-analytics-service/target/eval-demo"
```

| Argument | Default | Meaning |
| :--- | :--- | :--- |
| `--golden` | `eval/golden-set.json` | Golden set file |
| `--url` / `--results` | required (one of them) | Live system, or recorded results `{"<question>": ["source", ...]}` |
| `--k` | `5` | Top-k |
| `--out` | `eval/reports` | Writes `report.json` and `report.md` |
| `--baseline` | none | Committed `report.json`; exit code `1` if recall@k or MRR dropped by more than `--max-drop` (default `0.02`) or failures increased |

Exit codes: `0` ok, `1` regression, `2` bad arguments or unreadable input. A pipeline change must pass the run against the committed baseline before merge. Failed retrievals are logged, score 0 and are listed in the report instead of aborting the run.

Current state: retrieval metrics only. Faithfulness is not measured yet (S4 T3), the live mode reads `citations[].source` from `POST /api/rag/query`, and `eval/golden-set.example.json` is invented sample data showing the format; the real golden set (30+ pairs) and the committed baseline report follow once ingestion (S1 T1) provides the sample documents.

### Tests
```powershell
.\mvnw.cmd -pl audit-analytics-service test
```

---

## 🚀 Getting Started

### 1. Start the Database (PostgreSQL + pgvector)
```powershell
docker compose -f docker/postgres/pgvector.yml up -d
```

### 2. Set the Keys (OpenAI, JWT)
```powershell
$env:OPENAI_API_KEY="sk-..."
$env:JWT_SECRET="a-secret-of-at-least-32-characters..."   # optional in dev
```

### 3. Start the Microservices (in order, in separate terminals)
```powershell
.\mvnw.cmd -pl gateway-ingestion-service spring-boot:run   # first: hosts Eureka
.\mvnw.cmd -pl rag-query-service spring-boot:run
.\mvnw.cmd -pl audit-analytics-service spring-boot:run
```

### 4. Quick Test
```powershell
$t = (Invoke-RestMethod -Method Post http://localhost:8080/api/auth/token -ContentType 'application/json' -Body '{"username":"officer","password":"officer"}').access_token
Invoke-RestMethod -Method Post http://localhost:8080/api/rag/query -Headers @{Authorization="Bearer $t"} -ContentType 'application/json' -Body '{"query":"GDPR data retention"}'
```
