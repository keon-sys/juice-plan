// 예산 요약 탭: 환율 입력 + 카테고리별 합계 표.
// 합계는 서버(BudgetTotals)만 계산한다. 여기서는 그리기만 한다.
window.ViewSummary = (function () {
    const T = () => window.BudgetTypes;

    function rowHtml(row) {
        return '<tr>' +
            `<th scope="row">${T().categoryLabel(row.category)}</th>` +
            `<td class="num">${row.count}</td>` +
            `<td class="num">${T().moneyLines(row.currencies, row.total)}</td>` +
            `<td class="num">${T().moneyLines(row.currencies, row.perPerson)}</td>` +
            '</tr>';
    }

    function tableHtml(summary) {
        if (summary.rows.length === 0) {
            return '<p class="card muted">아직 지출 항목이 없습니다.</p>';
        }
        return '<table class="budget-table">' +
            '<thead><tr><th>카테고리</th><th class="num">건수</th>' +
            '<th class="num">2인 합계</th><th class="num">1인당</th></tr></thead>' +
            `<tbody>${summary.rows.map(rowHtml).join('')}</tbody>` +
            '<tfoot><tr>' +
                '<th scope="row">합계</th>' +
                `<td class="num">${summary.count}</td>` +
                `<td class="num">${T().moneyLines(summary.currencies, summary.total)}</td>` +
                `<td class="num">${T().moneyLines(summary.currencies, summary.perPerson)}</td>` +
            '</tr></tfoot></table>' +
            `<p class="muted budget-converted">100엔 = ₩${summary.ratePer100Jpy} 기준 ` +
            `<strong>대략 ${T().money('KRW', summary.convertedTotalKrw)}</strong>` +
            ` · 1인 ${T().money('KRW', summary.convertedPerPersonKrw)}</p>`;
    }

    function rateHtml(summary) {
        return '<div class="card budget-rate">' +
            '<label for="budgetRate">환율</label>' +
            '<div class="budget-rate__row">' +
                '<span>100엔 = ₩</span>' +
                `<input type="number" id="budgetRate" min="1" step="1" value="${summary.ratePer100Jpy}">` +
            '</div>' +
            '<p class="error" id="rateError" hidden></p>' +
            '</div>';
    }

    async function saveRate(value) {
        const errorEl = document.getElementById('rateError');
        errorEl.hidden = true;
        try {
            window.BUDGET_SUMMARY = await window.Api.saveBudgetRate(Number(value));
            show();
        } catch (e) {
            errorEl.textContent = e.message;
            errorEl.hidden = false;
        }
    }

    function init() {
        // 이 탭의 내용은 매번 새로 그려지므로 이벤트는 바깥 상자에 한 번만 건다
        document.getElementById('view-summary').addEventListener('change', (e) => {
            if (e.target.id === 'budgetRate') saveRate(e.target.value);
        });
    }

    function show() {
        const summary = window.BUDGET_SUMMARY;
        document.getElementById('view-summary').innerHTML =
            rateHtml(summary) + `<div class="card">${tableHtml(summary)}</div>`;
    }

    return { init, show };
})();
