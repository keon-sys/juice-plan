// 장소 종류 한 곳. 이모지·이름·색을 뷰마다 따로 쓰면 종류를 늘릴 때 한 군데씩
// 빠뜨리고, 빠뜨린 자리는 조용히 다른 종류처럼 보인다. 서버의 PlaceType 과 값을 맞춘다.
window.PlaceTypes = (function () {
    const TYPES = {
        RESTAURANT: { label: '음식점', emoji: '🍴', token: '--food', suffix: 'food' },
        ATTRACTION: { label: '관광지', emoji: '📍', token: '--attraction', suffix: 'attraction' },
        WAYPOINT: { label: '경유지', emoji: '🚏', token: '--waypoint', suffix: 'waypoint' },
    };

    // 서버에 새 종류가 생겼는데 화면이 아직 모를 때 빈칸을 내지 않기 위한 대비책
    const FALLBACK = { label: '장소', emoji: '📍', token: '--attraction', suffix: 'attraction' };

    function of(placeType) {
        return TYPES[placeType] || FALLBACK;
    }

    return {
        of,
        /** 목록 배지나 칩에 쓰는 '🍴 음식점' 꼴 */
        tag: (t) => of(t).emoji + ' ' + of(t).label,
        emoji: (t) => of(t).emoji,
        label: (t) => of(t).label,
        colorToken: (t) => of(t).token,
        badgeClass: (t) => 'badge--' + of(t).suffix,
        blockClass: (t) => 'tt-block--' + of(t).suffix,
        keys: () => Object.keys(TYPES),
    };
})();
