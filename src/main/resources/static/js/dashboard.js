/**
 * Dashboard Management for Recon-System
 * * CORE FUNCTIONALITIES:
 * 1. Charting: Visualizes transaction types and risk distributions using Chart.js.
 * 2. Ingestion: Handles asynchronous streaming uploads for large CSV datasets.
 * 3. Auditing: Supports both single and batch manual audit actions (Approve/Reject).
 * 4. UI States: Manages dynamic visibility of action bars and status messages.
 */

document.addEventListener('DOMContentLoaded', () => {
    
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
            batchActionsBar?.classList.add('flex'); // Ensure flex layout for alignment
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
     * Monitors changes to any element with the 'tx-checkbox' class
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
                labels: window.typeLabels, // Injected via Thymeleaf inline JS
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
                cutout: '75%' // Creates the modern "Ring" look
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
 * Handles Large-Scale CSV Ingestion via Multipart API
 * @param {HTMLInputElement} input - The file input element
 */
async function uploadFile(input) {
    if (!input.files || !input.files[0]) return;
    
    const file = input.files[0];
    if (!file.name.endsWith('.csv')) {
        renderMessage('❌ Error: Only .csv files are supported.', 'error');
        return;
    }

    // Provide visual feedback during long-running ingestion
    renderMessage(`⏳ Processing ${file.name}... Please wait, this may take a moment for large datasets.`, 'success');

    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch('/api/transactions/upload', { 
            method: 'POST', 
            body: formData 
        });

        if (response.ok) {
            renderMessage('✅ Ingestion successful! Refreshing dashboard...', 'success');
            setTimeout(() => window.location.reload(), 1200);
        } else {
            const errorText = await response.text();
            renderMessage(`❌ Upload failed: ${errorText}`, 'error');
        }
    } catch (error) {
        renderMessage(`❌ Network error: ${error.message}`, 'error');
    }
}

/**
 * Single Transaction Manual Audit Action
 * @param {number} id - Transaction Database ID
 * @param {string} status - Target status (e.g., PROCESSED, REJECTED)
 */
async function handleAction(id, status) {
    if (!confirm(`Are you sure you want to set this transaction to ${status}?`)) return;
    try {
        const response = await fetch(`/api/transactions/${id}/status?newStatus=${status}`, { method: 'PATCH' });
        if (response.ok) window.location.reload();
        else alert('❌ Action failed. Please check system logs.');
    } catch (error) {
        alert('❌ Network error occurred during the audit action.');
    }
}

/**
 * Batch Compliance Audit Action for multiple records
 * @param {string} status - Target status for all selected IDs
 */
async function handleBatchAction(status) {
    const checkedBoxes = document.querySelectorAll('.tx-checkbox:checked');
    const ids = Array.from(checkedBoxes).map(cb => parseInt(cb.value));

    if (ids.length === 0) return;
    if (!confirm(`Apply [${status}] to ${ids.length} selected records?`)) return;

    try {
        const response = await fetch('/api/transactions/batch-status', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ids: ids, newStatus: status })
        });

        if (response.ok) {
            window.location.reload();
        } else {
            alert('❌ Batch update failed.');
        }
    } catch (error) {
        alert('❌ Critical network error during batch processing.');
    }
}

/**
 * Renders dynamic status/error messages on the UI
 * @param {string} text - Message content
 * @param {string} type - 'success' or 'error'
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
 * Expose functions to the Global Window Object
 * This allows HTML inline attributes (onclick/onchange) to access them.
 */
window.uploadFile = uploadFile;
window.handleAction = handleAction;
window.handleBatchAction = handleBatchAction;
window.viewHtmlReport = () => window.open('/api/transactions/report/html', '_blank');