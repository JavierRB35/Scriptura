# Scriptura

Aplicación personal Android para la lectura de una Biblia católica de 73 libros, búsquedas y reflexiones asistidas por IA.

## Estado

**Desarrollo preliminar: serie 0.x.** No publicar como 1.0 hasta aprobación expresa.

## Reglas de seguridad

- La Biblia de Navarra es aportada por su propietario e importada localmente: **no se sube al repositorio ni se incluye en el APK público**.
- No se versionan claves Gemini, keystores `.jks`/`.keystore`, contraseñas ni secretos.
- Todos los APK de actualización se firman con un **keystore persistente** administrado mediante GitHub Actions Secrets.
- `applicationId` permanente: `com.scriptura.app`.
- GitHub Actions compila en JDK 17 con SDK 34 y AGP 8.7.3; la compilación local seguirá preparada para AndroidIDE-Rv2 y Gradle 8.9.

## Hoja de ruta

1. Fundación Android y firma reproducible.
2. Importador de EPUB local y validación por versículos.
3. Buscador, navegación de libros y comentarios.
4. Gemini API con credenciales almacenadas en Android Keystore.
5. Calendario y lecturas litúrgicas validadas (traducción identificada).
6. Lecturas aleatorias y personalizadas, favoritos e historial.

**Nunca subir al repositorio el EPUB original ni datos personales.**
