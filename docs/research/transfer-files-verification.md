# Native transfer file adapters — verification

Stage6 implementation: common TransferFiles port with session-only handles and fixed outcomes; bounded/cancellable IO; Android SAF/FileProvider and iOS document-picker/security-scope/coordinated read/activity adapters; lifecycle hosts and platform DI. No broad storage grants or persistent URI/URL access, database or wire-format changes.

Core tests exercise short reads/writes, actual byte limits, lying metadata, flush/close errors, cancellation, stale callbacks, host replacement/busy operations and fixed platform errors. Android uses a real isolated ContentProvider; native iOS uses Foundation streams and coordination on a booted simulator. UI and codec/decryption are outside the port.

This commit includes production adapters, wiring and core IO tests. Synthetic cross-provider fixture compatibility and actual system-dialog hosts/runners are delivered in the next separate commit. No successful real-system matrix or product transfer UI acceptance is claimed by this core test result.

The user explicitly authorized committing the remaining implementation on 2026-11-18. The stage remains **not Ready for acceptance** until the iOS system-dialog matrix is stable. Physical API24/minimum-iOS and minimum-OS crypto compatibility, product transfer UI and full Android↔iOS acceptance remain release gates.

## Isolated commit checks

Снимок коммита 1: Android **683/0/0/0**, native iOS file tests **25/0/0/0**, Android APK и обе iOS production/test compilation PASS. Workflow `e20fda31be0b5a5606614f7d71edec81`, «Проходит ли файловый коммит 1?».

Managed logs are retained; no push or publication.
