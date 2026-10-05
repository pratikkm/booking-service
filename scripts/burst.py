#!/usr/bin/env python3
import json
import os
import sys
import time
import uuid
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib import request, error

BASE_URL = sys.argv[1].rstrip('/') if len(sys.argv) > 1 else os.getenv('BASE_URL', 'http://localhost:8080')
REQUESTS = int(os.getenv('REQUESTS', '20000'))
CONCURRENCY = int(os.getenv('CONCURRENCY', '200'))
PRICE_PAISE = 25000
SEATS = [f'A{i}' for i in range(1, 51)]
HOT_SEAT = os.getenv('HOT_SEAT', 'A1')


def call(method, path, body=None, token=None, timeout=60):
    data = None if body is None else json.dumps(body).encode()
    headers = {'Accept': 'application/json'}
    if data is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = f'Bearer {token}'
    req = request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    started = time.time()
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode()
            try:
                parsed = json.loads(raw)
            except json.JSONDecodeError:
                parsed = None
            return resp.status, parsed, time.time() - started
    except error.HTTPError as exc:
        raw = exc.read().decode(errors='replace')
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = None
        return exc.code, parsed, time.time() - started
    except Exception as exc:
        return 599, {'error': str(exc)}, time.time() - started


def create_show():
    name = f'burst-{uuid.uuid4()}'
    status, body, _ = call(
        'POST',
        '/shows',
        {'name': name, 'seats': SEATS, 'price_paise': PRICE_PAISE, 'per_user_limit': 4},
        token=os.getenv('ADMIN_TOKEN', 'admin-token'),
    )
    if status != 201:
        raise SystemExit(f'create show failed: HTTP {status} body={body!r}')
    return body['id']


def reserve_one(index, show_id):
    # Each pair shares both user and idempotency key. One call creates the outcome; the other
    # must replay it rather than moving state a second time. Different pairs are different users.
    user_num = index // 2
    user = f'burst-user-{user_num}'
    key = f'burst-key-{user_num}'
    status, body, latency = call(
        'POST',
        f'/shows/{show_id}/reserve',
        {'seats': [HOT_SEAT], 'idempotency_key': key},
        token=user,
    )
    return status, body, latency


def main():
    print(f'BASE_URL={BASE_URL}')
    print(f'REQUESTS={REQUESTS} CONCURRENCY={CONCURRENCY} HOT_SEAT={HOT_SEAT}')
    show_id = create_show()
    print(f'created show={show_id}')

    outcomes = Counter()
    reason_counts = Counter()
    reservation_ids = set()
    latencies = []
    start = time.time()

    with ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
        futures = [pool.submit(reserve_one, i, show_id) for i in range(REQUESTS)]
        for fut in as_completed(futures):
            status, body, latency = fut.result()
            latencies.append(latency)
            if status == 201:
                outcomes['confirmed_http_201'] += 1
                if body and body.get('reservation_id'):
                    reservation_ids.add(body['reservation_id'])
            elif status == 409:
                outcomes['declined_409'] += 1
                reason = (body or {}).get('reason', 'unknown')
                reason_counts[reason] += 1
            elif 500 <= status <= 599:
                outcomes['5xx'] += 1
            else:
                outcomes[f'http_{status}'] += 1

    status, show, _ = call('GET', f'/shows/{show_id}')
    if status != 200:
        raise SystemExit(f'final show read failed: HTTP {status} body={show!r}')

    total = show['total_seats']
    available = show['available_seats']
    held = show['held_seats']
    confirmed = show['confirmed_seats']
    reconciled = (available + held + confirmed) == total
    hot_seat_states = [s for s in show['seats'] if s['seat'] == HOT_SEAT]

    elapsed = time.time() - start
    latencies.sort()
    p50 = latencies[len(latencies) // 2] if latencies else 0
    p95 = latencies[int(len(latencies) * 0.95)] if latencies else 0

    print('\nOutcome distribution:')
    for k, v in sorted(outcomes.items()):
        print(f'  {k}: {v}')
    print('Declines by reason:')
    for k, v in sorted(reason_counts.items()):
        print(f'  {k}: {v}')
    print(f'Unique reservation_ids observed: {len(reservation_ids)}')
    print(f'Elapsed seconds: {elapsed:.3f}')
    print(f'Latency p50/p95: {p50:.3f}s / {p95:.3f}s')
    print(f'Final reconciliation: available={available} held={held} confirmed={confirmed} total={total} -> {"PASS" if reconciled else "FAIL"}')
    print(f'Hot seat {HOT_SEAT}: {hot_seat_states}')

    failed = outcomes['5xx'] > 0 or not reconciled or len(reservation_ids) > 1 or confirmed != 1
    if failed:
        print('BURST RESULT: FAIL')
        sys.exit(2)
    print('BURST RESULT: PASS')


if __name__ == '__main__':
    main()
