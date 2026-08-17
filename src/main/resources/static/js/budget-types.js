// 예산 분류 이름과 색 한 곳. 서버의 enum 값과 키를 맞춘다. 뷰마다 이름을 따로 쓰면
// 종류를 늘릴 때 한 군데씩 빠뜨리고, 빠뜨린 자리는 조용히 빈칸이 된다.
// (place-types.js 와 같은 역할이다)
window.BudgetTypes = (function () {
    const CATEGORIES = {
        FLIGHT:   { label: '항공 (Flight)', token: '--cat-flight' },
        HOTEL:    { label: '숙박 (Hotel)', token: '--cat-hotel' },
        FOOD:     { label: '식비 (Food & Dining)', token: '--cat-food' },
        TRANSIT:  { label: '교통 (Transit)', token: '--cat-transit' },
        ACTIVITY: { label: '관광/입장료 (Activities)', token: '--cat-activity' },
        SHOPPING: { label: '쇼핑/기념품 (Shopping)', token: '--cat-shopping' },
        ETC:      { label: '기타 (eSIM/보험 등)', token: '--cat-etc' },
    };
    const METHODS = { CREDIT_CARD: '신용카드', TRAVEL_LOG: '트래블로그', CASH: '현금' };
    const SETTLEMENTS = {
        PENDING: { label: '미정산', suffix: 'pending' },
        DONE: { label: '완료', suffix: 'done' },
        NOT_APPLICABLE: { label: '해당없음', suffix: 'na' },
    };
    const SYMBOLS = { JPY: '¥', KRW: '₩' };

    // 서버에 새 값이 생겼는데 화면이 아직 모를 때 빈칸을 내지 않기 위한 대비책
    const CATEGORY_FALLBACK = { label: '기타', token: '--cat-etc' };
    const SETTLEMENT_FALLBACK = { label: '미정산', suffix: 'pending' };

    function category(key) { return CATEGORIES[key] || CATEGORY_FALLBACK; }
    function settlement(key) { return SETTLEMENTS[key] || SETTLEMENT_FALLBACK; }

    function money(currency, amount) {
        return (SYMBOLS[currency] || '') + Number(amount || 0).toLocaleString('ko-KR');
    }

    /**
     * 통화별 금액을 줄바꿈으로 쌓는다. 0 인 통화는 접는다 — 엔·원이 섞인 카테고리에서
     * 쓰지도 않은 쪽의 0 이 한 줄을 차지하면 읽는 눈이 헛돈다.
     * 전부 0 인 카테고리는 접을 게 없으므로 실제로 쓰인 통화를 그대로 0 으로 보여준다.
     * (합계만으로는 ¥0 인지 ₩0 인지 알 수 없어 서버가 currencies 를 함께 준다.)
     */
    function moneyLines(currencies, amounts) {
        if (!currencies || currencies.length === 0) return money('KRW', 0);
        const nonZero = currencies.filter((c) => (c === 'JPY' ? amounts.jpy : amounts.krw) !== 0);
        return (nonZero.length ? nonZero : currencies)
            .map((c) => money(c, c === 'JPY' ? amounts.jpy : amounts.krw))
            .join('<br>');
    }

    return {
        categoryLabel: (k) => category(k).label,
        categoryToken: (k) => category(k).token,
        categoryOrder: Object.keys(CATEGORIES),
        method: (k) => METHODS[k] || k,
        settlementLabel: (k) => settlement(k).label,
        settlementClass: (k) => 'badge--settle-' + settlement(k).suffix,
        money,
        moneyLines,
    };
})();
