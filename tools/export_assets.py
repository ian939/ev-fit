# EVUSERS DB에서 EV FIT 앱 자산(요금·충전소·차종 JSON)을 만드는 스크립트
# 사용: python -X utf8 tools/export_assets.py  (EVUSERS DB는 읽기 전용으로 연다)
import json
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]                       # ev-fit
DB = Path(__file__).resolve().parents[3] / "12. EVUSERS" / "data" / "evusers.db"
OUT = ROOT / "app" / "src" / "main" / "assets" / "data"
FAST_STAGES = ("s3", "s4", "s5")


def write(name: str, data) -> None:
    (OUT / name).write_text(
        json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )


def canon_brand(brand: str, model: str) -> str:
    if model.startswith(("GV", "ELECTRIFIED")):
        return "제네시스"
    return {"현대자동차": "현대", "기아 주식회사": "기아"}.get(brand, brand)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(DB.as_uri() + "?mode=ro", uri=True)

    snap_id, collected_at = con.execute(
        "SELECT id, collected_at FROM snapshots WHERE source='moe' ORDER BY id DESC LIMIT 1"
    ).fetchone()
    self_prices, roaming = {}, {}
    for charger, card, stage, price in con.execute(
        "SELECT charger_bid, card_bid, stage, price_krw_kwh FROM current_prices WHERE price_krw_kwh > 0"
    ):
        if card is None:
            self_prices[f"{charger}|{stage}"] = price
        else:
            roaming[f"{card}|{charger}|{stage}"] = price
    write("prices.json", {"snapshotId": snap_id, "collectedAt": collected_at, "self": self_prices, "roaming": roaming})

    # 충전소: 급속(s3~s5) 충전기가 하나 이상 있는 곳만
    stages = {}
    for stat_id, stage in con.execute(
        f"SELECT DISTINCT stat_id, stage FROM station_chargers WHERE stage IN {FAST_STAGES}"
    ):
        stages.setdefault(stat_id, set()).add(stage)
    ym = con.execute("SELECT ym FROM station_snapshots ORDER BY ym DESC LIMIT 1").fetchone()[0]
    rows = []
    for stat_id, name, addr, lat, lng, bid, max_kw, kind_detail in con.execute(
        "SELECT stat_id, name, addr, lat, lng, busi_id, max_output, kind_detail "
        "FROM stations WHERE n_fast > 0 AND busi_id IS NOT NULL ORDER BY stat_id"
    ):
        if stat_id not in stages:
            continue
        rows.append([
            stat_id, (name or "").strip(), (addr or "").strip(), round(lat, 6), round(lng, 6),
            bid, max_kw or 0, sorted(stages[stat_id]), kind_detail == "고속도로 휴게소",
        ])
    write("stations.json", {
        "snapshotYm": ym,
        "fields": ["id", "name", "addr", "lat", "lng", "bid", "maxKw", "stages", "highway"],
        "rows": rows,
    })

    fast_counts = {}
    for bid, n in con.execute(
        "SELECT busi_id, COUNT(*) FROM stations WHERE n_fast > 0 AND busi_id IS NOT NULL GROUP BY busi_id"
    ):
        fast_counts[bid] = n
    operators = [
        {"bid": bid, "name": name, "fastStations": fast_counts.get(bid, 0)}
        for bid, name in con.execute("SELECT bid, name FROM operators ORDER BY bid")
    ]
    write("operators.json", operators)

    vehicles = [
        {"model": model, "brand": canon_brand(brand, model), "batteryKwh": kwh, "kmPerKwh": eff}
        for brand, model, kwh, eff in con.execute(
            "SELECT brand, model, battery_kwh, efficiency_km_kwh FROM vehicles "
            "WHERE brand IN ('현대자동차','기아','기아 주식회사') AND battery_kwh IS NOT NULL ORDER BY model"
        )
    ]
    write("vehicles.json", vehicles)
    con.close()

    print(
        f"요금 스냅샷 {snap_id}({collected_at}) 회원가 {len(self_prices)} / 로밍 {len(roaming)} / "
        f"사업자 {len(operators)} / 급속 충전소 {len(rows)} / 차종 {len(vehicles)}"
    )


if __name__ == "__main__":
    main()
