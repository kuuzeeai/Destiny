# Destiny (Android)

App Android en Kotlin + Jetpack Compose (base: cliente OpenCode).

## Compilar

Cada `git push` a `dev` compila el APK release (R8 activado, firmado con llave fija) y lo
publica siempre en el mismo enlace:

`https://github.com/<usuario>/<repo>/releases/download/ultima/destiny.apk`

Secretos necesarios en GitHub (Settings → Secrets → Actions):
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Guarda una copia de `destiny.jks`: sin esa llave no se puede actualizar la app ya instalada.
