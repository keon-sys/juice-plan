const sourceForm = document.getElementById('sourceForm');
const submitBtn = sourceForm.querySelector('button[type="submit"]');
const sheet = document.getElementById('sourceSheet');
const backdrop = document.getElementById('sheetBackdrop');
const sheetTitle = document.getElementById('sheetTitle');
let editingId = null;

function openSheet() {
    sheet.classList.add('sheet--open');
    backdrop.classList.add('sheet--open');
}

function closeSheet() {
    sheet.classList.remove('sheet--open');
    backdrop.classList.remove('sheet--open');
}

document.getElementById('addSourceBtn').addEventListener('click', () => {
    editingId = null;
    sourceForm.reset();
    document.getElementById('reservationDeadlineWrap').style.display = 'none';
    document.getElementById('parseError').style.display = 'none';
    sheetTitle.textContent = '새 소스 추가';
    submitBtn.textContent = '저장';
    openSheet();
});

document.getElementById('sheetClose').addEventListener('click', closeSheet);
backdrop.addEventListener('click', closeSheet);

// 여행 기간 수정 폼 토글
const tripEditBtn = document.getElementById('tripEditBtn');
if (tripEditBtn) {
    tripEditBtn.addEventListener('click', () => {
        const form = document.getElementById('trip-edit');
        form.style.display = form.style.display === 'none' ? 'block' : 'none';
    });
}

// 필터 칩: 카드의 data-place-type / data-scheduled 로 숨긴다
document.querySelectorAll('.chip[data-filter]').forEach((chip) => {
    chip.addEventListener('click', () => {
        document.querySelectorAll('.chip[data-filter]').forEach((c) => c.classList.remove('chip--on'));
        chip.classList.add('chip--on');
        const filter = chip.dataset.filter;
        document.querySelectorAll('.source-card').forEach((card) => {
            const show =
                filter === 'all' ||
                (filter === 'unassigned' && card.dataset.scheduled === 'false') ||
                filter === card.dataset.placeType;
            card.style.display = show ? '' : 'none';
        });
    });
});

document.getElementById('parseLinkBtn').addEventListener('click', async () => {
    const url = document.getElementById('googleMapsUrl').value;
    const errorEl = document.getElementById('parseError');
    errorEl.style.display = 'none';

    try {
        const res = await fetch('/api/sources/parse-link', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url }),
        });

        if (res.status === 401) {
            alert('세션이 만료되었습니다. 다시 로그인해주세요.');
            window.location.href = '/';
            return;
        }

        const data = await res.json();

        if (!data.success) {
            errorEl.textContent = '링크에서 위치 정보를 찾을 수 없습니다. 이름과 위치를 직접 입력해주세요.';
            errorEl.style.display = 'block';
            return;
        }

        if (data.place.name) {
            document.getElementById('name').value = data.place.name;
        }
        document.getElementById('latitude').value = data.place.latitude;
        document.getElementById('longitude').value = data.place.longitude;
    } catch (e) {
        errorEl.textContent = '요청 중 오류가 발생했습니다. 이름과 위치를 직접 입력해주세요.';
        errorEl.style.display = 'block';
    }
});

document.getElementById('reservationRequired').addEventListener('change', (e) => {
    document.getElementById('reservationDeadlineWrap').style.display = e.target.checked ? 'block' : 'none';
});

document.querySelectorAll('.delete-btn').forEach((btn) => {
    btn.addEventListener('click', async () => {
        const id = btn.getAttribute('data-id');
        if (!confirm('삭제하시겠습니까?')) return;
        await fetch(`/sources/${id}`, { method: 'DELETE' });
        window.location.reload();
    });
});

// Edit affordance: reuses the add-source form inside the sheet. Clicking "수정" populates the
// form from the clicked item's data-* attributes and flips the form's submit handler over to a
// fetch-based PUT /sources/{id} call (PUT isn't a native HTML form method), instead of letting
// the form fall through to its default POST /sources submission.
document.querySelectorAll('.edit-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
        editingId = btn.getAttribute('data-id');

        document.getElementById('googleMapsUrl').value = btn.getAttribute('data-google-maps-url') || '';
        document.getElementById('name').value = btn.getAttribute('data-name') || '';
        document.getElementById('latitude').value = btn.getAttribute('data-latitude') || '';
        document.getElementById('longitude').value = btn.getAttribute('data-longitude') || '';

        const placeType = btn.getAttribute('data-place-type');
        sourceForm.querySelectorAll('input[name="placeType"]').forEach((radio) => {
            radio.checked = radio.value === placeType;
        });

        sourceForm.querySelector('input[name="durationHours"]').value = btn.getAttribute('data-duration-hours') || '0';
        sourceForm.querySelector('input[name="durationMinutesPart"]').value = btn.getAttribute('data-duration-minutes-part') || '0';

        const reservationRequired = btn.getAttribute('data-reservation-required') === 'true';
        const reservationCheckbox = document.getElementById('reservationRequired');
        reservationCheckbox.checked = reservationRequired;
        document.getElementById('reservationDeadlineWrap').style.display = reservationRequired ? 'block' : 'none';
        sourceForm.querySelector('input[name="reservationDeadline"]').value = btn.getAttribute('data-reservation-deadline') || '';

        sourceForm.querySelector('textarea[name="memo"]').value = btn.getAttribute('data-memo') || '';

        document.getElementById('parseError').style.display = 'none';
        sheetTitle.textContent = '소스 수정';
        submitBtn.textContent = '수정 저장';
        openSheet();
    });
});

sourceForm.addEventListener('submit', async (e) => {
    if (editingId === null) return;
    e.preventDefault();

    const params = new URLSearchParams(new FormData(sourceForm));
    if (!sourceForm.querySelector('#reservationRequired').checked) {
        params.set('reservationRequired', 'false');
    }

    try {
        const res = await fetch(`/sources/${editingId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString(),
        });
        if (!res.ok) throw new Error('요청 실패');
        window.location.reload();
    } catch (err) {
        alert('수정 실패, 다시 시도해주세요.');
    }
});
