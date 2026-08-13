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
