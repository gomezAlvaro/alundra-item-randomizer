# Alundra + Ocarina of Time: setup for 3 players (each plays both games)

Status: this is what works today. The fork's Python patcher builds the game. Alundra is not in upstream Archipelago, so everybody runs
the fork. Items you must read first are marked **Untested**.

## What each person needs

| Item | For | Notes |
|---|---|---|
| Python **3.13** (3.11.9 to 3.13) | Everyone | Python 3.14 does not work with Archipelago's `settings.py` |
| Git | Everyone | To clone the fork |
| Alundra (USA) v1.1 disc image (`.bin` + `.cue`) | Alundra | Your own copy, 600,359,760 bytes |
| PlayStation BIOS | Alundra | Only OpenBIOS has been tried; a real BIOS should work too |
| OoT ROM (vanilla, `.z64`) | OoT | Your own copy |
| BizHawk **2.11.1** (x64) | Both | Download from the BizHawk releases page. You run it twice at once |

Nobody shares discs or ROMs. Each person patches their own.

## 1. Install the fork (everyone)

```bash
git clone --branch alundra https://github.com/gomezAlvaro/Archipelago.git
cd Archipelago
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python Launcher.py
```

The first launch creates `host.yaml`. Open it and set the Alundra section:

```yaml
alundra_settings:
  rom_file: "C:/path/to/Alundra (USA) (v1.1).bin"
```

The fork builds the game with its own Python patcher, so nothing else is needed.

Use forward slashes in paths. Set the OoT ROM and BizHawk paths from the launcher as that game's guide says.

## 2. Slot names

Each person has two slots. Names are at most 16 characters and must be unique, for example:

`Luzbe-Alu`, `Luzbe-OoT`, `Friend1-Alu`, `Friend1-OoT`, `Friend2-Alu`, `Friend2-OoT`

## 3. YAML files

Each person writes two YAML files and sends them to the host.

Alundra (it has no options yet):

```yaml
name: Luzbe-Alu
game: Alundra
description: Alundra slot
Alundra: {}
```

OoT: generate your YAML with the launcher's "Generate Template Options" (or the site's OoT options page) and set
`name: Luzbe-OoT`. The defaults are fine for a first run.

## 4. Host: generate and run

1. Put all 6 YAML files in the `Players` folder of the host's fork.
2. Run `python Generate.py`. The result is a zip in `output/`.
3. Run the server: `python MultiServer.py output/AP_<seed>.archipelago`. It listens on port 38281.
4. Remote play needs your port reachable. Forward TCP 38281 on your router, or use a tunnel (Tailscale, ZeroTier,
   playit.gg) and give everybody the address. archipelago.gg cannot host this game.
5. Give each person their own files from the zip (below). The `.archipelago` file is only for the host.

## 5. Players: build and play Alundra

1. Open `AP_..._<your Alundra slot>.apalundra` with the launcher (double click, or "Open Patch" in the launcher).
   The BizHawk Client opens and runs the patcher. It writes a `.bin` and `.cue` next to the patch file.
2. Start BizHawk, load the `.cue`, and start a new game. The client connects once the game is running.
   (It needs the BIOS; set it in BizHawk's firmware options.)
3. In the client console, connect: `/connect <host address>:38281`. The slot name is read from the game.

In game: items from other players pop up as "Received X from Y". Opening a chest that holds another player's
item shows "Sent X to Y". Save in the starting house as usual; the received-items counter is saved with the game.

## 6. Players: build and play OoT

Follow the OoT guide in the launcher ("Ocarina of Time" setup guide). In short: open the `.apz5` patch from the zip
with the launcher, give it your ROM, then load the result in BizHawk and connect with the OoT client.

## 7. Running both at once

Run two BizHawk windows and two clients: the BizHawk Client (Alundra) and the OoT client. Start Alundra first,
then OoT, and check each client says it is connected to its own slot.

## Known limits

- **Untested: two BizHawk instances at once.** The Alundra client has only been tested alone.
- **Untested: OoT in this fork.** I have not generated or run an OoT game here.
- **Untested on BizHawk: chest checks and the "Sent X to Y" message.** Item delivery to the player is tested.
  The check logic was tested in PCSX-Redux with the same code.
- **Untested: the real ending / goal.** The goal flag works when forced, but I have not finished the game.
- 33 item sources (shops, trades, scripted rewards) keep their vanilla item and are not Archipelago locations.
- The Alundra logic has had light testing, so treat the first run as a trial.
- Run a short test before the real game: open one chest in Alundra and check the item arrives in OoT.
