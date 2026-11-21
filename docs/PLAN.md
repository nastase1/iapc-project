# Plan pe 3 săptămâni (21 nov – 7 dec 2026)

Deadline Epic 1: **luni, 7 decembrie**. Ultima săptămână are doar 3 zile (sâm–lun), deci e folosită pentru buffer, demo și regresie.

## Împărțirea taskurilor

| Persoană | Serviciu | Taskuri |
|---|---|---|
| **Teo** | `gateway-ingestion-service` | Story 1 complet (T1–4) + S2 T1 (web ingestion) + S2 T3 (watcher/webhook) |
| **Elena** | `rag-query-service` | Story 3 complet (T1–4) + S2 T2 (versioning) |
| **Nicol** | `audit-analytics-service` | Story 4 complet (T1–4) + S2 T4 (securitate SSRF) |

Task 1-urile sunt acoperite: S1T1 și S2T1 (Teo), S3T1 (Elena), S4T1 (Nicol).

## Săptămâna 1 (21–27 nov): fundația

**Toți, în primele 24 de ore:** stabiliți schema de metadate (`doc_id, version, source, page, chunk_index, checksum, is_latest, valid_from`) și tabela pgvector. Aprobați un set de 5–6 PDF-uri de test, inclusiv unul cu tabele și unul cu coloane multiple.

| Cine | Ordine | Livrabil |
|---|---|---|
| **Teo** | 1. S1 T1 PDF ingestion (prioritate maximă) | Chunks cu metadate complete în pgvector, configurabile, fără duplicate la re-rulare. **Gata până miercuri 25 nov.** |
| | 2. S1 T2 Update (checksum) | Detectare schimbare și înlocuire atomică. |
| **Elena** | 1. S3 T2 Query normalization | Reguli precompilate, test ReDoS, tabel de 15+ cazuri. |
| | 2. S3 T3 Acronym expansion | Dicționar în fișier extern, word boundaries. |
| | 3. S3 T1 Safe metadata filtering | Parsare filtre, whitelist, eroare 400. Începe cu date mock, apoi conectează la datele lui Teo. |
| **Nicol** | 1. S2 T4 Security | Validator URL ca modul separat (SSRF, allowlist, HTTPS, timeout, limită mărime, content-type, strip scripturi) + teste negative. |
| | 2. S4 T4 skeleton | Harness de evaluare (o comandă, recall@k, MRR) + golden set parțial. |
| | 3. S4 T2 Neighbor stitching | Logică și teste pe date mock; conectare la `chunk_index` real după S1 T1. |

**Checkpoint vineri 27 nov:** pipeline-ul minim merge cap-coadă (upload → chunks → query simplu); fiecare are cel puțin un task închis cu teste.

## Săptămâna 2 (28 nov – 4 dec): funcționalități principale

| Cine | Ordine | Livrabil |
|---|---|---|
| **Teo** | 1. S1 T3 Delete + S1 T4 Manifest/reindex | Orphan check, script de reconciliere, re-index cu alt model. |
| | 2. S2 T1 Internet ingestion | Pagină web, PDF remote, docs de repo; folosește validatorul lui Nicol; eșecul unei surse nu oprește batch-ul. |
| | 3. S2 T3 Automation | Watcher sub 30 s, webhook HMAC (401 la semnătură invalidă), dedupe, retry cu backoff. |
| **Elena** | 1. S2 T2 Versioning | `is_latest`, `valid_from`, parametrul `version`, test cu două versiuni. |
| | 2. S3 T4 Multi-query expander | RRF, fallback la query-ul original, latență/cost măsurate, recall@k vs baseline. |
| | 3. Colecție Postman | Demonstrează filtrele AND/OR de la S3 T1. |
| **Nicol** | 1. S4 T1 Reranker | Top-N 20 → top-k 5, model pinned, fallback la ordinea originală, MRR înainte/după. |
| | 2. S4 T3 Citations & grounding | Header per context block, prompt „doar din context", prag de similaritate, 10 întrebări out-of-scope refuzate. |
| | 3. S4 T4 Golden set final | 30+ perechi incl. fără răspuns, raport baseline commit-uit. |

**Checkpoint vineri 4 dec:** toate taskurile implementate; rămâne finisarea și demo-ul.

## Săptămâna 3 (5–7 dec): buffer, demo, livrare

| Zi | Ce se face |
|---|---|
| **Sâmbătă 5 dec** | Fiecare își închide restanțele și rulează testele (succes + eșec). Nicol rulează regresia pe golden set și actualizează raportul. |
| **Duminică 6 dec** | Review încrucișat și merge pe `main`. Verificare: niciun secret în repo, configurări documentate. Actualizare README. |
| **Luni 7 dec** | Demo către product owner; fiecare prezintă taskurile lui, Teo arată ingestion-ul cap-coadă. |

## Reguli de lucru

- **Blocaje:** dacă S1 T1 întârzie, Elena și Nicol continuă pe mock-uri; se anunță imediat.
- **Regresie:** orice schimbare de pipeline trece prin harness-ul de evaluare înainte de merge (cerință S4 T4).
- **Riscuri:** S2 T3 (webhook + retry + dedupe) și S4 T1 (măsurarea reranker-ului) sunt cele mai probabile să alunece; se încep devreme.

## Baseline pentru fiecare task

- Cod revizuit de un coleg și merge-uit pe `main`.
- Teste automate: cel puțin unul pe succes și unul pe eșec.
- Fără secrete în repo; configurarea documentată.
- Erori și evenimente cheie logate.
- README/docs actualizate; rezultatul demonstrat product owner-ului.
