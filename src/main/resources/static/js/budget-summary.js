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

    // 빈 상태 메시지는 chartHtml() 이 이미 보여준다("아직 금액이 없습니다"). 여기서 또
    // 카드를 내면 빈 예산 화면에 같은 뜻의 메시지가 두 번 뜬다.
    function tableHtml(summary) {
        if (summary.rows.length === 0) return '';
        return '<div class="card">' +
            '<table class="budget-table">' +
            '<thead><tr><th scope="col">카테고리</th><th scope="col" class="num">건수</th>' +
            '<th scope="col" class="num">2인 합계</th><th scope="col" class="num">1인당</th></tr></thead>' +
            `<tbody>${summary.rows.map(rowHtml).join('')}</tbody>` +
            '<tfoot><tr>' +
                '<th scope="row">합계</th>' +
                `<td class="num">${summary.count}</td>` +
                `<td class="num">${T().moneyLines(summary.currencies, summary.total)}</td>` +
                `<td class="num">${T().moneyLines(summary.currencies, summary.perPerson)}</td>` +
            '</tr></tfoot></table>' +
            `<p class="muted budget-converted">100엔 = ${T().money('KRW', summary.ratePer100Jpy)} 기준 ` +
            `<strong>대략 ${T().money('KRW', summary.convertedTotalKrw)}</strong>` +
            ` · 1인 ${T().money('KRW', summary.convertedPerPersonKrw)}</p>` +
            '</div>';
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

    function chartHtml(summary) {
        const slices = summary.rows
            .filter((r) => r.convertedKrw > 0)
            .map((r) => ({
                label: T().categoryLabel(r.category),
                value: r.convertedKrw,
                token: T().categoryToken(r.category),
            }));

        if (slices.length === 0) {
            return '<p class="card muted">아직 금액이 없습니다. 지출 내역에서 금액을 넣으면 비중이 보입니다.</p>';
        }

        const total = summary.convertedTotalKrw;
        const legend = slices.map((s) =>
            '<span class="donut-legend__item">' +
            `<i class="donut-legend__dot" style="background: var(${s.token})"></i>` +
            `${s.label} ${Math.round((s.value / total) * 100)}%` +
            '</span>').join('');

        return '<div class="card budget-chart">' +
            window.Donut.svg(slices, T().money('KRW', total)) +
            `<div class="donut-legend">${legend}</div>` +
            '</div>';
    }

    async function saveRate(value) {
        const errorEl = document.getElementById('rateError');
        errorEl.hidden = true;
        try {
            window.BUDGET_SUMMARY = await window.Api.saveBudgetRate(Number(value));
            renderBody(window.BUDGET_SUMMARY);
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
            rateHtml(summary) + '<div id="summary-body"></div>';
        renderBody(summary);
    }

    /** 환율을 고쳐도 입력칸은 그대로 두고 아래만 다시 그린다. 통째로 다시 그리면
        방금 손대던 입력칸이 사라져 모바일에서 키보드가 닫힌다. */
    function renderBody(summary) {
        document.getElementById('summary-body').innerHTML =
            chartHtml(summary) + tableHtml(summary);
    }

    return { init, show };
})();
