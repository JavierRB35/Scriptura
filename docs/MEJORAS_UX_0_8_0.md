# Scriptura — mejoras de experiencia de usuario · 0.8.0

**Fecha:** 5 de noviembre de 2026
**Base:** 0.7.9 (`main`), rama `feature/ux-0-8-0`
**Método:** evaluación heurística del código de interfaz (auditoría de primer uso previa: `INFORME_PRIMER_USO_0_7_2.md`) e implementación directa. La validación automatizada cubre compilación y pruebas unitarias; **sigue pendiente la comprobación manual en teléfono real** (TalkBack, rotación, EPUB privado, voz).

## Cambios por hallazgo

| # | Fricción en 0.7.9 | Solución en 0.8.0 |
|---|---|---|
| P0-1 | Muro de ~12 botones tras el texto; el avance de capítulo quedaba enterrado | Fila compacta de acciones (favorito/copiar/compartir/escuchar/más) + IA destacada + «Más herramientas» desplegable |
| P0-2 | «Continuar lectura» sólo en la pestaña Biblia | Tarjeta «Seguir leyendo» como primer elemento de Inicio |
| P0-3 | Tipografía y aspecto enterrados en Ajustes; etiqueta de tamaño desfasada tras cambiarla | Botón «Aa» junto al título; hoja inferior con vista previa, tamaño aplicado al texto abierto sin perder scroll, 4 aspectos |
| P0-4 | Atrás inconsistente: resultados de búsqueda iban a Inicio | Pila de navegación; Atrás devuelve al formulario de búsqueda y a los orígenes reales |
| P1-5 | Chip «Hoy» de Inicio sin acción; parecía botón roto | Renueva las lecturas del día y su descripción lo explica |
| P1-6 | `render()` reseteaba el scroll al cambiar ánimo/fecha/favorito | Se conserva el desplazamiento en re-render de la misma página; transición sutil sólo al cambiar de página |
| P1-7 | Diálogo de capítulo con lista de hasta 150 filas | Rejilla de 5 columnas para libros largos; lista simple ≤15; «Elegir capítulo» lleva al índice con filtro |
| P1-8 | Diálogo de consentimiento IA en cada consulta | Casilla «No volver a preguntar durante esta sesión» (consentimiento permanente sigue en Ajustes) |
| P1-9 | Éxito del recordatorio mostrado como diálogo de error | Toast de confirmación |
| P1-10 | Misa exigía pulsar «Consultar» siempre | Autocarga del día actual (caché primero, red después); reintentos sólo manuales |
| P1-11 | «Definir» oculto en el desbordamiento del menú de selección | `SHOW_AS_ACTION_ALWAYS` |
| P2-12 | Modo oscuro sólo manual | Aspecto «Automático · seguir el sistema» resuelto en cada arranque |
| P2-13 | Glifos Unicode como iconos (render variable por fabricante) | Iconos vectoriales Material en pestañas, ajustes y acciones |
| P2-14 | Todo en diálogos centrados | Hojas inferiores para acciones contextuales (anotar, preguntar, definir, reflexión) |
| P2-15 | Sin escucha del texto | «Escuchar»: TTS local de Android (`ReaderSpeech`), sin red ni claves |
| P2-16 | «Para mí» estático (1 pasaje fijo por ánimo) | Rotación diaria entre 3 pasajes por estado |
| P2-17 | Historial duplicado en Biblia y Guardados | Historial sólo en Guardados; Biblia apunta allí |
| P2-18 | Sin exportación legible de anotaciones | Ajustes → «Exportar subrayados y notas (texto)» vía `ACTION_CREATE_DOCUMENT`; formato en `AnnotationExport` con pruebas |
| P2-19 | Corte seco entre pantallas | Fundido de 130 ms al cambiar de página |
| P2-20 | Barra inferior fija de 64 dp recortaba fuentes grandes | Altura flexible con mínimo, relleno adaptado |

## Nuevos componentes

- `ReaderSpeech.java`: síntesis de voz local con división de capítulos largos en fragmentos aceptados por el motor.
- `AnnotationExport.java`: texto legible agrupado por libro y capítulo (lógica pura, testeada).
- `DisplayModes`: nuevo modo `SYSTEM` persistido como `3`; los IDs previos no cambian.
- `StudyAnnotations.all()`: lectura ordenada de toda la biblioteca de anotaciones.
- Iconos vectoriales: `ic_tab_*`, `ic_action_*` (12 drawables).

## Validación

- [x] `:app:assembleDebug` local (SDK 34, Java 17, Gradle 8.9)
- [x] `:app:testDebugUnitTest` completa (incluye `DisplayModesTest` ampliado y `AnnotationExportTest` nuevo)
- [x] Workflow `android-apk.yml` actualizado a `v0.8.0-build.N`
- [ ] Instalación sobre 0.7.9 en teléfono real conservando EPUB, favoritos y anotaciones
- [ ] TalkBack, fuentes grandes y voz en dispositivo físico
- [ ] Importación EPUB y misa diaria con red real

**Criterio de publicación:** pre-release 0.8.0 si CI y firma pasan; sigue sin declararse 1.0.
