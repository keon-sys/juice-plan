// 비중 도넛. 차트 라이브러리 없이 SVG arc 를 직접 그린다. 이 앱은 구글맵 말고 CDN 을
// 쓰지 않고, 색을 CSS 토큰으로 두면 다크모드가 저절로 따라온다.
window.Donut = (function () {
    const SIZE = 160;
    const CX = 80, CY = 80;
    const R = 58;          // 고리의 중심선 반지름
    const WIDTH = 22;

    function point(fraction) {
        // 0 = 12시, 시계방향
        const angle = fraction * 2 * Math.PI - Math.PI / 2;
        return [CX + R * Math.cos(angle), CY + R * Math.sin(angle)];
    }

    function arcPath(from, to) {
        const [x0, y0] = point(from);
        const [x1, y1] = point(to);
        const large = to - from > 0.5 ? 1 : 0;
        return `M ${x0.toFixed(2)} ${y0.toFixed(2)} A ${R} ${R} 0 ${large} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}`;
    }

    function strokeFor(token) {
        return `stroke="var(${token})" stroke-width="${WIDTH}" fill="none"`;
    }

    /**
     * slices: [{ label, value, token }] — value 는 같은 단위의 양수.
     * 값이 0인 조각은 부르는 쪽에서 걸러 보낸다.
     */
    function svg(slices, centerLabel) {
        const total = slices.reduce((sum, s) => sum + s.value, 0);
        if (total <= 0) return '';

        // 조각이 하나뿐이면 시작점과 끝점이 같아 호가 아무것도 그리지 않는다. 원을 그린다.
        const shapes = slices.length === 1
            ? `<circle cx="${CX}" cy="${CY}" r="${R}" ${strokeFor(slices[0].token)}></circle>`
            : slices.map((slice, i) => {
                const from = slices.slice(0, i).reduce((sum, s) => sum + s.value, 0) / total;
                const to = from + slice.value / total;
                return `<path d="${arcPath(from, to)}" ${strokeFor(slice.token)} stroke-linecap="butt"></path>`;
            }).join('');

        return `<svg class="donut" viewBox="0 0 ${SIZE} ${SIZE}" role="img" aria-label="카테고리 비중">` +
            shapes +
            `<text x="${CX}" y="${CY}" class="donut__label" text-anchor="middle" dominant-baseline="middle">` +
            `${centerLabel}</text>` +
            '</svg>';
    }

    return { svg };
})();
