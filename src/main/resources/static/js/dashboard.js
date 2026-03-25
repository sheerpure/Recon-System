/**
 * Dashboard Management for Recon-System
 * Handles Async uploads, Manual/Batch Audits, and UI interactions.
 */
document.addEventListener('DOMContentLoaded', () => {
    // UI Elements
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

    // --- 1. File Upload Logic ---
    uploadBtn?.addEventListener('click', () => fileInput.click());

    fileInput?.addEventListener('change', async (event) => {
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
                renderMessage(`❌ Upload failed: ${await response.text()}`, 'error');
                toggleLoading(false);
            }
        } catch (error) {
            renderMessage(`❌ Network error: ${error.message}`, 'error');
            toggleLoading(false);
        }
    });

    // --- 2. Batch Selection Logic ---
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

    selectAllBox?.addEventListener('change', (e) => {
        txCheckboxes.forEach(cb => cb.checked = e.target.checked);
        updateBatchUI();
    });

    txCheckboxes.forEach(cb => {
        cb.addEventListener('change', updateBatchUI);
    });

    // --- 3. UI Helper Functions ---
    function toggleLoading(isLoading) {
        if (!uploadBtn) return;
        uploadSpinner.classList.toggle('hidden', !isLoading);
        uploadBtnText.innerText = isLoading ? 'Starting...' : 'Upload Financial Log';
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
 * Single Transaction Action
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
 * Batch Transactions Action (🚀 New!)
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

// Global Exports
window.handleAction = handleAction;
window.handleBatchAction = handleBatchAction;