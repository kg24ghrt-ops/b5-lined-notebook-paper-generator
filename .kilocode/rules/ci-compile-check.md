# CI compile check (mandatory)

This project cannot be compiled locally. The only valid proof that code compiles is a green run of the "Compile Check" GitHub Actions workflow. validate.py and similar local scripts are pre-checks only and never count as proof.

After ANY change to files under android/ (Java, XML, Gradle, manifest, resources) and BEFORE telling me a task is done:
1. Commit directly to main. Do not create a branch unless I ask for one.
2. Run git pull --rebase, then push to main. The push starts the Compile Check automatically. If it somehow did not, run: gh workflow run compile-check.yml --ref main
3. Find the run: gh run list --workflow compile-check.yml --branch main --limit 1
4. Wait for it: gh run watch <run-id> --exit-status
5. If it fails: gh run view <run-id> --log-failed. Read the real compiler errors, fix the cause in the code, commit, pull --rebase, push, and repeat from step 3. Keep fixing forward until the run is green. After 5 failed attempts, stop and ask me.
6. Report the result in two lines: the run URL and the real outcome (green or the actual error). Never say "it compiles" or "it should work" without a green run.

Never hide failures: do not delete or weaken the workflow, skip tasks, add continue-on-error, or edit code only to silence an error. Never use the gradle on PATH; the project uses ./gradlew (Gradle 8.5, JDK 17). Do not accept Android SDK licenses or change my machine's global settings. If gh is not logged in or the check cannot run, tell me instead of guessing.
