# Natural Earth 육지·시도 경계와 OSRM 경로를 잘라·단순화해 앱 지도 자산(basemap.json, route_demo.json)을 만든다
# 사용: python -I -X utf8 tools/make_mapdata.py <ne_10m_land.shp> <ne_10m_admin_1_states_provinces.shp> <osrm.json>
import json
import sys
from pathlib import Path

import shapefile
from shapely.geometry import LineString, MultiLineString, Polygon, box, shape
from shapely.ops import unary_union

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/data"
# 경기·충청 회랑 주변(서해안 포함)
MIN_LNG, MIN_LAT, MAX_LNG, MAX_LAT = 126.2, 35.9, 128.2, 37.9
SIMPLIFY_DEG = 0.002   # 약 200m
ROUTE_SIMPLIFY_DEG = 0.0002  # 약 20m

# 지도 글자(시·도청·시청 대략 좌표) — 경로 주변 도시만
LABELS = [
    ("서울", 37.5665, 126.9780), ("수원", 37.2636, 127.0286), ("용인", 37.2411, 127.1776),
    ("평택", 36.9921, 127.1129), ("안성", 37.0080, 127.2797), ("천안", 36.8151, 127.1139),
    ("아산", 36.7898, 127.0019), ("세종", 36.4800, 127.2890), ("청주", 36.6424, 127.4890),
    ("대전", 36.3504, 127.3845),
]


def rings(geom):
    """Polygon/MultiPolygon의 바깥 고리만 [[lat,lng],...] 목록으로"""
    polys = [geom] if isinstance(geom, Polygon) else list(getattr(geom, "geoms", []))
    out = []
    for p in polys:
        if p.is_empty or p.area < 1e-4:
            continue
        out.append([[round(y, 4), round(x, 4)] for x, y in p.exterior.coords])
    return out


def lines(geom):
    parts = [geom] if isinstance(geom, LineString) else list(getattr(geom, "geoms", []))
    return [[[round(y, 4), round(x, 4)] for x, y in g.coords] for g in parts if isinstance(g, LineString) and len(g.coords) > 1]


def main(land_shp: str, admin_shp: str, osrm_json: str) -> None:
    bbox = box(MIN_LNG, MIN_LAT, MAX_LNG, MAX_LAT)

    land = unary_union([shape(s.__geo_interface__) for s in shapefile.Reader(land_shp).shapes()
                        if box(*s.bbox).intersects(bbox)]).intersection(bbox).simplify(SIMPLIFY_DEG)

    r = shapefile.Reader(admin_shp, encoding="utf-8")
    fields = [f[0] for f in r.fields[1:]]
    i_adm = fields.index("adm0_a3")
    provinces = [shape(sr.shape.__geo_interface__) for sr in r.iterShapeRecords() if sr.record[i_adm] == "KOR"]
    # 시도 경계선 = 각 시도 경계의 합에서 해안선(육지 경계)을 뺀 내륙 경계
    boundaries = unary_union([p.boundary for p in provinces]).intersection(bbox)
    coast = land.boundary.buffer(0.004)
    inland = boundaries.difference(coast).simplify(SIMPLIFY_DEG)
    if isinstance(inland, LineString):
        inland = MultiLineString([inland])

    basemap = {
        "source": "Natural Earth 10m (public domain)",
        "bbox": [MIN_LAT, MIN_LNG, MAX_LAT, MAX_LNG],
        "land": rings(land),
        "borders": lines(inland),
        "labels": [{"name": n, "lat": la, "lng": ln} for n, la, ln in LABELS],
    }

    osrm = json.loads(Path(osrm_json).read_text(encoding="utf-8"))
    rt = osrm["routes"][0]
    coords = rt["geometry"]["coordinates"]
    simple = LineString(coords).simplify(ROUTE_SIMPLIFY_DEG, preserve_topology=False)
    stop = osrm["waypoints"][1]["location"]
    route = {
        "source": "OSRM, © OpenStreetMap contributors",
        "from": "양재", "to": "정부대전청사",
        "distanceKm": round(rt["distance"] / 1000, 1),
        "legsKm": [round(l["distance"] / 1000, 1) for l in rt["legs"]],
        "stop": [round(stop[1], 6), round(stop[0], 6)],
        "points": [[round(y, 5), round(x, 5)] for x, y in simple.coords],
    }

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "basemap.json").write_text(json.dumps(basemap, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    (OUT / "route_demo.json").write_text(json.dumps(route, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("land rings", len(basemap["land"]), "pts", sum(len(x) for x in basemap["land"]))
    print("borders", len(basemap["borders"]), "pts", sum(len(x) for x in basemap["borders"]))
    print("route pts", len(route["points"]), "of", len(coords), route["distanceKm"], "km", route["legsKm"])


if __name__ == "__main__":
    main(*sys.argv[1:4])
