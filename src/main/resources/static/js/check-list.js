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
            // aria-label 을 item.name 으로 만들면 안 된다: 속성 안에 들어가는데
            // escapeHtml 은 textContent→innerHTML 방식이라 따옴표를 이스케이프하지 않는다.
            // 이름을 담은 span 을 가리키는 aria-labelledby 로 대신한다.
            `<input type="checkbox" class="check-row__box"${item.checked ? ' checked' : ''} aria-labelledby="check-name-${item.id}">` +
            // 이름을 버튼으로 감싸야 키보드로도 편집 시트를 열 수 있다
            '<button type="button" class="check-row__body">' +
                `<span class="check-row__name" id="check-name-${item.id}">${escapeHtml(item.name)}</span>` +
                (item.memo ? `<span class="check-row__memo muted">${escapeHtml(item.memo)}</span>` : '') +
            '</button>' +
            '</li>';
    }

    function skeletonHtml(tab) {
        // 체크리스트는 한 번에 여러 개를 몰아 넣는 화면이라 시트보다 한 줄 입력이 빠르다.
        // form 으로 감싸면 엔터가 그대로 제출이 된다.
        return `<form class="check-add" data-tab="${tab}" novalidate>` +
            `<input type="text" class="check-add__input" placeholder="${PLACEHOLDERS[tab]}" aria-label="항목 추가">` +
            '<button type="submit" class="btn check-add__btn">추가</button>' +
            '</form>' +
            '<div class="check-body"></div>';
    }

    /** 입력 폼을 포함한 뼈대는 탭마다 딱 한 번만 그린다. 체크 한 번, 시트 저장 한 번마다
        폼까지 다시 그리면 사용자가 입력칸에 적던 글자가 통째로 사라지기 때문이다. */
    function show(tab) {
        const view = document.getElementById('view-' + tab);
        if (!view.querySelector('.check-add')) {
            view.innerHTML = skeletonHtml(tab);
        }
        renderBody(tab);
    }

    function renderBody(tab) {
        const items = itemsOf(tab);
        const done = items.filter((i) => i.checked).length;

        document.querySelector(`#view-${tab} .check-body`).innerHTML =
            items.length === 0
                ? '<p class="card muted">아직 항목이 없습니다.</p>'
                : `<p class="muted check-progress">${done}/${items.length} 완료</p>` +
                  `<ul class="check-list">${items.map(rowHtml).join('')}</ul>`;
    }

    async function add(form) {
        const input = form.querySelector('.check-add__input');
        const button = form.querySelector('.check-add__btn');
        const name = input.value.trim();
        // await 전에 먼저 비운다: 응답이 오기 전에 엔터를 한 번 더 누르면
        // 같은 이름이 두 번 전송돼 항목이 중복 생성된다. 공백만 있던 입력도
        // 여기서 같이 비워져 사용자는 제출이 접수됐다는 걸 알 수 있다.
        input.value = '';
        if (!name) return;

        const tab = form.dataset.tab;
        button.disabled = true;
        try {
            window.CHECK_ITEMS.push(await window.Api.createCheckItem({
                list: LISTS[tab], name, memo: null,
            }));
            sortItems();
            window.CheckSection.refresh();
        } catch (e) {
            input.value = name; // 실패했으니 적던 이름을 되돌려준다
            alert(e.message);
        } finally {
            button.disabled = false;
        }
    }

    async function toggle(box) {
        const row = box.closest('.check-row');
        box.disabled = true;
        try {
            const updated = await window.Api.setCheckItemChecked(Number(row.dataset.id), box.checked);
            const i = window.CHECK_ITEMS.findIndex((it) => it.id === updated.id);
            // 다른 브라우저 탭에서 같은 항목을 이미 지웠다면 배열에 없을 수 있다
            if (i >= 0) window.CHECK_ITEMS[i] = updated;
            sortItems();
            window.CheckSection.refresh();
        } catch (e) {
            // 서버가 거절했는데 체크된 것처럼 보이면 안 된다
            box.checked = !box.checked;
            alert(e.message);
        } finally {
            box.disabled = false;
        }
    }

    let editingId = null;

    const sheet = () => document.getElementById('checkSheet');
    const backdrop = () => document.getElementById('checkBackdrop');

    function openSheet(item) {
        editingId = item.id;
        document.getElementById('checkName').value = item.name;
        document.getElementById('checkMemo').value = item.memo || '';
        document.getElementById('checkError').hidden = true;

        sheet().classList.add('sheet--open');
        backdrop().classList.add('sheet--open');
    }

    function closeSheet() {
        sheet().classList.remove('sheet--open');
        backdrop().classList.remove('sheet--open');
        editingId = null;
    }

    async function submit(e) {
        e.preventDefault();
        const errorEl = document.getElementById('checkError');
        errorEl.hidden = true;
        // 응답이 오는 동안 사용자가 이 시트를 닫고 다른 항목의 시트를 열 수 있다.
        // 미리 붙잡아 두지 않으면 늦게 도착한 응답이 그 다른 시트를 닫아버린다.
        const id = editingId;

        try {
            const updated = await window.Api.updateCheckItem(id, {
                name: document.getElementById('checkName').value,
                memo: document.getElementById('checkMemo').value || null,
            });
            const i = window.CHECK_ITEMS.findIndex((it) => it.id === updated.id);
            if (i >= 0) window.CHECK_ITEMS[i] = updated;
            if (editingId === id) closeSheet();
            window.CheckSection.refresh();
        } catch (err) {
            errorEl.textContent = err.message;
            errorEl.hidden = false;
        }
    }

    async function remove() {
        const item = window.CHECK_ITEMS.find((it) => it.id === editingId);
        const label = item ? `'${item.name}'을(를)` : '이 항목을';
        if (!confirm(`${label} 삭제하시겠습니까?`)) return;

        try {
            await window.Api.deleteCheckItem(editingId);
            window.CHECK_ITEMS = window.CHECK_ITEMS.filter((it) => it.id !== editingId);
            closeSheet();
            window.CheckSection.refresh();
        } catch (err) {
            alert(err.message);
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

        host.addEventListener('click', (e) => {
            const body = e.target.closest('.check-row__body');
            if (!body) return;
            const id = Number(body.closest('.check-row').dataset.id);
            openSheet(window.CHECK_ITEMS.find((it) => it.id === id));
        });

        document.getElementById('checkForm').addEventListener('submit', submit);
        document.getElementById('checkDelete').addEventListener('click', remove);
        document.getElementById('checkSheetClose').addEventListener('click', closeSheet);
        document.getElementById('checkBackdrop').addEventListener('click', closeSheet);
    }

    return { init, show };
})();
