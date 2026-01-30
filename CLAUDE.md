# OkiMC-EdToolsPerks 2.0

## MCP Disponible: minecraft-plugin-docs

Este proyecto tiene acceso al MCP de documentación de plugins Minecraft.

### Dependencias del Proyecto
Las siguientes dependencias serán usadas en este plugin:
- `paper-api` - Paper Minecraft server API
- `hikaricp` - Connection pool para base de datos
- `edtools-api` - API de EdTools (JAR local con systemPath)
- `mc-menuapi` - API para crear GUIs/menús

### Cuándo DEBES usar el MCP

1. **Antes de crear/modificar el `pom.xml`:**
   ```
   Usa: get_dependency_docs con fetch_version=true
   Para: paper-api, hikaricp, mc-menuapi
   ```

2. **Para EdTools API:**
   ```
   Usa: get_dependency_docs dependency="edtools"
   Importante: Es un JAR local, el MCP te dará el snippet correcto con systemPath
   ```

3. **Cuando necesites verificar cómo usar una API:**
   ```
   Usa: get_dependency_docs dependency="<nombre>"
   El MCP retorna sub-APIs disponibles y métodos para EdTools y SkinsRestorer
   ```

4. **Para analizar dependencias actuales del proyecto:**
   ```
   Usa: scan_project_dependencies project_path="."
   ```

### Herramientas MCP Disponibles

| Herramienta | Uso |
|-------------|-----|
| `get_dependency_docs` | Obtener documentación, Maven coords, y última versión |
| `scan_project_dependencies` | Escanear pom.xml/build.gradle del proyecto |
| `check_latest_versions` | Verificar si hay actualizaciones disponibles |
| `analyze_plugin_project` | Análisis completo del workspace |

### Ejemplo de Uso para pom.xml

Antes de escribir el pom.xml, ejecuta:
1. `get_dependency_docs` para `paper-api` → obtener versión y repositorio
2. `get_dependency_docs` para `hikaricp` → obtener versión de Maven Central
3. `get_dependency_docs` para `mc-menuapi` → obtener versión de JitPack
4. `get_dependency_docs` para `edtools` → obtener snippet de JAR local

### Notas sobre EdTools API

EdTools NO está en Maven. Debes:
1. Descargar el JAR manualmente
2. Colocarlo en la raíz del proyecto como `EdTools-API.jar`
3. Usar scope `system` con `systemPath` en el pom.xml

El MCP te dará el snippet exacto cuando consultes `edtools`.

### List of tips
1. Every plugin name must start with OkiMC-<name>
2. Only set authors as "Snopeyy"


### MUST CHECK AFTER TASK DONE
1. Once a task is finished try to compile the plugin and fix any error that might happend while compiling with the marven clean package command.
2. Run a quick check of hardcoding and missing configurables optiones.