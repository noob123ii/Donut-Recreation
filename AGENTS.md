# Project Notes (for coding agents)

## Local test server

The ready-to-run Paper server for this project is at:

```
C:\Users\cpatt\Desktop\Dev\Projects\Public\plugin-template-main\plugin-template-main\server
```

Start it from the repository root with `start-server.bat` (or `.\start-server.ps1`). It listens
on `localhost:25565`, uses `online-mode=false`, and runs Paper 26.3 build 241 with Java 25.

`server/` is gitignored. It contains the world, plugins and the Paper jar; do not delete it.

## Rebuild and install the plugin

```bat
gradlew.bat shadowJar
copy /Y build\libs\DonutRecreation.jar server\plugins\DonutRecreation.jar
```

Restart the server after copying the jar. Do not use Bukkit `/reload` for chunk/anti-xray
changes; clients also cache already-sent chunks, so reconnect after restarting.

## Version support

- Plugin `api-version`: `1.20.4`
- Compiled against Paper API `1.20.4-R0.1-SNAPSHOT`, Java 21 bytecode
- Tested running on Paper 26.3 and Leaf/Paper 1.21.11
- The test server includes ViaVersion, ViaBackwards and ViaRewind so older clients can join

## Base protection / anti-xray

The hider rewrites chunk/block packets (PacketEvents). `DeepslateProtection` masks the hidden
underground floor/band with noise, `OreVisibility` hides buried ores until exposed, and
`PlacedBlockHider` instantly hides player-placed base blocks at/above `hider.placed-hide-above-y`
(default 60) and placed containers below `hider.chest-hide-below-y` (default 0, i.e. under y=0)
from other players. Laggy surface/terrain hiding was removed.

Use `/donut xray` for status. After changing hider behavior, rebuild, copy the jar, restart the
server, and reconnect (clients cache chunks).
