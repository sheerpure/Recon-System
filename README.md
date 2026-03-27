## 📅 開發日誌 - 2026-03-25 ~ 03-28 (從大數據實戰到安全架構重構)

### 🎯 開發目標
對接 **Kaggle PaySim** 金融數據集，克服大檔案處理的記憶體限制，並解決雲端環境 (OCI) 下的 JWT 驗證與跨平台部署挑戰。

### 🛠️ 1. 環境架構與依賴修復 (Dependency Troubleshooting)
在導入 `Apache Commons CSV` 過程中遇到嚴重的環境阻礙，透過以下策略打通：
- **System Path Injection**: 由於網路環境限制導致無法從中央倉庫下載，採取「外部掛載」方案，在專案根目錄建立 `libs/` 資料夾，並於 `pom.xml` 使用 `<scope>system</scope>` 手動鏈結 JAR 檔，徹底解決 `Missing artifact` 報錯。
- **IDE Cache Synchronization**: 執行 `Java: Clean Java Language Server Workspace` 消除 VS Code 編譯器殘留的 `Unresolved compilation problem`。

### 🚀 2. 大數據處理與效能演進 (High-Volume Ingestion)
- **Streaming Parser**: 棄用一次性讀取的 `getRecords()`，改用 **Streaming (串流)** 模式逐行解析。這讓系統在處理 100MB+ 的 CSV 時，記憶體佔用保持在極低水平，成功解決 **OOM (OutOfMemory)** 問題。
- **Batch Persistence**: 實作每 1000 筆資料執行一次 `repository.saveAll()` 的批次寫入機制，並開啟 `rewriteBatchedStatements`，將資料庫插入效率提升至原本的 5 倍以上。

### 🔐 3. 驗證架構與安全重構 (Security & JWT)
- **Cookie-Header Dual Support**: 修改 `JwtAuthFilter`，使其優先解析 `Authorization: Bearer` Header，解決瀏覽器在非 HTTPS (HTTP/IP) 環境下拒收 Cookie 導致的 `Anonymous` 身分問題。
- **LocalStorage Persistence**: 在 `login.html` 實作 Token 攔截，登入成功後將 JWT 存入 `localStorage`，確保身分憑證在無 Cookie 狀態下依然有效。

### ⚖️ 4. 業務邏輯優化 (Business Logic Refinement)
- **Risk Mitigation Logic**: 修正原本「審核通過」卻因標籤邏輯導致「高風險計數」增加的 Bug。
- **Label Clearing Mechanism**: 當交易變更為 `PROCESSED` 時，同步將 `amlAlert` 設為 `null`，達成「管理員處理完畢 = 風險排除 = 儀表板數字下降」的數據閉環。

### 🐞 關鍵錯誤與解決方案總表 (Critical Bug Fixes)

| 錯誤現象 (Problem) | 根本原因 (Root Cause) | 最終解決方案 (Solution) |
| :--- | :--- | :--- |
| **Missing artifact org.apache.commons.csv** | Maven 中央倉庫連線逾時，且本地快取損壞 | **手動掛載**：建立 `libs/` 並在 `pom.xml` 指定 `systemPath` |
| **java.lang.OutOfMemoryError** | 試圖將百萬行 CSV 一次讀入記憶體 | **串流處理**：改用 `Iterator` 逐行解析並分批 Save |
| **DecodingException: Illegal base64 character** | JJWT 庫誤將包含 `!` 的 Secret 當成 Base64 解碼 | **Key 物件化**：改用 `Keys.hmacShaKeyFor` 強制 UTF-8 讀取 |
| **WeakKeyException (Key < 512 bits)** | HS512 算法要求密鑰長度至少 64 bytes | **密鑰強化**：將 `jwtSecret` 字串補長至 90 字元以上 |
| **403 Forbidden (AnonymousUser)** | HTTP 環境導致瀏覽器拒絕寫入帶屬性的 Cookie | **Header 轉向**：改用前端 `Authorization` Header 傳遞 JWT |
| **$'true\r': command not found** | Windows 腳本換行符 (`\r\n`) 與 Linux 不相容 | **格式歸一**：將指令串接為單行字串傳遞，消除隱形 `\r` |

### 🚦 目前進度
- [x] **Local Dependency Manual Injection** (解決環境連線障礙) ✅
- [x] **Streaming CSV Parser** (解決大檔案 OOM 問題) ✅
- [x] **JWT Security Hardening** (解決加密算法報錯與身分失效) ✅
- [x] **Audit Logic Correction** (修正審核通過後風險數據不降反升的問題) ✅