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

## Atlas geográfico preliminar (0.5.0)

- Sección **Lugares bíblicos** desde Inicio, sin añadir una sexta pestaña.
- Fichas con denominaciones actuales, coordenadas orientativas, referencias bíblicas y nivel de certeza.
- Itinerarios ilustrativos de Abraham, Jacob, Moisés, Jesús y Pablo con etapas seleccionadas.
- Atlas por libro para el canon de 73 libros: cuando aún no hay geografía catalogada, se avisa; no se inventan mapas.
- El botón «Lugares de este pasaje» busca topónimos citados en la selección, pero no es una identificación automática exhaustiva.
- Los esquemas de coordenadas **no son mapas topográficos ni rutas reales**: conectar puntos no implica conocer el camino histórico.
- Consulta opcional en OpenStreetMap externo, mediante acción explícita; requiere conexión a internet.
- Ampliaciones históricas y cartográficas pendientes en [#4](https://github.com/JavierRB35/Scriptura/issues/4).

El APK sigue firmado con el keystore privado de GitHub Actions; las credenciales, el EPUB personal y su contenido no están incluidos en el repositorio.

## Avisos automáticos de actualización — 0.5.1

- Scriptura consulta las versiones `0.x.y-build.N` **publicadas con APK** en las GitHub Releases de `JavierRB35/Scriptura`.
- **Repositorio privado:** GitHub rechaza consultas no autenticadas. Por eso, en Ajustes, el usuario configura **una sola vez** un Fine-grained personal access token de GitHub, limitado al repositorio Scriptura y **Contents: Read**. No se solicitan otros permisos.
- El token se cifra con AES-GCM mediante Android Keystore y se guarda **solo en el almacenamiento privado del dispositivo**. No incluirlo en el ZIP, repositorio, APK ni secretos compartidos. Puede revocarse desde GitHub o borrarse en Ajustes.
- La aplicación realiza comprobaciones al abrir (con control de frecuencia) y mediante `JobScheduler` cada 12 horas, con conexión de red. Android puede retrasar las tareas periódicas; no existe garantía de notificación instantánea.
- Una actualización disponible se muestra en Inicio y, si está permitido, se envía una notificación Android en un canal independiente al recordatorio de lectura. Se notifica cada etiqueta una sola vez para evitar repeticiones.
- Al pulsar se abre la página **privada** del Release en el navegador, donde el usuario inicia sesión si es necesario y descarga la APK firmada. **Jamás se instala automáticamente.**
- En Android 13+ hay que permitir `POST_NOTIFICATIONS` para los avisos del sistema, pero el banner dentro de la app no lo necesita.
- El modelo de versiones se comprueba con tests unitarios y no se notifica sobre borradores, tags sueltos ni Releases que carezcan de APK.
- Limitación de seguridad: aunque cifrado en el Keystore, el token personal es una credencial sensible. Usar una credencial limitada con caducidad; regenerarla al vencer.
- Para comprobar un APK publicado nuevo, no basta un commit o una compilación de PR: debe existir una **Release** firmada descargable.

## Atlas interactivo sobre cartografía real (candidata 0.6.0)

- Los mapas de libro, recorrido y lugar usan ahora **cartografía OpenStreetMap real**, con desplazamiento, zoom, marcadores pulsables, numeración de etapas y las líneas de recorrido superpuestas. Ya no se dibujan únicamente sobre un fondo vacío.
- Fuente: © OpenStreetMap contributors, con atribución visible en la propia vista. Se consultan exclusivamente las teselas que muestra el usuario (https://tile.openstreetmap.org/...). La app se identifica mediante un User-Agent propio, emplea HTTPS, almacenamiento temporal >= 7 días y **no** descarga regiones completas ni realiza precargas masivas.
- La app no necesita una API key, servicio de Google Play, SDK de mapas ni facturación de Google Cloud.
- Botón **«Ver esta zona por satélite en Google Maps»**: abre Google Maps mediante su URL oficial `map_action=map&basemap=satellite`, sin incrustar el satélite ni trasladar las líneas de itinerario. El satélite externo no precisa clave del Maps SDK.
- **Sin conexión:** se conservan descripciones, lugares, rutas y teselas previamente visualizadas si continúan en la caché; puede verse un aviso cuando el fondo cartográfico no esté disponible. No constituye un sistema completo de mapas offline.
- Las líneas discontinuas representan **orden narrativo** de paradas, no carreteras modernas ni caminos bíblicos arqueológicamente demostrados.
- Cualquier ubicación catalogada como `DEBATED`, `APPROX` o `REGION` conserva su advertencia.
- El módulo continúa como atlas divulgativo **preliminar**; quedan por ampliar topónimos y geografía de todos los libros, además de probar carga real de teselas y funcionamiento con restricciones de red en dispositivos.

### Referencias de plataforma
- Política de teselas: https://operations.osmfoundation.org/policies/tiles/
- URLs oficiales de mapas satélite: https://developers.google.com/maps/documentation/urls/get-started
- Google Maps SDK Android (requiere clave y facturación; no utilizado): https://developers.google.com/maps/documentation/android-sdk/usage-and-billing

## Novedades preliminares 0.7.0: índice, mapas y arte

- **Inicio**: se retiran las tarjetas redundantes de estado de ánimo y búsqueda (ya existen sus pestañas). Permanecen lecturas del día, acceso al índice, galería y actualizaciones.
- **Mapas fijo**: banda persistente «MAPAS BÍBLICOS» justo encima de las cinco pestañas originales, sin sustituirlas.
- **Índice de 73 libros**: acceso desde Inicio y Buscar. Cada ficha muestra capítulos, mapa y un extracto abreviado de la introducción editorial extraída localmente del EPUB personal de Navarra. El EPUB privado **no se sube ni se incluye** en APK o GitHub. Algunos libros comparten introducciones editoriales, indicadas en la ficha.
- **Arte y Biblia**: galería editorial de 62 obras clásicas vinculadas a pasajes, con título, artista y fechas aproximadas; seis fichas por página. Las reproducciones se solicitan en Wikimedia Commons y solo se muestran si el resultado coincide razonablemente con la obra y posee licencia identificada como dominio público o Creative Commons.
- **Sin clave API de imágenes**: el usuario puede abrir la búsqueda en Wikimedia Commons o la ficha concreta verificada para comprobar derechos y autoría. Una búsqueda que no dé coincidencia fiable no muestra una obra distinta.
- Son representaciones artísticas históricas de relatos bíblicos, nunca fotografías de acontecimientos bíblicos ni sustituto de exégesis.
- **Compatibilidad**: cinco pestañas, Java17, SDK34, Gradle8.9, AGP8.7.3, aplicación/firma y datos locales intactos. No pasar a 1.0 hasta validar con EPUB y teléfono real.

## Lectura contextual y splash (0.7.1)

- La selección prolongada utiliza la barra estándar de Android, manteniendo `Copiar`, `Compartir` y `Seleccionar todo`. La acción añadida `Definir` abre un globo con el significado de palabras del glosario bíblico inicial.
- Para palabras no catalogadas, se ofrece Wikcionario externo. Con clave Gemini configurada, existe explicación contextual opcional con aviso y consentimiento antes de enviar datos.
- Splash de identidad v2.0 con símbolo vectorial libro y luz, paleta azul noche y oro y lema. Comienza una búsqueda no bloqueante de nuevas releases; si no responde GitHub, la app abre con normalidad.
- Inicio añade el símbolo del libro como ilustración propia. Se mantienen índice de libros, las obras pictóricas existentes, mapas con base geográfica real, cinco pestañas, EPUB local, seguridad y firma anterior.
- La lámina gráfica aportada en ZIP v2.0 es conceptual y no una captura ni licencia para distribuir arte de terceros; las imágenes históricas tienen su propio catálogo y crédito.

## Scriptura 0.7.2 · experiencia de primer uso

- **Primera apertura:** guía opcional visible en Inicio, que se puede ocultar y recuperar desde Ajustes → Cómo utilizar Scriptura. Importar EPUB no es obligatorio para consultar índice, mapas y arte.
- **Inicio más sencillo:** se reduce el tamaño del elemento gráfico y el acceso al token privado de actualizaciones deja de distraer a nuevos usuarios; las nuevas versiones disponibles sí se anuncian.
- **Índice de 73 libros:** búsqueda por nombre sin distinguir acentos ni mayúsculas, selección Antiguo/Nuevo Testamento y filas pulsables compactas. 46 libros antiguos + 27 nuevos (canon católico).
- **Navegación contextual:** desde una lectura se regresa a Inicio, Para mí, Guardados o al índice según de dónde se abrió. Se restaura el pasaje al recrear la actividad.
- **Sin EPUB:** cada pantalla explica qué falta y ofrece una acción real para importar, sin bloquear la exploración del índice, los mapas ni las obras.
- **Introducciones del EPUB Navarra:** SQLite asocia los extractos a la revisión local del EPUB para evitar mostrar introducciones de un archivo anterior; se permiten cargas parciales con ausencia explícita en libros que no se hayan reconocido.
- **Transparencia:** splash condicional, versión de ajustes derivada de la APK instalada y mensajes de error de token diferenciados. Sin enviar contenido editorial ni credenciales al repositorio.
- **Informe y evaluación:** `docs/INFORME_PRIMER_USO_0_7_2.md`. Revisión heurística del código, no estudio de usuarios real. Pendiente QA con EPUB privado en el móvil y pruebas TalkBack.

Conserva JDK17, compileSdk/targetSdk34, Gradle8.9, AGP8.7.3, las cinco pestañas, el acceso fijo a Mapas, `applicationId=com.scriptura.app` y el mismo keystore para actualizar sobre instalaciones existentes.

## Scriptura 0.7.3 · estudio de los lugares y lecturas renovadas

- **Recorridos bíblicos:** cada etapa numerada incluye lugar, acontecimiento narrado, pasajes seleccionados y acciones **Leer el pasaje**, **Situar en Google Maps** y **Conocer el lugar**. Los ocho itinerarios tienen 42 etapas narrativas contextualizadas. Las referencias se abren directamente en el lector del EPUB personal de Navarra; sin EPUB, se ofrece su importación, sin copiar ni publicar la edición.
- **Puntos del mapa:** al pulsar un marcador de ruta, libro o lugar se abre **Google Maps** con la posición señalada mediante Maps URLs (no OpenStreetMap). La capa cartográfica interactiva dentro de Scriptura sigue siendo OpenStreetMap, con la atribución © OpenStreetMap contributors. No se necesita una clave Google Maps SDK.
- **Zoom:** doble pulsación sobre el mapa, gesto de pinza con previsualización fluida y controles +/−; el lugar bajo los dedos se conserva al cambiar el nivel de zoom.
- **Inicio:** dos lecturas propuestas (una del Antiguo y otra del Nuevo Testamento), con una explicación explícita del criterio. **Ver otros dos pasajes** cambia la selección conservando la misma durante la navegación hasta renovar o pasar al día siguiente.
- **Imagen principal:** cuatro ilustraciones originales de escenas y paisajes evocadores de la Biblia, distribuidas como código gráfico **local** en la APK. Alternan cada día y al renovar Inicio; no muestran de nuevo el logo, que se conserva en la cabecera, ni necesitan conexión.
- **Textos de ayuda:** se han simplificado mensajes técnicos que distraían del estudio y se han convertido referencias antes estáticas en botones de lectura. Se mantienen únicamente avisos históricos necesarios cuando la identificación de un lugar es discutida o regional y la atribución cartográfica exigida por OpenStreetMap.
- **Validación:** pruebas unitarias del orden de etapas, relación con marcadores, enlaces a escrituras y coordenadas Google Maps. El emulador comprueba carga de teselas y presenta pantallas auténticas sin EPUB personal. Pendiente comprobar zoom multitáctil en móvil real, lectura del EPUB personal y vínculo desde todas las fichas.

El atlas reproduce lugares de geografía moderna, **no** una reconstrucción fiable de itinerarios antiguos; las etapas siguen un orden narrativo y no se deben interpretar como recorridos históricos exactos. Conserva la serie preliminar, el certificado de firma, `com.scriptura.app`, Java 17, SDK 34, Gradle 8.9 y AGP 8.7.3.


## Contexto histórico local (v0.7.4)

El lector incluye el desplegable «Contexto histórico», con 100 fichas introductorias offline sobre pueblos, grupos religiosos, trabajos, costumbres e instituciones. Explicaciones cortas y ampliables, bibliografía orientativa y distinción AT/NT. La base local \`scriptura_contexto.db\` es independiente de la Biblia privada. Véase \`docs/CONTEXTO_HISTORICO.md\`.
