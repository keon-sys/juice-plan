// 체크리스트 목록. 세 탭이 이 렌더러 하나를 공유하고 어느 목록인지만 다르다.
window.ViewCheck = (function () {
    // 탭 이름 → 서버의 CheckList 값
    const LISTS = { shopping: 'SHOPPING', packing: 'PACKING', todo: 'TODO' };

    const PLACEHOLDERS = {
        shopping: '예: 시로이코이비토',
        packing: '예: 여권, 충전기',
        todo: '예: 출국 전 환전',
    };

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /** 서버와 같은 순서: 체크 안 한 것이 먼저, 그 안에서는 넣은 순서(id). */
    function sortItems() {
        window.CHECK_ITEMS.sort((a, b) =>
            (a.checked === b.checked ? a.id - b.id : (a.checked ? 1 : -1)));
    }

    function itemsOf(tab) {
        return window.CHECK_ITEMS.filter((i) => i.list === LISTS[tab]);
    }

    function rowHtml(item) {
        return `<li class="check-row${item.checked ? ' check-row--done' : ''}" data-id="${item.id}">` +
            `<input type="checkbox" class="check-row__box"${item.checked ? ' checked' : ''} aria-label="완료">` +
            // 이름을 버튼으로 감싸야 키보드로도 편집 시트를 열 수 있다
            '<button type="button" class="check-row__body">' +
                `<span class="check-row__name">${escapeHtml(item.name)}</span>` +
                (item.memo ? `<span class="check-row__memo muted">${escapeHtml(item.memo)}</span>` : '') +
            '</button>' +
            '</li>';
    }

    function show(tab) {
        const items = itemsOf(tab);
        const done = items.filter((i) => i.checked).length;

        document.getElementById('view-' + tab).innerHTML =
            // 체크리스트는 한 번에 여러 개를 몰아 넣는 화면이라 시트보다 한 줄 입력이 빠르다.
            // form 으로 감싸면 엔터가 그대로 제출이 된다.
            `<form class="check-add" data-tab="${tab}" novalidate>` +
                `<input type="text" class="check-add__input" placeholder="${PLACEHOLDERS[tab]}" aria-label="항목 추가">` +
                '<button type="submit" class="btn check-add__btn">추가</button>' +
            '</form>' +
            (items.length === 0
                ? '<p class="card muted">아직 항목이 없습니다.</p>'
                : `<p class="muted check-progress">${done}/${items.length} 완료</p>` +
                  `<ul class="check-list">${items.map(rowHtml).join('')}</ul>`);
    }

    /** 목록을 다시 그린 뒤 입력칸으로 돌아온다. 연달아 적을 때 손이 멈추지 않게 한다. */
    function focusInput(tab) {
        const input = document.querySelector(`#view-${tab} .check-add__input`);
        if (input) input.focus();
    }

    async function add(form) {
        const input = form.querySelector('.check-add__input');
        const name = input.value.trim();
        if (!name) return;

        const tab = form.dataset.tab;
        try {
            window.CHECK_ITEMS.push(await window.Api.createCheckItem({
                list: LISTS[tab], name, memo: null,
            }));
            sortItems();
            window.CheckSection.refresh();
            focusInput(tab);
        } catch (e) {
            alert(e.message);
        }
    }

    async function toggle(box) {
        const row = box.closest('.check-row');
        try {
            const updated = await window.Api.setCheckItemChecked(Number(row.dataset.id), box.checked);
            const i = window.CHECK_ITEMS.findIndex((it) => it.id === updated.id);
            window.CHECK_ITEMS[i] = updated;
            sortItems();
            window.CheckSection.refresh();
        } catch (e) {
            // 서버가 거절했는데 체크된 것처럼 보이면 안 된다
            box.checked = !box.checked;
            alert(e.message);
        }
    }

    function init() {
        // 목록은 매번 새로 그려지므로 이벤트는 바깥 상자에 한 번만 건다
        const host = document.querySelector('.section-body');

        host.addEventListener('submit', (e) => {
            if (!e.target.classList.contains('check-add')) return;
            e.preventDefault();
            add(e.target);
        });

        host.addEventListener('change', (e) => {
            if (e.target.classList.contains('check-row__box')) toggle(e.target);
        });
    }

    return { init, show };
})();
