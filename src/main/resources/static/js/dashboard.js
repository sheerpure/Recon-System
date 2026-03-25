/**
 * Dashboard Management for Recon-System
 * * CORE FUNCTIONALITIES:
 * 1. Charting: Visualizes transaction types and risk distributions using Chart.js.
 * 2. Ingestion: Handles asynchronous streaming uploads for large CSV datasets.
 * 3. Auditing: Supports both single and batch manual audit actions (Approve/Reject).
 * 4. UI States: Manages dynamic visibility of action bars and status messages.
 * 5. Security: Injects JWT tokens into all outgoing fetch requests.
 */

/**
 * Retrieves the JWT token from localStorage and formats it for HTTP headers
 * @returns {Object} Headers object containing Authorization or empty
 */
function getAuthHeader() {
    const token = localStorage.getItem('jwt_token');
    return token ? { 'Authorization': `Bearer ${token}` } : {};
}

document.addEventListener('DOMContentLoaded', () => {
    
    // Check if token exists, if not, redirect to login (basic client-side guard)
    if (!localStorage.getItem('jwt_token')) {
        window.location.href = '/login';
        return;
    }

    // --- 1. DOM Element Selectors ---
    const fileInput = document.getElementById('fileInput');
    const statusMessage = document.getElementById('statusMessage');
    const selectAllBox = document.getElementById('selectAll');
    const batchActionsBar = document.getElementById('batchActions');
    const selectedCountDisplay = document.getElementById('selectedCount');

    // --- 2. Batch Selection & UI Logic ---
    
    /**
     * Updates the visibility and count of the Batch Actions floating bar
     */
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

    /**
     * Global "Select All" checkbox toggle logic
     */
    if (selectAllBox) {
        selectAllBox.addEventListener('change', (e) => {
            document.querySelectorAll('.tx-checkbox').forEach(cb => {
                cb.checked = e.target.checked;
            });
            updateBatchUI();
        });
    }

    /**
     * Event Delegation for individual checkboxes
     */
    document.addEventListener('change', (e) => {
        if (e.target.classList.contains('tx-checkbox')) {
            updateBatchUI();
        }
    });

    // --- 3. Chart.js Visualization Logic ---

    // Doughnut Chart: Transaction Type Breakdown
    const typeCanvas = document.getElementById('typePieChart');
    if (typeCanvas && window.typeLabels && window.typeLabels.length > 0) {
        new Chart(typeCanvas.getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: window.typeLabels,
                datasets: [{
                    data: window.typeData,
                    backgroundColor: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'],
                    borderWidth: 0,
                    hoverOffset: 12
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'bottom', labels: { usePointStyle: true, padding: 20 } }
                },
                cutout: '75%'
            }
        });
    }

    // Bar Chart: Risk Distribution Status
    const riskCanvas = document.getElementById('riskBarChart');
    if (riskCanvas && window.riskLabels && window.riskLabels.length > 0) {
        new Chart(riskCanvas.getContext('2d'), {
            type: 'bar',
            data: {
                labels: window.riskLabels,
                datasets: [{
                    label: 'Count',
                    data: window.riskData,
                    backgroundColor: '#6366f1',
                    borderRadius: 8
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: { beginAtZero: true, grid: { color: '#f1f5f9' } },
                    x: { grid: { display: false } }
                },
                plugins: { legend: { display: false } }
            }
        });
    }
});

/**
 * --- 4. Global Action Handlers ---
 */

/**
 * Handles Large-Scale CSV Ingestion via Multipart API with JWT
 */
async function uploadFile(input) {
    if (!input.files || !input.files[0]) return;
    
    const file = input.files[0];
    if (!file.name.endsWith('.csv')) {
        renderMessage('❌ Error: Only .csv files are supported.', 'error');
        return;
    }

    renderMessage(`⏳ Processing ${file.name}... Please wait.`, 'success');

    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch('/api/transactions/upload', { 
            method: 'POST',
            headers: {
                ...getAuthHeader() // Injects Bearer Token
            },
            body: formData 
        });

        if (response.status === 403) {
            renderMessage('🚫 Access Denied: Admin role required.', 'error');
            return;
        }

        if (response.ok) {
            renderMessage('✅ Ingestion successful!', 'success');
            setTimeout(() => window.location.reload(), 1200);
        } else {
            renderMessage(`❌ Upload failed: ${await response.text()}`, 'error');
        }
    } catch (error) {
        renderMessage(`❌ Network error: ${error.message}`, 'error');
    }
}

/**
 * Single Transaction Manual Audit with JWT
 */
async function handleAction(id, status) {
    if (!confirm(`Are you sure you want to set this transaction to ${status}?`)) return;
    try {
        const response = await fetch(`/api/transactions/${id}/status?newStatus=${status}`, { 
            method: 'PATCH',
            headers: { ...getAuthHeader() }
        });

        if (response.status === 403) {
            alert('🚫 Access Denied: Admin role required for auditing.');
            return;
        }

        if (response.ok) window.location.reload();
        else alert('❌ Action failed.');
    } catch (error) {
        alert('❌ Network error during audit.');
    }
}

/**
 * Batch Compliance Audit Action with JWT
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
                ...getAuthHeader() 
            },
            body: JSON.stringify({ ids: ids, newStatus: status })
        });

        if (response.status === 403) {
            alert('🚫 Access Denied: Admin role required.');
            return;
        }

        if (response.ok) window.location.reload();
        else alert('❌ Batch update failed.');
    } catch (error) {
        alert('❌ Network error.');
    }
}

/**
 * Clears security credentials and redirects to login
 */
function handleLogout() {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('username');
    window.location.href = '/login';
}

/**
 * UI Message Renderer
 */
function renderMessage(text, type) {
    const statusMessage = document.getElementById('statusMessage');
    if (!statusMessage) return;
    statusMessage.innerText = text;
    statusMessage.classList.remove('hidden', 'bg-red-50', 'text-red-700', 'bg-emerald-50', 'text-emerald-700');
    statusMessage.classList.add('block', 'border', 'p-4', 'rounded-xl', 'mb-6');
    
    if (type === 'error') {
        statusMessage.classList.add('bg-red-50', 'text-red-700', 'border-red-100');
    } else {
        statusMessage.classList.add('bg-emerald-50', 'text-emerald-700', 'border-emerald-100');
    }
}

/**
 * Global Exposure
 */
window.uploadFile = uploadFile;
window.handleAction = handleAction;
window.handleBatchAction = handleBatchAction;
window.handleLogout = handleLogout;
window.viewHtmlReport = () => {
    const token = localStorage.getItem('jwt_token');
    // Note: window.open standard GET doesn't easily support headers. 
    // Usually handled via a temporary cookie or a specialized download endpoint.
    window.open(`/api/transactions/report/html?token=${token}`, '_blank');
};