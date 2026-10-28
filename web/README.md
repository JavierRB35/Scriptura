# Scriptura Web · Biblioteca personal de estudio (preliminar 0.1.2)

Versión **web local para PC**, desarrollada en paralelo a la versión Android 0.7.5.
No sustituye la APK ni modifica su certificado, datos privados o código. Implementación en
**Python 3.10+ (solo biblioteca estándar), SQLite, HTML, CSS y JavaScript**, sin proveedores
cloud, paquetes npm, cuentas ni servicios de terceros obligatorios.

## Ejecutar en Windows

1. Descarga el ZIP de la rama `feature/scriptura-web-library` desde el repositorio privado de GitHub y descomprímelo **fuera de carpetas compartidas o sincronizadas**.
2. Comprueba que tienes instalado Python 3.10 o posterior. Puedes verificarlo con `py -3 --version`.
3. Abre `web/INICIAR_SCRIPTURA_WINDOWS.bat`. El navegador se abrirá en **http://127.0.0.1:8765**.
4. Mantén abierta la consola mientras utilices Scriptura. Ciérrala con Ctrl+C al terminar.

Alternativamente, desde PowerShell en la carpeta `web`:

```powershell
py -3 launcher.py
```

En macOS y Linux:

```sh
cd web
python3 launcher.py
```

Si la apertura del navegador falla, abre manualmente **http://127.0.0.1:8765**.
El servidor solo escucha en 127.0.0.1: **no habilitar acceso externo ni publicar el puerto**.

## Mejoras de lectura y anotaciones (revisión de interfaz)

- La búsqueda de la sección **Biblia** y la barra superior de navegación del lector
  permanecen visibles al desplazarse por el texto. Desde esa barra se puede cambiar
  de libro, capítulo, ir al anterior/siguiente, guardar favorito o añadir un vínculo.
- Los 73 libros aparecen en tarjetas con **nombre y número de capítulos en líneas
  separadas**. La cuadrícula se adapta a la resolución del PC.
- El panel derecho permite **escribir, guardar, editar y eliminar notas privadas**
  asociadas al capítulo de la Biblia, a una sección EPUB o a una página PDF.
- Para relacionar una nota con una frase concreta, selecciona el texto del
  lector y pulsa **Usar selección** antes de **Guardar nota**. El botón **Ver cita**
  regresa al texto. En PDF, puedes introducir manualmente una cita.
- Las notas se guardan en SQLite, se incluyen en **Copia de seguridad** y
  permanecen tras reiniciar Scriptura. Se solicita confirmación antes de
  abandonar una lectura que tiene una anotación sin guardar.
- Las anotaciones del lector no se sincronizan con Android.
- Para mantener tus 35.000+ versículos y documentos al actualizar los archivos
  del programa, **conserva siempre la carpeta privada `web/data/`**. No borres
  ni sobrescribas `scriptura-web.db` o `uploads/`.

## Funcionalidades incluidas

- **Biblia privada**: índice de los 73 libros, importación por separado del EPUB personal
  de la *Sagrada Biblia de Navarra* y búsqueda local de versículos con el mismo
  marcador `tx-libro-capítulo-versículo` que reconoce la app Android.
- **EPUB**: lectura de libros importados por secciones ordenadas por el índice spine
  del archivo. El texto se extrae sin ejecutar JavaScript ni HTML activo del EPUB.
- **PDF**: lectura mediante el visor PDF del navegador, con navegación a una página.
- **Biblioteca documental**: añadir, buscar, clasificar y etiquetar documentos como
  *Libros, Mapas, Imágenes, Documentos* u *Otros*. Se admiten EPUB, PDF, PNG,
  JPG, JPEG, WEBP, GIF, TXT, MD, DOCX, XLSX, KML, GPX y ZIP.
- **Referencias entre texto y recursos**: seleccionar con el ratón un fragmento
  concreto de un capítulo bíblico o sección EPUB y asociarlo con cualquier archivo
  de la biblioteca o una URL web; se guarda la cita literal y su posición dentro
  de esa sección. Desde la relación se puede regresar al texto de origen.
- **Referencias PDF**: por página y cita indicada manualmente, asociable también a
  documentos, imágenes o URL. La selección exacta de coordenadas del PDF todavía
  no está implementada.
- **Anotaciones privadas por capítulo o cita**, con edición, borrado y regreso al texto.
- **Favoritos, historial, copias de seguridad ZIP** desde la interfaz y eliminación
  de documentos o relaciones con confirmación.

La interfaz mantiene la paleta azul noche y oro de Scriptura, con disposición de escritorio
y adaptación a pantallas más pequeñas.

## Dónde se guardan tus datos

La primera ejecución crea, automáticamente, la carpeta **`web/data/`**:

```text
web/
├── server.py
├── app.js
├── index.html
├── style.css
├── launcher.py
├── INICIAR_SCRIPTURA_WINDOWS.bat
└── data/                 ← PRIVADO; ignorado por Git
    ├── scriptura-web.db
    └── uploads/
```

No subir `data/`, copias de seguridad ni tus EPUB/PDF privados a GitHub.
No hay sincronización automática con los datos guardados por Android. Para trasladar
los datos privados Android a PC se necesitará un exportador local explícito.

**Copia de seguridad**: el botón descarga un ZIP que incluye SQLite y los archivos.
Este ZIP **no está cifrado**. Guárdalo en una ubicación segura.

**Restauración manual (con Scriptura detenido)**: conserva una copia de la carpeta
`web/data/` actual, descomprime el ZIP de confianza en esa carpeta, de manera que
`scriptura-web.db` y `uploads/` queden directamente bajo `data/`. Vuelve a
arrancar el servidor. No mezcles dos copias de datos diferentes.

Para cambiar la ubicación de los datos, define `SCRIPTURA_DATA_DIR` en el entorno
antes de iniciar el servidor.

## Limitaciones de esta fase

- El lector EPUB muestra **texto por secciones**: no preserva maquetación original,
  imágenes internas, enlaces de notas ni toda la estructura editorial del libro.
- Los PDF usan el visor del navegador. La vinculación por página + cita no almacena
  todavía la posición visual exacta del texto en la página.
- No hay editor avanzado de anotaciones, sincronización PC-móvil ni cuentas.
- Quedan por migrar módulos de la aplicación Android: atlas geográfico interactivo,
  galería pictórica, misa/calendario, propuestas «Para mí», Gemini, glosario y
  contexto histórico. No se debe declarar paridad total ni una versión estable 1.0.
- Los documentos DOCX/XLSX/KML/GPX/ZIP se pueden guardar, clasificar, descargar
  y vincular, pero no tienen previsualización enriquecida.
- El indexador bíblico específico solo reconoce el EPUB privado de Navarra
  compatible con el importador Android: **no interpreta cualquier Biblia EPUB**.
- No se adjunta al repositorio material editorial protegido por copyright.

## Pruebas

Desde `web/` ejecuta:

```sh
python -m unittest -v test_server.py
```

Incluye pruebas para servidor local, índice de libros, lectura EPUB de ejemplo,
importación de imágenes y PDF, vínculos a documentos/URL, rechazo de esquemas
peligrosos, copia de seguridad y eliminación de referencias dependientes.

La ejecución de esas pruebas y una verificación manual en el PC real siguen siendo
necesarias antes de considerar esta rama lista para uso habitual. 

## Novedades 0.1.2: asociaciones visibles y SVG

- Al seleccionar una frase con el ratón, Scriptura muestra un menú flotante con
  **Añadir nota**, **Vincular URL** y **Vincular archivo**.
- Para una nota: selecciona el fragmento → **Añadir nota** → escribe en el panel derecho
  → **Guardar nota**. También puedes escribir una nota general sin selección.
- Para una URL: selecciona el fragmento → **Vincular URL** → introduce la dirección
  HTTPS/HTTP y el nombre de la fuente → **Guardar vínculo**.
- Los fragmentos con anotaciones quedan resaltados en **amarillo**, con documentos
  o URL en **azul**, y con ambos tipos de recursos en **violeta**. Pulsa la frase
  resaltada para abrir una ficha de las asociaciones en el panel derecho.
- La barra fija de lectura separa ahora la navegación **Libro/Capítulo/Anterior/Siguiente**
  de las acciones de estudio y favoritos.
- Se admiten imágenes **SVG** con contenido vectorial seguro. Al importar se elimina
  contenido activo y metadatos no visuales. SVG con scripts, recursos externos o
  elementos no admitidos se rechazan con explicación. Se comprueba el contenido, no
  solo la extensión.
- La importación diferencia entre un rechazo del servidor y la pérdida de conexión con
  la instancia local de Python, mostrando recomendaciones específicas.

### Actualizar conservando tus libros y notas

1. Descarga una **Copia de seguridad** de la biblioteca actual.
2. Cierra Scriptura y su proceso Python.
3. Copia `web/data/` completa a la versión nueva (con `scriptura-web.db` y `uploads/`).
4. Sustituye el código de `web/`, pero **no sobrescribas ni borres `web/data/`**.
5. Inicia de nuevo Scriptura; la migración SQLite crea nuevas tablas sin eliminar las existentes.
6. Comprueba tus documentos, una nota de prueba y una URL vinculada.

La prueba con el archivo real `12_tribus_de_Israel.svg` debe realizarse en el PC del
usuario, dado que únicamente contamos con la captura del error y no con su contenido.
