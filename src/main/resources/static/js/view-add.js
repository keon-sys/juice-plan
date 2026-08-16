// 장소 추가 뷰: 여행 기간 카드 + 필터 칩 + 소스 목록 + 추가/수정 시트.
window.ViewAdd = (function () {
    let editingId = null;
    let filter = 'all';

    const sheet = () => document.getElementById('sourceSheet');
    const backdrop = () => document.getElementById('sheetBackdrop');
    const form = () => document.getElementById('sourceForm');

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // 자정 기준 분을 목록용 표기로. 25:00~28:00 은 다음날 01:00~04:00 으로 접는다.
    function clockLabel(startMinutes) {
        const m = startMinutes % 1440;
        return String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
    }

    // ---- 여행 기간 ----
    function renderTrip() {
        const el = document.getElementById('trip-widget');
        if (!window.TRIP) {
            el.innerHTML =
                `<p class="muted" style="margin:0 0 8px;">먼저 여행 기간을 설정해주세요.</p>` +
                `<label for="tripStart">시작일</label><input type="date" id="tripStart">` +
                `<label for="tripEnd">종료일</label><input type="date" id="tripEnd">` +
                `<p class="error" id="tripError" style="display:none;"></p>` +
                `<button type="button" class="btn btn--primary btn--block" id="tripSave" style="margin-top:16px;">여행 기간 설정</button>`;
        } else {
            el.innerHTML =
                `<div style="display:flex; align-items:center; justify-content:space-between; gap:8px;">` +
                    `<div><div class="muted">여행 기간</div>` +
                    `<strong>${window.TRIP.startDate} ~ ${window.TRIP.endDate}</strong></div>` +
                    `<button type="button" class="btn btn--ghost" id="tripEditBtn">수정</button>` +
                `</div>` +
                `<div id="tripEdit" hidden>` +
                    `<label for="tripStart">시작일</label><input type="date" id="tripStart" value="${window.TRIP.startDate}">` +
                    `<label for="tripEnd">종료일</label><input type="date" id="tripEnd" value="${window.TRIP.endDate}">` +
                    `<p class="error" id="tripError" style="display:none;"></p>` +
                    `<button type="button" class="btn btn--primary btn--block" id="tripSave" style="margin-top:16px;">저장</button>` +
                `</div>`;

            el.querySelector('#tripEditBtn').addEventListener('click', () => {
                const box = el.querySelector('#tripEdit');
                box.hidden = !box.hidden;
            });
        }

        el.querySelector('#tripSave').addEventListener('click', saveTrip);
    }

    async function saveTrip() {
        const startDate = document.getElementById('tripStart').value;
        const endDate = document.getElementById('tripEnd').value;
        const errorEl = document.getElementById('tripError');
        errorEl.style.display = 'none';

        if (!startDate || !endDate) {
            errorEl.textContent = '시작일과 종료일을 모두 입력해주세요.';
            errorEl.style.display = 'block';
            return;
        }

        try {
            window.TRIP = await window.Api.saveTrip({ startDate, endDate });
            show();
        } catch (e) {
            errorEl.textContent = e.message;
            errorEl.style.display = 'block';
        }
    }

    // ---- 소스 목록 ----
    function visibleSources() {
        return window.SOURCES.filter((s) => {
            if (filter === 'all') return true;
            if (filter === 'unassigned') return !s.scheduledDate;
            return s.placeType === filter;
        });
    }

    function renderList() {
        const list = document.getElementById('source-list');
        list.innerHTML = '';

        if (window.SOURCES.length === 0) {
            list.innerHTML = '<p class="card muted">아직 등록한 장소가 없습니다. 오른쪽 아래 + 버튼으로 추가해주세요.</p>';
            return;
        }

        const shown = visibleSources();
        if (shown.length === 0) {
            list.innerHTML = '<p class="card muted">조건에 맞는 장소가 없습니다.</p>';
            return;
        }

        shown.forEach((s) => {
            const card = document.createElement('div');
            card.className = 'card source-card';
            // 버튼을 설명 아래에 깔면 카드마다 한 줄씩 높이를 더 먹는다.
            // 오른쪽 끝에 붙이면 설명 높이 안에 들어가 목록이 훨씬 짧아진다.
            card.innerHTML =
                `<div class="source-card__body">` +
                    `<div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">` +
                        `<span class="badge ${s.placeType === 'RESTAURANT' ? 'badge--food' : 'badge--attraction'}">` +
                        `${s.placeType === 'RESTAURANT' ? '🍴 음식점' : '📍 관광지'}</span>` +
                        `<strong>${escapeHtml(s.name)}</strong>` +
                    `</div>` +
                    `<div class="muted" style="margin-top:4px;">${s.durationMinutes}분 · ` +
                        (s.scheduledDate
                            ? `${s.scheduledDate} ${clockLabel(s.startMinutes)}`
                            : '미배정') +
                    `</div>` +
                    (s.reservationRequired
                        ? `<div class="badge badge--reservation" style="margin-top:8px;">🔔 예약 마감 ${s.reservationDeadline || '-'}</div>`
                        : '') +
                    (s.memo ? `<p class="muted" style="white-space:pre-wrap; margin:8px 0 0;">${escapeHtml(s.memo)}</p>` : '') +
                `</div>` +
                `<div class="source-card__actions">` +
                    `<button type="button" class="btn" data-act="edit">수정</button>` +
                    `<button type="button" class="btn btn--danger" data-act="delete">삭제</button>` +
                `</div>`;

            // 카드를 탭하면 지도가 그 위치로 간다 (버튼 탭은 제외)
            card.addEventListener('click', (e) => {
                if (e.target.closest('button')) return;
                window.MapView.focus(s);
            });
            card.querySelector('[data-act="edit"]').addEventListener('click', () => openSheet(s));
            card.querySelector('[data-act="delete"]').addEventListener('click', () => remove(s));

            list.appendChild(card);
        });
    }

    async function remove(source) {
        if (!confirm(`'${source.name}'을(를) 삭제하시겠습니까?`)) return;
        try {
            await window.Api.deleteSource(source.id);
            window.SOURCES = window.SOURCES.filter((s) => s.id !== source.id);
            show();
        } catch (e) {
            alert(e.message);
        }
    }

    // ---- 시트 ----
    function openSheet(source) {
        editingId = source ? source.id : null;
        const f = form();
        f.reset();
        document.getElementById('parseError').style.display = 'none';
        document.getElementById('sourceError').style.display = 'none';
        window.MapView.clearPreview();

        if (source) {
            document.getElementById('googleMapsUrl').value = source.googleMapsUrl;
            document.getElementById('name').value = source.name;
            document.getElementById('latitude').value = source.latitude;
            document.getElementById('longitude').value = source.longitude;
            f.querySelectorAll('input[name="placeType"]').forEach((r) => {
                r.checked = r.value === source.placeType;
            });
            f.querySelector('input[name="durationHours"]').value = Math.floor(source.durationMinutes / 60);
            f.querySelector('input[name="durationMinutesPart"]').value = source.durationMinutes % 60;
            document.getElementById('reservationRequired').checked = source.reservationRequired;
            document.getElementById('reservationDeadlineWrap').style.display =
                source.reservationRequired ? 'block' : 'none';
            document.getElementById('reservationDeadline').value = source.reservationDeadline || '';
            document.getElementById('memo').value = source.memo || '';
            window.MapView.showPreview(source.latitude, source.longitude);
        } else {
            document.getElementById('reservationDeadlineWrap').style.display = 'none';
        }

        document.getElementById('sheetTitle').textContent = source ? '장소 수정' : '새 장소 추가';
        document.getElementById('sourceSubmit').textContent = source ? '수정 저장' : '저장';
        sheet().classList.add('sheet--open');
        backdrop().classList.add('sheet--open');
    }

    function closeSheet() {
        sheet().classList.remove('sheet--open');
        backdrop().classList.remove('sheet--open');
        window.MapView.clearPreview();
        editingId = null;
    }

    function readForm() {
        const f = form();
        const checked = f.querySelector('input[name="placeType"]:checked');
        return {
            googleMapsUrl: document.getElementById('googleMapsUrl').value,
            name: document.getElementById('name').value,
            latitude: Number(document.getElementById('latitude').value),
            longitude: Number(document.getElementById('longitude').value),
            placeType: checked ? checked.value : null,
            durationHours: Number(f.querySelector('input[name="durationHours"]').value || 0),
            durationMinutesPart: Number(f.querySelector('input[name="durationMinutesPart"]').value || 0),
            reservationRequired: document.getElementById('reservationRequired').checked,
            reservationDeadline: document.getElementById('reservationDeadline').value || null,
            memo: document.getElementById('memo').value || null,
        };
    }

    async function submit(e) {
        e.preventDefault();
        const errorEl = document.getElementById('sourceError');
        errorEl.style.display = 'none';
        const payload = readForm();

        try {
            if (editingId === null) {
                const created = await window.Api.createSource(payload);
                window.SOURCES.push(created);
            } else {
                const updated = await window.Api.updateSource(editingId, payload);
                const i = window.SOURCES.findIndex((s) => s.id === editingId);
                window.SOURCES[i] = updated;
            }
            closeSheet();
            show();
        } catch (err) {
            errorEl.textContent = err.message;
            errorEl.style.display = 'block';
        }
    }

    async function parseLink() {
        const url = document.getElementById('googleMapsUrl').value;
        const errorEl = document.getElementById('parseError');
        errorEl.style.display = 'none';

        try {
            const data = await window.Api.parseLink(url);
            if (!data.success) {
                errorEl.textContent = '링크에서 위치 정보를 찾을 수 없습니다. 이름과 위치를 직접 입력해주세요.';
                errorEl.style.display = 'block';
                return;
            }
            if (data.place.name) document.getElementById('name').value = data.place.name;
            document.getElementById('latitude').value = data.place.latitude;
            document.getElementById('longitude').value = data.place.longitude;
            window.MapView.showPreview(data.place.latitude, data.place.longitude);
        } catch (e) {
            errorEl.textContent = '요청 중 오류가 발생했습니다. 이름과 위치를 직접 입력해주세요.';
            errorEl.style.display = 'block';
        }
    }

    // ---- 공개 ----
    function init() {
        document.getElementById('addSourceBtn').addEventListener('click', () => openSheet(null));
        document.getElementById('sheetClose').addEventListener('click', closeSheet);
        document.getElementById('sheetBackdrop').addEventListener('click', closeSheet);
        document.getElementById('parseLinkBtn').addEventListener('click', parseLink);
        document.getElementById('sourceForm').addEventListener('submit', submit);

        document.getElementById('reservationRequired').addEventListener('change', (e) => {
            document.getElementById('reservationDeadlineWrap').style.display =
                e.target.checked ? 'block' : 'none';
        });

        document.querySelectorAll('.chip[data-filter]').forEach((chip) => {
            chip.addEventListener('click', () => {
                document.querySelectorAll('.chip[data-filter]').forEach((c) => c.classList.remove('chip--on'));
                chip.classList.add('chip--on');
                filter = chip.dataset.filter;
                renderList();
            });
        });
    }

    function show() {
        renderTrip();
        renderList();
        window.MapView.clear();
        window.MapView.showPins(window.SOURCES, false);
    }

    return { init, show };
})();
