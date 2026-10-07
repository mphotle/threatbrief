# 🛡️ ThreatBrief — AI-Automated Security Intelligence Aggregator

**ThreatBrief** is a reactive full-stack security platform that aggregates daily vulnerability feeds from the **National Vulnerability Database (NVD)**, 
processes and ranks CVE risks, and leverages **Large Language Models (Hugging Face)** to generate executive threat intelligence briefings.

🌐 **Live Demo:** [https://threatbrief.co.za](https://threatbrief.co.za)

---

## 📐 Architecture & Data Flow

~~~mermaid
flowchart TD
    Start(["User Selects Date / Startup Scheduler Triggered"]) --> ValidateDate{"Is Date Within<br/>Past 14 Days?"}
    
    ValidateDate -->|"No"| ErrorOut["Throw InvalidDateRangeException / Return 400"]
    ValidateDate -->|"Yes"| CheckBriefCache{"In ThreatBrief<br/>Caffeine Cache?"}
    
    CheckBriefCache -->|"Yes (Cache Hit)"| ReturnBrief["Return Cached ThreatBrief"]
    
    CheckBriefCache -->|"No (Cache Miss)"| CheckNvdCache{"In NVD<br/>Caffeine Cache?"}
    
    CheckNvdCache -->|"Yes (Cache Hit)"| PrepPrompt["LlmService: Prepare Prompt"]
    
    CheckNvdCache -->|"No (Cache Miss)"| FetchNVD["WebClient: Request NVD REST API"]
    FetchNVD --> Resilience["Apply Retry Backoff on 5xx / Rate Limit Check"]
    Resilience --> ParseNVD["NvdResponseParser: Parse CVSS v2/v3/v4 & Severities"]
    ParseNVD --> CacheNVD["Cache DailyVulnerabilities"]
    CacheNVD --> PrepPrompt
    
    PrepPrompt --> FilterCVEs["Sort Top 20 CVEs by Score & Truncate Descriptions under 200 Chars"]
    FilterCVEs --> CallHF["HuggingFaceProvider: Post to Router API /v1/chat/completions"]
    CallHF --> ExtractText["Extract Generated Markdown Content"]
    
    ExtractText --> StoreBriefCache["Cache ThreatBrief Object"]
    StoreBriefCache --> ReturnBrief
    
    ReturnBrief --> FrontendRender["Frontend: Parse Markdown via marked.js"]
    FrontendRender --> DisplayUI(["Render Briefing to Executive Dashboard"])
    ErrorOut --> DisplayUI
~~~

## 💡 Engineering & Architectural Highlights

### Reactive I/O & API Resilience
Integrating with external APIs like the National Vulnerability Database (NVD) and Hugging Face introduced network latency and strict rate limits as 
primary system bottlenecks. To ensure the application remains responsive under heavy load, the backend is built on Spring WebFlux for non-blocking I/O. 
Outbound calls to NVD feature automated exponential backoff retries for transient 5xx errors, while the parsing layer dynamically normalizes CVSS metrics 
across versions 2.0, 3.0, 3.1, and 4.0—falling back gracefully to unrated states when NIST records are incomplete.

### Two-Tier Caching & Prompt Optimization
To deliver sub-second page loads without burning through LLM token limits or triggering API rate caps, ThreatBrief uses an in-memory Caffeine caching 
architecture paired with background pre-generation. An automated scheduler warms the cache on startup and hourly for a rolling 14-day UTC window. Before 
passing raw CVE feeds to the LLM, the data pipeline filters and sorts vulnerabilities by CVSS severity, caps the payload at the top 20 most critical threats, 
and truncates individual descriptions. This keeps prompts highly focused for executive summary generation while staying strictly within free-tier context windows.

### Resource-Conscious Cloud Runtime
Deploying on a memory-constrained AWS EC2 instance required explicit resource safeguards. The build pipeline uses multi-stage Docker builds paired with JVM 
heap boundaries (`-Xmx512m`) and host-level swap allocation to eliminate out-of-memory crashes during compilation. In production, the application runs inside a 
lightweight Temurin Alpine JRE container as a restricted non-root user (`threatuser`), isolated behind an Nginx reverse proxy handling SSL termination and port 
forwarding.

---

## 🛠️ Tech Stack

* **Backend:** Java 25, Spring Boot 4.1.1 (WebFlux), Netty, Caffeine Cache, Jackson JSR-310
* **Frontend:** Vanilla JS (Async/Await), HTML5, CSS3 (Dark Theme), Marked.js
* **Testing:** JUnit 5, Reactor Test, MockWebServer
* **DevOps & Cloud:** Docker (Multi-stage), Nginx (Certbot SSL), AWS EC2 (Ubuntu)
