# Export and import implementation plan

Основание: согласованные docs/research/export-import-iteration-3.md и основные layer specifications.

Реализованы неизвестное обслуживание, снимок, строгий JSON/валидатор и plain/encrypted конверт. Runtime crypto и JSON зависимости/лицензии подключены; импорт/системные файлы/native UI ещё не реализованы.

- Неизвестное обслуживание и миграция — реализованы.
- Целостный снимок — реализован.
- Кодек/шифрование — реализованы; физический API24, max-memory и minified runtime smoke остаются release gates.
- Атомарный импорт/provenance, native file ports и native transfer UI — не реализованы.
- Каждый логический code commit проверяется Android host suite и iOS production/test compilation.

Проверка логического коммита: Android host suite 574 тестов, 0 failures/errors/skips; iOS production/test compilation — PASS. gradle-run workflow bbbe2f2c094da4f66d63c2ae8e8e419c, sequence 3.
