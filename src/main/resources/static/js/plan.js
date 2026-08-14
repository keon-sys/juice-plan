(function () {
    const appEl = document.getElementById('plan-app');
    const tripStart = new Date(appEl.dataset.tripStart);
    const tripEnd = new Date(appEl.dataset.tripEnd);

    const days = [];
    for (let d = new Date(tripStart); d <= tripEnd; d.setDate(d.getDate() + 1)) {
        days.push(new Date(d).toISOString().slice(0, 10));
    }

    let selectedDate = days[0];
    let map;
    const markers = {};

    function renderDateTabs() {
        const container = document.getElementById('date-tabs');
        container.innerHTML = '';
        days.forEach((date) => {
            const btn = document.createElement('button');
            btn.textContent = date;
            btn.classList.toggle('active', date === selectedDate);
            btn.addEventListener('click', () => {
                selectedDate = date;
                renderDateTabs();
                renderTimetable();
                renderDayNote();
            });
            container.appendChild(btn);
        });
    }

    function renderDayNote() {
        const textarea = document.getElementById('day-note-text');
        textarea.value = DAY_NOTES[selectedDate] || '';
    }

    async function saveDayNote() {
        const textarea = document.getElementById('day-note-text');
        const memo = textarea.value;
        try {
            const res = await fetch(`/api/day-notes/${selectedDate}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ memo }),
            });
            if (!res.ok) throw new Error('요청 실패');
            if (memo.trim() === '') {
                delete DAY_NOTES[selectedDate];
            } else {
                DAY_NOTES[selectedDate] = memo;
            }
        } catch (e) {
            alert('저장 실패, 다시 시도해주세요.');
        }
    }

    function availableSources() {
        const unscheduled = SOURCES.filter((s) => !s.scheduledDate);
        if (!map) return unscheduled;
        const bounds = map.getBounds();
        if (!bounds) return unscheduled;
        return unscheduled.filter((s) => bounds.contains({ lat: s.latitude, lng: s.longitude }));
    }

    function renderAvailableList() {
        const list = document.getElementById('available-list');
        list.innerHTML = '';
        availableSources().forEach((s) => {
            const item = document.createElement('div');
            item.className = 'source-card';
            item.textContent = s.name;
            item.draggable = true;
            item.dataset.id = s.id;
            item.addEventListener('click', () => focusOnMap(s));
            item.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('text/plain', String(s.id));
            });
            list.appendChild(item);
        });
    }

    function timetableSources() {
        return SOURCES
            .filter((s) => s.scheduledDate === selectedDate)
            .sort((a, b) => a.sortOrder - b.sortOrder);
    }

    function renderTimetable() {
        const container = document.getElementById('timetable');
        container.innerHTML = '';
        timetableSources().forEach((s) => {
            const item = document.createElement('div');
            item.className = 'timetable-item';
            item.draggable = true;
            item.dataset.id = s.id;

            const label = document.createElement('span');
            label.textContent = s.name;
            item.appendChild(label);

            const removeBtn = document.createElement('button');
            removeBtn.textContent = 'X';
            removeBtn.addEventListener('click', () => removeFromSchedule(s.id));
            item.appendChild(removeBtn);

            item.addEventListener('click', () => focusOnMap(s));
            item.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('text/plain', String(s.id));
            });

            container.appendChild(item);
        });
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

    async function addToSchedule(sourceId) {
        const currentIds = timetableSources().map((s) => s.id);
        if (currentIds.includes(sourceId)) return;
        await saveDay([...currentIds, sourceId]);
    }

    async function removeFromSchedule(sourceId) {
        try {
            const res = await fetch(`/api/schedule/${sourceId}`, { method: 'DELETE' });
            if (!res.ok) throw new Error('요청 실패');
            const source = SOURCES.find((s) => s.id === sourceId);
            source.scheduledDate = null;
            source.sortOrder = 0;
            renderAvailableList();
            renderTimetable();
        } catch (e) {
            alert('저장 실패, 다시 시도해주세요.');
        }
    }

    async function saveDay(orderedIds) {
        try {
            const res = await fetch(`/api/schedule/day/${selectedDate}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sourceIds: orderedIds }),
            });
            if (!res.ok) throw new Error('요청 실패');

            orderedIds.forEach((id, index) => {
                const source = SOURCES.find((s) => s.id === id);
                source.scheduledDate = selectedDate;
                source.sortOrder = index;
            });
            SOURCES.forEach((s) => {
                if (s.scheduledDate === selectedDate && !orderedIds.includes(s.id)) {
                    s.scheduledDate = null;
                    s.sortOrder = 0;
                }
            });

            renderAvailableList();
            renderTimetable();
        } catch (e) {
            alert('저장 실패, 다시 시도해주세요.');
        }
    }

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
        });

        SOURCES.forEach((s) => {
            const marker = new google.maps.Marker({
                position: { lat: s.latitude, lng: s.longitude },
                map,
                title: s.name,
                icon: s.placeType === 'RESTAURANT'
                    ? { url: 'https://maps.google.com/mapfiles/ms/icons/red-dot.png' }
                    : undefined,
            });
            markers[s.id] = marker;
        });

        map.addListener('bounds_changed', debounce(renderAvailableList, 300));
    }

    window.initMap = initMap;

    document.getElementById('day-note-save').addEventListener('click', saveDayNote);

    const availableListEl = document.getElementById('available-list');
    availableListEl.addEventListener('dragover', (e) => e.preventDefault());
    availableListEl.addEventListener('drop', (e) => {
        e.preventDefault();
        const id = Number(e.dataTransfer.getData('text/plain'));
        removeFromSchedule(id);
    });

    const timetableEl = document.getElementById('timetable');
    timetableEl.addEventListener('dragover', (e) => e.preventDefault());
    timetableEl.addEventListener('drop', (e) => {
        e.preventDefault();
        const id = Number(e.dataTransfer.getData('text/plain'));
        addToSchedule(id);
    });

    renderDateTabs();
    renderDayNote();
    renderAvailableList();
    renderTimetable();
})();
