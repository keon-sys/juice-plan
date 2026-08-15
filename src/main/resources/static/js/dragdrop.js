// Pointer Events 기반 드래그. HTML5 Drag and Drop API는 모바일 브라우저에서
// dragstart/drop 이벤트를 발생시키지 않으므로 직접 구현한다.
window.DragDrop = (function () {
    const DRAG_THRESHOLD_PX = 6;   // 이보다 적게 움직이면 탭으로 본다

    function makeDraggable(el, opts) {
        // touch-action: none 이 없으면 브라우저 스크롤이 pointermove를 가로챈다.
        el.style.touchAction = 'none';

        el.addEventListener('pointerdown', (e) => {
            // 마우스는 주버튼만
            if (e.pointerType === 'mouse' && e.button !== 0) return;

            const startX = e.clientX;
            const startY = e.clientY;
            let dragging = false;
            let ghost = null;

            function cleanup() {
                el.removeEventListener('pointermove', onPointerMove);
                el.removeEventListener('pointerup', onPointerUp);
                el.removeEventListener('pointercancel', onPointerUp);
                if (ghost) {
                    ghost.remove();
                    ghost = null;
                }
            }

            function onPointerMove(ev) {
                const dx = ev.clientX - startX;
                const dy = ev.clientY - startY;

                if (!dragging) {
                    if (Math.hypot(dx, dy) < DRAG_THRESHOLD_PX) return;
                    dragging = true;
                    // 포인터를 캡처해야 요소 밖으로 나가도 이벤트가 계속 온다.
                    el.setPointerCapture(ev.pointerId);
                    // 지도 핸들바처럼 요소 자체가 따라 움직이는 경우엔 고스트가 방해된다.
                    if (!opts.noGhost) {
                        ghost = el.cloneNode(true);
                        ghost.classList.add('drag-ghost');
                        ghost.style.width = el.offsetWidth + 'px';
                        document.body.appendChild(ghost);
                    }
                    if (opts.onStart) opts.onStart(opts.data, startX, startY);
                }

                if (ghost) {
                    ghost.style.left = ev.clientX + 'px';
                    ghost.style.top = ev.clientY + 'px';
                }
                if (opts.onMove) opts.onMove(opts.data, ev.clientX, ev.clientY);
            }

            function onPointerUp(ev) {
                const wasDragging = dragging;
                cleanup();

                if (wasDragging) {
                    // pointercancel 은 드롭이 아니라 취소다.
                    if (ev.type === 'pointerup' && opts.onDrop) {
                        opts.onDrop(opts.data, ev.clientX, ev.clientY);
                    } else if (opts.onCancel) {
                        opts.onCancel(opts.data);
                    }
                } else if (ev.type === 'pointerup' && opts.onTap) {
                    opts.onTap(opts.data);
                }
            }

            el.addEventListener('pointermove', onPointerMove);
            el.addEventListener('pointerup', onPointerUp);
            el.addEventListener('pointercancel', onPointerUp);
        });
    }

    return { makeDraggable };
})();
