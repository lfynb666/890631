# opencode APK

Android WebView wrapper for the `opencode web` UI.

## Build Policy

Do not build locally. Push changes to `main` and let GitHub Actions produce the debug APK artifact.

## Use

Start opencode on a computer in the same LAN:

```bash
opencode web --hostname 0.0.0.0 --port 4096
```

Open the APK, enter the computer's LAN host and port, then connect.
