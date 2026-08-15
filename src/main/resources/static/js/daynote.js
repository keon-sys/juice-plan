// 접히는 참고사항 컴포넌트. 기본은 한 줄로 접혀 있고 헤더를 탭하면 펼쳐진다.
// 저장 버튼을 전체 폭으로 두면 그 날 메모가 아니라 전체 계획을 저장하는 것처럼 보이므로
// 내용 폭으로 오른쪽에 붙인다.
window.DayNote = (function () {
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function firstLine(memo) {
        const line = memo.split('\n')[0].trim();
        return line.length > 40 ? line.slice(0, 40) + '…' : line;
    }

    /**
     * @param container   그릴 요소
     * @param memo        현재 메모 (없으면 '')
     * @param editable    false 면 textarea 와 저장 버튼 없이 내용만 보여준다
     * @param onSave      (memo) => Promise, editable 일 때만 쓰인다
     */
    function render(container, memo, editable, onSave) {
        const open = container.dataset.open === 'true';
        const saved = memo || '';
        // 접었다 펴도 입력하던 내용이 날아가지 않도록 들고 있는다.
        // 미리보기 줄에는 저장된 내용만 보여준다 — 저장 안 된 글을 저장된 것처럼 보이면 안 된다.
        const editing = editable && container._draft != null ? container._draft : saved;

        container.className = 'card daynote';
        container.innerHTML =
            `<button type="button" class="daynote__header">` +
                `<span>📝 참고사항</span>` +
                (open ? '' : `<span class="daynote__preview muted">${saved ? escapeHtml(firstLine(saved)) : '참고사항 없음'}</span>`) +
                (!open && editable && container._draft != null && container._draft !== saved
                    ? `<span class="daynote__dirty" title="저장하지 않은 변경">●</span>` : '') +
                `<span class="daynote__caret muted">${open ? '▴' : '▾'}</span>` +
            `</button>` +
            (open
                ? (editable
                    ? `<textarea class="daynote__text" rows="2" placeholder="이 날짜의 참고사항을 입력하세요">${escapeHtml(editing)}</textarea>` +
                      `<div class="daynote__actions"><button type="button" class="btn daynote__save">저장</button></div>`
                    : `<p class="daynote__read muted">${saved ? escapeHtml(saved) : '참고사항 없음'}</p>`)
                : '');

        container.querySelector('.daynote__header').addEventListener('click', () => {
            if (open && editable) {
                container._draft = container.querySelector('.daynote__text').value;
            }
            container.dataset.open = open ? 'false' : 'true';
            render(container, memo, editable, onSave);
        });

        if (open && editable) {
            container.querySelector('.daynote__save').addEventListener('click', async () => {
                const value = container.querySelector('.daynote__text').value;
                await onSave(value);
                container._draft = null;   // 저장했으니 초안을 버린다
            });
        }
    }

    /** 날짜가 바뀌면 이전 날짜의 초안을 들고 있으면 안 된다. */
    function reset(container) {
        container._draft = null;
    }

    return { render, reset };
})();
