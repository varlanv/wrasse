What I found and left as is:

- Every file that still has edits rewrites the entire patch file. Over a compile that touches N edited files that is N
  rewrites of a growing file, so quadratic bytes and a create, write, and rename per file. Under a format run on a large
  module this is the dominant IO cost. The fix is an append-only journal per compilation with compaction on read, or a
  single write at compilation end. FIR checkers have no clean end-of-compilation hook, so the journal is the realistic
  option.
- Each rewrite re-sorts every entry's edits (sortedByDescending inside WPatchWriter.write). Sorting once when the entry
  is created removes that.
- The applier copies each source file about four times: read, hash via toByteArray, StringBuilder (content), toString,
  then write. Hashing over the string directly and writing the builder without a final toString would cut two copies.
- The source hash in the plugin copies the file text to a byte array and creates a MessageDigest per file. Streaming the
  chars through an encoder into a reused digest avoids the copy.
- Files.exists followed by Files.readString on the patch file is two syscalls where one try/catch read would do.
- FileEdits - check if `WEdit` may be redesigned to work with StringSlice; maybe `sourceHash: String` too. Consider
  avoiding `Files.readString` / `Files.writeString` in favor of more performant approaches.

None of these were measured. The JMH corpus covers the walk and printer only, and there is no IO benchmark. If you want
this done properly, the order I would take is: journal instead of full rewrite, then the applier copies, then the hash,
each measured with a format run on kryptoid timed end to end.

## Status after the file-IO pass

Measured with `PatchIoBenchmark` (`:testing:wrasse-benchmarks:jmh`): one simulated compilation
recording 400 files with 4 edits each, and applying those patches to 400 files.

| step | before | after |
|------|-------:|------:|
| recording a compilation's patch entries | 97.7 ms (whole file rewritten per changed file) | 3.7 ms (one appended block per change) |
| applying 400 patched files | 56.5 ms | 57.3 ms |

- Journal instead of full rewrite: done. `WPatchStore` appends one block per change; the reader
  treats the file as a journal (last block per path wins, a `hash:-` tombstone removes the path);
  the store compacts on first load and whenever superseded blocks outnumber live entries. A clean
  compilation still leaves a header-only file. Any tool that greps `file:` lines must apply the
  same last-block-wins rule; the TestKit spec was updated to do so.
- Re-sorting on every rewrite: moot, each entry is now written once.
- Applier copies: done (bytes read once, hashed as read, decoded once, output streamed in
  segments), but it does not measure faster: 400 small files are bound by open/write/rename
  syscalls, not by copying 3 KB strings. Kept because it allocates less, not for speed.
- Streamed source hash in the plugin: done (`Sha256.ofText`); the applier hashes the raw bytes
  (`Sha256.ofBytes`), identical for valid UTF-8.
- `Files.exists` before reads: replaced by a single read with `NoSuchFileException` handled, in
  the store and the applier.
- `WEdit`/`FileEdits` over `StringSlice`: checked and not done. A patch file is parsed once per
  compilation and once per apply; the per-edit `String` for the replacement is a few thousand
  small allocations against file IO in the tens of milliseconds, and `WEdit.replacement` is
  compared and spliced as a `String` throughout rules, `EditPlan` and `DocSplicer`. Nothing to
  gain there that the benchmark could see.
