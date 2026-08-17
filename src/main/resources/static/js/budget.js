// 예산 섹션 라우팅. shell.js 와 같은 방식으로 탭을 옮길 때 서버로 가지 않고 주소만 바꾼다.
// 여기엔 지도가 없어 지킬 게 없지만, 탭을 누를 때마다 페이지를 새로 받을 이유도 없다.
(function () {
    const TABS = ['summary', 'list'];
    const DEFAULT_TAB = 'summary';
    const BASE = '/budget';   // BudgetPageController 의 @GetMapping("/budget/{tab}") 과 같아야 한다

    let current = null;

    const VIEWS = {
        summary: window.ViewSummary,
        list: window.ViewList,
    };

    function tabFromPath() {
        const last = window.location.pathname.replace(/\/+$/, '').split('/').pop();
        return TABS.includes(last) ? last : DEFAULT_TAB;
    }

    function pathOf(tab) {
        return BASE + '/' + tab;
    }

    function show(tab) {
        TABS.forEach((t) => {
            document.getElementById('view-' + t).hidden = t !== tab;
        });
        document.querySelectorAll('footer nav a[data-tab]').forEach((a) => {
            a.classList.toggle('active', a.dataset.tab === tab);
        });
        current = tab;
        VIEWS[tab].show();
    }

    function route() {
        const tab = tabFromPath();
        show(tab);
        if (window.location.pathname !== pathOf(tab)) {
            history.replaceState({}, '', pathOf(tab));
        }
    }

    function navigate(tab) {
        if (window.location.pathname === pathOf(tab)) return;
        history.pushState({}, '', pathOf(tab));
        route();
    }

    window.addEventListener('popstate', route);

    // 링크는 진짜 주소를 그대로 두고(새 탭·복사가 되도록) 클릭만 가로챈다.
    // 화살표에는 data-tab 이 없어 여기 걸리지 않고 페이지를 옮긴다.
    document.querySelectorAll('footer nav a[data-tab]').forEach((a) => {
        a.addEventListener('click', (e) => {
            e.preventDefault();
            navigate(a.dataset.tab);
        });
    });

    // 데이터가 바뀌었을 때 지금 탭을 다시 그리게 하는 통로
    window.BudgetSection = {
        refresh: () => { if (current) VIEWS[current].show(); },
        currentTab: () => current,
    };

    // 각 뷰의 한 번뿐인 초기화(이벤트 바인딩)를 먼저 돌린다
    Object.values(VIEWS).forEach((v) => v.init());

    route();
})();
