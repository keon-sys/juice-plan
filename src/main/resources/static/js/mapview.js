// 지도 인스턴스를 하나만 만들고 뷰들이 공유한다.
// 뷰가 바뀌면 마커/선만 다시 그리고 지도 자체는 그대로 둔다.
window.MapView = (function () {
    let map = null;
    let markers = [];
    let path = null;
    let previewMarker = null;
    let onBoundsChanged = null;
    // 지도는 화면 전체를 쓰고 시트가 그 위를 덮는다. 시트에 가려진 아래쪽 높이를
    // 셸이 알려주면, 핀을 맞출 때 그만큼을 빼고 보이는 띠 안으로 올린다.
    let hiddenBottomPx = 0;

    const FIT_MARGIN = 24;
    const MIN_VISIBLE_PX = 160;   // 시트가 지도를 다 덮어도 이만큼은 남은 셈 치고 계산한다

    // ---- 핀 모양 ----
    // 구글 기본 물방울 핀 대신 단순한 원을 쓴다. 겹쳐도 덜 지저분하고,
    // 배정된 곳에는 순번을 얹을 자리가 생긴다.
    //
    //   배정됨   — 타입 색으로 꽉 채운 큰 원 + 흰 순번
    //   미배정   — 같은 색 테두리만 두른 작은 빈 원 (아직 자리를 못 잡았다는 뜻)

    /** 색은 CSS 토큰에서 읽는다. 다크모드 전환도 저절로 따라온다. */
    function token(name) {
        return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    }

    function colorOf(source) {
        return token(source.placeType === 'RESTAURANT' ? '--food' : '--attraction');
    }

    function circle(fill, stroke, strokeWeight, scale) {
        return {
            path: google.maps.SymbolPath.CIRCLE,
            fillColor: fill,
            fillOpacity: 1,
            strokeColor: stroke,
            strokeWeight,
            scale,
        };
    }

    function isReady() {
        return map !== null;
    }

    function init(elementId, sources) {
        map = new google.maps.Map(document.getElementById(elementId), {
            center: {
                lat: sources[0] ? sources[0].latitude : 37.5665,
                lng: sources[0] ? sources[0].longitude : 126.9780,
            },
            zoom: 13,
            disableDefaultUI: true,
            zoomControl: true,
            // 지도 바닥은 시트에 가려 있다. 줌 컨트롤을 위로 붙여 늘 보이게 한다.
            zoomControlOptions: { position: google.maps.ControlPosition.RIGHT_TOP },
        });

        // 셸이 시트 단계를 먼저 정한 뒤에 지도가 만들어진다. 그래서 첫 화면에서는
        // 지도가 중앙 정렬된 채로 뜨고 핀이 시트 뒤에 숨는다. 지도가 자리를 잡으면
        // 지금 가려진 만큼을 한 번 반영해 핀을 보이는 띠로 끌어올린다.
        google.maps.event.addListenerOnce(map, 'idle', () => {
            const px = effectiveHiddenBottom();
            if (px > 0) map.panBy(0, px / 2);
        });

        map.addListener('bounds_changed', debounce(() => {
            if (onBoundsChanged) onBoundsChanged();
        }, 300));
    }

    function debounce(fn, wait) {
        let timeout;
        return (...args) => {
            clearTimeout(timeout);
            timeout = setTimeout(() => fn(...args), wait);
        };
    }

    function clear() {
        markers.forEach((m) => m.setMap(null));
        markers = [];
        if (path) {
            path.setMap(null);
            path = null;
        }
    }

    /**
     * 소스마다 핀을 찍는다. numbered=true 면 넘긴 순서대로 1,2,3… 을 얹는다.
     * 시각순으로 정렬해 넘기면 타임테이블·동선 선과 번호가 그대로 맞는다.
     *
     * 여러 번 불러도 쌓이므로, 미배정과 배정을 다른 모양으로 찍으려면
     * 두 번 나눠 부르면 된다.
     */
    function showPins(sources, numbered) {
        if (!isReady()) return;
        const ring = token('--surface');
        const labelColor = token('--on-accent');

        sources.forEach((s, i) => {
            const color = colorOf(s);
            markers.push(new google.maps.Marker({
                position: { lat: s.latitude, lng: s.longitude },
                map,
                title: s.name,
                icon: numbered ? circle(color, ring, 2, 13) : circle(ring, color, 3, 7),
                label: numbered
                    ? { text: String(i + 1), color: labelColor, fontSize: '12px', fontWeight: '700' }
                    : undefined,
                // 번호 핀이 미배정 핀에 가리지 않게 한 겹 위로 올린다
                zIndex: numbered ? 2 : 1,
            }));
        });
    }

    /** 순서대로 선으로 잇는다. 두 곳 미만이면 그리지 않는다. */
    function showRoute(sources) {
        if (!isReady() || sources.length < 2) return;
        path = new google.maps.Polyline({
            path: sources.map((s) => ({ lat: s.latitude, lng: s.longitude })),
            map,
            strokeOpacity: 0.8,
            strokeWeight: 3,
        });
    }

    /**
     * 시트에 가려진 아래쪽 높이(px). 셸이 핸들 단계를 바꿀 때마다 알려준다.
     *
     * 여백(fitPadding)만으로는 fitTo/focus 를 새로 부를 때만 핀이 제자리를 찾는다.
     * 단계를 바꾸는 것만으로도 핀이 보이는 띠 안에 들어와야 하므로, 띠가 줄거나
     * 늘어난 만큼의 절반을 지도에 직접 밀어준다. 그러면 띠 한가운데 있던 지점이
     * 계속 띠 한가운데 남는다 — 사용자가 직접 옮겨둔 위치도 지워지지 않는다.
     */
    function setHiddenBottom(px) {
        const before = effectiveHiddenBottom();
        hiddenBottomPx = Math.max(0, px);
        const after = effectiveHiddenBottom();
        if (isReady() && after !== before) map.panBy(0, (after - before) / 2);
    }

    /**
     * 시트가 지도를 완전히 덮은 상태에서는 가려진 높이가 지도 높이와 같아진다.
     * 그대로 여백으로 넘기면 맞출 공간이 없어 지도가 엉뚱하게 축소되므로,
     * 최소한의 띠는 남아 있다고 치고 자른다.
     */
    function effectiveHiddenBottom() {
        const mapHeight = isReady() ? map.getDiv().offsetHeight : 0;
        if (mapHeight <= 0) return 0;
        return Math.min(hiddenBottomPx, Math.max(0, mapHeight - MIN_VISIBLE_PX));
    }

    /** fitBounds/panToBounds 에 넘길 여백. 아래쪽은 시트에 가린 만큼 더 준다. */
    function fitPadding() {
        return {
            top: FIT_MARGIN,
            right: FIT_MARGIN,
            left: FIT_MARGIN,
            bottom: FIT_MARGIN + effectiveHiddenBottom(),
        };
    }

    function fitTo(sources) {
        if (!isReady() || sources.length === 0) return;
        const bounds = new google.maps.LatLngBounds();
        sources.forEach((s) => bounds.extend({ lat: s.latitude, lng: s.longitude }));
        map.fitBounds(bounds, fitPadding());
    }

    function focus(source) {
        if (!isReady()) return;
        // panTo 는 지도 한가운데로 보내므로 시트 뒤에 숨는다. 한 점짜리 bounds 를
        // 패딩과 함께 넘겨 보이는 띠 안으로 올린다.
        const point = { lat: source.latitude, lng: source.longitude };
        const bounds = new google.maps.LatLngBounds();
        bounds.extend(point);
        map.panToBounds(bounds, fitPadding());
        const marker = markers.find((m) => m.getTitle() === source.name);
        if (marker) {
            marker.setAnimation(google.maps.Animation.BOUNCE);
            setTimeout(() => marker.setAnimation(null), 1400);
        }
    }

    /** 시트에서 위경도를 가져왔을 때 띄우는 임시 핀. 기존 핀과 색이 다르다. */
    function showPreview(lat, lng) {
        if (!isReady()) return;
        clearPreview();
        previewMarker = new google.maps.Marker({
            position: { lat, lng },
            map,
            // 아직 저장 전이라는 뜻으로 강조색 원. 다른 핀과 색으로 구분된다.
            icon: circle(token('--primary'), token('--surface'), 3, 10),
            zIndex: 999,
        });
        const bounds = new google.maps.LatLngBounds();
        bounds.extend({ lat, lng });
        map.panToBounds(bounds, fitPadding());
    }

    function clearPreview() {
        if (previewMarker) {
            previewMarker.setMap(null);
            previewMarker = null;
        }
    }

    /**
     * 시트에 가리지 않고 눈에 보이는 영역 안의 소스만 남긴다.
     * 지도가 아직 없으면 전부 통과시킨다.
     *
     * getBounds() 는 시트 뒤까지 포함한 지도 전체를 돌려주므로 아래쪽을 잘라낸다.
     * 위도를 픽셀 비율대로 선형 보간하는 건 메르카토르에서 정확하지 않지만,
     * 이 결과는 레일 정렬 순서에만 쓰이므로 이 정도면 충분하다.
     */
    function withinBounds(sources) {
        if (!isReady()) return sources;
        const bounds = map.getBounds();
        if (!bounds) return sources;

        const mapHeight = map.getDiv().offsetHeight;
        const south = bounds.getSouthWest().lat();
        const north = bounds.getNorthEast().lat();
        const visibleSouth = mapHeight > 0
            ? south + (north - south) * (effectiveHiddenBottom() / mapHeight)
            : south;

        return sources.filter((s) =>
            bounds.contains({ lat: s.latitude, lng: s.longitude }) && s.latitude >= visibleSouth);
    }

    function setBoundsListener(fn) {
        onBoundsChanged = fn;
    }

    // 지도 엘리먼트는 화면 전체에 고정돼 크기가 변하지 않으므로
    // 예전처럼 resize 를 직접 쏴줄 필요가 없다.

    return {
        init, isReady, clear, showPins, showRoute, fitTo, focus,
        showPreview, clearPreview, withinBounds, setBoundsListener, setHiddenBottom,
    };
})();
