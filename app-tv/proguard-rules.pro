# Keep rules for the TV release/benchmark build (R8 full mode).
#
# Deliberately small. Verified on a shrunk build: Home/detail/search/settings, playback, JSON and image
# loading, the SQLDelight database, secure storage and the serialized navigation back stack after process
# death all work without app-specific keep rules, because the libraries ship consumer rules for what they
# reflect on (kotlinx.serialization, Ktor engine discovery, OkHttp, Tink/protobuf, Media3, Compose,
# AndroidX). Koin 3.5 resolves definitions through lambdas and Coil/SQLDelight need nothing beyond that.
# Only add a rule here after a runtime failure of the shrunk build proves it is needed, and say why.

# Crash reports from a shrunk build stay readable: line numbers survive and the original file name is
# recovered from mapping.txt (build/outputs/mapping/<variant>/).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
