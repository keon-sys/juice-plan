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

    function init() {
        // 편집은 다음 단계에서 붙인다
    }

    function show() {
        const rows = window.BUDGET_SUMMARY.rows;
        document.getElementById('view-list').innerHTML = rows.length === 0
            ? '<p class="card muted">아직 지출 항목이 없습니다.</p>'
            : rows.map(groupHtml).join('');
    }

    return { init, show };
})();
