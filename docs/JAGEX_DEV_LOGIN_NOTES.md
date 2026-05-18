# Jagex Account Dev Client Login Notes

When changing Jagex characters/accounts for RuneLite dev testing:

1. Open **RuneLite (Configure)** from the Windows Start Menu.
2. Ensure **Client arguments** contains:
   ```text
   --insecure-write-credentials
   ```
3. Click **Save**.
4. Open **Jagex Launcher**.
5. Select the character/account you want to test.
6. Launch **RuneLite** from the Jagex Launcher.
7. Log in fully once.
8. Close RuneLite.
9. Launch the IntelliJ dev client:
   ```text
   ExamplePluginTest.main()
   ```

The IntelliJ/dev RuneLite client should reuse the latest saved launcher credentials.

## Important security notes

- Do not upload or share `credentials.properties`.
- Do not commit `credentials.properties` to GitHub.
- If changing accounts/characters, repeat the Launcher login step.
- When finished development, delete:
  ```text
  C:\Users\conno\.runelite\credentials.properties
  ```
- If you need to invalidate saved sessions, use **End sessions** in your Jagex account settings.

## Useful paths

RuneLite credentials file:

```text
C:\Users\conno\.runelite\credentials.properties
```

RS State Bridge telemetry file:

```text
C:\Users\conno\.rs-state-bridge\latest_state.json
```
