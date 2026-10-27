# Contexto histórico · Scriptura 0.7.4

## Uso
El lector ofrece un único desplegable **Contexto histórico** debajo del pasaje; empieza cerrado para no desplazar los controles de lectura. Cada ficha detectada muestra una definición breve y permite desplegar explicaciones ampliadas y bibliografía orientativa. No hay pestañas adicionales, ni consultas a internet al abrir el desplegable.

## Modelo de datos
- Base local **scriptura_contexto.db**, separada de `scriptura.db` y de los datos personales. El código utiliza `SQLiteOpenHelper` con versión de esquema 1.
- Los 100 conceptos iniciales se distribuyen en tres archivos de recursos JSON y se importan únicamente cuando se crea la base. Sus futuras migraciones no tocan EPUB, notas, favoritos ni credenciales.
- Cada ficha contiene ID, nombre, grupo, período, texto breve y ampliado, términos equivalentes, alcance histórico (Antiguo Testamento, Nuevo Testamento o ambos), advertencia de incertidumbre y bibliografía de orientación.
- Reconocimiento por palabras o expresiones **completas**, insensible a mayúsculas y tildes y filtrado por período general del libro. No genera definiciones para palabras que no estén en el catálogo ni utiliza Gemini sin permiso.

## Bibliografía académica de orientación
- Catherine Hezser (ed.), *The Oxford Handbook of Jewish Daily Life in Roman Palestine* (Oxford University Press, 2010). https://doi.org/10.1093/oxfordhb/9780199216437.001.0001
- E. P. Sanders, *Judaism: Practice and Belief, 63 BCE–66 CE* (1992). https://www.jstor.org/stable/j.ctt17mcs1x
- Ann E. Killebrew y Margreet Steiner (eds.), *The Oxford Handbook of the Archaeology of the Levant* (2013). https://academic.oup.com/edited-volume/34497
- Victor H. Matthews, *The History of Bronze and Iron Age Israel* (2018). https://doi.org/10.1093/oso/9780190231149.001.0001
- Sean Freyne, «Galilee and Judaea in the first century», *The Cambridge History of Christianity* (2008). https://www.cambridge.org/core/books/abs/cambridge-history-of-christianity/galilee-and-judaea-in-the-first-century/84FB3F1D56614D7AFF4C1FEAFBD23F69
- Robert T. Anderson y Terry Giles, *The Samaritan Pentateuch* (Society of Biblical Literature, 2012). https://www.jstor.org/stable/j.ctt32bzhn

**Estas son referencias generales por tema y época, no citas de página verificadas individualmente para cada una de las 100 fichas.** Los resúmenes son originales, breves y expresamente orientativos. No sustituyen la investigación histórica ni se debe inferir certeza donde hay debates arqueológicos.

## Bibliografía narrativa aportada por el usuario
La serie *Caballo de Troya*, de J. J. Benítez, es una **referencia complementaria para imaginar y proponer preguntas** sobre vida cotidiana y costumbres (catálogo: https://planetabenitez.com/coleccion/coleccion-caballo-de-troya/). No se utiliza como fundamento académico de afirmaciones históricas, porque emplea una trama de viaje temporal y reconstrucción narrativa. No se copia ni distribuye contenido protegido.

## Criterios de calidad
- No presentar a fariseos o samaritanos como grupos monolíticos.
- Diferenciar la época de Canaán del contexto romano de los Evangelios.
- Señalar reconstrucciones e incertidumbres; los nombres de oficio y sus condiciones sociales varían.
- Revisar progresivamente cada ficha con referencias específicas y páginas de origen.
- Sin EPUB incluido en GitHub o en la aplicación distribuida.

## Pruebas
La CI valida 100 fichas, unicidad de identificadores, referencias y formato; JUnit prueba coincidencias de palabra completa, tildes, exclusión de coincidencias parciales y filtrado AT/NT. Las capturas y la verificación de la experiencia real sobre el EPUB siguen requiriendo pruebas en dispositivo.
