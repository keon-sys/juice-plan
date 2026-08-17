// 서버 호출 한 곳. 실패하면 서버가 준 메시지를 담은 Error를 던진다.
window.Api = (function () {
    async function send(method, url, body) {
        const res = await fetch(url, {
            method,
            headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body),
        });

        if (!res.ok) {
            let message = '요청에 실패했습니다.';
            try {
                const data = await res.json();
                if (data && data.error) message = data.error;
            } catch (e) {
                // 본문이 JSON이 아니면 기본 메시지를 쓴다
            }
            throw new Error(message);
        }

        // 204 이거나 본문이 비어 있으면 null
        const text = await res.text();
        return text ? JSON.parse(text) : null;
    }

    return {
        createSource: (payload) => send('POST', '/api/sources', payload),
        updateSource: (id, payload) => send('PUT', `/api/sources/${id}`, payload),
        deleteSource: (id) => send('DELETE', `/api/sources/${id}`),
        saveTrip: (payload) => send('POST', '/api/trip', payload),
        assignSchedule: (id, date, startMinutes) => send('PUT', `/api/schedule/${id}`, { date, startMinutes }),
        removeSchedule: (id) => send('DELETE', `/api/schedule/${id}`),
        saveDayNote: (date, memo) => send('POST', `/api/day-notes/${date}`, { memo }),
        parseLink: (url) => send('POST', '/api/sources/parse-link', { url }),
        budgetSummary: () => send('GET', '/api/budget/summary'),
        saveBudgetRate: (ratePer100Jpy) => send('PUT', '/api/budget/rate', { ratePer100Jpy }),
    };
})();
