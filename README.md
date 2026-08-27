# High-Throughput Multi-Tenant Flash Sale Concurrency Engine

A multi-threaded Java simulation engine designed to solve high-concurrency inventory contention, race conditions, and asynchronous payment processing across isolated multi-tenant flash sale campaigns.

---

## Architectural Highlights

- **Multi-Tenant Partitioning:** Isolated `InventoryManager` and per-sale user quotas preventing cross-campaign stock cannibalization.
- **Lock-Free Concurrency Primitives:** Atomic decrements (CAS loops) and contiguous order ID generation under contested buyer threads.
- **Asynchronous Order Processing:** Non-blocking producer-consumer pipeline decoupled via `LinkedBlockingQueue` and `CompletableFuture`.
- **State Machine Integrity:** Enforces the State Pattern to guarantee idempotent order transitions (`PENDING` $\rightarrow$ `SUCCESS` / `FAILED` $\rightarrow$ `REFUNDED`) and eliminate double refunds.
- **Decoupled Telemetry:** Real-time analytics, financial reconciliation, and notification dispatch using the Observer Pattern.

---

## System Architecture

```mermaid
flowchart TD
    subgraph Ingress ["1. Concurrent Ingress Layer"]
        B[50 Concurrent Buyer Threads] -->|2,500 Checkouts| FSM[FlashSaleManager Singleton]
    end

    subgraph MultiTenant ["2. Multi-Tenant Business Boundary"]
        FSM -->|Route by saleId| FS1[FlashSale #1 - 10% Off]
        FSM -->|Route by saleId| FS2[FlashSale #2 - 20% Off]
        FSM -->|Route by saleId| FS5[FlashSale #5 - Flat $50 Off]

        FS1 --> INV1[(InventoryManager #1)]
        FS2 --> INV2[(InventoryManager #2)]
        FS5 --> INV5[(InventoryManager #5)]
    end

    subgraph AsyncCore ["3. Async Processing & Concurrency Core"]
        FSM -->|Enqueues Valid Order| Q[(LinkedBlockingQueue)]
        Q --> W[40 Worker Consumer Threads]
        W --> PP[PaymentProcessor]
    end

    subgraph StateAndTelemetry ["4. State Machine & Telemetry"]
        PP -->|State Transition| OS{Order State}
        OS -->|Mark SUCCESS/FAILED| CF[CompletableFuture Result]
        OS -->|Notify State Change| OP[OrderPublisher]
        OP --> AN[Analytics Real-Time Dashboard]
        OP --> EN[Email/Alert Notification Dispatch]
    end
