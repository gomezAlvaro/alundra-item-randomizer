# Alundra + Ocarina of Time: setup for 3 players (each plays both games)

Status: generation, patching and both clients were tested together on one machine (see "What was tested" at the
end). Alundra is not in upstream Archipelago, so everybody runs the fork.

## What each person needs

| Item | For | Notes |
|---|---|---|
| Python **3.13** (3.11.9 to 3.13) | Everyone | Python 3.14 breaks Archipelago's settings and the OoT world |
| Git | Everyone | To clone the fork |
| Alundra (USA) v1.1 disc image (`.bin` + `.cue`) | Alundra | Your own copy, 600,359,760 bytes |
| PlayStation BIOS | Alundra | Only OpenBIOS has been tried; a real BIOS should work too |
| Ocarina of Time **v1.0 (USA)** ROM, `.z64` | OoT, and the **host** | md5 `5bd1fe107bf8106b2ab6650abecd54d6`. Rev 1, Rev A and other versions are refused |
| BizHawk 2.11.1 (x64) | Both | You run it twice at once, one window per game |

Nobody shares discs or ROMs. Each person patches their own. The host also needs the OoT ROM, because generating an
OoT slot reads it.

## 1. Install the fork (everyone)

```bash
git clone --branch alundra https://github.com/gomezAlvaro/Archipelago.git
cd Archipelago
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python Launcher.py
```

Make sure `python` is 3.13. The first launch creates `host.yaml`. Set the game files in it (forward slashes):

```yaml
alundra_settings:
  rom_file: "C:/path/to/Alundra (USA) (v1.1).bin"
oot_options:
  rom_file: "C:/path/to/Legend of Zelda, The - Ocarina of Time (USA).z64"
```

The Alundra game is built by a patcher included in the fork, so nothing else is needed for it.

## 2. Slot names

Each person has two slots. Names are at most 16 characters and must be unique, for example:

`Luzbe-Alu`, `Luzbe-OoT`, `Friend1-Alu`, `Friend1-OoT`, `Friend2-Alu`, `Friend2-OoT`

## 3. YAML files

Each person writes two YAML files and sends them to the host.

Alundra (it has no options yet):

```yaml
name: Luzbe-Alu
game: Alundra
Alundra: {}
```

OoT: generate your YAML with the launcher's "Generate Template Options" (or the site's OoT options page) and set
`name: Luzbe-OoT`. The defaults are fine for a first run (`Ocarina of Time: {}` works).

## 4. Host: generate and run

1. Put all 6 YAML files in the `Players` folder of the host's fork.
2. Run `python Generate.py`. The result is a zip in `output/` (6 slots took about 10 seconds).
3. Run the server: `python MultiServer.py output/AP_<seed>.archipelago`. It listens on port 38281.
4. Remote play needs your port reachable. Forward TCP 38281 on your router, or use a tunnel (Tailscale, ZeroTier,
   playit.gg) and give everybody the address. archipelago.gg cannot host this game.
5. Give each person their own two patch files from the zip. The `.archipelago` file is only for the host.

The zip holds `.apalundra` files for the Alundra slots and `.apz5` files for the OoT slots.

## 5. Players: Alundra

1. Open your `.apalundra` with the launcher (double click, or "Open Patch"). The BizHawk Client opens and builds the
   game, writing a `.bin` and a `.cue` next to the patch file (a few seconds).
2. In BizHawk, load the `.cue` (it needs the BIOS in its firmware options), then open Tools > Lua Console and load
   `data/lua/connector_bizhawk_generic.lua` from your Archipelago folder, unless the client started BizHawk itself.
3. Start a new game. In the BizHawk Client, connect: `/connect <host address>:38281`. The slot name is read from the
   game.

In game: items from other players pop up as "Received X from Y". Opening a chest that holds another player's item
shows "Sent X to Y". Save in the starting house as usual; the received-items counter is saved with the game.

## 6. Players: OoT

1. Open your `.apz5` with the launcher. The Ocarina of Time Client patches your ROM and writes a `.z64`
   next to the patch file.
2. Open that `.z64` in a **second** BizHawk window.
3. **Wait until the game has booted** (the intro is playing), then open Tools > Lua Console and load
   `data/lua/connector_oot.lua`. The script reads game memory once when it loads, so loading it too early breaks it.
4. In the OoT client, `/connect <host address>:38281`. The slot name is read from the ROM.

## 7. Running both at once

Run two BizHawk windows and two clients: the BizHawk Client (Alundra) and the Ocarina of Time Client. They talk to
BizHawk on different ports (43055 for Alundra, 28921 for OoT), so they do not collide. Don't run two OoT games on one
machine, since they would share port 28921.

## What was tested

On one machine with Python 3.13, BizHawk 2.11.1 and the real OoT v1.0 ROM:

- Generating the 6 slots (3 Alundra + 3 OoT), patching an Alundra game and an OoT game from it.
- Both BizHawk windows open at the same time, each with its own client joined to the same server as its own slot.
- Alundra: items from the server arrive with an on-screen message, chests are reported as locations (including to
  another player's Alundra slot), and "Sent X to Y" shows in game.
- OoT: the client logs in and reports the right slot name.

## Not tested yet

- OoT receiving an item from another game. My test OoT game was stuck on a text box, so the item stayed queued.
- Playing either game for real, including Alundra's ending, and loading a saved Alundra game with the client open.
- A real PlayStation BIOS. Only OpenBIOS was used.
- Different machines over the internet (everything ran on localhost).
- The OoT guide recommends BizHawk 2.10, and its script warns that 2.11.1 is newer than it knows. It connected fine.
- 33 Alundra item sources (shops, trades, scripted rewards) keep their vanilla item and are not locations.
- Alundra's logic has had light testing, so treat the first run as a trial.

Run a short test before the real game: open one chest in Alundra and check the item arrives in the other game.
