# Transfer snapshot verification

This commit implements only the read-only logical SQLite snapshot and selection contract. ExportSelectionTest and TransferSnapshotTest cover 23 cases; production/test iOS compilation and Android host tests are required before committing. Codec, crypto providers, import, native file handoff and UI remain future work.
