// 동선 변경 뷰: 날짜 스트립 + 참고사항 + 좌 소스 레일 + 우 타임테이블 드래그 배정.
window.ViewPlan = (function () {
    const TG = window.TimeGrid;

    let selectedDate = null;
    let sheetSourceId = null;

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

    // ---- 소스 레일 ----
    function renderRail() {
        const rail = document.getElementById('source-rail');
        rail.innerHTML = '';
        const unscheduled = window.SOURCES.filter((s) => !s.scheduledDate);
        window.MapView.withinBounds(unscheduled).forEach((s) => {
            const card = document.createElement('div');
            card.className = 'card source-card';
            card.dataset.id = String(s.id);
            card.innerHTML =
                `<div>${s.placeType === 'RESTAURANT' ? '🍴' : '📍'} ${escapeHtml(s.name)}</div>` +
                `<div class="muted">${s.durationMinutes}분${s.reservationRequired ? ' · 🔔' : ''}</div>`;
            attachDrag(card, s.id);
            rail.appendChild(card);
        });
    }

    // ---- 타임테이블 ----
    function renderHourLines() {
        const container = document.getElementById('hour-lines');
        if (container.childElementCount > 0) return;   // 한 번만 그린다
        for (let m = TG.DAY_START; m <= TG.DAY_END; m += 60) {
            const line = document.createElement('div');
            line.className = 'hour-line';
            line.style.top = TG.topFor(m) + 'px';
            line.textContent = TG.formatSlot(m);
            container.appendChild(line);
        }
    }

    function renderTimetable() {
        const blocksEl = document.getElementById('blocks');
        blocksEl.innerHTML = '';

        const laid = TG.layoutBlocks(scheduledOn(selectedDate).map((s) => ({
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
            el.dataset.id = String(s.id);

            const top = TG.topFor(b.startMinutes);
            const gridBottom = TG.topFor(TG.DAY_END);
            el.style.top = top + 'px';
            el.style.height = Math.min(TG.heightFor(b.durationMinutes), gridBottom - top) + 'px';
            el.style.width = `calc((100% - 4px) / ${b.columnCount})`;
            el.style.left = `calc((100% - 4px) / ${b.columnCount} * ${b.column})`;

            const end = b.startMinutes + b.durationMinutes;
            const endLabel = end > TG.DAY_END ? '28:00+' : TG.formatSlot(end);
            el.textContent = `${TG.formatSlot(b.startMinutes)}–${endLabel} ${s.name}`;

            attachDrag(el, s.id);
            blocksEl.appendChild(el);
        });
    }

    // ---- 드래그 ----
    function attachDrag(el, sourceId) {
        window.DragDrop.makeDraggable(el, {
            data: sourceId,
            onMove: (id, x, y) => showPreview(id, x, y),
            onDrop: (id, x, y) => commitDrop(id, x, y),
            onCancel: () => hidePreview(),
            onTap: (id) => openTimeSheet(id),
        });
    }

    function pointToStartMinutes(clientY) {
        const scroll = document.getElementById('timetable-scroll');
        const rect = scroll.getBoundingClientRect();
        return TG.snapToSlot(clientY - rect.top + scroll.scrollTop);
    }

    function isOver(elementId, x, y) {
        const rect = document.getElementById(elementId).getBoundingClientRect();
        return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
    }

    function hidePreview() {
        document.getElementById('drop-preview').hidden = true;
    }

    function showPreview(sourceId, x, y) {
        const preview = document.getElementById('drop-preview');
        if (!isOver('timetable-scroll', x, y)) {
            preview.hidden = true;
            return;
        }
        const s = window.SOURCES.find((v) => v.id === sourceId);
        const start = pointToStartMinutes(y);
        const gridBottom = TG.topFor(TG.DAY_END);
        const top = TG.topFor(start);

        preview.hidden = false;
        preview.style.top = top + 'px';
        preview.style.height = Math.min(TG.heightFor(s.durationMinutes), gridBottom - top) + 'px';

        const end = start + s.durationMinutes;
        preview.textContent =
            `${TG.formatSlot(start)} – ${end > TG.DAY_END ? '28:00+' : TG.formatSlot(end)}`;

        autoScroll(y);
    }

    // 포인터가 타임테이블 위/아래 가장자리 40px 안에 있으면 스크롤한다.
    function autoScroll(clientY) {
        const scroll = document.getElementById('timetable-scroll');
        const rect = scroll.getBoundingClientRect();
        const EDGE = 40;
        if (clientY - rect.top < EDGE) scroll.scrollTop -= 12;
        else if (rect.bottom - clientY < EDGE) scroll.scrollTop += 12;
    }

    async function commitDrop(sourceId, x, y) {
        hidePreview();
        if (isOver('timetable-scroll', x, y)) {
            await assign(sourceId, selectedDate, pointToStartMinutes(y));
        } else if (isOver('source-rail', x, y)) {
            await unassign(sourceId);
        }
    }

    // ---- API ----
    async function assign(sourceId, date, startMinutes) {
        const s = window.SOURCES.find((v) => v.id === sourceId);
        try {
            await window.Api.assignSchedule(sourceId, date, startMinutes);
            s.scheduledDate = date;
            s.startMinutes = startMinutes;
        } catch (e) {
            alert(e.message);
        }
        show();
    }

    async function unassign(sourceId) {
        const s = window.SOURCES.find((v) => v.id === sourceId);
        try {
            await window.Api.removeSchedule(sourceId);
            s.scheduledDate = null;
            s.startMinutes = null;
        } catch (e) {
            alert(e.message);
        }
        show();
    }

    // ---- 시각 수정 시트 (드래그가 어려울 때의 폴백) ----
    function openTimeSheet(sourceId) {
        const s = window.SOURCES.find((v) => v.id === sourceId);
        sheetSourceId = sourceId;
        window.MapView.focus(s);

        document.getElementById('timeSheetTitle').textContent = s.name;

        const select = document.getElementById('timeSheetSelect');
        select.innerHTML = '';
        for (let m = TG.DAY_START; m <= TG.LAST_START; m += TG.SLOT) {
            const opt = document.createElement('option');
            opt.value = String(m);
            opt.textContent = TG.formatSlot(m);
            select.appendChild(opt);
        }
        select.value = String(s.startMinutes != null ? s.startMinutes : 600);

        document.getElementById('timeSheetRemove').hidden = !s.scheduledDate;
        document.getElementById('timeSheet').classList.add('sheet--open');
        document.getElementById('timeSheetBackdrop').classList.add('sheet--open');
    }

    function closeTimeSheet() {
        document.getElementById('timeSheet').classList.remove('sheet--open');
        document.getElementById('timeSheetBackdrop').classList.remove('sheet--open');
        sheetSourceId = null;
    }

    // ---- 공개 ----
    function init() {
        document.getElementById('timeSheetClose').addEventListener('click', closeTimeSheet);
        document.getElementById('timeSheetBackdrop').addEventListener('click', closeTimeSheet);

        document.getElementById('timeSheetSave').addEventListener('click', async () => {
            const start = Number(document.getElementById('timeSheetSelect').value);
            const id = sheetSourceId;
            closeTimeSheet();
            await assign(id, selectedDate, start);
        });

        document.getElementById('timeSheetRemove').addEventListener('click', async () => {
            const id = sheetSourceId;
            closeTimeSheet();
            await unassign(id);
        });
    }

    function show() {
        ensureSelectedDate();

        window.DateStrip.render(
            document.querySelector('[data-datestrip="plan"]'),
            days(),
            selectedDate,
            (date) => window.SOURCES.filter((s) => s.scheduledDate === date).length,
            (date) => {
                selectedDate = date;
                // 날짜가 바뀌면 이전 날짜의 저장 안 된 초안을 들고 가면 안 된다
                window.DayNote.reset(document.querySelector('[data-daynote="plan"]'));
                show();
            }
        );

        window.DayNote.render(
            document.querySelector('[data-daynote="plan"]'),
            window.DAY_NOTES[selectedDate] || '',
            true,
            async (memo) => {
                try {
                    await window.Api.saveDayNote(selectedDate, memo);
                    if (memo.trim() === '') delete window.DAY_NOTES[selectedDate];
                    else window.DAY_NOTES[selectedDate] = memo;
                } catch (e) {
                    alert(e.message);
                }
            }
        );

        renderHourLines();
        renderRail();
        renderTimetable();

        window.MapView.clear();
        window.MapView.showPins(window.SOURCES, false);
        window.MapView.showRoute(scheduledOn(selectedDate));
    }

    /** 지도를 움직이면 왼쪽 레일만 다시 걸러진다. 전체를 다시 그리면 스크롤이 튄다. */
    function onBoundsChanged() {
        renderRail();
    }

    return { init, show, onBoundsChanged };
})();
