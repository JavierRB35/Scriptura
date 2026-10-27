# Scriptura · Identidad visual v2.0

Recursos y reglas aplicados desde el archivo entregado «Scriptura_UI_Developer_Pack v2.0.zip» (27/10/2026).

## Diseño
- Azul noche: `#0D2338`; oro: `#DAA85C`; pergamino: `#F5E7C8`; azul gris: `#587A9A`; carbón: `#1E2428`; gris: `#E5E7E8`; papel: `#FFFCF6`.
- Títulos de marca/editoriales con serif del sistema; interfaz con sans nativo, sin descargar ni redistribuir fuentes.
- Cabecera con emblema y acceso a ajustes; navegación inferior de **cinco** destinos reales: Inicio, Buscar, Misa, Para mí, Guardados.
- Lector y atlas son páginas internas, no pestañas adicionales.
- Los recursos `drawable/ic_scriptura_*` y `mipmap-anydpi-v26/ic_launcher*` provienen del ZIP. La pantalla conceptual y la lámina de marca son **referencias visuales** y no capturas ni funciones implementadas.

## Recursos
- `app/src/main/res/values/scriptura_colors.xml`
- `app/src/main/res/values/scriptura_styles.xml`
- Iconos vectoriales Android de primer plano, fondo y versión monocroma
- Manifest con `@mipmap/ic_launcher` y `@mipmap/ic_launcher_round`

## Garantías
- Se preserva Java 17, SDK34, minSdk26, AGP8.7.3, Gradle8.9.
- Se preserva `applicationId=com.scriptura.app` y la misma firma de las releases, sin exponer secretos.
- No se añade funcionalidad ficticia, no se cargan imágenes externas y no se modifica el EPUB privado.
- Versión en revisión: **0.6.0** (no es versión estable).
- Pruebas e incidencias: #1, #2, #3, #4.

## Estado de QA
La compilación y los unit tests de GitHub Actions se deberán verificar en el PR.
Las capturas de emulador/dispositivo deben capturarse **realmente**; ningún mockup conceptual debe figurar como evidencia.
La instalación sobre release anterior y persistencia de datos debe verificarse con un dispositivo/emulador y documentarse expresamente.
