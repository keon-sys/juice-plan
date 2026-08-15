(function () {
    const appEl = document.getElementById('dayview-app');
    const TG = window.TimeGrid;
    const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

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
    let dayMarkers = [];
    let dayPath = null;

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function scheduledOn(date) {
        return SOURCES
            .filter((s) => s.scheduledDate === date && s.startMinutes != null)
            .sort((a, b) => a.startMinutes - b.startMinutes);
    }

    function renderDateStrip() {
        const strip = document.getElementById('date-strip');
        strip.innerHTML = '';
        days.forEach((date) => {
            const d = new Date(date + 'T00:00:00');
            const count = scheduledOn(date).length;

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

    function renderItinerary() {
        const list = document.getElementById('itinerary');
        list.innerHTML = '';
        const items = scheduledOn(selectedDate);

        if (items.length === 0) {
            list.innerHTML = '<p class="card muted">이 날은 아직 일정이 없습니다.</p>';
            return;
        }

        items.forEach((s, i) => {
            const end = s.startMinutes + s.durationMinutes;
            const endLabel = end > TG.DAY_END ? '28:00+' : TG.formatSlot(end);

            const card = document.createElement('div');
            card.className = 'card';
            card.innerHTML =
                `<div style="display:flex; align-items:center; gap:8px;">` +
                    `<span class="badge ${s.placeType === 'RESTAURANT' ? 'badge--food' : 'badge--attraction'}">${i + 1}</span>` +
                    `<strong>${TG.formatSlot(s.startMinutes)}–${endLabel}</strong>` +
                `</div>` +
                `<div style="margin-top:4px;">${s.placeType === 'RESTAURANT' ? '🍴' : '📍'} ${escapeHtml(s.name)}</div>` +
                (s.reservationRequired
                    ? `<div class="muted" style="margin-top:4px;">🔔 예약 필요 (마감 ${s.reservationDeadline || '-'})</div>`
                    : '') +
                (s.memo
                    ? `<p class="muted" style="white-space:pre-wrap; margin:8px 0 0;">${escapeHtml(s.memo)}</p>`
                    : '');
            list.appendChild(card);
        });
    }

    function renderDayNote() {
        const el = document.getElementById('day-note-view');
        const memo = DAY_NOTES[selectedDate];
        el.innerHTML = memo
            ? `<strong>참고사항</strong><p class="muted" style="white-space:pre-wrap;">${escapeHtml(memo)}</p>`
            : '<span class="muted">참고사항 없음</span>';
    }

    function renderMap() {
        if (!map) return;

        dayMarkers.forEach((m) => m.setMap(null));
        dayMarkers = [];
        if (dayPath) {
            dayPath.setMap(null);
            dayPath = null;
        }

        const items = scheduledOn(selectedDate);
        if (items.length === 0) return;

        const bounds = new google.maps.LatLngBounds();
        const path = [];

        items.forEach((s, i) => {
            const pos = { lat: s.latitude, lng: s.longitude };
            path.push(pos);
            bounds.extend(pos);
            dayMarkers.push(new google.maps.Marker({
                position: pos, map, label: String(i + 1), title: s.name,
            }));
        });

        if (path.length >= 2) {
            dayPath = new google.maps.Polyline({ path, map, strokeOpacity: 0.8, strokeWeight: 3 });
        }
        map.fitBounds(bounds);
    }

    function renderAll() {
        renderDateStrip();
        renderItinerary();
        renderDayNote();
        renderMap();
    }

    window.initMap = function () {
        map = new google.maps.Map(document.getElementById('map'), {
            center: { lat: SOURCES[0]?.latitude ?? 37.5665, lng: SOURCES[0]?.longitude ?? 126.9780 },
            zoom: 13,
            disableDefaultUI: true,
            zoomControl: true,
        });
        renderMap();
    };

    renderAll();
})();
