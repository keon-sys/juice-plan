(function () {
    const MAP_HEIGHT = 200;   // style.css 의 #map-panel height 와 같아야 한다
    const TABS = ['add', 'plan', 'day'];
    const DEFAULT_TAB = 'day';

    const sheetArea = document.getElementById('sheet-area');
    const handle = document.getElementById('map-handle');

    let current = null;
    let collapsed = false;    // true = 뷰가 지도를 덮은 상태

    const VIEWS = {
        add: window.ViewAdd,
        plan: window.ViewPlan,
        day: window.ViewDay,
    };

    // ---- 지도 접기 ----
    // 지도를 display:none 하거나 높이를 0으로 만들지 않는다. 구글맵은 크기가 0이 되면
    // 타일 로딩이 깨진다. 대신 뷰 영역을 위로 올려 덮는다.
    function applyOffset(px, animate) {
        sheetArea.style.transition = animate ? 'transform .25s ease' : 'none';
        sheetArea.style.transform = `translateY(${px}px)`;
    }

    function setCollapsed(next) {
        collapsed = next;
        applyOffset(collapsed ? -MAP_HEIGHT : 0, true);
        handle.setAttribute('aria-label', collapsed ? '지도 펴기' : '지도 접기');
        if (!collapsed) {
            // 다시 보이게 됐을 때 타일이 깨지지 않도록 알린다
            setTimeout(() => window.MapView.refresh(), 260);
        }
    }

    let dragStartOffset = 0;
    let dragOriginY = 0;

    function offsetDuring(clientY) {
        const next = dragStartOffset + (clientY - dragOriginY);
        return Math.max(-MAP_HEIGHT, Math.min(0, next));
    }

    window.DragDrop.makeDraggable(handle, {
        data: null,
        noGhost: true,
        onStart: (_, startX, startY) => {
            dragStartOffset = collapsed ? -MAP_HEIGHT : 0;
            dragOriginY = startY;
        },
        onMove: (_, x, y) => applyOffset(offsetDuring(y), false),
        // 절반을 넘겼는지로 붙일 쪽을 정한다
        onDrop: (_, x, y) => setCollapsed(offsetDuring(y) < -MAP_HEIGHT / 2),
        onCancel: () => setCollapsed(collapsed),
        onTap: () => setCollapsed(!collapsed),
    });

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
    route();
})();
