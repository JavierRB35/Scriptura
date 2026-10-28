# Anotaciones de estudio en Scriptura Android (rama experimental)

## Alcance
- En la Biblia importada desde el EPUB privado de Navarra, pulsación prolongada -> seleccionar fragmento **dentro de un único versículo** -> `Anotar`.
- Tipos: **nota escrita**, **URL de mapa**, **enlace web** y **atlas bíblico interno** (del libro al que pertenece el pasaje).
- El texto seleccionado queda resaltado (amarillo para nota, verde para mapa/atlas y azul para web). Un toque corto sobre el resaltado abre la anotación completa en un diálogo desplazable.
- Las anotaciones también figuran bajo el texto para facilitar el acceso con lectores de pantalla o cuando no se puede tocar un resaltado.
- Desde el diálogo se puede editar, eliminar previa confirmación y abrir el enlace asociado.
- Alternativa accesible: `Añadir nota a un versículo` en la ficha del pasaje. Permite guardar notas sin seleccionar letras.

## Identidad de anotación y almacenamiento
- SQLite independiente: `scriptura-study-annotations.db`, privado del dispositivo.
- Cada entrada contiene libro, capítulo, versículo, fragmento literal, desplazamiento relativo al texto del versículo, tipo, comentario y URL.
- Se comprueba la coincidencia del texto antes de mostrar el resaltado. Si cambia la edición o texto y no puede reanclarse sin ambigüedad, **no se resalta en otra posición**; permanece accesible en la lista de notas del versículo.
- No se modifica el archivo EPUB del usuario, la tabla de versículos, la firma, el paquete Android ni los permisos de la aplicación.
- Los enlaces se restringen a HTTP/HTTPS con host. Su apertura requiere la acción voluntaria del usuario. No se envían anotaciones a servicios externos.
- Las notas se conservan al actualizar la APK firmada con la misma clave y `applicationId`. **No hay todavía copia de seguridad, transferencia entre móviles ni sincronización con la versión web**.

## Fuera de alcance
- No se modifica el lector PDF ni se añaden anotaciones sobre páginas PDF.
- No permite seleccionar fragmentos que atraviesen varios versículos; se debe anotar cada versículo o usar una nota general.
- El atlas interno abre el mapa asociado al **libro bíblico**, no sitúa automáticamente el pasaje en un punto geográfico exacto.

## Validación necesaria antes de fusionar
1. Compilar con Android SDK 34 + JDK 17 + AGP 8.7.3 + Gradle 8.9; ejecutar `AnnotationAnchorsTest`.
2. Actualizar sobre instalación anterior con la **APK Release firmada** (no APK debug), comprobar favoritos, historial, clave Gemini, EPUB importado.
3. Abrir un pasaje, seleccionar una frase del versículo, pulsar Anotar, guardar texto, cerrar la app y volver: resaltado, globo y texto completo deben permanecer.
4. Editar la nota y eliminarla previa confirmación; probar dos notas sobre el mismo fragmento.
5. Repetir para URL web, URL de Google Maps/OpenStreetMap y vínculo al atlas interno. Comprobar también URLs maliciosas (`javascript:`, `file:`) rechazadas.
6. Verificar que `Definir`, Copiar, Compartir, Seleccionar todo y la pulsación prolongada conservan su funcionamiento.
7. Verificar selección parcial de un capítulo, un versículo aislado y selección cruzando dos versículos (rechazo explícito).
8. Revisar accesibilidad, rotación, textos largos y navegación hacia atrás tras abrir atlas o navegador.
