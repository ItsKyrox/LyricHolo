# Modrinth publishing checklist

## Project details

- **Name:** LyricHolo
- **Author:** ItsKyrox_MC
- **Supported game version in this artifact:** Minecraft 1.21.11
- **Loaders:** Fabric
- **Environment:** Client-side
- **License:** Not declared yet. Select and include the intended license before publishing; do not mark the project MIT or another license without the author's decision.

## Upload

1. Build with `gradlew.bat build` using Java 21.
2. Upload the remapped jar from `build/libs/lyricholo-1.21.11-1.0.0.jar`.
3. Mark it for Minecraft 1.21.11 and Fabric only. Do not tag 26.1.2; that port is not present.
4. Add Fabric API as a required dependency. JNA is bundled in the mod jar.
5. Use the README compatibility and privacy sections in the project description.
6. Select the license and project source/issues URLs in Modrinth before making the project public.
7. Verify the upload's Modrinth version metadata and image preview in the site UI.

## Known release limitations

- Media detection currently supports Windows only.
- YouTube Music uses an unofficial endpoint and can break if its protocol changes.
- Lyric availability and word-level timing depend on external providers.
- The game itself and live account/provider behavior were not exercised as part of this source review.

- Pacifico font asset and its SIL Open Font License are bundled in the jar. The project's own source license remains to be selected.
- Smooth Noto Sans and Pacifico font assets, plus their SIL Open Font License texts, are bundled in the jar. The project source license remains to be selected.
