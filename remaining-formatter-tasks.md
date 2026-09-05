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
