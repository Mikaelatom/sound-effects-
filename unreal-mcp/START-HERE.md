# Connect Claude to Unreal: the easy way

About 5 minutes. You need **Claude Desktop** and **Unreal Engine 5** installed.

## Step 1: Download this folder

Click this link to download a ZIP:
**https://github.com/mikaelatom/sound-effects-/archive/refs/heads/claude/zen-ptolemy-li8b5o.zip**

Unzip it somewhere you'll keep it, such as your Documents folder. Inside, open the **`unreal-mcp`** folder.

## Step 2: Double-click the setup file

- **Windows:** double-click **`SETUP-Windows.bat`**
  - If you see *"Windows protected your PC"*, click **More info**, then **Run anyway**.
- **Mac:** right-click **`SETUP-Mac.command`**, choose **Open**, then click **Open** again.

A black window opens and installs everything. When it asks:

> *Drag your project's .uproject file into this window and press Enter*

find your Unreal project folder, drag the **`.uproject`** file (e.g. `MyGame.uproject`) into the black window, and press **Enter**. This switches on the Unreal settings Claude needs.

When it says **All done!**, press Enter to close the window.

## Step 3: Restart and try it

1. **Fully quit Claude Desktop.** Closing the window isn't enough:
   - Windows: right-click the Claude icon near the clock and choose **Quit**.
   - Mac: click **Claude** in the menu bar and choose **Quit Claude**.
2. Open Claude Desktop again.
3. Open your project in Unreal and keep it open.
4. Ask Claude: **"What's in my Unreal level?"**

When Claude asks permission to use an `unreal_...` tool, click **Allow**.

---

### Something not working?

- **Claude says it can't find Unreal**: make sure your project is open in Unreal. If you skipped the drag-and-drop step, turn the settings on by hand:
  1. In Unreal, open **Edit → Plugins**, search **Python**, tick **Python Editor Script Plugin**, and restart Unreal.
  2. Open **Edit → Project Settings**, search **remote execution**, and tick **Enable Remote Execution**.
- **No Unreal tools in Claude**: you probably need to fully quit and reopen Claude Desktop (Step 3.1).
- **A firewall pop-up appears**: click **Allow**.
- **Still stuck**: take a screenshot of the setup window and show it to Claude.

You can run the setup again any time. It's safe, and it backs up anything it changes.
