# EduPulse AI – Intelligent Multi-Tenant Compliance & Audit Assistant

EduPulse AI este o platformă inteligentă de asistență pentru conformitate și audit intern dedicată industriilor strict reglementate (Fintech, Healthcare, Higher Education). Sistemul permite încărcarea documentelor mari și eterogene de politici/reglementări și interogarea acestora în limbaj natural pentru analiză automată a decalajelor de conformitate (Gap Analysis), verificarea conformității politicilor și generarea de rapoarte gata de audit cu citări stricte.

---

## 🏛️ Arhitectura pe Microservicii

Proiectul este structurat ca un proiect Maven multi-modul cu **strict 3 microservicii** – câte unul pentru fiecare membru al echipei – utilizând **Spring Boot 4 / Java 21**, **Spring Cloud 2025.1** (Eureka, Gateway Server MVC, LoadBalancer) și **Spring AI 2.0+** conectat la **PostgreSQL + pgvector**:

```
                                [ Frontend / Client ]
                                          │  JWT (Bearer)
                                          ▼
        ┌──────────────────────────────────────────────────────────────────┐
        │  [1] gateway-ingestion-service (Port 8080)          — Membru 1   │
        │  ┌──────────────┐ ┌──────────────────┐ ┌──────────────────────┐  │
        │  │ Eureka Server│ │ API Gateway      │ │ Document Ingestion   │  │
        │  │ /eureka      │ │ JWT + RBAC,      │ │ /api/ingestion/**    │  │
        │  │              │ │ rutare lb://     │ │ async + hierarchical │  │
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
        │ — Membru 2       │    │ — Membru 3       │        │
        └─────────┬────────┘    └─────────┬────────┘        │
                  │                       │                 │
                  └───────────────────────┼─────────────────┘
                                          ▼
                            ┌───────────────────────────┐
                            │   PostgreSQL + pgvector   │
                            │    (Relational + HNSW)    │
                            └───────────────────────────┘
```

* **Un singur punct de intrare:** clienții apelează doar portul `8080`. Gateway-ul validează token-ul JWT, aplică RBAC și rutează prin Eureka (`lb://rag-query-service`, `lb://audit-analytics-service`). Endpoint-urile de ingestie sunt servite local.
* **Propagarea contextului de tenancy:** gateway-ul suprascrie headerele `X-Tenant-Id`, `X-Org-Unit`, `X-User-Id`, `X-User-Roles` cu valorile din JWT (valorile trimise de client sunt ignorate).
* **Comunicare inter-servicii:** `audit-analytics-service` apelează `rag-query-service` prin `RagQueryClient` (`RestClient` `@LoadBalanced`, rezolvat prin Eureka).

---

## 👥 Împărțirea Rolurilor și Responsabilităților (3 Coechipieri)

Pentru o distribuire echilibrată a complexității, sarcinile sunt împărțite între cei 3 membri ai echipei astfel:

### 🔹 Membru 1: Infrastructură Cloud, Securitate & Ingestion Asincron
* **Microserviciu alocat:** `gateway-ingestion-service` (Eureka Server + API Gateway + Document Ingestion într-un singur serviciu)
* **Responsabilități principale:**
  1. **Service Discovery & Gateway:**
     - Configurare Eureka Server (embedded în `gateway-ingestion-service`) pentru clusterizare și reziliență.
     - Configurare API Gateway (Spring Cloud Gateway Server MVC) cu rutare dinamică bazată pe Service ID (`lb://...`).
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
* **Microserviciu alocat:** `rag-query-service`
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
* **Microserviciu alocat:** `audit-analytics-service`
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
├── pom.xml                           # Root Maven POM (Multi-module, 3 module)
├── mvnw / mvnw.cmd / .mvn/           # Maven Wrapper
├── gateway-ingestion-service/        # [1] Port 8080 - Eureka + API Gateway (JWT/RBAC) + Ingestie asincrona
│   └── src/main/java/com/edupulse/gateway/
│       ├── security/                 #     JWT (HS256), RBAC, emitere token-uri demo
│       ├── routing/                  #     Rute lb:// + propagare tenant din JWT
│       └── ingestion/                #     Upload & Hierarchical Chunking
├── rag-query-service/                # [2] Port 8081 - Advanced RAG & Hybrid Search (pgvector + tsvector)
├── audit-analytics-service/          # [3] Port 8082 - Gap Analysis Agent & Audit Trail
└── docker/
    └── postgres/
        ├── pgvector.yml              # PostgreSQL cu extensia pgvector (port 5434, DB edupulse_db)
        └── init.sql                  # Extensii vector / hstore / uuid-ossp
```

---

## 🔌 Porturi și Endpoint-uri Principale

Toate apelurile client trec prin `http://localhost:8080`.

| Microserviciu | Port | Endpoint Cheie | Roluri | Descriere |
| :--- | :--- | :--- | :--- | :--- |
| **[1] Gateway & Ingestion** | `8080` | `POST /api/auth/token` | public | Emitere JWT (utilizatori demo `admin`/`admin`, `officer`/`officer`) |
| | | `POST /api/ingestion/upload` | ADMIN, COMPLIANCE_OFFICER | Upload & ingestie asincronă documente |
| | | `/eureka-ui`, `/eureka/**` | public (rețea internă) | Dashboard și registru Eureka |
| **[2] RAG Query Engine** | `8081` | `POST /api/rag/query` | ADMIN, COMPLIANCE_OFFICER | Interogare Advanced RAG cu citări |
| **[3] Audit & Gap Analysis** | `8082` | `POST /api/gap-analysis/run` | ADMIN, COMPLIANCE_OFFICER | Declanșare raport de analiză a decalajelor |
| | | `/api/audit/**` | ADMIN | Audit trail & rapoarte |

---

## 🚀 Ghid de Pornire a Proiectului

### 1. Pornirea Bazei de Date (PostgreSQL + pgvector)
```powershell
docker compose -f docker/postgres/pgvector.yml up -d
```

### 2. Setarea Cheilor (OpenAI, JWT)
```powershell
$env:OPENAI_API_KEY="sk-..."
$env:JWT_SECRET="un-secret-de-minim-32-de-caractere..."   # optional in dev
```

### 3. Pornirea Microserviciilor (în ordine, în terminale separate)
```powershell
.\mvnw.cmd -pl gateway-ingestion-service spring-boot:run   # primul: gazduieste Eureka
.\mvnw.cmd -pl rag-query-service spring-boot:run
.\mvnw.cmd -pl audit-analytics-service spring-boot:run
```

### 4. Test rapid
```powershell
$t = (Invoke-RestMethod -Method Post http://localhost:8080/api/auth/token -ContentType 'application/json' -Body '{"username":"officer","password":"officer"}').access_token
Invoke-RestMethod -Method Post http://localhost:8080/api/rag/query -Headers @{Authorization="Bearer $t"} -ContentType 'application/json' -Body '{"query":"GDPR data retention"}'
```
