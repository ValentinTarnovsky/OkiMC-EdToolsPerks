# OkiMC-EdToolsPerks

Gacha-style perk system for EdTools. Players can roll for perks that provide permanent boosts to their tools.

## Requirements

- Paper/Spigot 1.20+
- Java 17+
- [EdTools](https://polymart.org/resource/edtools.4012) (required dependency)
- PlaceholderAPI (optional)

## Features

### Gacha System
- **6 rarity tiers**: Common, Uncommon, Rare, Epic, Legendary, Mythic (Morada)
- **Pity system**: Guarantees high-tier perk after X rolls without getting one
- **Roll animation**: Animated GUI when rolling
- **Perk levels**: Perks can level up when getting duplicates

### Per-Tool Perks
- Each EdTools tool has its own perk pool
- Perks boost different currencies (orbs, coins, exp, etc.)
- Support for global enchantment boosters

### Database
- SQLite (default) or MySQL
- Connection pooling with HikariCP
- Async operations

### Integration
- **EdTools**: Uses configured currency to purchase rolls
- **PlaceholderAPI**: Placeholders to display perk info

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/perks` | Opens main menu | `edtoolsperks.use` |
| `/perks help` | Shows help | `edtoolsperks.use` |
| `/perks reload` | Reloads configurations | `edtoolsperks.admin.reload` |
| `/perks rolls <player> <add/set/remove> <amount>` | Manage rolls | `edtoolsperks.admin.rolls` |
| `/perks perk <player> <perk> <level>` | Assign perk to player | `edtoolsperks.admin.perks` |
| `/perks removeperk <player> <tool>` | Remove perk from tool | `edtoolsperks.admin.perks` |
| `/perks resetpity <player>` | Reset pity counter | `edtoolsperks.admin.pity` |
| `/perks debug` | Toggle debug mode | `edtoolsperks.admin.debug` |

**Aliases**: `/perk`, `/edtoolsperks`, `/etp`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `edtoolsperks.*` | Full access | op |
| `edtoolsperks.use` | Basic usage (GUI, info) | true |
| `edtoolsperks.admin` | All admin commands | op |
| `edtoolsperks.admin.reload` | Reload configs | op |
| `edtoolsperks.admin.rolls` | Manage rolls | op |
| `edtoolsperks.admin.perks` | Manage perks | op |
| `edtoolsperks.admin.pity` | Reset pity | op |
| `edtoolsperks.admin.debug` | Debug mode | op |

## Placeholders

Requires PlaceholderAPI.

### General
- `%edtoolsperks_rolls%` - Available rolls
- `%edtoolsperks_pity%` - Current pity counter
- `%edtoolsperks_pity_threshold%` - Pity threshold
- `%edtoolsperks_pity_progress%` - Progress (count/threshold)
- `%edtoolsperks_pity_percent%` - Progress as percentage
- `%edtoolsperks_perk_count%` - Number of active perks
- `%edtoolsperks_animations%` - Animation status

### Per Tool
Replace `<tool>` with EdTools tool ID (e.g., `pickaxe`, `axe`, `hoe`, `shovel`)

- `%edtoolsperks_perk_<tool>%` - Perk name (with color)
- `%edtoolsperks_perk_<tool>_level%` - Perk level
- `%edtoolsperks_perk_<tool>_category%` - Category
- `%edtoolsperks_perk_<tool>_boost%` - Boost description
- `%edtoolsperks_has_perk_<tool>%` - true/false

## Configuration

### config.yml
- Database configuration
- Category definitions and properties
- Pity system (threshold, guaranteed category)
- Animation settings
- Display names for currencies and tools

### perks.yml
- Definition of all available perks
- Each perk has: name, description, tool, category, chance, levels with boosts

### messages.yml
- All plugin messages (100% configurable)

### guis.yml
- GUI configuration

## Installation

1. Download the JAR from [Releases](../../releases)
2. Place in `plugins/` folder
3. Make sure EdTools is installed
4. Restart the server
5. Configure `config.yml` and `perks.yml` as needed

## Building

```bash
mvn clean package
```

The JAR is generated at `target/OkiMC-EdToolsPerks-2.0.0.jar`

## Author

**Snopeyy** - [OkiMC](https://okimc.com)

## License

Private use - OkiMC Network
