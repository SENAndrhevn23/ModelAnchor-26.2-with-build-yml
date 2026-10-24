# ModelAnchor — Minecraft Java 26.2

A client-side Fabric mod starter for importing Wavefront **OBJ** meshes and attaching them to a player's animated model. Attachments follow their selected body part, so they appear in third person (F5) and move with head/arm/leg animations.

## Current features

- Import one or more `.obj` files through a desktop editor opened from Minecraft with **O**.
- Attach each model to `HEAD`, `BODY`, `RIGHT_ARM`, `LEFT_ARM`, `RIGHT_LEG`, or `LEFT_LEG`.
- Adjust scale, X/Y/Z offsets, and X/Y/Z rotations.
- Enable/disable or remove attachments; settings persist to `config/modelanchor.json`.
- Triangulates OBJ polygon faces and reads `v`, `vt`, `vn`, negative OBJ indices, and MTL `Kd` diffuse colors.
- Imported files are copied into `config/modelanchor/models/` to keep them with the profile.
- Rendering uses Minecraft's entity rendering pipeline; this does not use raw OpenGL.
- Attachments are currently local cosmetics: only your own avatar is customized, and other players do not receive your model data over multiplayer.

## Important limitations in this starter

- The current importer draws OBJ geometry using its MTL diffuse color on a white texture. MTL texture maps (`map_Kd`) are not sampled yet.
- Direct selection of a Minecraft block by registry ID is not implemented in this first version. To attach a block-like mesh, export it as OBJ from a modeling tool and import that file.
- Body positions are pivots on the vanilla player model. Fine-tune the offsets for your model's origin and coordinate conventions.
- This project is packaged as source; it has **not** been compiled or launch-tested in this environment because Java 25 and dependency downloads were unavailable here. Check the build result before treating it as a finished release.

## Requirements

- Minecraft Java Edition **26.2**
- Fabric Loader **0.19.3** or a compatible newer 26.2 build
- Fabric API **0.155.2+26.2**
- **Java 25 JDK** (Minecraft 26.2's Fabric metadata requires Java 25 or newer)

## Build configuration note

Minecraft 26.2 uses the non-obfuscated Fabric Loom setup. The build script therefore declares Fabric Loader and Fabric API with Gradle `implementation`, rather than the older `modImplementation` configuration used in older mod templates.

## Build automatically with GitHub Actions

The included `.github/workflows/build.yml` compiles the mod automatically when you push commits, open or update a pull request, or manually run the workflow. It uses Java 25 and uploads the compiled mod JAR as an artifact.

To get the JAR:

1. Upload this project to a GitHub repository, including the `.github/workflows/build.yml` file.
2. Open the repository's **Actions** tab and select **Build ModelAnchor JAR**.
3. Open a successful workflow run and download the `ModelAnchor-Minecraft-26.2` artifact.
4. Extract the downloaded artifact ZIP and copy the `.jar` into your Minecraft instance's `mods` folder.

The artifact is retained for 14 days. A workflow run can only produce a JAR if the Gradle build succeeds; this project has not yet been independently compile-tested in this environment.

## Build on Linux

From the extracted project directory:

```bash
chmod +x gradlew
./gradlew build
```

The first run bootstraps Gradle 9.5.1 and downloads Fabric/Minecraft build dependencies, so internet access is required. The resulting mod JAR should be in `build/libs/` with the shortest filename (not the `-sources` JAR).

Then copy the mod JAR into your Minecraft instance's `mods/` folder along with Fabric API. Do not put the project ZIP itself in `mods/`.

## Use it

1. Launch Minecraft 26.2 with Fabric and Fabric API.
2. Join a world and press **O** to open the ModelAnchor editor window.
3. Click **Import OBJ…**, choose the mesh, and select the resulting attachment in the list.
4. Choose a body part and set scale/offset/rotation, then click **Apply / Save**.
5. Press **F5** to see it on your player. The attachment follows the selected body part's animation.

Offsets are in player-model pixels; **16 pixels = 1 Minecraft block**. Scale is a multiplier applied to the OBJ's own coordinate units. If the model is too large, try a smaller value such as `0.05` or `0.01`.

## Sample model

`sample_models/teal_diamond.obj` and its companion MTL can be imported to check that OBJ geometry and diffuse material colors are being read.
