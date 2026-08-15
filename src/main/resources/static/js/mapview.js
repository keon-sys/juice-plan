// 지도 인스턴스를 하나만 만들고 뷰들이 공유한다.
// 뷰가 바뀌면 마커/선만 다시 그리고 지도 자체는 그대로 둔다.
window.MapView = (function () {
    let map = null;
    let markers = [];
    let path = null;
    let previewMarker = null;
    let onBoundsChanged = null;

    const FOOD_ICON = { url: 'https://maps.google.com/mapfiles/ms/icons/red-dot.png' };
    const PREVIEW_ICON = { url: 'https://maps.google.com/mapfiles/ms/icons/yellow-dot.png' };

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

    /** 소스마다 핀을 찍는다. numbered=true 면 1,2,3… 라벨을 붙인다. */
    function showPins(sources, numbered) {
        if (!isReady()) return;
        sources.forEach((s, i) => {
            markers.push(new google.maps.Marker({
                position: { lat: s.latitude, lng: s.longitude },
                map,
                title: s.name,
                label: numbered ? String(i + 1) : undefined,
                icon: (!numbered && s.placeType === 'RESTAURANT') ? FOOD_ICON : undefined,
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

    function fitTo(sources) {
        if (!isReady() || sources.length === 0) return;
        const bounds = new google.maps.LatLngBounds();
        sources.forEach((s) => bounds.extend({ lat: s.latitude, lng: s.longitude }));
        map.fitBounds(bounds);
    }

    function focus(source) {
        if (!isReady()) return;
        map.panTo({ lat: source.latitude, lng: source.longitude });
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
            icon: PREVIEW_ICON,
            zIndex: 999,
        });
        map.panTo({ lat, lng });
    }

    function clearPreview() {
        if (previewMarker) {
            previewMarker.setMap(null);
            previewMarker = null;
        }
    }

    /** 지금 지도에 보이는 영역 안의 소스만 남긴다. 지도가 아직 없으면 전부 통과시킨다. */
    function withinBounds(sources) {
        if (!isReady()) return sources;
        const bounds = map.getBounds();
        if (!bounds) return sources;
        return sources.filter((s) => bounds.contains({ lat: s.latitude, lng: s.longitude }));
    }

    function setBoundsListener(fn) {
        onBoundsChanged = fn;
    }

    /** 지도를 가렸다 되살릴 때 타일이 깨지지 않게 리사이즈를 알린다. */
    function refresh() {
        if (!isReady()) return;
        google.maps.event.trigger(map, 'resize');
    }

    return {
        init, isReady, clear, showPins, showRoute, fitTo, focus,
        showPreview, clearPreview, withinBounds, setBoundsListener, refresh,
    };
})();
