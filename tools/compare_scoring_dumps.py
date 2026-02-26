#!/usr/bin/env python3
"""
Comparador de dumps de scoring (cliente vs servidor).
Uso: python compare_scoring_dumps.py client.json server.json
El script busca el array `debug_scoring_dump` dentro de cada JSON (o acepta que el propio archivo sea ese array)
Alinea por campo `time` y compara weather, derived, waterTempForScoring y resultado (overall, confidence).
Imprime la primera diferencia encontrada y un resumen con conteos.

Salida: por pantalla y genera `scoring_compare_report.txt` con detalles.
"""
import sys
import json
from datetime import datetime
from math import isfinite

TOLERANCES = {
    'temperature': 0.5,
    'pressure': 1.0,
    'humidity': 1.0,
    'windSpeed': 0.5,
    'precipitation': 0.1,
    'deltaPressure1h': 1.0,
    'deltaPressure3hAvg': 1.0,
    'deltaTemp1h': 0.5,
    'rainPrev6h': 0.1,
    'rainSum24h': 0.1,
    'windStability3h': 0.05,
    'waterTempForScoring': 0.2,
    'confidence': 0.01
}


def load_dump(path):
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    # if top-level has data.debug_scoring_dump
    if isinstance(data, dict) and 'debug_scoring_dump' in data:
        arr = data['debug_scoring_dump']
    elif isinstance(data, dict) and 'data' in data and isinstance(data['data'], dict) and 'debug_scoring_dump' in data['data']:
        arr = data['data']['debug_scoring_dump']
    elif isinstance(data, list):
        arr = data
    else:
        # try to locate array by scanning
        arr = None
        if isinstance(data, dict):
            for k, v in data.items():
                if isinstance(v, list) and len(v) > 0 and isinstance(v[0], dict) and 'time' in v[0]:
                    arr = v
                    break
        if arr is None:
            raise ValueError(f"No debug_scoring_dump array found in {path}")
    # normalize each entry: expect { input: {...}, result: {...} } or flat objects
    normalized = []
    for e in arr:
        if isinstance(e, dict) and 'input' in e and 'result' in e:
            inp = e['input'] or {}
            res = e['result'] or {}
        else:
            inp = e.get('weather') if isinstance(e, dict) else {}
            res = {k: e.get(k) for k in ('activity_overall', 'activity_confidence')} if isinstance(e, dict) else {}
        # allow embedded input/derived
        entry = {
            'time': (inp.get('time') or e.get('time')),
            'weather': inp.get('weather') or e.get('weather') or {},
            'derived': inp.get('derived') or e.get('derived') or {},
            'waterTempForScoring': inp.get('waterTempForScoring') if 'waterTempForScoring' in inp else (e.get('waterTempForScoring') if isinstance(e, dict) else None),
            'overall': res.get('overall') or res.get('activity_overall') or e.get('activity_overall') or None,
            'confidence': res.get('confidence') or res.get('activity_confidence') or e.get('activity_confidence') or None
        }
        normalized.append(entry)
    return normalized


def parse_time_to_epoch(tstr):
    if not tstr: return None
    # try ISO
    try:
        # handle possible missing timezone
        dt = datetime.fromisoformat(tstr.replace('Z', '+00:00'))
        return dt.timestamp()
    except Exception:
        try:
            # fallback parse common format
            dt = datetime.strptime(tstr[:19], '%Y-%m-%dT%H:%M:%S')
            return dt.timestamp()
        except Exception:
            return None


def approx_equal(a, b, tol):
    if a is None or b is None: return a == b
    try:
        if not (isfinite(a) and isfinite(b)): return a == b
    except Exception:
        return a == b
    return abs(a - b) <= tol


def compare_entries(c_entry, s_entry):
    diffs = []
    # compare weather keys
    wkeys = ['temperature','pressure','humidity','windSpeed','precipitation']
    for k in wkeys:
        ca = c_entry['weather'].get(k)
        sa = s_entry['weather'].get(k)
        tol = TOLERANCES.get(k, 0.1)
        if ca is None and sa is None: continue
        if not approx_equal(float(ca) if ca is not None else None, float(sa) if sa is not None else None, tol):
            diffs.append((f'weather.{k}', ca, sa, tol))
    # derived
    dkeys = ['deltaPressure1h','deltaPressure3hAvg','deltaTemp1h','rainPrev6h','rainSum24h','windStability3h']
    for k in dkeys:
        ca = c_entry['derived'].get(k)
        sa = s_entry['derived'].get(k)
        tol = TOLERANCES.get(k, 0.1)
        if ca is None and sa is None: continue
        try:
            ca_f = float(ca) if ca is not None else None
        except Exception:
            ca_f = None
        try:
            sa_f = float(sa) if sa is not None else None
        except Exception:
            sa_f = None
        if not approx_equal(ca_f, sa_f, tol):
            diffs.append((f'derived.{k}', ca, sa, tol))
    # waterTempForScoring
    ca = c_entry.get('waterTempForScoring')
    sa = s_entry.get('waterTempForScoring')
    if not approx_equal(float(ca) if ca is not None else None, float(sa) if sa is not None else None, TOLERANCES['waterTempForScoring']):
        diffs.append(('waterTempForScoring', ca, sa, TOLERANCES['waterTempForScoring']))
    # result overall
    if c_entry.get('overall') is not None or s_entry.get('overall') is not None:
        if int(c_entry.get('overall') or -9999) != int(s_entry.get('overall') or -9999):
            diffs.append(('overall', c_entry.get('overall'), s_entry.get('overall'), 0))
    # confidence
    if not approx_equal(float(c_entry.get('confidence') or 0.0), float(s_entry.get('confidence') or 0.0), TOLERANCES['confidence']):
        diffs.append(('confidence', c_entry.get('confidence'), s_entry.get('confidence'), TOLERANCES['confidence']))
    return diffs


def main():
    if len(sys.argv) < 3:
        print('Usage: python compare_scoring_dumps.py client.json server.json')
        sys.exit(2)
    client_path = sys.argv[1]
    server_path = sys.argv[2]
    client = load_dump(client_path)
    server = load_dump(server_path)
    # index server by time epoch
    s_index = {}
    for e in server:
        epoch = parse_time_to_epoch(e.get('time'))
        if epoch is None:
            # fallback to raw string
            s_index[e.get('time')] = e
        else:
            s_index[str(int(epoch))] = e
    report_lines = []
    mismatches = 0
    total = 0
    first_mismatch = None
    for c in client:
        total += 1
        # try find matching server by parsing time
        c_epoch = parse_time_to_epoch(c.get('time'))
        s_match = None
        if c_epoch is not None:
            s_match = s_index.get(str(int(c_epoch)))
        if s_match is None:
            # try by raw time key
            s_match = s_index.get(c.get('time'))
            if s_match is None:
                # try find server entry with same time string
                for v in server:
                    if v.get('time') == c.get('time'):
                        s_match = v; break
        if s_match is None:
            report_lines.append(f"No matching server entry for client time={c.get('time')}")
            mismatches += 1
            if first_mismatch is None:
                first_mismatch = ('missing', c, None)
            continue
        diffs = compare_entries(c, s_match)
        if diffs:
            mismatches += 1
            if first_mismatch is None:
                first_mismatch = (diffs, c, s_match)
            report_lines.append(f"DIFF time={c.get('time')} diffs={diffs}")
        else:
            report_lines.append(f"OK time={c.get('time')}")
    summary = f"Compared {total} entries. Mismatches: {mismatches}."
    print(summary)
    if first_mismatch:
        print('\nFirst mismatch:')
        diffs, c, s = first_mismatch
        if diffs == 'missing':
            print('Client entry missing match on server:', c)
        else:
            print('Client:', json.dumps(c, indent=2, ensure_ascii=False))
            print('Server:', json.dumps(s, indent=2, ensure_ascii=False))
            print('Diffs:', diffs)
    # write full report
    with open('scoring_compare_report.txt', 'w', encoding='utf-8') as rf:
        rf.write(summary + "\n\n")
        for l in report_lines:
            rf.write(l + "\n")
    print('Report saved to scoring_compare_report.txt')

if __name__ == '__main__':
    main()
