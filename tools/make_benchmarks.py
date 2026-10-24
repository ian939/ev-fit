# EV FIT 리포트용 동일 차종 충전 통계 합성 데이터 생성(시연용 가정치, 실제 통계 아님)
# 사용: python -X utf8 tools/make_benchmarks.py  (export_assets.py가 만든 vehicles.json을 읽는다)
import json
import random
from pathlib import Path

DATA = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "data"
CLASSES = [50, 100, 200, 350]


def peak_kw(model: str) -> int:
    m = model.replace(" ", "")
    if any(k in m for k in ("아이오닉5", "아이오닉6", "아이오닉9", "GV60", "EV6", "EV9", "ELECTRIFIEDG80", "ELECTRIFIEDGV70")):
        return 230   # 800V 계열 가정
    if "레이" in m:
        return 50
    return 110


def f(mid: float) -> float:
    """SOC 구간 중앙값에 따른 출력 비율(감소 곡선 가정)"""
    if mid <= 50:
        return 1.0
    if mid <= 80:
        return 1.0 - (mid - 50) / 30 * 0.55
    return 0.45 - (mid - 80) / 20 * 0.30


def median_kw(peak: int, charger_kw: int, mid: float) -> float:
    return round(min(charger_kw * 0.95, peak * f(mid)), 1)


def main() -> None:
    vehicles = json.loads((DATA / "vehicles.json").read_text(encoding="utf-8"))
    models = []
    for v in vehicles:
        model = v["model"]
        peak = peak_kw(model)
        rng = random.Random(f"{model}-42")
        bands, avg = {}, {}
        for c in CLASSES:
            rows = []
            for start in range(0, 100, 10):
                med = median_kw(peak, c, start + 5)
                rows.append({"from": start, "to": start + 10, "p25": round(med * 0.88, 1), "median": med, "p75": round(med * 1.06, 1)})
            bands[str(c)] = rows
            mids = [r["median"] for r in rows if 10 <= r["from"] <= 70]
            avg[str(c)] = round(sum(mids) / len(mids), 1)
        models.append({
            "model": model,
            "peakKw": peak,
            "members": rng.randint(300, 3000),
            "successRatePct": round(rng.uniform(97.0, 99.6), 1),
            "satisfaction": round(rng.uniform(4.3, 4.9), 1),
            "avgKwByClass": avg,
            "bands": bands,
        })
    out = {"synthetic": True, "note": "시연용 합성 데이터 — 공식 제원·실측 통계 아님", "models": models}
    (DATA / "benchmarks.json").write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"benchmarks.json — 차종 {len(models)}개")


if __name__ == "__main__":
    main()
