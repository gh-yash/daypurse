# Used for every release build except the Play bundle (see app/build.gradle.kts).
# R8 still shrinks and obfuscates; only its optimization passes are skipped, because their
# output isn't deterministic and F-Droid needs builds that are identical byte for byte.
-dontoptimize
