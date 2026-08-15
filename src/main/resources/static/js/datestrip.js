// 여행 기간의 날짜 목록과 가로 스크롤 날짜 선택 스트립. 동선 변경과 계획 보기가 공유한다.
window.DateStrip = (function () {
    const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

    /**
     * 여행 기간을 'YYYY-MM-DD' 문자열 배열로 편다.
     * 'YYYY-MM-DDT00:00:00' 으로 파싱해 로컬 자정으로 고정한다.
     * 'YYYY-MM-DD' 만 넘기면 UTC로 해석돼 타임존에 따라 하루씩 밀린다.
     */
    function daysOf(trip) {
        if (!trip) return [];
        const days = [];
        const cur = new Date(trip.startDate + 'T00:00:00');
        const end = new Date(trip.endDate + 'T00:00:00');
        while (cur <= end) {
            const y = cur.getFullYear();
            const m = String(cur.getMonth() + 1).padStart(2, '0');
            const d = String(cur.getDate()).padStart(2, '0');
            days.push(`${y}-${m}-${d}`);
            cur.setDate(cur.getDate() + 1);
        }
        return days;
    }

    /**
     * @param container 스트립을 그릴 요소
     * @param days      daysOf() 결과
     * @param selected  선택된 날짜 문자열
     * @param countOf   (date) => number, 칸에 표시할 개수
     * @param onSelect  (date) => void
     */
    function render(container, days, selected, countOf, onSelect) {
        container.innerHTML = '';
        days.forEach((date) => {
            const d = new Date(date + 'T00:00:00');
            const count = countOf(date);

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'date-strip__item' + (date === selected ? ' date-strip__item--on' : '');
            btn.innerHTML =
                `<span class="muted">${WEEKDAYS[d.getDay()]}</span>` +
                `<strong>${d.getDate()}</strong>` +
                `<span class="muted">${count > 0 ? count + '곳' : ''}</span>`;
            btn.addEventListener('click', () => onSelect(date));
            container.appendChild(btn);
        });
    }

    return { daysOf, render };
})();
