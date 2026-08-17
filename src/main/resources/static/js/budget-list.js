// 지출 내역 탭: 카테고리로 묶은 카드 목록. 8열 표를 모바일에 밀어 넣지 않는다.
window.ViewList = (function () {
    const T = () => window.BudgetTypes;

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /** 1인당은 나눗셈 한 번이라 여기서 낸다. 카테고리 합계는 서버가 준 값을 쓴다. */
    function perPerson(item) {
        return T().money(item.currency, Math.floor(item.amount / 2));
    }

    function cardHtml(item) {
        const name = item.name
            ? `<strong>${escapeHtml(item.name)}</strong>`
            : '<strong class="muted">(이름 없음)</strong>';
        return `<div class="card budget-card" data-id="${item.id}">` +
            `<div class="budget-card__head">${name}</div>` +
            '<div class="budget-card__meta">' +
                `<span class="muted">${T().method(item.paymentMethod)}</span>` +
                `<span class="badge ${T().settlementClass(item.settlement)}">${T().settlementLabel(item.settlement)}</span>` +
            '</div>' +
            '<div class="budget-card__amount">' +
                `<strong>${T().money(item.currency, item.amount)}</strong>` +
                `<span class="muted">1인 ${perPerson(item)}</span>` +
            '</div>' +
            (item.memo ? `<p class="muted budget-card__memo">${escapeHtml(item.memo)}</p>` : '') +
            '</div>';
    }

    function groupHtml(row) {
        const items = window.BUDGET_ITEMS.filter((i) => i.category === row.category);
        return '<div class="budget-group">' +
            '<h2 class="budget-group__head">' +
                `${T().categoryLabel(row.category)}` +
                `<span class="muted">${row.count}건 · ${T().moneyLines(row.currencies, row.total)}</span>` +
            '</h2>' +
            items.map(cardHtml).join('') +
            '</div>';
    }

    let editingId = null;

    const sheet = () => document.getElementById('budgetSheet');
    const backdrop = () => document.getElementById('budgetBackdrop');
    const form = () => document.getElementById('budgetForm');

    function currencyValue() {
        return form().querySelector('input[name="currency"]:checked').value;
    }

    /** 저장하지 않는 미리보기. 통화나 금액을 고치면 따라 바뀐다. */
    function renderPerPerson() {
        const amount = Number(document.getElementById('budgetAmount').value || 0);
        document.getElementById('budgetPerPerson').textContent =
            '1인 ' + T().money(currencyValue(), Math.floor(Math.max(0, amount) / 2));
    }

    function openSheet(item) {
        editingId = item ? item.id : null;
        const f = form();
        f.reset();
        document.getElementById('budgetError').hidden = true;

        document.getElementById('budgetName').value = item ? item.name : '';
        document.getElementById('budgetCategory').value = item ? item.category : 'FOOD';
        document.getElementById('budgetMethod').value = item ? item.paymentMethod : 'TRAVEL_LOG';
        document.getElementById('budgetAmount').value = item ? item.amount : 0;
        document.getElementById('budgetMemo').value = (item && item.memo) || '';
        f.querySelectorAll('input[name="currency"]').forEach((r) => {
            r.checked = r.value === (item ? item.currency : 'JPY');
        });
        f.querySelectorAll('input[name="settlement"]').forEach((r) => {
            r.checked = r.value === (item ? item.settlement : 'PENDING');
        });

        document.getElementById('budgetSheetTitle').textContent = item ? '지출 수정' : '새 지출 추가';
        document.getElementById('budgetSubmit').textContent = item ? '수정 저장' : '저장';
        document.getElementById('budgetDelete').hidden = !item;
        renderPerPerson();

        sheet().classList.add('sheet--open');
        backdrop().classList.add('sheet--open');
    }

    function closeSheet() {
        sheet().classList.remove('sheet--open');
        backdrop().classList.remove('sheet--open');
        editingId = null;
    }

    function readForm() {
        const f = form();
        return {
            name: document.getElementById('budgetName').value,
            category: document.getElementById('budgetCategory').value,
            paymentMethod: document.getElementById('budgetMethod').value,
            currency: currencyValue(),
            amount: Number(document.getElementById('budgetAmount').value || 0),
            settlement: f.querySelector('input[name="settlement"]:checked').value,
            memo: document.getElementById('budgetMemo').value || null,
        };
    }

    /** 합계는 서버만 계산한다. 항목을 고쳤으면 요약을 다시 받아온다. */
    async function reload() {
        window.BUDGET_SUMMARY = await window.Api.budgetSummary();
        window.BudgetSection.refresh();
    }

    /** 서버는 카테고리 선언 순 → id 순으로 목록을 낸다(BudgetService.list 와 동일한 규칙).
        수정으로 카테고리가 바뀐 항목은 배열 안 옛 자리에 그대로 남으므로, 화면 순서가
        서버와 어긋나지 않도록 매 변경 뒤 같은 기준으로 다시 정렬해 둔다. */
    function sortItems() {
        const order = T().categoryOrder;
        window.BUDGET_ITEMS.sort((a, b) =>
            order.indexOf(a.category) - order.indexOf(b.category) || a.id - b.id);
    }

    async function submit(e) {
        e.preventDefault();
        const errorEl = document.getElementById('budgetError');
        errorEl.hidden = true;

        try {
            const payload = readForm();
            if (editingId === null) {
                window.BUDGET_ITEMS.push(await window.Api.createBudgetItem(payload));
            } else {
                const updated = await window.Api.updateBudgetItem(editingId, payload);
                const i = window.BUDGET_ITEMS.findIndex((it) => it.id === editingId);
                window.BUDGET_ITEMS[i] = updated;
            }
            sortItems();
            closeSheet();
            await reload();
        } catch (err) {
            errorEl.textContent = err.message;
            errorEl.hidden = false;
        }
    }

    async function remove() {
        const item = window.BUDGET_ITEMS.find((it) => it.id === editingId);
        const label = item && item.name ? `'${item.name}'을(를)` : '이 항목을';
        if (!confirm(`${label} 삭제하시겠습니까?`)) return;

        try {
            await window.Api.deleteBudgetItem(editingId);
            window.BUDGET_ITEMS = window.BUDGET_ITEMS.filter((it) => it.id !== editingId);
            closeSheet();
            await reload();
        } catch (err) {
            alert(err.message);
        }
    }

    function init() {
        // 카드는 매번 새로 그려지므로 클릭은 바깥 상자에서 한 번만 받는다
        document.getElementById('view-list').addEventListener('click', (e) => {
            const card = e.target.closest('.budget-card');
            if (!card) return;
            openSheet(window.BUDGET_ITEMS.find((it) => it.id === Number(card.dataset.id)));
        });

        document.getElementById('addBudgetBtn').addEventListener('click', () => openSheet(null));
        document.getElementById('budgetSheetClose').addEventListener('click', closeSheet);
        document.getElementById('budgetBackdrop').addEventListener('click', closeSheet);
        document.getElementById('budgetDelete').addEventListener('click', remove);
        document.getElementById('budgetForm').addEventListener('submit', submit);
        document.getElementById('budgetForm').addEventListener('input', renderPerPerson);
        document.getElementById('budgetForm').addEventListener('change', renderPerPerson);
    }

    function show() {
        const rows = window.BUDGET_SUMMARY.rows;
        document.getElementById('view-list').innerHTML = rows.length === 0
            ? '<p class="card muted">아직 지출 항목이 없습니다.</p>'
            : rows.map(groupHtml).join('');
    }

    return { init, show };
})();
