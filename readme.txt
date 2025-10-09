
Made by .dokinchan. / flyingpossum (on discord)
╔════════════════════════════════════════════════════════════════════════════╗
║                         CivLabsRadios Plugin v1.0.3                        ║
║                   Advanced Radio Communication System                       ║
╚════════════════════════════════════════════════════════════════════════════╝

┌─────────────────────────────────────────────────────────────────────────────┐
│ OVERVIEW                                                                    │
└─────────────────────────────────────────────────────────────────────────────┘

CivLabsRadios integrates with Simple Voice Chat to provide realistic radio
communication across your Minecraft server. Players can set up transmitter
and receiver radios, enabling voice chat to be broadcast over specific
frequencies with positional audio emanating from speaker blocks.

┌─────────────────────────────────────────────────────────────────────────────┐
│ FEATURES                                                                    │
└─────────────────────────────────────────────────────────────────────────────┘

✓ Two GUI Modes:
  • Simple Mode: Classic 3x9 grid with 9 frequencies
  • Slider Mode: Advanced tuner with up to 1024 frequencies

✓ Frequency Management:
  • Only one transmitter per frequency server-wide
  • Unlimited receivers on any frequency
  • Real-time frequency overview to see what's in use
  • Paginated frequency browser (only shows occupied frequencies)

✓ Dimension Support:
  • Radios remember their home dimension
  • Optional dimension-locking (radios only work in placed dimension)
  • Color-coded GUIs (Red for Nether, Purple for End, White for Overworld)

✓ Operator System:
  • Transmitters require an active operator within range
  • Auto-disable when operator moves too far or logs off
  • Shift-right-click radios to view operator info

✓ Voice Integration:
  • Seamless Simple Voice Chat integration
  • Positional audio from speaker blocks
  • Operator isolation (transmitter operators only hear receivers)

✓ Sound Effects:
  • Configurable sounds for all interactions
  • Frequency change, enable/disable, errors, and clicks

✓ Privacy & Security:
  • Coordinate display toggle (disabled by default)
  • Admin-only location viewing
  • Protects base locations from being revealed

✓ Admin Tools:
  • Force-free occupied frequencies
  • Switch modes on-the-fly
  • Toggle coordinate visibility
  • Debug logging
  • Config reload without restart
  • List all radios with details

┌─────────────────────────────────────────────────────────────────────────────┐
│ INSTALLATION                                                                │
└─────────────────────────────────────────────────────────────────────────────┘

REQUIREMENTS:
  • Paper/Purpur/Spigot 1.21 or higher
  • Simple Voice Chat plugin (required dependency)
  • Java 21

INSTALLATION STEPS:

1. Download Simple Voice Chat
   Get it from: https://modrinth.com/plugin/simple-voice-chat
   Place voicechat-bukkit-[version].jar in your /plugins folder

2. Install CivLabsRadios
   Place CivLabsRadios-1.0.3.jar in your /plugins folder

3. Start your server
   The plugin will generate default configuration files

4. Configure the plugin (optional)
   Edit /plugins/CivLabsRadios/config.yml to customize:
   • Radio mode and max frequencies
   • Operator/speaker radius
   • Dimension restrictions
   • Sound effects
   • Messages
   • Crafting recipe

5. Reload or restart
   Use /radio reload or restart your server to apply changes

6. Give yourself a radio
   Use /radio give to get your first radio item

TROUBLESHOOTING INSTALLATION:
  • Check that Simple Voice Chat loads before CivLabsRadios
  • Ensure you're using Paper/Purpur (Spigot may have issues)
  • Check console for errors on startup
  • Verify Java 21 is installed: /version

┌─────────────────────────────────────────────────────────────────────────────┐
│ GETTING STARTED                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

1. OBTAIN A RADIO:
   Command: /radio give (requires admin permission)
   Or craft if enabled in config.yml

2. PLACE THE RADIO:
   Place the radio barrel block where you want your transmitter or receiver.

3. CONFIGURE THE RADIO:
   • Right-click to open the GUI
   • Set TX frequency (transmit)
   • Set RX frequency (receive/listen)
   • Click Enable to activate transmitter

4. USE THE RADIO:
   • Stand within range (default 30 blocks) of your transmitter
   • Speak in Simple Voice Chat
   • Your voice will play from all receivers tuned to your TX frequency

┌─────────────────────────────────────────────────────────────────────────────┐
│ COMMANDS                                                                    │
└─────────────────────────────────────────────────────────────────────────────┘

/radio help
  Shows all available commands

/radio give
  Gives you a Radio item (admin only)

/radio list
  Lists all radios on the server with their status
  Shows coordinates only if enabled by admin

/radio free <frequency>
  Force-disables any transmitter on the specified frequency (admin only)

/radio mode <simple|slider> [maxFreq]
  Switch between GUI modes (admin only)
  • simple: 9 frequencies, grid layout
  • slider: 1-1024 frequencies (default 128), tuner layout
  Example: /radio mode slider 256

/radio coords <on|off|toggle>
  Toggle coordinate display in frequency overview and /radio list (admin only)
  Default: OFF (for privacy and base protection)

/radio debug <on|off|toggle>
  Toggle debug logging (admin only)

/radio reload
  Reload configuration without restarting server (admin only)

┌─────────────────────────────────────────────────────────────────────────────┐
│ GUI CONTROLS                                                                │
└─────────────────────────────────────────────────────────────────────────────┘

SIMPLE MODE (9 Frequencies):
  • Row 1: Click to select TX frequency (1-9)
  • Row 2: Click to select RX frequency (1-9)
  • Row 3: Status, Enable/Disable, Close

SLIDER MODE (Up to 1024 Frequencies):
  • Red Concrete: Decrease TX/RX by 1 or 10
  • Lime Concrete: Increase TX/RX by 1 or 10
  • Compass: Jump to specific frequency via chat input
  • Spyglass: View frequency overview (see all active frequencies)
  • Lever/Torch: Enable/Disable transmitter
  • Book: View radio status

FREQUENCY OVERVIEW:
  • Shows only occupied frequencies (no clutter!)
  • Each entry displays: operator name, world, dimension
  • Coordinates shown only if admin enabled
  • Pagination with arrow buttons (45 per page)
  • Statistics panel showing total/active/available counts
  • Clean navigation back to radio GUI

┌─────────────────────────────────────────────────────────────────────────────┐
│ TIPS & TRICKS                                                               │
└─────────────────────────────────────────────────────────────────────────────┘

• Shift-right-click a radio to see detailed info (operator, status, frequencies)
• Use the frequency overview before choosing a frequency to avoid conflicts
• Receivers (RX) can listen to any frequency without conflicts
• Only one transmitter (TX) can be active per frequency
• Operators must stay within range of their transmitter
• Radios work cross-dimension unless restrictToDimension is enabled
• Coordinates are hidden by default to protect base locations
• Use /radio reload to apply config changes without restarting

┌─────────────────────────────────────────────────────────────────────────────┐
│ PERMISSIONS                                                                 │
└─────────────────────────────────────────────────────────────────────────────┘

civlabs.radio.base (default: true)
  Basic radio usage and commands

civlabs.radio.admin (default: op)
  Admin commands: give, free, mode, coords, debug, reload

┌─────────────────────────────────────────────────────────────────────────────┐
│ CONFIGURATION                                                               │
└─────────────────────────────────────────────────────────────────────────────┘

Edit config.yml to customize:
  • Radio mode (simple/slider) and max frequencies
  • Operator and speaker radius
  • Base block type (default: BARREL)
  • Coordinate visibility (default: hidden)
  • Dimension restrictions
  • Sound effects
  • Custom messages
  • Crafting recipe

Most changes can be applied with /radio reload, but some (like voice settings)
may require a full server restart.

┌─────────────────────────────────────────────────────────────────────────────┐
│ TROUBLESHOOTING                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

Q: Radio won't enable?
A: Make sure you've set a TX frequency first (must be 1 or higher).

Q: Can't hear voice through radio?
A: Verify Simple Voice Chat is installed and the speaker is within range.

Q: Frequency shows as locked?
A: Another radio is transmitting on that frequency. Use /radio list or the
   frequency overview to find active transmitters.

Q: Radio disabled automatically?
A: Operator moved out of range or logged off. Stay within configured radius.

Q: Voice sounds wrong?
A: Check speakerRadius in config.yml and ensure dimensions match if
   restrictToDimension is enabled.

Q: Coordinates showing when they shouldn't?
A: Use /radio coords off to disable coordinate display.

Q: Changes not applying?
A: Use /radio reload or restart the server. Some settings require full restart.

┌─────────────────────────────────────────────────────────────────────────────┐
│ CHANGELOG                                                                   │
└─────────────────────────────────────────────────────────────────────────────┘

v1.0.3:
  • Added /radio coords command to toggle coordinate visibility
  • Added /radio reload command
  • Coordinates now hidden by default for privacy
  • Improved frequency overview pagination
  • Better shift-click radio info display
  • Fixed coordinate leak in frequency overview
  • Updated to show version in help command

v1.0.2:
  • Added frequency overview with pagination
  • Improved slider GUI with better labels
  • Enhanced shift-click radio information
  • Better sound feedback

v1.0.0:
  • Initial release

┌─────────────────────────────────────────────────────────────────────────────┐
│ SUPPORT                                                                     │
└─────────────────────────────────────────────────────────────────────────────┘

Dependencies: Paper 1.21+, Simple Voice Chat
Version: 1.0.3
Created by: .dokinchan. / flyingpossum

Contact me on Discord for any questions, bug reports, or feature requests.

Thank you for using CivLabsRadios! 📻

═══════════════════════════════════════════════════════════════════════════════
