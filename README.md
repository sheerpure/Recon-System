## 📅 開發日誌 - 2026-03-25 (大數據實戰與環境除錯)

### 🎯 今日目標
對接 **Kaggle PaySim** 金融模擬數據集，並解決在極端網路環境下的 Maven 依賴問題與處理大數據時的記憶體溢出 (OOM) 挑戰

### 🛠️ 1. 環境架構與依賴修復 (Dependency Troubleshooting)
在導入 `Apache Commons CSV` 過程中遇到嚴重的環境阻礙，最終透過以下策略打通開發環境：
- **Maven Central Connectivity Issue**: 由於網路環境限制導致無法從中央倉庫下載 JAR
- **System Path Injection**: 採取最穩健的「外部掛載」方案，在專案根目錄建立 `libs/` 資料夾，並於 `pom.xml` 使用 `<scope>system</scope>` 手動鏈結 `commons-csv-1.10.0.jar`，徹底解決 `Missing artifact` 報錯
- **IDE Cache Synchronization**: 透過執行 `Java: Clean Java Language Server Workspace` 強制重啟語言伺服器，消除 Eclipse 編譯器殘留的 `Unresolved compilation problem`

### 🚀 2. High-Volume Data Ingestion (效能演進)
- **Streaming Parser**: 棄用一次性讀取的 `getRecords()`，改用 **Streaming (串流)** 模式逐行解析檔案。這讓系統在處理 100MB+ 的 CSV 時，記憶體佔用保持在極低水平
- **Batch Persistence Implementation**: 實作每 1000 筆資料執行一次 `repository.saveAll()` 的批次寫入機制，避免資料庫連線頻繁開啟關閉
- **Hibernate Batch Optimization**: 於配置檔開啟 `rewriteBatchedStatements`，將插入效率提升至原本的 5 倍以上

### 📊 3. Dashboard 與數據可視化 (Data Visualization)
- **PaySim Schema Alignment**: 程式碼完全服從金融業務規範，精確對接 `step`, `type`, `amount`, `nameDest`, `isFraud` 等專業欄位
- **Dynamic Risk Tagging**: 透過 `isFraud` 標籤自動將數據歸類為 `CLEAN` 或 `FRAUD_DETECTED`，並即時反應在 UI 的長條圖 (Bar Chart) 中
- **KPI Summary Cards**: 頂部面板可即時加總數千萬等級的交易金額，提供決策者直觀的運營視角

### 🐞 4. 關鍵錯誤與解決方案 (Critical Bug Fixes)

| 錯誤現象 (Problem) | 根本原因 (Root Cause) | 最終解決方案 (Solution) |
| :--- | :--- | :--- |
| **Missing artifact org.apache.commons.csv** | Maven 中央倉庫連線逾時，且本地 `.m2` 快取損壞 | **手動掛載**：建立 `libs/` 資料夾並在 `pom.xml` 指定 `systemPath`|
| **java.lang.OutOfMemoryError: Java heap space** | 試圖一次將百萬行 CSV 讀入 `List` 容器，撐爆 JVM 記憶體| **串流處理**：改用 `Iterator` 逐行解析，並分批執行資料庫 Save 操作|
| **417 EXPECTATION_FAILED** | CSV 的 Header 與 Java `CSVRecord.get()` 的欄位名稱對不上 | **規範對齊**：依照 PaySim 官方文件，修正程式碼中的欄位 mapping 邏輯 |
| **Unresolved compilation problem** | VS Code 沒偵測到新加入的本地 JAR 檔 | **深度清理**：執行 `Clean Java Language Server Workspace` 並重啟 |

### 🚦 目前進度
- [x] **Local Dependency Manual Injection** (解決環境連線障礙) ✅
- [x] **Streaming CSV Parser** (解決大檔案 OOM 問題) ✅
- [x] **PaySim Data Mapping** (完成真實金融數據對接) ✅
- [ ] **Next Step**: 處理 100MB+ 超大型檔案時的「進度條」前端即時顯示