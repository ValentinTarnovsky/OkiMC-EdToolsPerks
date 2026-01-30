# OkiMC-EdToolsPerks

Sistema de perks tipo gacha para EdTools. Los jugadores pueden hacer "rolls" para obtener perks que dan boosts permanentes a sus herramientas.

## Requisitos

- Paper/Spigot 1.20+
- Java 17+
- [EdTools](https://polymart.org/resource/edtools.4012) (dependencia requerida)
- PlaceholderAPI (opcional)

## Caracteristicas

### Sistema Gacha
- **6 categorias de rareza**: Comun, Poco Comun, Rara, Epica, Legendaria, Morada
- **Sistema de Pity**: Garantiza perk de categoria alta despues de X rolls sin obtener una
- **Animacion de roll**: GUI animada al momento de hacer roll
- **Niveles de perk**: Los perks pueden subir de nivel al obtener duplicados

### Perks por Herramienta
- Cada herramienta de EdTools tiene su propio pool de perks
- Los perks dan boosts a diferentes currencies (orbes, monedas, exp, etc.)
- Soporte para boosters de encantamientos globales

### Base de Datos
- SQLite (por defecto) o MySQL
- Connection pooling con HikariCP
- Operaciones asincronas

### Integracion
- **EdTools**: Usa la moneda configurada para comprar rolls
- **PlaceholderAPI**: Placeholders para mostrar info de perks

## Comandos

| Comando | Descripcion | Permiso |
|---------|-------------|---------|
| `/perks` | Abre el menu principal | `edtoolsperks.use` |
| `/perks help` | Muestra ayuda | `edtoolsperks.use` |
| `/perks reload` | Recarga configuraciones | `edtoolsperks.admin.reload` |
| `/perks rolls <player> <add/set/remove> <amount>` | Gestiona rolls | `edtoolsperks.admin.rolls` |
| `/perks perk <player> <perk> <level>` | Asigna perk a jugador | `edtoolsperks.admin.perks` |
| `/perks removeperk <player> <tool>` | Remueve perk de herramienta | `edtoolsperks.admin.perks` |
| `/perks resetpity <player>` | Reinicia contador de pity | `edtoolsperks.admin.pity` |
| `/perks debug` | Toggle modo debug | `edtoolsperks.admin.debug` |

**Aliases**: `/perk`, `/edtoolsperks`, `/etp`

## Permisos

| Permiso | Descripcion | Default |
|---------|-------------|---------|
| `edtoolsperks.*` | Acceso completo | op |
| `edtoolsperks.use` | Uso basico (GUI, info) | true |
| `edtoolsperks.admin` | Todos los comandos admin | op |
| `edtoolsperks.admin.reload` | Recargar configs | op |
| `edtoolsperks.admin.rolls` | Gestionar rolls | op |
| `edtoolsperks.admin.perks` | Gestionar perks | op |
| `edtoolsperks.admin.pity` | Resetear pity | op |
| `edtoolsperks.admin.debug` | Modo debug | op |

## Placeholders

Requiere PlaceholderAPI.

### Generales
- `%edtoolsperks_rolls%` - Rolls disponibles
- `%edtoolsperks_pity%` - Contador de pity actual
- `%edtoolsperks_pity_threshold%` - Threshold de pity
- `%edtoolsperks_pity_progress%` - Progreso (count/threshold)
- `%edtoolsperks_pity_percent%` - Progreso en porcentaje
- `%edtoolsperks_perk_count%` - Numero de perks activos
- `%edtoolsperks_animations%` - Estado de animaciones

### Por Herramienta
Reemplaza `<tool>` con el ID de herramienta de EdTools (ej: `pickaxe`, `axe`, `hoe`, `shovel`)

- `%edtoolsperks_perk_<tool>%` - Nombre del perk (con color)
- `%edtoolsperks_perk_<tool>_level%` - Nivel del perk
- `%edtoolsperks_perk_<tool>_category%` - Categoria
- `%edtoolsperks_perk_<tool>_boost%` - Descripcion del boost
- `%edtoolsperks_has_perk_<tool>%` - true/false

## Configuracion

### config.yml
- Configuracion de base de datos
- Definicion de categorias y sus propiedades
- Sistema de pity (threshold, categoria garantizada)
- Configuracion de animacion
- Nombres de display para currencies y herramientas

### perks.yml
- Definicion de todos los perks disponibles
- Cada perk tiene: nombre, descripcion, herramienta, categoria, chance, niveles con boosts

### messages.yml
- Todos los mensajes del plugin (100% configurable)

### guis.yml
- Configuracion de las interfaces graficas

## Instalacion

1. Descargar el JAR de [Releases](../../releases)
2. Colocar en la carpeta `plugins/`
3. Asegurar que EdTools esta instalado
4. Reiniciar el servidor
5. Configurar `config.yml` y `perks.yml` segun necesidad

## Compilacion

```bash
mvn clean package
```

El JAR se genera en `target/OkiMC-EdToolsPerks-2.0.0.jar`

## Autor

**Snopeyy** - [OkiMC](https://okimc.com)

## Licencia

Uso privado - OkiMC Network
