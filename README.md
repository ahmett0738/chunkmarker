# Chunk Marker

Fabric 1.21.11 client-side mod. Marks chunks that contain:

- grown vines (bottom piece of a hanging vine that rests on a block) - vine icon
- level-5 beehives / bee nests - honey bottle icon
- kelp that reached the water surface (each one counts 1) - kelp icon
- amethyst clusters - amethyst cluster icon

A flat 16x16 icon is drawn at y=62 over the chunk and is visible through terrain.
Only one icon per chunk is shown. Press **Right Shift** to open the settings.
Works on servers (client only, no packets).

The built jar is `build/libs/chunkmarker-1.0.0.jar` (not the `-sources` one).
