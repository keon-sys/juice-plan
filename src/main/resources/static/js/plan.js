(function () {
    const appEl = document.getElementById('plan-app');
    const TG = window.TimeGrid;
    const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

    // ---- 날짜 목록 ----
    // 'YYYY-MM-DDT00:00:00' 으로 파싱해 로컬 자정으로 고정한다.
    // 'YYYY-MM-DD' 만 넘기면 UTC로 해석돼 타임존에 따라 하루씩 밀린다.
    const days = [];
    {
        const cur = new Date(appEl.dataset.tripStart + 'T00:00:00');
        const end = new Date(appEl.dataset.tripEnd + 'T00:00:00');
        while (cur <= end) {
            const y = cur.getFullYear();
            const m = String(cur.getMonth() + 1).padStart(2, '0');
            const d = String(cur.getDate()).padStart(2, '0');
            days.push(`${y}-${m}-${d}`);
            cur.setDate(cur.getDate() + 1);
        }
    }

    let selectedDate = days[0];
    let map;
    const markers = {};
    let dayPath = null;
    let sheetSourceId = null;

    function isUnauthorized(res) {
        if (res.status === 401) {
            alert('세션이 만료되었습니다. 다시 로그인해주세요.');
            window.location.href = '/';
            return true;
        }
        return false;
    }

    function scheduledOn(date) {
        return SOURCES
            .filter((s) => s.scheduledDate === date && s.startMinutes != null)
            .sort((a, b) => a.startMinutes - b.startMinutes);
    }

    // ---- 날짜 스트립 ----
    function renderDateStrip() {
        const strip = document.getElementById('date-strip');
        strip.innerHTML = '';
        days.forEach((date) => {
            const d = new Date(date + 'T00:00:00');
            const count = SOURCES.filter((s) => s.scheduledDate === date).length;

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'date-strip__item' + (date === selectedDate ? ' date-strip__item--on' : '');
            btn.innerHTML =
                `<span class="muted">${WEEKDAYS[d.getDay()]}</span>` +
                `<strong>${d.getDate()}</strong>` +
                `<span class="muted">${count > 0 ? count + '곳' : ''}</span>`;
            btn.addEventListener('click', () => {
                selectedDate = date;
                renderAll();
            });
            strip.appendChild(btn);
        });
    }

    // ---- 소스 레일 ----
    // 미배정 소스만 보여준다. 지도가 준비되면 지도 영역 안의 것만 남긴다.
    function railSources() {
        const unscheduled = SOURCES.filter((s) => !s.scheduledDate);
        if (!map) return unscheduled;
        const bounds = map.getBounds();
        if (!bounds) return unscheduled;
        return unscheduled.filter((s) => bounds.contains({ lat: s.latitude, lng: s.longitude }));
    }

    function renderRail() {
        const rail = document.getElementById('source-rail');
        rail.innerHTML = '';
        railSources().forEach((s) => {
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

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
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
            const s = SOURCES.find((x) => x.id === b.id);
            const el = document.createElement('div');
            el.className = 'tt-block ' +
                (s.placeType === 'RESTAURANT' ? 'tt-block--food' : 'tt-block--attraction') +
                (s.reservationRequired ? ' tt-block--reserved' : '');
            el.dataset.id = String(s.id);

            const top = TG.topFor(b.startMinutes);
            // 28:00을 넘기는 블록은 그리드 밖으로 삐져나오지 않게 아래에서 자른다.
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

    // ---- 참고사항 ----
    function renderDayNote() {
        document.getElementById('day-note-text').value = DAY_NOTES[selectedDate] || '';
    }

    async function saveDayNote() {
        const memo = document.getElementById('day-note-text').value;
        try {
            const res = await fetch(`/api/day-notes/${selectedDate}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ memo }),
            });
            if (isUnauthorized(res)) return;
            if (!res.ok) throw new Error('요청 실패');
            if (memo.trim() === '') delete DAY_NOTES[selectedDate];
            else DAY_NOTES[selectedDate] = memo;
        } catch (e) {
            alert('저장 실패, 다시 시도해주세요.');
        }
    }

    // ---- 지도 ----
    function renderMapForDay() {
        if (!map) return;
        if (dayPath) {
            dayPath.setMap(null);
            dayPath = null;
        }
        const path = scheduledOn(selectedDate).map((s) => ({ lat: s.latitude, lng: s.longitude }));
        if (path.length < 2) return;
        dayPath = new google.maps.Polyline({ path, map, strokeOpacity: 0.8, strokeWeight: 3 });
    }

    function focusOnMap(source) {
        if (!map) return;
        map.panTo({ lat: source.latitude, lng: source.longitude });
        const marker = markers[source.id];
        if (marker) {
            marker.setAnimation(google.maps.Animation.BOUNCE);
            setTimeout(() => marker.setAnimation(null), 1400);
        }
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
        const s = SOURCES.find((v) => v.id === sourceId);
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
        const s = SOURCES.find((v) => v.id === sourceId);
        const prev = { scheduledDate: s.scheduledDate, startMinutes: s.startMinutes };
        try {
            const res = await fetch(`/api/schedule/${sourceId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ date, startMinutes }),
            });
            if (isUnauthorized(res)) return;
            if (!res.ok) throw new Error('요청 실패');

            s.scheduledDate = date;
            s.startMinutes = startMinutes;
        } catch (e) {
            s.scheduledDate = prev.scheduledDate;
            s.startMinutes = prev.startMinutes;
            alert('저장 실패, 다시 시도해주세요.');
        }
        renderAll();
    }

    async function unassign(sourceId) {
        const s = SOURCES.find((v) => v.id === sourceId);
        const prev = { scheduledDate: s.scheduledDate, startMinutes: s.startMinutes };
        try {
            const res = await fetch(`/api/schedule/${sourceId}`, { method: 'DELETE' });
            if (isUnauthorized(res)) return;
            if (!res.ok) throw new Error('요청 실패');

            s.scheduledDate = null;
            s.startMinutes = null;
        } catch (e) {
            s.scheduledDate = prev.scheduledDate;
            s.startMinutes = prev.startMinutes;
            alert('저장 실패, 다시 시도해주세요.');
        }
        renderAll();
    }

    // ---- 시각 수정 시트 ----
    // 드래그가 어려운 상황을 위한 폴백: 탭하면 시각을 직접 고른다.
    function openTimeSheet(sourceId) {
        const s = SOURCES.find((v) => v.id === sourceId);
        sheetSourceId = sourceId;
        focusOnMap(s);

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

    // ---- 지도 초기화 ----
    function debounce(fn, wait) {
        let timeout;
        return (...args) => {
            clearTimeout(timeout);
            timeout = setTimeout(() => fn(...args), wait);
        };
    }

    function initMap() {
        map = new google.maps.Map(document.getElementById('map'), {
            center: { lat: SOURCES[0]?.latitude ?? 37.5665, lng: SOURCES[0]?.longitude ?? 126.9780 },
            zoom: 13,
            disableDefaultUI: true,
            zoomControl: true,
        });

        SOURCES.forEach((s) => {
            markers[s.id] = new google.maps.Marker({
                position: { lat: s.latitude, lng: s.longitude },
                map,
                title: s.name,
                icon: s.placeType === 'RESTAURANT'
                    ? { url: 'https://maps.google.com/mapfiles/ms/icons/red-dot.png' }
                    : undefined,
            });
        });

        map.addListener('bounds_changed', debounce(renderRail, 300));
        renderMapForDay();
    }

    window.initMap = initMap;

    // ---- 렌더 ----
    function renderAll() {
        renderDateStrip();
        renderHourLines();
        renderRail();
        renderTimetable();
        renderDayNote();
        renderMapForDay();
    }

    // #planner 는 화면 나머지를 채워야 한다. 날짜 스트립·지도·참고사항 높이가
    // 바뀔 때마다 남는 높이를 CSS 변수로 알려준다.
    function updatePlannerHeight() {
        const tabbar = parseInt(
            getComputedStyle(document.documentElement).getPropertyValue('--tabbar-h'), 10
        ) || 56;
        const used =
            document.getElementById('date-strip').offsetHeight +
            document.getElementById('map-panel').offsetHeight +
            document.getElementById('map-toggle-row').offsetHeight +
            document.getElementById('day-note').offsetHeight +
            tabbar;
        document.documentElement.style.setProperty('--planner-offset', used + 'px');
    }

    // ---- 이벤트 바인딩 ----
    document.getElementById('day-note-save').addEventListener('click', saveDayNote);

    document.getElementById('map-toggle').addEventListener('click', (e) => {
        const panel = document.getElementById('map-panel');
        panel.classList.toggle('collapsed');
        e.target.textContent = panel.classList.contains('collapsed') ? '지도 펼치기' : '지도 접기';
        // 높이 전환 애니메이션(.2s)이 끝난 뒤에 재계산한다
        setTimeout(updatePlannerHeight, 220);
    });

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

    document.getElementById('timeSheetClose').addEventListener('click', closeTimeSheet);
    document.getElementById('timeSheetBackdrop').addEventListener('click', closeTimeSheet);

    window.addEventListener('resize', updatePlannerHeight);

    renderAll();
    updatePlannerHeight();
})();
