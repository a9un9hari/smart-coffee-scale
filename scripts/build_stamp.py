# PlatformIO pre-build script: forces src/build_stamp.cpp to recompile on
# EVERY build, so its __DATE__/__TIME__ always reflect the build that
# produced the image. Without this, the stamp only changed when the file
# holding it was edited, so an OTA-installed fix still showed the old build
# time on the splash (2026-09-28). Touching the file isn't enough - SCons
# decides by content hash, not mtime - hence AlwaysBuild on its object.
Import("env")

env.AlwaysBuild(env.File("$BUILD_DIR/src/build_stamp.cpp.o"))
