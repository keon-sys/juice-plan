// Pointer Events 기반 드래그. HTML5 Drag and Drop API는 모바일 브라우저에서
// dragstart/drop 이벤트를 발생시키지 않으므로 직접 구현한다.
//
// 터치에서는 목록을 넘기려는 손짓과 일정을 옮기려는 손짓이 똑같이 '누르고 끌기'라
// 구분할 방법이 없다. 그래서 터치는 잠깐 꾹 눌러야 드래그가 걸리고, 그전에 움직이면
// 브라우저가 그냥 스크롤한다. 마우스는 스크롤이 휠이라 섞일 일이 없으므로 예전처럼
// 조금만 움직여도 바로 끌린다.
window.DragDrop = (function () {
    const DRAG_THRESHOLD_PX = 6;   // 마우스가 이보다 적게 움직이면 탭으로 본다
    const HOLD_MS = 350;           // 터치는 이만큼 꾹 눌러야 드래그가 걸린다
    const HOLD_TOLERANCE_PX = 8;   // 꾹 누르는 동안 봐주는 손떨림

    /**
     * 드래그가 걸린 뒤로만 브라우저 스크롤을 막는다. touch-action 은 제스처가
     * 시작된 다음에 바꿔봐야 진행 중인 제스처엔 먹지 않아서, touchmove 를
     * 직접 가로채는 방법밖에 없다.
     */
    function blockTouchScroll() {
        const stop = (e) => e.preventDefault();
        document.addEventListener('touchmove', stop, { passive: false });
        return () => document.removeEventListener('touchmove', stop);
    }

    function makeDraggable(el, opts) {
        // 즉시 드래그(지도 핸들)는 브라우저 스크롤과 경쟁하면 안 되니 아예 막는다.
        // 꾹 누르기 쪽은 세로 스크롤을 브라우저에 맡겨야 목록이 넘어간다.
        el.style.touchAction = opts.instant ? 'none' : 'pan-y';

        el.addEventListener('pointerdown', (e) => {
            // 마우스는 주버튼만
            if (e.pointerType === 'mouse' && e.button !== 0) return;

            const holdToDrag = !opts.instant && e.pointerType !== 'mouse';
            const tapSlop = holdToDrag ? HOLD_TOLERANCE_PX : DRAG_THRESHOLD_PX;
            const startX = e.clientX;
            const startY = e.clientY;
            let dragging = false;
            let ghost = null;
            let holdTimer = null;
            let unblockScroll = null;

            function cancelHold() {
                if (holdTimer === null) return;
                clearTimeout(holdTimer);
                holdTimer = null;
            }

            function cleanup() {
                cancelHold();
                el.removeEventListener('pointermove', onPointerMove);
                el.removeEventListener('pointerup', onPointerUp);
                el.removeEventListener('pointercancel', onPointerUp);
                if (unblockScroll) {
                    unblockScroll();
                    unblockScroll = null;
                }
                el.classList.remove('drag-armed');
                if (ghost) {
                    ghost.remove();
                    ghost = null;
                }
            }

            function moveTo(clientX, clientY) {
                if (ghost) {
                    ghost.style.left = clientX + 'px';
                    ghost.style.top = clientY + 'px';
                }
                if (opts.onMove) opts.onMove(opts.data, clientX, clientY);
            }

            function startDrag(clientX, clientY) {
                dragging = true;
                // 포인터를 캡처해야 요소 밖으로 나가도 이벤트가 계속 온다.
                el.setPointerCapture(e.pointerId);
                // 지도 핸들바처럼 요소 자체가 따라 움직이는 경우엔 고스트가 방해된다.
                if (!opts.noGhost) {
                    ghost = el.cloneNode(true);
                    ghost.classList.add('drag-ghost');
                    ghost.style.width = el.offsetWidth + 'px';
                    document.body.appendChild(ghost);
                }
                if (holdToDrag) {
                    // 손가락이 화면을 가려 고스트가 안 보일 수 있다. 진동과 원본 흐려짐으로
                    // '집혔다'를 알린다. 고스트를 복제한 뒤에 흐려야 고스트까지 흐려지지 않는다.
                    unblockScroll = blockTouchScroll();
                    el.classList.add('drag-armed');
                    if (navigator.vibrate) navigator.vibrate(15);
                }
                if (opts.onStart) opts.onStart(opts.data, startX, startY);
                moveTo(clientX, clientY);
            }

            function onPointerMove(ev) {
                if (dragging) {
                    moveTo(ev.clientX, ev.clientY);
                    return;
                }

                const moved = Math.hypot(ev.clientX - startX, ev.clientY - startY);
                if (holdToDrag) {
                    // 꾹 누르기가 끝나기 전에 움직였으면 옮기려는 게 아니라 넘기려는 것이다
                    if (moved > HOLD_TOLERANCE_PX) cancelHold();
                    return;
                }
                if (moved < DRAG_THRESHOLD_PX) return;
                startDrag(ev.clientX, ev.clientY);
            }

            function onPointerUp(ev) {
                const wasDragging = dragging;
                const moved = Math.hypot(ev.clientX - startX, ev.clientY - startY);
                cleanup();

                if (wasDragging) {
                    // pointercancel 은 드롭이 아니라 취소다.
                    if (ev.type === 'pointerup' && opts.onDrop) {
                        opts.onDrop(opts.data, ev.clientX, ev.clientY);
                    } else if (opts.onCancel) {
                        opts.onCancel(opts.data);
                    }
                } else if (ev.type === 'pointerup' && moved <= tapSlop && opts.onTap) {
                    opts.onTap(opts.data);
                }
            }

            el.addEventListener('pointermove', onPointerMove);
            el.addEventListener('pointerup', onPointerUp);
            el.addEventListener('pointercancel', onPointerUp);

            if (holdToDrag) {
                // 누른 자리에서 시작한다. 여기까지 왔다면 손가락은 거의 안 움직였다.
                holdTimer = setTimeout(() => {
                    holdTimer = null;
                    startDrag(startX, startY);
                }, HOLD_MS);
            }
        });
    }

    return { makeDraggable };
})();
