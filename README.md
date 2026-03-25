# 🚀 Recon-System: 金融對帳自動化 MVP

這是一個基於 **Java Spring Boot 3** 開發的金融對帳系統 (Financial Reconciliation System)。
目標是自動化比對銀行流水帳與內部交易紀錄，並整合 **AML (反洗錢)** 異常監控邏輯。

---

## 📅 開發日誌 - 2026-03-25 (大數據實戰與效能優化)

### 🎯 今日目標
將系統提升，對接 **Kaggle PaySim** 模擬數據集，解決大檔案處理造成的系統阻塞與記憶體溢出問題。

### 🚀 1. High-Volume Data Ingestion (大數據吞吐能力)
- **Kaggle Dataset Integration**: 成功對接真實世界模擬數據集 `PaySim`，包含 `CASH_OUT`、`TRANSFER` 等金融特徵。
- **Batch Persistence Strategy**: 實作 **Batch Size = 1000** 的批次寫入機制，大幅優化資料庫 I/O 效率，確保系統能穩定處理 100,000+ 筆交易樣本。
- **Async Processing (@Async)**: 建立專用的 `fileImportExecutor` 執行緒池，實作**非同步背景上傳**。前端點擊後立即釋放請求，解決 HTTP Timeout 與 UI 卡死問題。

### 📄 2. Server-Side Pagination (伺服器端分頁)
- **O(1) Performance**: 棄用傳統 `findAll()`，全面導入 Spring Data **`Pageable`**。即使資料庫累積百萬筆數據，每次僅精確查詢 20 筆，確保 Dashboard 秒開不卡頓。
- **Dynamic Metadata**: 透過 `Page<Transaction>` 物件傳遞，自動計算「總頁數」、「當前頁碼」與「總筆數」，並同步於前端 UI 渲染專業的分頁導覽列。

### 📊 3. Advanced Exception Management (異常管理功能)
- **Real-time KPI Dashboard**: 頂部新增三組統計卡片，動態計算 **10 億級總交易量**、**高風險預警數** 與 **待稽核案件數**。
- **Multi-Tab Filtering**: 實作「一鍵過濾」標籤頁（All / Risk / Pending），讓合規官能從海量數據中精確鎖定異常（如單筆超過 $200,000 的高額轉帳）。
- **Enhanced AML Logic**: 整合 Kaggle 原生 `isFraud` 標籤與自定義風險規則，自動標記 `CONFIRMED_FRAUD_PATTERN` 預警。

### 🐞 4. Critical Debugging & Problem Solving (關鍵除錯紀錄)

| 錯誤現象 | 根本原因 (Root Cause) | 解決方案 |
| :--- | :--- | :--- |
| **413 Payload Too Large** | Spring Boot 預設上傳限制僅 1MB，無法載入 Kaggle 大檔案。 | 於 `application.properties` 調高 `max-file-size` 與 `max-request-size` 至 **500MB**。 |
| **Type Mismatch (Async)** | Controller 試圖直接接收 `@Async` 方法回傳的 `List` 容器。 | 改用 **Fire and Forget** 模式，讓 Service 背景跑，Controller 立即回傳 200 OK 確保連線不中斷。 |
| **Out of Memory (OOM)** | 原本 `findAll()` 試圖一次將數十萬筆 DOM 元素塞入瀏覽器。 | 導入 **Server-side Pagination**，嚴格限制單次 API 數據回傳量。 |

### 🚦 目前進度 (Current Status)
- [x] **Asynchronous Ingestion Engine** (非同步上傳引擎實作) ✅
- [x] **Server-side Pagination** (分頁邏輯實作) ✅
- [x] **Real-time Analytics Dashboard** (即時 KPI 監控面板) ✅
- [x] **GitHub Repository Updates** (完成專業 README 撰寫) ✅
- [ ] **Next Step**: 實作「一鍵導出 Excel/PDF 報表」與「多條件精確搜索功能」。

---

## 🛠️ 技術亮點 (Technical Highlights)
- **架構進化**: 系統成功從「單執行緒同步」轉向「多執行緒異步」架構，具備處理真實金融日誌的擴展性。
- **UI 優化**: 頂部新增 Filter Tabs 與分頁導覽，讓管理介面具備 SaaS 產品級的操控感。

## ⚙️ 啟動配置更新
若要處理大型 CSV 檔案，請確保 `application.properties` 包含：
```properties
spring.servlet.multipart.max-file-size=500MB
spring.servlet.multipart.max-request-size=500MB
spring.jpa.properties.hibernate.jdbc.batch_size=1000