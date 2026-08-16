(function () {
    const MAP_PEEK = 200;     // style.css 의 #sheet-area top(--map-h) 과 같아야 한다
    const HANDLE_PEEK = 52;   // 시트를 끝까지 내렸을 때 남겨둘 손잡이 높이
    const TABS = ['add', 'plan', 'day'];
    const DEFAULT_TAB = 'day';

    const sheetArea = document.getElementById('sheet-area');
    const handle = document.getElementById('map-handle');

    let current = null;

    const VIEWS = {
        add: window.ViewAdd,
        plan: window.ViewPlan,
        day: window.ViewDay,
    };

    // ---- 지도 3단 핸들 ----
    // 지도는 화면 전체에 깔려 있고 시트가 그 위를 덮는다. 지도 엘리먼트 자체는
    // 크기가 변하지 않는다. 구글맵은 크기가 0이 되면 타일 로딩이 깨지기 때문이다.
    //
    // 시트는 바닥이 탭바에 고정된 채 윗변(top)만 움직인다. 단계가 올라갈수록
    // 시트가 길어지고 지도가 덜 보인다.
    //   3단계 = 시트가 화면을 다 덮음
    //   2단계 = 지도 위쪽 200px 띠만 보임 (기본)
    //   1단계 = 손잡이만 남고 지도가 화면 전체
    const STEPS = [3, 2, 1];
    let step = 2;

    /** 시트 바닥과 화면 바닥 사이 거리(탭바 + 안전영역). CSS 로 고정돼 변하지 않는다. */
    function bottomInset() {
        return Math.max(0, window.innerHeight - sheetArea.getBoundingClientRect().bottom);
    }

    /** 각 단계에서 시트 윗변의 화면 좌표(px). */
    function topOf(n) {
        if (n === 3) return 0;
        if (n === 2) return MAP_PEEK;
        return Math.max(MAP_PEEK, window.innerHeight - bottomInset() - HANDLE_PEEK);
    }

    function applyTop(px, animate) {
        sheetArea.style.transition = animate ? 'top .25s ease' : 'none';
        sheetArea.style.top = px + 'px';
    }

    function setStep(next) {
        step = next;
        const top = topOf(step);
        applyTop(top, true);
        // 시트에 가린 지도 아래쪽 높이를 알려준다. 지도는 그만큼 핀을 위로 올린다.
        window.MapView.setHiddenBottom(window.innerHeight - top);
        handle.setAttribute('aria-label',
            step === 3 ? '지도 보기' : step === 2 ? '지도 크게 보기' : '지도 접기');
        // 보이는 지도 범위가 달라졌으므로 미배정 레일도 다시 나눈다
        if (current === 'plan') window.ViewPlan.onBoundsChanged();
    }

    /** 드래그를 놓은 위치에서 가장 가까운 단계로 붙인다. */
    function nearestStep(top) {
        return STEPS.reduce((best, n) =>
            Math.abs(topOf(n) - top) < Math.abs(topOf(best) - top) ? n : best);
    }

    let dragStartTop = 0;
    let dragOriginY = 0;

    function topDuring(clientY) {
        const next = dragStartTop + (clientY - dragOriginY);
        return Math.max(topOf(3), Math.min(topOf(1), next));
    }

    window.DragDrop.makeDraggable(handle, {
        data: null,
        noGhost: true,
        onStart: (_, startX, startY) => {
            dragStartTop = topOf(step);
            dragOriginY = startY;
        },
        // 끄는 동안은 시트만 따라온다. 지도를 매 프레임 옮기면 눈이 어지럽고,
        // 손을 뗄 때 setStep 이 한 번에 맞춰준다.
        onMove: (_, x, y) => applyTop(topDuring(y), false),
        onDrop: (_, x, y) => setStep(nearestStep(topDuring(y))),
        onCancel: () => setStep(step),
        // 탭은 3 → 2 → 1 → 3 으로 한 칸씩 내려간다
        onTap: () => setStep(STEPS[(STEPS.indexOf(step) + 1) % STEPS.length]),
    });

    // 주소창이 접히거나 화면이 돌아가면 1단계 위치와 지도 여백이 달라진다
    window.addEventListener('resize', () => setStep(step));

    // ---- 해시 라우팅 ----
    function tabFromHash() {
        const hash = window.location.hash.replace('#', '');
        return TABS.includes(hash) ? hash : DEFAULT_TAB;
    }

    function show(tab) {
        // 여행 기간이 없으면 나머지 두 뷰가 그릴 게 없다
        const effective = (!window.TRIP && tab !== 'add') ? 'add' : tab;

        TABS.forEach((t) => {
            document.getElementById(`view-${t}`).hidden = t !== effective;
        });
        document.querySelectorAll('footer nav a').forEach((a) => {
            a.classList.toggle('active', a.dataset.tab === effective);
        });
        document.getElementById('addSourceBtn').hidden = effective !== 'add';

        current = effective;
        VIEWS[effective].show();
    }

    function route() {
        show(tabFromHash());
    }

    window.addEventListener('hashchange', route);

    // ---- 뷰가 데이터를 바꿨을 때 현재 뷰를 다시 그리게 하는 통로 ----
    window.Shell = {
        refresh: () => {
            if (current) VIEWS[current].show();
        },
        currentTab: () => current,
    };

    // ---- 지도 준비 ----
    window.initMap = function () {
        window.MapView.init('map', window.SOURCES);
        window.MapView.setBoundsListener(() => {
            // 지도를 움직이면 동선 변경의 왼쪽 레일만 다시 걸러진다
            if (current === 'plan') window.ViewPlan.onBoundsChanged();
        });
        if (current) VIEWS[current].show();
    };

    // 각 뷰의 한 번뿐인 초기화(이벤트 바인딩)를 먼저 돌린다
    Object.values(VIEWS).forEach((v) => v.init());
    setStep(step);   // 시작 위치를 잡고 지도에 가려진 높이를 알려준다
    route();
})();
