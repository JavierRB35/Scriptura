# Anotaciones de estudio en Scriptura Android

## Alcance
- En la Biblia importada desde el EPUB privado de Navarra, pulsación prolongada -> seleccionar texto **dentro de uno o varios versículos del mismo capítulo** -> `Anotar`, `Subrayar` o `Interpretar con IA`.
- Tipos: **nota escrita**, **URL de mapa**, **enlace web**, **atlas bíblico interno** (del libro al que pertenece el pasaje) e **interpretación IA** asociada al texto.
- El texto seleccionado queda resaltado (amarillo para nota, verde para mapa/atlas y azul para web). Un toque corto sobre el resaltado abre la anotación completa en un diálogo desplazable.
- Las anotaciones también figuran bajo el texto para facilitar el acceso con lectores de pantalla o cuando no se puede tocar un resaltado.
- Desde el diálogo se puede editar, eliminar previa confirmación y abrir el enlace asociado.
- Alternativa accesible: `Añadir nota por versículos` en la ficha del pasaje. Se elige versículo inicial y final, pudiendo coincidir; la nota aparece en la lista aunque no haya una selección literal que resaltar.

## Interpretaciones IA vinculadas al texto
- Al seleccionar un fragmento y ejecutar «Interpretar con IA», el diálogo muestra «Cerrar» (sin guardar) y «Añadir al texto» (activo únicamente cuando existe una respuesta válida). Al añadir se guarda la interpretación íntegra vinculada al texto exacto, libro, capítulo y versículos, se cierra el diálogo y se recupera la posición de lectura previa.
- Al ejecutar la interpretación general sin texto seleccionado, «Añadir al texto» asocia la respuesta al **primer versículo del capítulo**, sin sustituir el texto bíblico. Se puede acceder a esa interpretación desde la anotación del versículo o desde «Mis subrayados y anotaciones».
- La interpretación IA se almacena como un nuevo tipo en la base SQLite de anotaciones existente, **sin migración de esquema**. Puede consultarse, exportarse o eliminarse como otras anotaciones, pero no se transforma silenciosamente en una nota escrita. El resultado solo se almacena si el usuario pulsa «Añadir al texto».

## Biblioteca de subrayados y anotaciones
- En **Biblia → Mis subrayados y anotaciones** se reúnen los textos subrayados y las notas (incluidos enlaces y mapas asociados), mostrando su fuente como libro, capítulo y versículo o intervalo.
- La biblioteca ordena los registros por el canon de 73 libros de `Books.NAMES`: primero Antiguo Testamento y después Nuevo Testamento, dentro de cada libro por capítulo y versículo.
- Al principio presenta un índice de los libros con registros, con su número total de subrayados y anotaciones entre paréntesis. Pulsar un libro desplaza la página a su sección, sin abandonar la biblioteca. Cada entrada abre el capítulo en el versículo de origen.
- Los datos se leen de la base SQLite existente; no se modifican su esquema, contenido, copias de seguridad ni las anotaciones previas. Si no hay anotaciones, se muestra una indicación para crearlas.

## Identidad de anotación y almacenamiento
- SQLite independiente: `scriptura-study-annotations.db`, privado del dispositivo.
- Cada entrada contiene libro, capítulo, versículos inicial y final, fragmento literal, desplazamiento relativo al inicio del versículo inicial, tipo, comentario y URL. La base pasa de v1 a v2 mediante una columna añadida sin borrar ni reescribir las anotaciones anteriores (las entradas v1 conservan como final su único versículo).
- Se comprueba la coincidencia del texto antes de mostrar el resaltado. Si cambia la edición o texto y no puede reanclarse sin ambigüedad, **no se resalta en otra posición**; permanece accesible en la lista de notas del versículo.
- No se modifica el archivo EPUB del usuario, la tabla de versículos, la firma, el paquete Android ni los permisos de la aplicación.
- Los enlaces se restringen a HTTP/HTTPS con host. Su apertura requiere la acción voluntaria del usuario. No se envían anotaciones a servicios externos.
- Las notas se conservan al actualizar la APK firmada con la misma clave y `applicationId`. La copia de seguridad local de Scriptura incluye la base de anotaciones y acepta los esquemas v1 y v2; no hay sincronización automática con la versión web.

## Fuera de alcance
- No se modifica el lector PDF ni se añaden anotaciones sobre páginas PDF.
- No se admiten selecciones que atraviesen varios **capítulos**: cada anotación o subrayado queda dentro de un capítulo. Si el texto de otra edición no coincide, se conserva el registro pero se omite el resaltado para evitar anclajes incorrectos.
- El atlas interno abre el mapa asociado al **libro bíblico**, no sitúa automáticamente el pasaje en un punto geográfico exacto.

## Validación necesaria antes de fusionar
1. Compilar con Android SDK 34 + JDK 17 + AGP 8.7.3 + Gradle 8.9; ejecutar `AnnotationAnchorsTest` y `AnnotationExportTest`. Validar la migración v1 -> v2 y que la recuperación de copias anteriores siga funcionando.
2. Actualizar sobre instalación anterior con la **APK Release firmada** (no APK debug), comprobar favoritos, historial, clave Gemini, EPUB importado.
3. Abrir un pasaje, seleccionar una frase del versículo, pulsar Anotar, guardar texto, cerrar la app y volver: resaltado, globo y texto completo deben permanecer.
4. Editar la nota y eliminarla previa confirmación; probar dos notas sobre el mismo fragmento.
5. Repetir para URL web, URL de Google Maps/OpenStreetMap y vínculo al atlas interno. Comprobar también URLs maliciosas (`javascript:`, `file:`) rechazadas.
6. Verificar que `Definir`, Copiar, Compartir, Seleccionar todo y la pulsación prolongada conservan su funcionamiento.
7. Verificar selección parcial de un capítulo, un versículo aislado y selecciones que abarcan dos o más versículos (aceptación), así como límites inválidos y capítulos diferentes (rechazo).
8. Revisar accesibilidad, rotación, textos largos y navegación hacia atrás tras abrir atlas o navegador.
