# YM Technicals - features

## Creeper system (CreeperBlaster3000, CreeperSpeaker3000, code sheet)

Package `com.yesmenn.technicals.creeper`. Moved over from the old YesMenn! mod and rebuilt.

### Usage
- **CreeperBlaster3000**: right click opens the screen. Left: title, seek bar (drag to seek), back/play-pause/stop/
  forward, loop, volume, range (1-128 blocks), pairing code (three color fields: left click next color, right click
  previous). Right: searchable song library of the server (click = select, x = delete after a confirmation, only with
  permission) and a link field (YouTube or direct audio file) with "Download". A rising redstone signal toggles
  play/pause. The front glows while playing.
- **CreeperSpeaker3000**: plays what the blaster with the same code (same dimension, loaded) plays, with its own range
  and volume. Screen: what is playing, code, volume, range. Using a second speaker on a half speaker makes a full one
  (glowing front while playing). Placed standing or hanging (upside down), with a mounting plate when a block is above
  (standing) / below (hanging).
- **Code sheet**: use on a blaster to copy its code, then on speakers to apply it.
- Create's wrench turns both blocks.
- WTHIT (optional): looking at a blaster or speaker shows the song with play position; with Shift held also the code
  as colored squares, range and volume.

### How it works
- **Codes**: `CreeperCode`, 3 slots x 8 colors packed into one int (0..511). New blasters take red-green-blue if no
  loaded blaster of the dimension uses it, else a random free code; new speakers start with red-green-blue.
- **Server state**: the blaster stores song (library ID, title, length in ticks), mode (stopped/playing/paused),
  `startTick` (game time at song position 0) and `pausedTicks`. The position is `gameTime - startTick`, never counted
  per tick; the ticker only checks the song end (loop or stop). `version` increases on every playback restart.
- **Channels** (`server/CreeperChannels`): blasters and speakers push plain data copies when they load, change or
  unload. Nothing there reads block entities or chunks (the old version did, which loaded chunks from inside chunk
  loading/unloading and crashed servers). Changed codes are sent once at the end of the level tick as
  `ChannelState` to all players of the dimension; joining/respawning/changing dimension sends all channels.
- **Library** (`server/SongLibrary`): `<server>/ymtechnicals/creeper_blaster_3000/library/<sha256>.ogg` plus
  `library.json` (title, artist, added by name and UUID, length, size, time). Length comes from the Ogg pages (`OggInfo`), no ffprobe at play
  time. Old songs from `YesMenn/CreeperBlaster3000/downloads` are moved in once at server start; old blasters find
  their song by the old file name.
- **Downloads** (`server/SongDownloads`): needs the permission `ymtechnicals.creeperblaster.download` (default:
  operators, `downloadPermissionLevel`). Links to the `ytDlpHosts` (YouTube) go through yt-dlp with `--ignore-config`
  and `--use-extractors default,-generic`; other links only when they end in one of `directAudioExtensions`: then
  the file is downloaded here (redirects followed by hand, every hop must be a public address, no HTML/text, max
  300 MB) and converted by ffmpeg with `-protocol_whitelist file`. All links must resolve to public addresses. Result:
  Ogg Vorbis mono 48 kHz quality 6. Length limit, 15 minute timeout, one download per player, all on the
  `CreeperWorker` threads; running processes are killed when the server stops.
- **Re-download**: entries keep their `source` link (yt-dlp `webpage_url`; old YesMenn songs get it back from the
  YouTube ID in their old file name). Right click on a song -> "Download again" (owner with the download permission
  or delete_any): downloaded like a new link, then `SongLibrary.replace` swaps the file (keeps title, adder, age; the
  old ID is stored in `replaces`), loaded blasters switch via `CreeperChannels.songReplaced` at the same position,
  unloaded ones follow `SongLibrary.resolve` when they load. Quota counts the new size minus the old one.
- **Uploads** (`server/SongUploads`, `client/CreeperUploads`): "File..." (system dialog via tinyfd, own thread) or
  dropping a file on the blaster screen. `UploadStart` (name, size) -> checks (`allowUploads` "auto"/true/false,
  `maxUploadMiB`, extension, permission, quota) -> the client sends 30 KiB pieces in order, at most 512 KiB ahead of
  the server's `UploadAck`; the server writes them on the worker into a temp file, an empty piece ends the upload,
  then it is converted like a direct link (ffmpeg with `-protocol_whitelist file` and `-format_whitelist` audio
  containers only). Idle uploads are dropped after 60 s.
- **Permissions without a permission mod**: the owner of a single player / LAN world always has them, everybody else
  by permission level (`CreeperPermission.byDefault`).
- **Tags of files**: for uploads and direct links `SongDownloads.Job.readTags` runs ffprobe (same protocol/format
  whitelist as ffmpeg) and takes `title` and `artist` (else `album_artist`), key case ignored; without a title tag the
  file name is the title.
- **Queue** (`SongDownloads`): links, re-downloads and finished uploads of a player run one after the other; while
  one runs, new jobs wait (max 20, checked again when they start; offline players lose their queue, queued upload
  files are deleted). Status texts show "(+n waiting)". The client queues uploads too (multi-select in the file
  dialog, several dropped files).
- **Sync on the sound card clock**: a mixin on `Channel.attachBufferStream` (common `ymtechnicals.mixins.json`) hands
  each `CreeperSoundInstance.PcmStream` its OpenAL source before the first read. If another source of the same
  playback (code, version, song) is playing, the new one starts at that source's real position (bytes handed out
  minus still queued buffers plus the OpenAL offset, `AL_SOFT_source_latency` when available), on the sound thread
  right before it starts. Only without a playing sibling the shared anchor from the system clock is used, which drifts
  slightly against the sound card over minutes.
- **Prefetch** (`CreeperAudio.prefetch`): for blasters within range + 48 that have a song chosen but do not play,
  the client fetches and decodes it in advance, so "Play" starts at 0:00 (otherwise the position runs on during the
  transfer).
- **Limits**: each player's own songs count against `playerQuotaMiB` (1 GiB) / `operatorQuotaMiB` (50 GiB, with
  `ymtechnicals.creeperblaster.operator_limits`); optional `playerDailyLimitMiB` / `operatorDailyLimitMiB` over a
  rolling 24 hours (in memory, also counts deleted songs). Checked before and after the download. Own songs can be
  deleted with the download permission, others' with `ymtechnicals.creeperblaster.delete_any`. The screen shows
  usage and limits (`Library` payload, per player).
- **Tools** (`AudioTools`, `ToolsConfig` = `config/ymtechnicals-tools.json`): yt-dlp and ffmpeg/ffprobe from the
  PATH, `<game or server folder>/ymtechnicals/creeper_blaster_3000/tools/<os-arch>` or the old YesMenn tools folder;
  downloaded only when `confirmed` and `installYtDlp` / `installFfmpeg` allow it. The client asks once in the main
  menu (`ToolsSetupScreen`); a dedicated server logs a note until the admin sets `confirmed`. At every game start
  (main menu) and server start the tools are prepared in the background and an own yt-dlp copy is updated
  (`yt-dlp -U`) when `autoUpdateYtDlp`.
- **Transfers** (`server/SongTransfers`): a client asks for a song it does not have when a source comes within
  range + 48 blocks; the server sends the Ogg file in 32 KiB pieces, limited per player per tick
  (`transferKiBPerSecond`). The client checks the SHA-256 and keeps it in `ymtechnicals/creeper_blaster_3000/cache`
  (max 1 GiB, oldest dropped).
- **Playback** (`client/CreeperAudio`, `CreeperSoundInstance`): every 2 ticks each source of a playing channel gets
  a gain from its distance (1 up to 20 % of its range, then a cosine fade to 0 at the range) times its volume; the
  loudest play, max 4 per channel and 6 in total (Minecraft has only a few streaming channels), playing sources get a
  small bonus so they do not flap at the cap. Gains of one channel are balanced by total power (sum of squares <= 1).
  Each source is a positional sound in the record category with OpenAL attenuation off (OpenAL only pans), its
  volume fades towards the target (in ~0.5 s, out ~0.35 s). Audio comes from the song decoded once into shared mono
  PCM (`SongCache`; songs not asked for in 30 s are dropped, at most 2 kept). All sources of a channel start from one anchor (song position at a
  moment of the client's audio clock, which stands still while the game is paused), taken from the game time when
  the first source starts, so later sources start exactly where the others are. Sable sub-levels move the sound
  position (`SablePositions`).
- **Screens**: YM kit (`client/screen`), values read live from the block entity / channel state; settings are sent as
  actions, slider changes at most every 120 ms. Status and download progress come as `BlasterStatus`.
- **WTHIT** (`compat/CreeperWthitPlugin`, client plugin in `waila_plugins.json`): reads only client data (block
  entity sync, channel states), no server plugin. The WTHIT jar lies in `libs/` (compileOnly, plus badpackets for dev
  runs).
- **Config** (server, `CreeperConfig`, `config/ymtechnicals-server.json`, read at server start, missing values
  filled in): `allowDownloads`, `downloadPermissionLevel`, `ytDlpHosts`, `allowDirectAudioLinks`,
  `directAudioExtensions`, `allowUploads`, `maxUploadMiB`, `maxSongMinutes`, `playerQuotaMiB`, `operatorQuotaMiB`, `playerDailyLimitMiB`,
  `operatorDailyLimitMiB`, `transferKiBPerSecond`.
- **Loaders**: block entity `onLoad` is called by NeoForge itself and by Fabric's block entity load event; the song
  stream comes from `CreeperSoundInstance.getStream` on NeoForge and from `MixinSoundEngineFabric` on Fabric. A
  full channel sync starts with code -1 (client forgets all channels), so no respawn/dimension client event is
  needed.
- **Old worlds**: `yesmenn:creeper_blaster_3000`, `creeper_speaker_3000` and `code_sheet` are aliases of the new IDs
  when the YesMenn mod is not installed; old block entity and code sheet data (Code0-2, CachedFileName, ...) are read.

## Observers Eye

- Screen (YM kit, `client/screen/ObserversEyeScreen`): shape, width (sets X and Z), size X/Y/Z, offset X/Y/Z as
  sliders with reset icons, output (on/off or pulse), zone preview, filter (players, mobs & animals, listed players),
  player list (add by name or with one click from the online players, remove with the bin icon). The world stays
  visible and the window fades out while a slider is dragged; `ObserversEyeRenderer` draws the zone from the screen's
  unsaved values while it is open. Changes are sent at most every 120 ms and on close.

## Soundeffectz3000

- Screen (YM kit, `client/screen/Soundeffectz3000Screen`): one list with search, mod and category filter (max 400
  rows shown, the search narrows it down); left click chooses, right click plays the sound only on this client.
  Volume (0-200 %), pitch (25-400 %) and range (1-128) as sliders with reset icons, loop and redstone switches.
  "Play" applies the settings and plays; closing the screen saves them.

