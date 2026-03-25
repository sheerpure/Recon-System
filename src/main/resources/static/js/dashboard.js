/**
 * Dashboard Management for Recon-System
 * * CORE FUNCTIONALITIES:
 * 1. Charting: Visualizes transaction types and risk distributions using Chart.js.
 * 2. Ingestion: Handles asynchronous streaming uploads for large CSV datasets.
 * 3. Auditing: Supports both single and batch manual audit actions.
 * 4. Security: Injects JWT tokens into all outgoing fetch requests and URL redirects.
 */

/**
 * Retrieves the JWT token from localStorage and formats it for HTTP headers
 * @returns {Object} Headers object containing Authorization or empty
 */
function getAuthHeader() {
    const token = localStorage.getItem('jwt_token');
    return token ? { 'Authorization': `Bearer ${token}` } : {};
}

/**
 * --- 1. DOM INITIALIZATION ---
 * Runs when the page content is fully loaded.
 */
document.addEventListener('DOMContentLoaded', () => {
    
    // Security Guard: Check if token exists
    const token = localStorage.getItem('jwt_token');
    const username = localStorage.getItem('username');

    if (!token) {
        console.warn("[Security] No token found. Redirecting to login...");
        window.location.href = '/login';
        return;
    }

    // Update the UI with the real username from localStorage
    const userDisplay = document.getElementById('displayUsername');
    if (userDisplay && username) {
        userDisplay.innerText = username;
    }

    // --- 2. Batch Selection UI Logic ---
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

    if (selectAllBox) {
        selectAllBox.addEventListener('change', (e) => {
            document.querySelectorAll('.tx-checkbox').forEach(cb => {
                cb.checked = e.target.checked;
            });
            updateBatchUI();
        });
    }

    document.addEventListener('change', (e) => {
        if (e.target.classList.contains('tx-checkbox')) {
            updateBatchUI();
        }
    });

    // --- 3. Chart.js Visualization ---
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
                cutout: '75%',
                plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, padding: 20 } } }
            }
        });
    }

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
 * --- 4. GLOBAL ACTION HANDLERS ---
 * Defined outside DOMContentLoaded to ensure global availability.
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
            headers: { ...getAuthHeader() },
            body: formData 
        });

        if (response.status === 401) { handleLogout(); return; }
        if (response.ok) {
            renderMessage('✅ Upload successful!', 'success');
            setTimeout(() => window.location.reload(), 1000);
        } else {
            renderMessage(`❌ Error: ${await response.text()}`, 'error');
        }
    } catch (error) {
        renderMessage('❌ Network error during upload.', 'error');
    }
}

async function handleAction(id, status) {
    if (!confirm(`Confirm ${status} for this transaction?`)) return;
    try {
        const response = await fetch(`/api/transactions/${id}/status?newStatus=${status}`, { 
            method: 'PATCH',
            headers: { ...getAuthHeader() }
        });
        if (response.status === 401) { handleLogout(); return; }
        if (response.ok) window.location.reload();
    } catch (error) {
        alert('❌ Error performing action.');
    }
}

async function handleBatchAction(status) {
    const checkedBoxes = document.querySelectorAll('.tx-checkbox:checked');
    const ids = Array.from(checkedBoxes).map(cb => parseInt(cb.value));

    if (ids.length === 0) return;
    if (!confirm(`Apply [${status}] to ${ids.length} records?`)) return;

    try {
        const response = await fetch('/api/transactions/batch-status', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json', ...getAuthHeader() },
            body: JSON.stringify({ ids: ids, newStatus: status })
        });
        if (response.status === 401) { handleLogout(); return; }
        if (response.ok) window.location.reload();
    } catch (error) {
        alert('❌ Batch update failed.');
    }
}

function exportFilteredReport() {
    const token = localStorage.getItem('jwt_token');
    if (!token) {
        window.location.href = '/login';
        return;
    }
    const urlParams = new URLSearchParams(window.location.search);
    const params = urlParams.toString(); 
    const exportUrl = `/api/transactions/export?${params}${params ? '&' : ''}token=${token}`;
    
    console.log("[Export] Initiating download...");
    window.location.href = exportUrl;
}

function viewHtmlReport() {
    const token = localStorage.getItem('jwt_token');
    if (!token) {
        window.location.href = '/login';
        return;
    }
    window.open(`/api/transactions/report/html?token=${token}`, '_blank');
}

function handleLogout() {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('username');
    window.location.href = '/login';
}

function renderMessage(text, type) {
    const statusMessage = document.getElementById('statusMessage');
    if (!statusMessage) return;
    statusMessage.innerText = text;
    statusMessage.className = `block border p-4 rounded-xl mb-6 ${type === 'error' ? 'bg-red-50 text-red-700 border-red-100' : 'bg-emerald-50 text-emerald-700 border-emerald-100'}`;
}

/**
 * --- 5. GLOBAL EXPOSURE ---
 * Attaching functions to the window object for HTML onclick access.
 */
window.uploadFile = uploadFile;
window.handleAction = handleAction;
window.handleBatchAction = handleBatchAction;
window.handleLogout = handleLogout;
window.exportFilteredReport = exportFilteredReport;
window.viewHtmlReport = viewHtmlReport;