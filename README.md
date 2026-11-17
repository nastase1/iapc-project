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
| **[3] Audit & Gap Analysis** | `8082` | `POST /api/gap-analysis/run` | ADMIN, COMPLIANCE_OFFICER | Trigger a gap analysis report |
| | | `/api/audit/**` | ADMIN | Audit trail & reports |

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
