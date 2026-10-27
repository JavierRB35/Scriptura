# Scriptura — auditoría de experiencia de primer uso

**Fecha:** 27 de octubre de 2026  
**Base examinada:** Scriptura 0.7.1-build.58 (`main`)  
**Revisión correctiva:** candidata 0.7.2, `feature/primer-uso-ux-0-7-1`  
**Método:** evaluación heurística de recorridos de un usuario nuevo a partir del código de las pantallas y del flujo de interfaz. **No equivale a una sesión de usabilidad observada con personas ni a una prueba sobre el teléfono del propietario.**

## Perfil adoptado

Persona que instala Scriptura por primera vez, no conoce GitHub, Gemini, la estructura de la Biblia ni por qué necesita importar un EPUB. Quiere explorar la aplicación sin riesgo de borrar archivos y comprender de inmediato sus cinco pestañas.

## Recorridos evaluados

1. **Primera apertura sin EPUB ni claves.** Ve el splash y la portada; debe distinguir explorar del paso opcional de importar Navarra.
2. **Buscar un libro.** Entra en un índice de 73 libros y necesita encontrar «Génesis», «Juan» o «Salmos» sin recorrer decenas de tarjetas.
3. **Leer un pasaje y regresar.** Abre desde Inicio, Favoritos, Para mí o índice; pulsar Atrás debe devolverlo al contexto correcto.
4. **Consultar Mapas y Arte.** Debe descubrir el acceso fijo a Mapas, reconocer cuándo hay conexión y saber que las pinturas son interpretaciones, no fotografías históricas.
5. **Utilizar Ajustes.** Encontrar importación, lectura y guía sin creer que el token privado de GitHub o una API de Gemini son obligatorios.
6. **Reemplazar un EPUB.** No se deben presentar las introducciones procedentes de un libro diferente como si fueran del nuevo archivo.

## Hallazgos y mejoras

| Prioridad | Fricción detectada en 0.7.1 | Mejora de la candidata 0.7.2 | Estado |
|---|---|---|---|
| P0 | El splash decía que comprobaba actualizaciones incluso sin token; nuevo usuario podía pensar que era requisito | Mensaje inicial condicional; destaca que no es obligatorio registrarse | Implementado |
| P0 | El acceso de GitHub aparecía en Inicio antes de que el usuario comprendiera la aplicación | La configuración de actualizaciones permanece en Ajustes y solo se prioriza un aviso **cuando existe una actualización real** | Implementado |
| P0 | Sin EPUB, Buscar y Guardados no explicaban el siguiente paso ni daban suficiente acceso a la importación | Mensaje de estado vacío con acción real «Importar mi Biblia» y rutas de exploración sin EPUB | Implementado |
| P0 | El índice de 73 libros era muy largo, sin filtro; cada entrada duplicaba un botón | Campo de búsqueda por título (insensible a acentos), filtros Antiguo/Nuevo y una tarjeta pulsable por libro | Implementado |
| P1 | Al volver de una lectura se iba a Buscar incluso si venía de otro sitio | Se conserva el destino de origen y el pasaje seleccionado al recrear la pantalla | Implementado |
| P1 | El icono del splash dominaba la primera pantalla e Inicio desaprovechaba altura | Imagen de portada reducida en Inicio sin alterar el logo de marca | Implementado |
| P1 | El usuario no tenía una guía repetible | Tarjeta inicial descartable y «Cómo utilizar Scriptura» en Ajustes | Implementado |
| P1 | Una reimportación podía conservar un extracto de introducción de un EPUB anterior | Base de introducciones ligada a la huella local de tamaño/fecha del EPUB; se reextrae si cambia | Implementado |
| P1 | Una edición parcial de Navarra con menos de 60 introducciones hacía fallar todas | Se admiten extracciones parciales y se informa del número real, sin inventar textos faltantes | Implementado |
| P1 | Error de Android Keystore se mostraba genéricamente como formato de token | Mensajes diferenciados sin exponer la credencial | Implementado |
| P2 | No existe verificación funcional reproducible de la importación editorial sobre **tu EPUB real** | Añadir prueba en móvil ARM64; comparar fichas de Gn, Sal, Mt, Lc, Jn y Ap con textos fuente | Pendiente |
| P2 | Algunas de las 62 obras catalogadas podrían no tener miniatura correcta o licencia suficiente | Auditoría individual de fichas Wikimedia Commons, atribución específica y límites de red | Pendiente, #1 |
| P2 | Las capas cartográficas usan geografía contemporánea, no límites de época | Atlas histórico con niveles de certeza y capas por períodos, bibliografía verificable | Pendiente, #4 |
| P2 | No está verificado TalkBack, pantalla pequeña, rotación y fuentes grandes en móvil real | Pruebas de accesibilidad y dispositivos variados | Pendiente, #3 |

## Decisiones de interfaz

La aplicación mantiene las **cinco pestañas** `Inicio · Buscar · Misa · Para mí · Guardados` y la banda persistente a **Mapas**. No se crean controles ficticios ni se pide una cuenta al inicio. El EPUB sigue siendo personal y opcional para explorar, pero **indispensable** para mostrar el texto íntegro y los comentarios protegidos de la edición de Navarra. Las claves de Gemini y GitHub no se comparten ni se incorporan en la APK.

## Criterios de aceptación para la integración

- [ ] Compilación `assembleDebug` y `testDebugUnitTest` en GitHub Actions.
- [ ] Comprobar el filtro sin acentos y la distribución de 46 libros antiguos y 27 nuevos.
- [ ] Generar APK release firmada con la misma clave de la serie 0.x; verificar `apksigner`.
- [ ] Realizar instalación de 0.7.2 sobre la release 0.7.1 sin borrar datos.
- [ ] Probar importación y resumen editorial en el móvil físico con el EPUB privado.
- [ ] Capturas reales y revisión TalkBack cuando haya un dispositivo/emulador disponible.

**Criterio de publicación:** la actualización puede distribuirse como **pre-release 0.7.2** si pasan CI y firma, pero las pruebas en dispositivo y el resto de funciones no probadas impiden etiquetarla como versión definitiva 1.0.
