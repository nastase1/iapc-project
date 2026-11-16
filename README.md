# EduPulse AI – Intelligent Multi-Tenant Compliance & Audit Assistant

EduPulse AI este o platformă inteligentă de asistență pentru conformitate și audit intern dedicată industriilor strict reglementate (Fintech, Healthcare, Higher Education). Sistemul permite încărcarea documentelor mari și eterogene de politici/reglementări și interogarea acestora în limbaj natural pentru analiză automată a decalajelor de conformitate (Gap Analysis), verificarea conformității politicilor și generarea de rapoarte gata de audit cu citări stricte.

---

## 🏛️ Arhitectura pe Microservicii

Proiectul este structurat ca un proiect Maven multi-modul, utilizând **Spring Boot 3.x / Java 21**, **Spring Cloud** (Eureka, Gateway) și **Spring AI 2.0+** conectat la **PostgreSQL + pgvector**:

```
                                [ Frontend / Client ]
                                          │
                                          ▼
                          ┌───────────────────────────────┐
                          │    API Gateway (Port 8080)    │
                          │  (Securitate JWT & Rutare)    │
                          └───────────────┬───────────────┘
                                          │
                  ┌───────────────────────┼───────────────────────┐
                  ▼                       ▼                       ▼
        ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
        │ Document         │    │ Compliance RAG   │    │ Gap Analysis &   │
        │ Ingestion Svc    │    │ Query Engine     │    │ Audit Trail Svc  │
        │ (Port 8081)      │    │ (Port 8082)      │    │ (Port 8083)      │
        └─────────┬────────┘    └─────────┬────────┘    └─────────┬────────┘
                  │                       │                       │
                  └───────────────────────┼───────────────────────┘
                                          ▼
                            ┌───────────────────────────┐
                            │    Eureka Discovery Svc   │
                            │        (Port 8761)        │
                            └───────────────────────────┘
                                          │
                                          ▼
                            ┌───────────────────────────┐
                            │   PostgreSQL + pgvector   │
                            │    (Relational + HNSW)    │
                            └───────────────────────────┘
```

---

## 👥 Împărțirea Rolurilor și Responsabilităților (3 Coechipieri)

Pentru o distribuire echilibrată a complexității, sarcinile sunt împărțite între cei 3 membri ai echipei astfel:

### 🔹 Membru 1: Infrastructură Cloud, Securitate & Ingestion Asincron
* **Module alocate:** `discovery-service`, `api-gateway`, `document-ingestion-service`
* **Responsabilități principale:**
  1. **Service Discovery & Gateway:**
     - Configurare `discovery-service` (Eureka Server) pentru clusterizare și reziliență.
     - Configurare `api-gateway` (Spring Cloud Gateway) cu rutare dinamică bazată pe Service ID (`lb://...`).
  2. **Securitate & Tenancy:**
     - Autentificare și autorizare stateless via token-uri JWT la nivel de API Gateway.
     - Controlul accesului bazat pe roluri (RBAC): `ROLE_ADMIN`, `ROLE_COMPLIANCE_OFFICER`.
  3. **Document Ingestion Pipeline:**
     - Acceptare fișiere multi-format: PDF, DOCX, TXT.
     - Procesare asincronă pentru fișiere mari (> 10MB) utilizând `@Async` / JobRunr / Thread Pool dedicat pentru a nu bloca apelurile API.
     - Implementare **Hierarchical Chunking**: fragmentare la nivel de child chunks (e.g. 200 tokens pentru potrivire vectorială) mapate pe parent chunks (e.g. 1000 tokens pentru context bogat).
     - Salvarea embeddings în PostgreSQL (`pgvector`) alături de metadate multi-tenant (`tenant_id`, `org_unit`, `access_level`).

---

### 🔹 Membru 2: Core Advanced RAG & AI Orchestration
* **Module alocate:** `rag-query-service`
* **Responsabilități principale:**
  1. **Pre-Retrieval:**
     - Implementarea `MultiQueryExpander`: rescrierea automată a prompt-ului utilizatorului în 3-4 formulări din unghiuri tehnice diferite pentru a maximiza recall-ul.
  2. **Retrieval Hibrid & Metadata Filtering:**
     - **Hybrid Search:** Combinarea căutării semantice `pgvector` (index HNSW) cu căutarea clasică full-text `tsvector` pentru coduri legislative sau termeni exacți.
     - Filtrare strictă bazată pe metadate (`Filter.Expression`) pentru izolarea datelor per organizație / nivel de securitate.
  3. **Post-Retrieval:**
     - **Reranking:** Reordonarea primelor rezultate extrase folosind un Cross-Encoder / Cohere Reranker.
     - **Context Compression:** `CompressionQueryTransformer` pentru eliminarea zgomotului și reducerea consumului de tokens.
  4. **Generare, Guardrails & Citări:**
     - Integrare `RetrievalAugmentationAdvisor` în `ChatClient`.
     - **Strict Source Citations:** Generarea răspunsurilor cu trimiteri exacte la documentul sursă, pagină și fragmentul de text.
     - **Safety Guardrails:** Implementare `SafeguardAdvisor` și `TopicBoundaryAdvisor` pentru a preveni halucinațiile și devierile de la domeniul de conformitate.

---

### 🔹 Membru 3: Agentic Gap Analysis, Audit Trail & Analytics
* **Module alocate:** `audit-analytics-service`
* **Responsabilități principale:**
  1. **Automated Gap Analysis (Agentic Workflow):**
     - Flux autonom care preia un document nou de reglementare, îl descompune chunk-cu-chunk, interoghează baza de politici existente (`rag-query-service`) și generează un raport structurat cu decalajele de conformitate identificate.
  2. **Audit Trail & Istoric:**
     - Jurnalizarea tuturor interogărilor de conformitate și a contextului efectiv utilizat de LLM pentru fiecare răspuns.
     - Păstrarea traseului de audit pentru transparență și conformitate legislativă.
  3. **Rapoarte & Export:**
     - Generare și export de evaluări și rapoarte de conformitate în format structurat (JSON / PDF / CSV).
     - Monitorizarea indicatorilor de performanță (NFR: latență răspuns < 2.0s per căutare semantică).

---

## 📁 Structura Directoarelor Proiectului

```text
├── pom.xml                           # Root Maven POM (Multi-module)
├── discovery-service/                # Port 8761 - Eureka Service Registry
├── api-gateway/                      # Port 8080 - Spring Cloud Gateway & JWT
├── document-ingestion-service/       # Port 8081 - Ingestie asincrona & Hierarchical Chunking
├── rag-query-service/                # Port 8082 - Advanced RAG & Hybrid Search (pgvector + tsvector)
├── audit-analytics-service/          # Port 8083 - Gap Analysis Agent & Audit Trail
├── docker/
│   └── postgres/
│       └── pgvector.yml              # PostgreSQL cu extensia pgvector
└── backend-service/                  # Codul initial monolitic de la laborator (referinta)
```

---

## 🔌 Porturi și Endpoint-uri Principale

| Serviciu | Port | Endpoint Cheie | Descriere |
| :--- | :--- | :--- | :--- |
| **API Gateway** | `8080` | `/api/**` | Punct unic de intrare, rutare și securitate |
| **Discovery Service** | `8761` | `/eureka` | Panou de control și registru Eureka |
| **Document Ingestion** | `8081` | `POST /api/ingestion/upload` | Upload & ingestie asincronă documente |
| **RAG Query Engine** | `8082` | `POST /api/rag/query` | Interogare Advanced RAG cu citări |
| **Audit & Gap Analysis** | `8083` | `POST /api/gap-analysis/run` | Declanșare raport de analiză a decalajelor |

---

## 🚀 Ghid de Pornire a Proiectului

### 1. Pornirea Bazei de Date (PostgreSQL + pgvector)
```powershell
docker compose -f docker/postgres/pgvector.yml up -d
```

### 2. Setarea Cheii de API (OpenAI)
```powershell
$env:OPENAI_API_KEY="sk-..."
```

### 3. Pornirea Microserviciilor (în ordine)
1. **Discovery Service:**
   ```powershell
   cd discovery-service; ..\backend-service\mvnw.cmd spring-boot:run
   ```
2. **API Gateway:**
   ```powershell
   cd api-gateway; ..\backend-service\mvnw.cmd spring-boot:run
   ```
3. **Serviciile de Business:**
   Porniți în terminale separate:
   - `document-ingestion-service`
   - `rag-query-service`
   - `audit-analytics-service`
