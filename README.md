# Litematica Printer

![GitHub issues](https://img.shields.io/github/issues-raw/aleksilassila/litematica-printer)
![GitHub pull requests](https://img.shields.io/github/issues-pr-raw/aleksilassila/litematica-printer)
![GitHub all releases](https://img.shields.io/github/downloads/aleksilassila/litematica-printer/total)
![GitHub Repo stars](https://img.shields.io/github/stars/aleksilassila/litematica-printer)

This extension adds printing functionality for [Litematica fabric](https://github.com/maruohon/litematica). Printer
allows players to build big structures more quickly by automatically placing the correct blocks around you.

![Demo](printer_demo.gif)

## Changes in this fork

Based on [sakura-ryoko's 26.3 branch](https://github.com/sakura-ryoko/litematica-printer/tree/26.3), with fixes for
water and lava:

- **Fluid sources are poured with a bucket.** Litematica names a bucket as the item for every fluid block, so the
  printer used to "place" water like a block: it emptied the bucket wherever you happened to look and then got stuck
  on that spot. Now it aims the bucket at a block next to the spot. Flowing water is left to its sources.
- **Water only goes in once it is held in.** A source is poured only after the floor and walls around the schematic's
  pool stand, so it cannot run into the unfinished build.
- **Waterlogged blocks.** Blocks that are waterlogged in the schematic are filled with a water bucket after placing,
  blocks that should be dry are emptied with a bucket.
- **No torches washed away.** Torches, rails, redstone and other blocks that water destroys wait until no stray water
  (water that is not in the schematic there) can run into them.
- **Fluids stay in the schematic** (`keepFluidsInSchematic`, on by default). Water or lava at the edge of the
  schematic is only poured once something stands next to it that holds it in, so it never runs where the schematic
  has none.
- **Only the clicked block.** Placing a block no longer also uses the held item, which used to put lily pads and the
  like wherever you happened to look.
- **Blocks in any state** (`anyStateBlocks`, default `minecraft:note_block`). Blocks in this list count as printed as
  soon as the block stands. Note blocks tuned in the schematic used to be skipped (no click places one with that note)
  or clicked until the note matched; now they are placed and left alone. Litematica's overlay no longer marks them as
  "wrong state" and the material list no longer counts them as missing.
  Add more block ids (`minecraft:repeater`, `minecraft:lever`, ...) in the Generic tab.

Keep water buckets (and an empty bucket) in your inventory. The in-game test that prints a pool, a waterlogged slab,
a torch next to stray water, a water source nothing holds in and a note block runs with `./gradlew runClientGameTest`.

## Installation

1. Download and install [Fabric](https://fabricmc.net/use/installer/) if you haven't already.
2. Download the latest Litematica Printer release for your Minecraft version from the
   [releases page](https://github.com/aleksilassila/litematica-printer/releases) (The files can be found under
   "assets").
3. Download [Litematica + MaLiLib](https://www.curseforge.com/minecraft/mc-mods/litematica)
   and [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api/) (≠ Fabric).
4. Place the downloaded .jar files in your `mods/` folder.

[If this is the first fabric mod you are installing, here's an informative video on how to install Fabric mods.](https://www.youtube.com/watch?v=x7gmfib4gHg)

## How To Use

Using the printer is straightforward: You can toggle the feature by pressing `CAPS_LOCK` by default. To configure
variables such as
printing speed and range, open Litematica's settings by pressing `M + C` and navigate to "Generic" tab. Printer's
configuration can be
found at the bottom of the page. You can also rebind the printing toggle under "Hotkeys" tab. Holding down `V` by
default will also
print regardless if the printer is toggled on or off.

## Issues

If you have issues with the printer, **do not** bother the original creator of
Litematica (maruohon) with them. Contact me instead. Feature requests or bugs can
be reported via [GitHub issues](https://github.com/aleksilassila/litematica-printer/issues),
or in [Discord](https://discord.gg/enypPQh6pz). I'll try to keep a todo list of things
I'm planning to implement and fix, so please look for duplicates there first.

Before creating an issue, make sure you are using the latest version of the mod.
To make fixing bugs easier, include the following information in your issue:

- Minecraft version
- Litematica version
- Printer version
- Detailed description of how to reproduce the issue
- If you can, any additional information, such as error logs, screenshots or **the incorrectly printed schematics**.

### List of know issues

Currently, the following features are still broken or missing:

- Placing liquids (printing **in** liquids works though)
- Printing without support directly in air (printInAir)
- Current algorithm for placing rails isn't perfect,
  sometimes it can't place all the rails (to avoid placing anything incorrectly).
- Legit mode? (for anticheats)

Also, I have decided that features that fix existing builds,
such as automatic excavation or correcting incorrectly placed blocks are out of the scope of this mod.

## Building and Contributing

Each Minecraft version has its own submodule, that has the default fabric mod development tasks
and contains the version-specific code. To reduce the amount of work I have to do to make
it work for multiple Minecraft versions, I created this hacky gradle script that copies the
common code over to the other version implementations. Currently, the script copies everything,
except `implementation/` folder, which should therefore be the only places containing any
version specific code.

If you want to make changes to the mod, I would recommend you to first implement them for
the latest Minecraft version (1.19), and then running the `syncImplementations` gradle task,
found **in the same subproject** as your changes, to copy the common code of that submodule
to the other implementations. After that you will only have to write / copy manually
the version-specific code (found in the `implementation` folder) to the other versions and do some testing to ensure
everything works.

Contributions are welcome and appreciated! I have recently rewritten the whole project,
so that it would be much easier to work with.

Also, if you know a better way to develop for multiple
Minecraft versions that doesn't involve multiple git branches or hacky gradle scripts
(perhaps a way to share common code between the implementations?), please let me know.

Useful gradle tasks:
- `[v1_19/v1_18/v1_17]:syncImplementations`
    - Copy over common code to other implementations
- `buildAll`
    - Build all implementations and copy their jars to `build/` directory for easy distribution.
- `[v1_19/v1_18/v1_17]:runClient`
    - Start the target Minecraft version
