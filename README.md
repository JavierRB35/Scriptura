# Scriptura · Biblia católica personal

App Android nativa para búsqueda local de la Biblia de la Universidad de Navarra, lectura por libros/capítulos/versículos, favoritos, historial, recomendaciones por estado de ánimo y comentarios bajo demanda mediante Gemini.

**Serie 0.x: desarrollo preliminar. No declarar v1.0 sin autorización del propietario.**

## Privacidad y licencias

- **No se sube ni distribuye el EPUB protegido**. Cada usuario importa su propio ejemplar adquirido. Se copia y procesa exclusivamente en almacenamiento privado de Android.
- Las notas/explicaciones de Gemini son generadas por IA, pueden equivocarse y no constituyen doctrina oficial.
- Gemini recibe únicamente el pasaje y los datos de contexto que el usuario confirma enviar. La cuota gratuita depende del proyecto AI Studio; la aplicación no activa facturación.
- La clave Gemini queda cifrada con Android Keystore, sin incorporarla al código ni guardarla en GitHub.
- El calendario litúrgico de la Misa consulta **lecturasdeldia.org**, un proyecto **independiente** que usa datos del leccionario de España. El texto oficial del leccionario es propiedad de la CEE. Los textos se leen en el sitio externo o en la traducción personal importada. Se deben contrastar variaciones locales.
- Las preferencias, favoritos, historial y consultas almacenadas son locales. No hay cuenta ni servidor propio.

## Firma estable de las actualizaciones

Requiere GitHub Repository Secrets (nunca publicar valores): `SCRIPTURA_KEYSTORE_BASE64`, `SCRIPTURA_KEYSTORE_PASSWORD`, `SCRIPTURA_KEY_PASSWORD`. El alias es `scriptura`. Todos los APK Release usan la misma firma y `applicationId=com.scriptura.app`.

La Action `.github/workflows/android-apk.yml` compila, verifica `apksigner`, crea un artefacto y publica una Release preliminar `v0.x.x-build.N` con APK directamente instalable. El `versionCode` crece con `GITHUB_RUN_NUMBER` para permitir actualizaciones sin desinstalar.

## Compilación en el móvil (solo diagnóstico)

AndroidIDE-Rv2, Java 17, SDK 34, AGP 8.7.3, Gradle 8.9. Ejecuta `sh build-on-phone.sh` dentro de `/storage/emulated/0/Download/Scriptura/`. La APK DEBUG local **no tiene la misma firma** que Release y no se instala sobre ella. Instala siempre la APK firmada publicada en GitHub.

## Desarrollo

Versión 0.3.0: biblioteca con lector y notas de Navarra, búsqueda literal y por palabra, interpretación opcional con Gemini, descubrimiento diario, estados de ánimo, misa por fecha (enlace y referencias desde un proveedor independiente), guardados y recordatorio diario. Se requiere verificar funcionamiento del importador con el EPUB real **en el móvil**, permisos y conectividad con la API Google configurada por el propietario.

## Limitaciones de validación

- La compilación automatizada **no sustituye** comprobar instalación, importación de un EPUB y funcionamiento del teléfono real. Debe verificarse tras instalar.
- Se comprueba la estructura del EPUB de Navarra aportado, pero **no se adjunta** al repositorio ni a Releases.
- Los comentarios extraídos son fragmentos del ejemplar privado y pueden faltar para algunos versículos.
- Lecturas del calendario: el proyecto externo cubre una ventana de fechas limitada, sus textos son propiedad de CEE. Scriptura abre la web en su propio visor y recupera referencias cuando consigue analizarlas. **No es una API oficial** y sus cambios pueden afectar la extracción.
- La consulta Gemini solo se valida en el dispositivo con una clave propia y una cuota disponible. Las respuestas no son magisterio de la Iglesia.
