/**
 * Dashboard Management for Recon-System (Header-Based Auth Version)
 * * CORE FUNCTIONALITIES:
 * 1. Charting: Visualizes transaction types and risk distributions using Chart.js.
 * 2. Ingestion: Handles CSV uploads with manual Authorization Headers.
 * 3. Auditing: Supports both individual and batch transaction status updates.
 * 4. Security: Utilizes LocalStorage for JWT persistence to bypass HTTP/IP-based Cookie restrictions.
 */

/**
 * --- 1. SECURITY UTILS ---
 */

/**
 * Retrieves the JWT token from localStorage and prepares the Authorization header.
 * Necessary because modern browsers often block HttpOnly cookies on non-HTTPS (IP-based) connections.
 * @returns {Object} Headers object containing the Bearer token or an empty object.
 */
function getAuthHeader() {
    const token = localStorage.getItem('jwt_token');
    return token ? { 'Authorization': `Bearer ${token}` } : {};
}

/**
 * --- 2. DOM INITIALIZATION ---
 */
document.addEventListener('DOMContentLoaded', () => {
    
    const token = localStorage.getItem('jwt_token');
    const username = localStorage.getItem('username');

    /**
     * Client-Side Guard: If no token exists, the session is invalid.
     * Redirect to login immediately.
     */
    if (!token) {
        console.warn("[Security] No valid session token found. Redirecting to login...");
        window.location.href = '/login';
        return;
    }

    // Display the authenticated username in the UI
    const userDisplay = document.getElementById('displayUsername');
    if (userDisplay && username) {
        userDisplay.innerText = username;
    }

    // Initialize UI components for batch operations
    setupBatchUI();

    // Render data visualizations using global variables provided by Thymeleaf
    renderCharts();
});

/**
 * --- 3. GLOBAL ACTION HANDLERS ---
 */

/**
 * Handles the asynchronous upload of transaction datasets.
 * Resets the current repository state and ingests new data.
 * @param {HTMLInputElement} input - The file input element.
 */
async function uploadFile(input) {
    if (!input.files || !input.files[0]) return;
    const file = input.files[0];
    
    renderMessage(`⏳ Processing ${file.name}...`, 'success');
    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch('/api/transactions/upload', { 
            method: 'POST',
            headers: { 
                ...getAuthHeader() // Injecting JWT manually via Headers
            },
            body: formData 
        });

        // Handle Session Expiry or Permission Denied
        if (response.status === 401 || response.status === 403) {
            handleLogout();
            return;
        }

        if (response.ok) {
            renderMessage('✅ Upload successful! Refreshing...', 'success');
            setTimeout(() => window.location.reload(), 1000);
        } else {
            const errorText = await response.text();
            renderMessage(`❌ Error: ${errorText}`, 'error');
        }
    } catch (error) {
        renderMessage('❌ Network error: Could not reach the server.', 'error');
    }
}

/**
 * Handles individual status updates (e.g., approving or rejecting a specific transaction).
 * Matches the backend endpoint: PATCH /api/transactions/{id}/status?status=...
 * @param {number} id - The database ID of the transaction.
 * @param {string} status - The new status (e.g., 'PROCESSED', 'REJECTED').
 */
async function handleAction(id, status) {
    if (!confirm(`Confirm [${status}] for this transaction?`)) return;
    
    try {
        const response = await fetch(`/api/transactions/${id}/status?status=${status}`, { 
            method: 'PATCH',
            headers: { 
                ...getAuthHeader() // Injecting JWT manually via Headers
            }
        });
        
        if (response.status === 401 || response.status === 403) {
            alert('❌ Unauthorized: Your session may have expired.');
            handleLogout();
            return;
        }
        
        if (response.ok) {
            window.location.reload();
        } else {
            alert('❌ Failed to update transaction status.');
        }
    } catch (error) {
        console.error("Action error:", error);
        alert('❌ Network error during operation.');
    }
}

/**
 * Handles batch status updates for all selected checkboxes.
 * Matches the backend endpoint: PATCH /api/transactions/batch-status
 * @param {string} status - The new status to apply to all selected records.
 */
async function handleBatchAction(status) {
    const checkedBoxes = document.querySelectorAll('.tx-checkbox:checked');
    const ids = Array.from(checkedBoxes).map(cb => parseInt(cb.value));

    if (ids.length === 0) return;
    if (!confirm(`Apply [${status}] to ${ids.length} selected records?`)) return;

    try {
        const response = await fetch('/api/transactions/batch-status', {
            method: 'PATCH',
            headers: { 
                'Content-Type': 'application/json',
                ...getAuthHeader() // Injecting JWT manually via Headers
            },
            body: JSON.stringify({ ids: ids, newStatus: status })
        });
        
        if (response.status === 401 || response.status === 403) {
            handleLogout();
            return;
        }

        if (response.ok) {
            window.location.reload();
        } else {
            alert('❌ Batch update failed.');
        }
    } catch (error) {
        alert('❌ Network error during batch operation.');
    }
}

/**
 * Clears security credentials from LocalStorage and redirects to the login page.
 */
function handleLogout() {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('username');
    window.location.href = '/login';
}

/**
 * --- 4. UI & VISUALIZATION HELPERS ---
 */

/**
 * Initializes the "Select All" logic and manages the visibility of the batch actions bar.
 */
function setupBatchUI() {
    const selectAllBox = document.getElementById('selectAll');
    const batchActionsBar = document.getElementById('batchActions');
    const selectedCountDisplay = document.getElementById('selectedCount');

    const updateBatchUI = () => {
        const checkedBoxes = document.querySelectorAll('.tx-checkbox:checked');
        const count = checkedBoxes.length;
        if (count > 0) {
            batchActionsBar?.classList.remove('hidden');
            batchActionsBar?.classList.add('flex'); 
            if (selectedCountDisplay) selectedCountDisplay.innerText = count;
        } else {
            batchActionsBar?.classList.add('hidden');
            batchActionsBar?.classList.remove('flex');
        }
    };

    selectAllBox?.addEventListener('change', (e) => {
        document.querySelectorAll('.tx-checkbox').forEach(cb => cb.checked = e.target.checked);
        updateBatchUI();
    });

    document.addEventListener('change', (e) => {
        if (e.target.classList.contains('tx-checkbox')) updateBatchUI();
    });
}

/**
 * Renders data visualization charts using Chart.js.
 * Requires labels and data to be pre-populated in the global window object by Thymeleaf.
 */
function renderCharts() {
    const typeCanvas = document.getElementById('typePieChart');
    if (typeCanvas && window.typeLabels?.length > 0) {
        new Chart(typeCanvas.getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: window.typeLabels,
                datasets: [{
                    data: window.typeData,
                    backgroundColor: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'],
                    borderWidth: 0
                }]
            },
            options: { responsive: true, maintainAspectRatio: false, cutout: '75%' }
        });
    }

    const riskCanvas = document.getElementById('riskBarChart');
    if (riskCanvas && window.riskLabels?.length > 0) {
        new Chart(riskCanvas.getContext('2d'), {
            type: 'bar',
            data: {
                labels: window.riskLabels,
                datasets: [{ label: 'Transactions', data: window.riskData, backgroundColor: '#6366f1', borderRadius: 8 }]
            },
            options: { responsive: true, maintainAspectRatio: false }
        });
    }
}

/**
 * Displays a status or error message banner to the user.
 * @param {string} text - The message to display.
 * @param {string} type - 'success' or 'error' to determine styling.
 */
function renderMessage(text, type) {
    const statusMessage = document.getElementById('statusMessage');
    if (!statusMessage) return;
    statusMessage.innerText = text;
    statusMessage.className = `block border p-4 rounded-xl mb-6 ${
        type === 'error' 
            ? 'bg-red-50 text-red-700 border-red-100' 
            : 'bg-emerald-50 text-emerald-700 border-emerald-100'
    }`;
}

// Expose methods to the global scope for HTML event listeners (onclick, onchange)
window.uploadFile = uploadFile;
window.handleAction = handleAction;
window.handleBatchAction = handleBatchAction;
window.handleLogout = handleLogout;