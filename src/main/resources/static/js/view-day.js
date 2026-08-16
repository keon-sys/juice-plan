// 계획 보기 뷰: 읽기 전용 타임테이블. 편집 화면과 같은 그림으로 시간 감각을 잇는다.
window.ViewDay = (function () {
    const TG = window.TimeGrid;
    const NO_PLAN_SCROLL_MINUTES = 540;   // 일정이 없는 날은 09:00 근처를 보여준다

    let selectedDate = null;

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function days() {
        return window.DateStrip.daysOf(window.TRIP);
    }

    function ensureSelectedDate() {
        const list = days();
        if (list.length === 0) {
            selectedDate = null;
        } else if (!list.includes(selectedDate)) {
            selectedDate = list[0];
        }
    }

    function scheduledOn(date) {
        return window.SOURCES
            .filter((s) => s.scheduledDate === date && s.startMinutes != null)
            .sort((a, b) => a.startMinutes - b.startMinutes);
    }

    /**
     * 열릴 때 스크롤할 위치(px). 04:00부터 보여주면 대부분 빈 공간이므로
     * 첫 일정 30분 전으로 맞춘다. 일정이 없으면 09:00.
     */
    function firstScrollTarget(items) {
        const anchor = items.length > 0
            ? Math.max(TG.DAY_START, items[0].startMinutes - TG.SLOT)
            : NO_PLAN_SCROLL_MINUTES;
        return TG.topFor(anchor);
    }

    function renderHourLines() {
        const container = document.getElementById('day-hour-lines');
        if (container.childElementCount > 0) return;
        for (let m = TG.DAY_START; m <= TG.DAY_END; m += 60) {
            const line = document.createElement('div');
            line.className = 'hour-line';
            line.style.top = TG.topFor(m) + 'px';
            line.textContent = TG.formatSlot(m);
            container.appendChild(line);
        }
    }

    function renderTimetable(items) {
        const blocksEl = document.getElementById('day-blocks');
        blocksEl.innerHTML = '';

        // 지도의 번호 마커와 짝을 맞추기 위해 시간순 순번을 미리 매긴다
        const orderOf = {};
        items.forEach((s, i) => { orderOf[s.id] = i + 1; });

        const laid = TG.layoutBlocks(items.map((s) => ({
            id: s.id,
            startMinutes: s.startMinutes,
            durationMinutes: s.durationMinutes,
        })));

        laid.forEach((b) => {
            const s = window.SOURCES.find((x) => x.id === b.id);
            const el = document.createElement('div');
            el.className = 'tt-block ' +
                (s.placeType === 'RESTAURANT' ? 'tt-block--food' : 'tt-block--attraction') +
                (s.reservationRequired ? ' tt-block--reserved' : '');

            const top = TG.topFor(b.startMinutes);
            const gridBottom = TG.topFor(TG.DAY_END);
            el.style.top = top + 'px';
            el.style.height = Math.min(TG.heightFor(b.durationMinutes), gridBottom - top) + 'px';
            el.style.width = `calc((100% - 4px) / ${b.columnCount})`;
            el.style.left = `calc((100% - 4px) / ${b.columnCount} * ${b.column})`;

            const end = b.startMinutes + b.durationMinutes;
            const endLabel = end > TG.DAY_END ? '28:00+' : TG.formatSlot(end);
            el.innerHTML =
                `<div><strong>${orderOf[s.id]}.</strong> ${TG.formatSlot(b.startMinutes)}–${endLabel}</div>` +
                `<div>${s.placeType === 'RESTAURANT' ? '🍴' : '📍'} ${escapeHtml(s.name)}</div>` +
                (s.reservationRequired ? `<div>🔔 ${s.reservationDeadline || ''}</div>` : '');

            el.addEventListener('click', () => window.MapView.focus(s));
            blocksEl.appendChild(el);
        });
    }

    function init() {
        // 읽기 전용이라 바인딩할 이벤트가 없다
    }

    function show() {
        ensureSelectedDate();
        const items = scheduledOn(selectedDate);

        window.DateStrip.render(
            document.querySelector('[data-datestrip="day"]'),
            days(),
            selectedDate,
            (date) => { selectedDate = date; show(); }
        );

        window.DayNote.render(
            document.querySelector('[data-daynote="day"]'),
            window.DAY_NOTES[selectedDate] || '',
            false,
            null
        );

        const scroll = document.getElementById('day-timetable-scroll');
        const empty = document.getElementById('day-empty');
        scroll.hidden = items.length === 0;
        empty.hidden = items.length > 0;

        if (items.length > 0) {
            renderHourLines();
            renderTimetable(items);
            scroll.scrollTop = firstScrollTarget(items);
        }

        window.MapView.clear();
        window.MapView.showPins(items, true);
        window.MapView.showRoute(items);
        window.MapView.fitTo(items);
    }

    return { init, show, firstScrollTarget };
})();
