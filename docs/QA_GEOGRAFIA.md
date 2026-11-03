# Verificación de geografía · 3 de noviembre de 2026

## Completado antes de fusionar

- Compilación de los recursos con AAPT2 y de todos los fuentes main/debug con
  Java 17 contra Android SDK 34; generación D8, alineación y verificación de una
  APK de debug. No se usa su firma para actualizar instalaciones del usuario.
- Batería completa: **72 pruebas unitarias correctas**, incluidas las ocho
  pruebas de datos, referencias y validación de respuestas geográficas.
- Regeneración byte a byte del catálogo a partir del commit documentado y de la
  instantánea de etiquetas españolas: 1.342 fichas, 1.309 puntos y 33 sin ubicación.
- Instalación de la APK y ejecución de `GeographyQaActivity` en Android 9/API 28,
  con texto sintético y sin importar ni distribuir ninguna edición bíblica.
- SQLite real: 15 lugares de Hechos 16, lectura del capítulo completo, rechazo
  de un fragmento como análisis editorial completo, almacenamiento y reapertura
  del análisis de IA, lugares nuevos sin coordenadas y recuperación de registros
  aprendidos desde otro capítulo del mismo libro.
- Instantánea de la nueva base con el método compatible con SQLite anterior a
  3.27: archivo creado, abierto y comprobado, conservando los análisis.

Resultado de la actividad:

```text
PASS: 15 lugares, capítulo completo, caché persistente, fuente y lugares sin coordenadas.
```

La prueba detectó y permitió corregir dos requisitos del método antiguo: crear
el destino antes de ATTACH y evitar que Android cree `android_metadata` en ese
destino antes de copiar el esquema original.

## Límites de esta comprobación

El emulador carece de aceleración y presentó un aviso de System UI durante la
captura. La aplicación terminó sus comprobaciones de datos y mostró el diálogo;
se revisó la pantalla después de cerrar ese aviso. El fondo cartográfico remoto
no estuvo disponible en esta red: se comprobaron los marcadores y el aviso de
falta de conexión, no una descarga real de teselas. No se han usado claves
personales de Gemini/Groq ni el EPUB privado del usuario.

La publicación firmada debe comprobar adicionalmente versión, huella SHA-256,
nombre del asset y coincidencia de certificado con la APK oficial anterior.
