/**
 * Dashboard Management for Recon-System
 * Handles Async uploads, Manual/Batch Audits, and UI interactions.
 */
document.addEventListener('DOMContentLoaded', () => {
    // --- 1. UI Elements Initialization ---
    const uploadBtn = document.getElementById('uploadBtn');
    const fileInput = document.getElementById('csvFileInput');
    const uploadSpinner = document.getElementById('uploadSpinner');
    const uploadBtnText = document.getElementById('uploadBtnText');
    const statusMessage = document.getElementById('statusMessage');
    
    // Batch UI Elements
    const selectAllBox = document.getElementById('selectAll');
    const txCheckboxes = document.querySelectorAll('.tx-checkbox');
    const batchActionsBar = document.getElementById('batchActions');
    const selectedCountDisplay = document.getElementById('selectedCount');

    // --- 2. File Upload Logic ---
    if (uploadBtn && fileInput) {
        uploadBtn.addEventListener('click', () => fileInput.click());

        fileInput.addEventListener('change', async (event) => {
            const file = event.target.files[0];
            if (!file || !file.name.endsWith('.csv')) {
                renderMessage('❌ Error: Please select a valid CSV file.', 'error');
                return;
            }

            toggleLoading(true);
            const formData = new FormData();
            formData.append('file', file);

            try {
                const response = await fetch('/api/transactions/upload', { method: 'POST', body: formData });
                if (response.ok) {
                    renderMessage('✅ Ingestion started in background. Refreshing in 5s...', 'success');
                    setTimeout(() => window.location.reload(), 5000);
                } else {
                    const errorText = await response.text();
                    renderMessage(`❌ Upload failed: ${errorText}`, 'error');
                    toggleLoading(false);
                }
            } catch (error) {
                renderMessage(`❌ Network error: ${error.message}`, 'error');
                toggleLoading(false);
            }
        });
    }

    // --- 3. Batch Selection Logic ---
    const updateBatchUI = () => {
        const checkedBoxes = document.querySelectorAll('.tx-checkbox:checked');
        const count = checkedBoxes.length;
        
        if (count > 0) {
            batchActionsBar?.classList.remove('hidden');
            if (selectedCountDisplay) selectedCountDisplay.innerText = count;
        } else {
            batchActionsBar?.classList.add('hidden');
        }
    };

    if (selectAllBox) {
        selectAllBox.addEventListener('change', (e) => {
            txCheckboxes.forEach(cb => cb.checked = e.target.checked);
            updateBatchUI();
        });
    }

    txCheckboxes.forEach(cb => {
        cb.addEventListener('change', updateBatchUI);
    });

    // --- 4. Chart.js Visualization Logic  ---
    
    // 交易類型圓餅圖 (Pie/Doughnut Chart)
    const typeCanvas = document.getElementById('typePieChart');
    if (typeCanvas) {
        new Chart(typeCanvas.getContext('2d'), {
            type: 'doughnut',
            data: {
                // 讀取 index.html 傳進的全域變數
                labels: window.typeLabels || ['No Data'],
                datasets: [{
                    data: window.typeData || [0],
                    backgroundColor: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'],
                    borderWidth: 0,
                    hoverOffset: 10
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'bottom', labels: { usePointStyle: true, padding: 20 } }
                },
                cutout: '70%' // 
            }
        });
    }

    //  (Bar Chart)
    const riskCanvas = document.getElementById('riskBarChart');
    if (riskCanvas) {
        new Chart(riskCanvas.getContext('2d'), {
            type: 'bar',
            data: {
                labels: window.riskLabels || ['No Data'],
                datasets: [{
                    label: 'Transaction Count',
                    data: window.riskData || [0],
                    backgroundColor: '#6366f1',
                    borderRadius: 6 
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: { beginAtZero: true, grid: { display: false } },
                    x: { grid: { display: false } }
                },
                plugins: {
                    legend: { display: false } 
                }
            }
        });
    }

    // --- 5. UI Helper Functions ---
    function toggleLoading(isLoading) {
        if (!uploadBtn) return;
        uploadSpinner?.classList.toggle('hidden', !isLoading);
        if (uploadBtnText) uploadBtnText.innerText = isLoading ? 'Processing...' : 'Upload Financial Log';
        uploadBtn.disabled = isLoading;
        uploadBtn.classList.toggle('opacity-70', isLoading);
    }

    function renderMessage(text, type) {
        if (!statusMessage) return;
        statusMessage.innerText = text;
        statusMessage.classList.remove('hidden', 'bg-red-50', 'text-red-700', 'bg-emerald-50', 'text-emerald-700');
        statusMessage.classList.add('border', type === 'error' ? 'bg-red-50' : 'bg-emerald-50');
        statusMessage.classList.add(type === 'error' ? 'text-red-700' : 'text-emerald-700');
    }
});

/**
 * Single Transaction Action (Manual Audit)
 */
async function handleAction(id, status) {
    if (!confirm(`Confirm manual audit: Set transaction to ${status}?`)) return;
    try {
        const response = await fetch(`/api/transactions/${id}/status?newStatus=${status}`, { method: 'PATCH' });
        if (response.ok) window.location.reload();
        else alert('❌ Failed to update status.');
    } catch (error) {
        alert('❌ Network error occurred.');
    }
}

/**
 * Batch Transactions Action (Compliance Audit)
 */
async function handleBatchAction(status) {
    const checkedBoxes = document.querySelectorAll('.tx-checkbox:checked');
    const ids = Array.from(checkedBoxes).map(cb => parseInt(cb.value));

    if (ids.length === 0) return;
    if (!confirm(`Apply ${status} to ${ids.length} selected transactions?`)) return;

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
        alert('❌ Network error during batch update.');
    }
}

window.handleAction = handleAction;
window.handleBatchAction = handleBatchAction;