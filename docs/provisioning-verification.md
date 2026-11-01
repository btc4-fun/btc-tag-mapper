# Optional provisioning verification

## Parent release gate

After the cancellation/radio-off fixes, the parent reran the real signed release
build with the existing shared key: Python 16 passed, core/app release unit tests
passed, release lint passed, APK package/version/permissions/ZIP/certificate and
packaged scanner checks passed. The public certificate remains unchanged.

The actual compiled AddressTransport was executed on the host JVM against the
deployed HTTPS endpoint with an unfunded synthetic public address; it returned
the expected 404/not_found. This is host transport evidence, not Android-device
TLS or a successful registered-wallet lookup. An initial known preview address
was correctly rejected locally by the seller's existing preview exclusion.

No ADB device was connected. Physical NFC writing, locking, rediscovery and
cross-phone link opening remain unverified. Earlier sections below describe the
implementation work before these parent checks; they are not current claims that
the backend is undeployed or that production signing has not run.

Scope: seller repository only, based on `972c7aa`. No commit, push, tag, release,
production request, signing-secret read or physical NFC write/lock was performed.
The pre-existing untracked `docs/customer-github-handoff.fa.md` was preserved.

## Final executed gates

```text
Python tools: 16 passed
Core JUnit: 37 passed
Android host JUnit: 57 passed
Failures / errors / skipped: 0 / 0 / 0
Debug lint: 0 issues
Debug assemble: PASS
Scanner runtime classes in APK: PASS
APK ZIP integrity: PASS
Exact APK permission set: PASS
Version: 1.1.0 / code 4
```

Final full run used JDK 17, the checked-in wrapper and cached dependencies:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto \
ANDROID_HOME=/home/saeb/android-sdk \
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --offline --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks
python3 -B -m unittest discover -s tools -p 'test_*.py' -q
python3 -B tools/verify_scanner_runtime.py app/build/outputs/apk/debug/app-debug.apk
git diff --check
```

Counts were parsed from the resulting JUnit XML, not estimated from source.
The compiler still reports existing deprecated Android API usage; lint is clean.

```text
app/build/outputs/apk/debug/app-debug.apk
2318352 bytes
SHA-256: 9d380f2f64f72ad066ab9f33253e1c1f4c5abda7dbb37b1593f46828573c218d

android.permission.NFC
android.permission.CAMERA
android.permission.INTERNET
com.saebm.btcnfcseller.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

This is a debug artifact, not a production-signed release. The release verifier
still pins the owner's unchanged certificate:

```text
7c4ccefc998b2407b22c2a7a3bc97e9b5f9baca3089886a5e71dcfef85b0fa3b
```

## RED/GREEN evidence and regression coverage

Executed failing tests before the corresponding implementations for: initial
consent state, exact lookup binding, explicit write arming, tag transport,
write/readback without locking, cancellation during preflight, separate lock and
verification, one-shot HTTP request, whole-response drip deadline, chunked
response decoding, saved-record disclosure, integrated lookup review/overwrite,
background/address-change invalidation, NDEF URI encoding, separate lock controls,
rate-limit display/admission, duplicate JSON keys, invalid-response display,
offline capture reader regression, repeated cancellation preserving unknown
outcomes, obsolete overwrite dialog authorization, actual explicit lookup dispatch,
and keeping reader mode alive until tag I/O completes. Python permission/version
and dynamic release-note tests were also observed failing before configuration
updates. Additional negative-path tests were added as coverage of existing guards.

Real loopback HTTP tests cover exact GET path, no cookie/auth headers, content-length
and chunked bodies, continuous-drip deadline with peer termination, oversize and
truncated bodies, ambiguous framing, cancellation before connect, and one request
only for 302/400/404/429/503 including Retry-After zero. These are plaintext local
transport tests, not live HTTPS or deployed-site evidence. Production uses platform
TLS trust and HTTPS hostname checking; no redirects or retries are implemented.

Injected tag tests cover wrong UID, insufficient capacity, read-only and unsupported
tag, readback mismatch, tag loss, missing consent, stale callbacks, cancellation
before side effects and late success after cancellation, lock capability/content/UID
checks, write-unlocked, lock-awaiting-verification and locked-verified states.
Android tests cover one standard URI record without AAR, saved-record selection,
separate disclosure, asynchronous dispatch, review, stale dialogs, lifecycle fences,
rate-limit delay, strict JSON parsing and reader flags. The reader-lifetime regression
is a source contract, not a physical-radio test.

## Unchanged contracts and remaining gates

### Independent-review cancellation follow-up

Reproduced the actual capture callback failure with Robolectric tag dispatch before
and after disclosure cancellation: the second UID remained unchanged (RED), then
updated after the fix (GREEN). Separately observed RED/GREEN for radio-off retaining
`LOCK_ARMED` and for cancellation restarting the reader while tag I/O was queued.
The added injectable tag-port boundary was also driven by a failing test.

Cancellation now separates authority invalidation from reader refresh, so every
dialog cancellation restores capture without recursive cancellation/refresh calls.
Radio loss invalidates pending lookup, review, write, lock and verification authority;
turning it back on cannot reuse old lock consent. Reader refresh is deferred while
tag I/O is queued or active and applied after completion. Late effects cannot turn
an unknown outcome into success. A test blocks an already-admitted synthetic write,
cancels and toggles the radio, then proves the effect may finish while the state stays
unknown and only offline capture resumes.

`ProvisioningReaderTest` adds 16 host tests covering negative-button and back/cancel
paths for disclosure, overwrite and lock dialogs, radio transitions, stale reader
callbacks, queued/active I/O and background cancellation. The existing provisioning
UI fixture now explicitly enables synthetic NFC hardware: its old expectation of
arming with disabled hardware conflicted with the new fail-closed radio policy.
All final Gradle gates ran serially, followed by Python tests. APK signature, ZIP,
scanner classes and permissions were checked. No external network, physical NFC,
production signing, commit or push was used in this follow-up; existing transport
tests only exercise local loopback sockets.

`CaptureStore`, capture schema, `uid,address,captured_at` CSV, address validator and
existing scanner/export flow remain unchanged. No manual serial field or
address-based webpage URL was added. No customer app or app-links changes.

**Physical NFC write, irreversible lock, rediscovery behavior on actual phones,
cross-phone URI opening, visual handset QA, and live deployed lookup are unverified.**
The backend contract was read from the parent workspace but was not deployed by
this task. An in-flight hardware operation cannot be undone. Cancellation fences
unstarted admitted generations and never converts a late callback into success;
unknown outcomes require independent tag inspection. System DNS resolution may
outlive the caller deadline in a daemon worker, but its socket is closed and the
request is fenced before transmission. Production TLS handshake behavior still
needs device/network integration verification.

Parent owns independent review, production signing and any later publication.
