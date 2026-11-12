# Automated build

Every push to `main` runs `.github/workflows/build.yml` on Java 21. The workflow runs the progression unit tests, creates the shaded plugin JAR, verifies that the JAR is non-empty, and uploads it as the `THE-ONE-Paper-1.21.11` artifact.

Do not deploy an artifact unless the workflow run is green.
