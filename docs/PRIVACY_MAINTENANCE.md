# Maintaining the privacy notice

The canonical policy is `app/src/main/assets/legal/privacy.html`. Settings displays
that bundled file offline, without JavaScript or network access. Links open in an
external app. The Pages workflow publishes the same file, without keeping a second
policy copy in sync.

## Publish

1. Review the notice against the release's actual behavior.
2. In the repository's **Settings → Pages → Build and deployment**, choose
   **GitHub Actions** as the source.
3. Merge the approved change into `main`. Changes to the policy on `main` trigger
   **Publish privacy policy**, or run that workflow manually against `main`.
4. Confirm that `https://amine-ao.github.io/JustWatch/privacy.html` is accessible.

Deployment is limited to `main`. Preparing this workflow does not enable Pages or
publish anything by itself. A policy website deployment does not rebuild the APK;
the installed app continues to show the policy bundled with its version.

## Review when behavior changes

Check dependencies for advertising, analytics and crash reporting, and revisit
network destinations, automatic update checks, download approval, local storage,
cleanup, diagnostics, clipboard access and WebView behavior. Increment the notice's
effective date when its disclosures change.

The contact currently uses the maintainer's GitHub profile and public issues, as
requested. It warns users not to disclose private information publicly. Add a
private contact channel before requesting sensitive information. This notice is a
description of the app, not a guarantee of compliance with every applicable law.
