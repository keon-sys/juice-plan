// 타임테이블 순수 계산. DOM에 접근하지 않으므로 콘솔에서 직접 호출해 검증할 수 있다.
window.TimeGrid = (function () {
    const DAY_START = 240;   // 04:00 — 그리드 상단
    const DAY_END = 1680;    // 28:00 — 그리드 하단 경계 (배정 가능한 시각이 아니다)
    const SLOT = 30;         // 슬롯 길이(분)
    const SLOT_H = 28;       // 슬롯 높이(px) — style.css의 --slot-h와 반드시 같아야 한다
    const LAST_START = DAY_END - SLOT;  // 27:30 — 배정 가능한 마지막 시작 시각

    function pad(n) {
        return String(n).padStart(2, '0');
    }

    // 24시 이후는 25:00~28:00으로 표기한다 (다음날 새벽을 이어서 보여주기 위함).
    function formatSlot(minutes) {
        return pad(Math.floor(minutes / 60)) + ':' + pad(minutes % 60);
    }

    function clamp(v, lo, hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    // 그리드 상단(04:00) 기준 y좌표(px) -> 30분 스냅된 시작 분
    function snapToSlot(offsetY) {
        const raw = DAY_START + (offsetY / SLOT_H) * SLOT;
        const snapped = Math.round(raw / SLOT) * SLOT;
        return clamp(snapped, DAY_START, LAST_START);
    }

    function topFor(startMinutes) {
        return ((startMinutes - DAY_START) / SLOT) * SLOT_H;
    }

    function heightFor(durationMinutes) {
        return Math.max(SLOT_H, (durationMinutes / SLOT) * SLOT_H);
    }

    // 겹치는 블록을 구글 캘린더처럼 가로로 나눈다.
    // 1) 시작 시각 오름차순 정렬
    // 2) 시간이 이어지는 동안 하나의 그룹으로 묶는다
    // 3) 그룹 안에서 앞 블록과 겹치지 않는 가장 왼쪽 컬럼에 배정
    // 4) 그룹 전체가 같은 columnCount를 공유해 폭이 어긋나지 않게 한다
    function layoutBlocks(blocks) {
        const sorted = blocks
            .slice()
            .sort((a, b) => a.startMinutes - b.startMinutes || a.id - b.id);

        const result = [];
        let group = [];
        let groupEnd = -1;

        function flush() {
            if (group.length === 0) return;
            const columnCount = Math.max(...group.map((g) => g.column)) + 1;
            group.forEach((g) => result.push({ ...g, columnCount }));
            group = [];
            groupEnd = -1;
        }

        for (const b of sorted) {
            // 최소 한 슬롯은 차지한다고 보고 겹침을 판정한다 (렌더링 높이와 맞춘다).
            const end = b.startMinutes + Math.max(SLOT, b.durationMinutes);

            if (b.startMinutes >= groupEnd) {
                flush();
            }

            // 이 그룹에서 비어 있는 가장 왼쪽 컬럼을 찾는다
            let column = 0;
            while (group.some((g) => g.column === column && g.end > b.startMinutes)) {
                column += 1;
            }

            group.push({ ...b, end, column });
            groupEnd = Math.max(groupEnd, end);
        }
        flush();

        return result.map(({ end, ...rest }) => rest);
    }

    return { DAY_START, DAY_END, LAST_START, SLOT, SLOT_H, formatSlot, snapToSlot, topFor, heightFor, layoutBlocks };
})();
