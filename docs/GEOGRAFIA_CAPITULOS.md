# Lugares del capítulo y catálogo ampliable

La acción recupera el capítulo completo de `BibleStore`, aunque se haya abierto
un fragmento. Mapa y lista se presentan sobre el lector, sin redibujarlo al cerrar.

## Datos y alcance

`geografia_catalogo.json`: 1.342 identidades de lugar de OpenBible.info, 1.309 con
una propuesta de coordenadas y 33 sin ubicación. Son fichas básicas generadas
desde metadatos, no 1.342 estudios históricos revisados. Se conservan las 36
fichas editoriales anteriores. Las regiones tienen puntos orientativos; no se
dibujan rutas históricas supuestas ni fronteras no documentadas.

Fuente fijada: `openbibleinfo/Bible-Geocoding-Data`, commit
`7eb18a5ee62f27b9b93bd6689ea272d76dd23b8f`. Atribución: OpenBible.info, CC BY 4.0.
Licencia completa: `OPENBIBLE-LICENSE.txt`. Se transforman nombres y referencias;
no se redistribuyen traducciones bíblicas, imágenes ni geometrías poligonales OSM.
Nombres españoles: correspondencias editoriales e instantánea Wikidata (CC0,
2026-11-03). Cuando falta equivalencia española se conserva el nombre de origen.

El índice externo contiene referencias de 66 libros y su numeración inglesa;
no acredita cobertura completa de los 73 libros de Navarra ni equivalencia de
todas sus numeraciones. En los deuterocanónicos se buscan nombres españoles
conocidos, sin atribuir exhaustividad. Encontrar lugares no equivale a completar
el análisis: los resultados locales se consideran parciales por defecto.

Hechos 16 tiene una capa contextual de 15 lugares, contrastada con
<https://bible.usccb.org/es/bible/acts/16>. Distingue visitas, referencias,
procedencias y destinos no alcanzados. Tiatira es procedencia de Lidia, no una
etapa de Pablo. Antes de aplicar esta capa como revisada se contrasta que el
texto local contenga los nombres y los 40 versículos del capítulo.

## Persistencia

La base `scriptura_geografia.db` es independiente del EPUB y se incluye en la
copia y restauración existentes. Tablas: `geo_places` (datos iniciales y lugares
aprendidos con origen separado), `geo_occurrences` (referencias), `geo_analyses`
(descripciones propias del capítulo), `geo_meta` (versión del catálogo).

La clave de cada análisis combina versión, libro, capítulo y SHA-256 del texto
completo. Cambiar de traducción invalida la coincidencia sin borrar lo anterior.
Actualizar los datos iniciales conserva los lugares y análisis aprendidos.
Los registros aprendidos se recuperan también como antecedentes en otros
capítulos del mismo libro, indicando su contexto de origen. No se asignan
coordenadas ni se equiparan automáticamente lugares homónimos de otros libros.

Se consulta SQLite antes de la IA. Solo al seleccionar la opción, y previa
autorización geográfica, puede enviarse a Gemini el capítulo y los nombres
candidatos. Se reutiliza el respaldo Groq únicamente si ya estaba autorizado.
No se envían el EPUB entero, las notas ni el historial. La autorización se revoca
en Ajustes y no se concede mediante una restauración de copia. Los análisis de
IA guardados se reutilizan; una nueva revisión requiere acción expresa.

El contrato JSON geográfico es independiente del comentario breve. No recorta a
9.000 caracteres: rechaza capítulos superiores a 60.000 en lugar de truncarlos.
Se rechazan respuestas truncadas, referencias inexistentes, nombres ausentes
del versículo y correspondencias incompatibles con el catálogo. No se aceptan
coordenadas de IA. Un lugar nuevo permanece en la lista sin marcador hasta que
exista identificación documentada. Una respuesta no sobrescribe las coordenadas
del catálogo ni elimina lugares identificados localmente. Generado no significa
revisado, aunque el modelo declare su análisis completo.

## Regeneración y QA

```sh
python tools/build_geography_catalog.py /ruta/Bible-Geocoding-Data
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

El generador usa la instantánea `tools/geography_es_labels.json` y no necesita
red. Metadatos: commit de origen, SHA-256, licencia y transformaciones.
`GeographyDataTest` verifica datos, Hechos 16, límites de nombres, huellas de
caché, referencias falsas y lugares sin coordenadas. La actividad exclusiva de
debug verifica SQLite real, reapertura, instantánea y mapa con texto sintético:

```sh
adb shell am start -n com.scriptura.app/.GeographyQaActivity
adb shell run-as com.scriptura.app cat files/geography-qa-result.txt
```

No usa EPUB privado ni proveedores reales. La APK de debug usa firma de pruebas;
no sustituye una actualización firmada con el certificado habitual del usuario.
