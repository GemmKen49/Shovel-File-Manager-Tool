# The target and the independently optimized harness share a class loader.
# Give test-only classes a private namespace so their short names cannot shadow
# classes in the APK being verified. This does not change the shipping APK.
-repackageclasses com.shovel.filemanager.test.internal
