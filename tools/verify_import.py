#!/usr/bin/env python3
"""Offline Java -> real Python importer proof, exclusively disposable SQLite.

Run with python3 -B tools/verify_import.py. Reference imports never write pycache.
No CLI accepts a DB URL; no real wallet/key/network operations are performed.
"""
import sys
sys.dont_write_bytecode = True
import argparse
import contextlib
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', type=Path, default=ROOT.parent / 'btc_envelope_maker')
    args = parser.parse_args()
    reference = args.reference.resolve()
    if reference == ROOT:
        raise SystemExit('Reference must be a separate read-only repository')
    sys.path.insert(0, str(reference))
    from btc_envelope_maker import btc_db, nfc_bindings, address_validation
    java = Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-17-amazon-corretto')) / 'bin'
    addresses = (ROOT / 'core/src/test/resources/synthetic-addresses.txt').read_text().splitlines()
    assert len(addresses) == len(set(addresses)) == 5001
    assert all(address_validation.valid_inventory_address(a) for a in addresses)
    scratch = Path(os.environ.get('TMPDIR', Path.home() / '.hermes/cache/scratch'))
    scratch.mkdir(parents=True, exist_ok=True)
    sources = sorted((ROOT / 'core/src/main/java').rglob('*.java'))
    sources += [ROOT / 'core/src/proof/java/ExportProof.java']
    hashes = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    with tempfile.TemporaryDirectory(prefix='seller-import-proof-', dir=scratch) as folder:
        work = Path(folder)
        copied = work / 'sources'
        copied.mkdir()
        snapshots = []
        for p in sources:
            target = copied / p.name
            shutil.copyfile(p, target)
            snapshots.append(target)
        classes = work / 'classes'
        classes.mkdir()
        subprocess.run([str(java / 'javac'), '-encoding', 'UTF-8', '-d', str(classes), *map(str, snapshots)], check=True)

        def execute(mode, input_path, output_path):
            subprocess.run([str(java / 'java'), '-cp', str(classes), 'ExportProof', mode, str(input_path), str(output_path)], check=True)

        # Independent reference comparisons: valid programs + every single-symbol mutation.
        vectors = list(addresses) + list(address_validation.PUBLIC_TEST_ADDRESSES) + [
            'bc1qqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqsw9e2a9',
            'bc1pqyqszqgpqyqszqgpqyqszqgpqyqszqgp0hg7y6',
            'bc1qqyqszqgpqyqszqgpqyqszqgpqyqszqgp340evn',
        ]
        for a in addresses[:20]:
            vectors += [a.upper(), 'bitcoin:' + a, ' ' + a, a + ' ', a.replace('bc1', 'tb1', 1)]
            vectors += [a[:i] + ('q' if a[i] != 'q' else 'p') + a[i+1:] for i in range(len(a))]
        vector_input, verdicts = work / 'vectors.txt', work / 'verdicts.txt'
        vector_input.write_text('\n'.join(vectors) + '\n')
        execute('validate', vector_input, verdicts)
        actual = verdicts.read_text().splitlines()
        expected = [str(address_validation.valid_inventory_address(a)).lower() for a in vectors]
        assert actual == expected

        timestamp = '2026-11-01T01:00:00.123456+03:30'  # UTC day is October 31.
        captures = [(f'{i:08X}', a, timestamp) for i, a in enumerate(addresses)]

        def export(rows, name):
            path = work / (name + '.tsv')
            path.write_text(''.join('\t'.join(r) + '\n' for r in rows))
            destination = work / name
            execute('export', path, destination)
            return sorted(destination.glob('*.csv'))

        parts = export(captures, 'captures')
        assert [p.name for p in parts] == ['2026-10-31-part-001.csv', '2026-10-31-part-002.csv']
        parsed = [nfc_bindings._read_csv(p) for p in parts]
        assert [len(rows) for rows in parsed] == [5000, 1]
        assert all(p.stat().st_size <= 1024 * 1024 for p in parts)
        assert [(r['uid'], r['address'], r['captured_at']) for rows in parsed for r in rows] == captures
        db = work / 'disposable.sqlite'
        url = 'sqlite:///' + str(db)
        btc_db.check(url)
        ledger = [btc_db.Row(f'SYNTHETIC-{i:05d}', a, 'synthetic-jalali', '2020-01-02 03:04:05 UTC') for i, a in enumerate(addresses)]
        assert btc_db.save_rows(url, 'wallets', ledger) == (5001, 0)
        with contextlib.closing(sqlite3.connect(db)) as conn, conn:
            conn.execute("UPDATE wallets SET contact_type='synthetic-type', contact_value='synthetic-value', notes='synthetic-note'")

        def snapshot(include_uid=False):
            columns = 'serial,address,jalali_datetime,created_utc,contact_type,contact_value,notes'
            if include_uid:
                columns += ',nfc_uid'
            with contextlib.closing(sqlite3.connect(db)) as conn:
                return conn.execute('SELECT ' + columns + ' FROM wallets ORDER BY serial').fetchall()

        before = snapshot()
        nfc_bindings.migrate_nfc_uid(url)
        assert snapshot() == before
        first = [nfc_bindings.import_csv(url, p) for p in parts]
        replay = [nfc_bindings.import_csv(url, p) for p in parts]
        assert first == [(5000, 0), (1, 0)]
        assert replay == [(0, 5000), (0, 1)]
        assert snapshot() == before
        bound = snapshot(True)
        assert [r[-1] for r in bound] == [r[0] for r in captures]
        # Free a synthetic row to exercise UID conflict and batch atomicity.
        with contextlib.closing(sqlite3.connect(db)) as conn, conn:
            conn.execute('UPDATE wallets SET nfc_uid=NULL WHERE address=?', (addresses[-1],))
        before_conflict = snapshot(True)
        conflict_cases = [
            [('FFFFFFFF', addresses[0], timestamp)],  # same address, new UID
            [(captures[0][0], addresses[-1], timestamp)],  # same UID, new address
            [('FFFFFFFE', addresses[-1], timestamp), ('FFFFFFFF', addresses[0], timestamp)],
        ]
        for i, rows in enumerate(conflict_cases):
            files = export(rows, f'conflict-{i}')
            try:
                nfc_bindings.import_csv(url, files[0])
            except btc_db.DbError:
                pass
            else:
                raise AssertionError('Importer accepted conflicting binding')
            assert snapshot(True) == before_conflict
        # Recovery restores the released synthetic binding, without metadata changes.
        assert nfc_bindings.import_csv(url, parts[1]) == (1, 0)
        assert snapshot(True) == bound
        assert snapshot() == before
        assert hashes == {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
        result = {
            'status': 'PASS', 'validator_comparisons': len(vectors),
            'exported_rows': len(captures), 'part_rows': [len(r) for r in parsed],
            'part_bytes': [p.stat().st_size for p in parts],
            'first_import': first, 'replay_import': replay,
            'conflict_cases_rejected_atomically': len(conflict_cases),
            'serial_and_all_metadata_preserved': True,
            'captured_at_preserved_in_csv_not_persisted_by_reference': True,
            'source_sha256': hashes,
            'executed_class_sha256': {str(p.relative_to(classes)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(classes.rglob('*.class'))},
            'reference_sha256': {p: hashlib.sha256((reference / 'btc_envelope_maker' / p).read_bytes()).hexdigest() for p in ['address_validation.py', 'btc_db.py', 'nfc_bindings.py']},
        }
    assert not Path(folder).exists()
    result['disposable_fixture_removed'] = True
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
