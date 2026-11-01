# Daily Dose of Ironmon — randomizer fork (UPR-ZX 4.6.1)

Public GPL-3 fork of [Universal Pokemon Randomizer ZX](https://github.com/Ajarmar/universal-pokemon-randomizer-zx)
used by the Daily Dose extension and pipeline. No ROMs or ROM-derived data are stored here.

## Build

Requirements: JDK 8+ (the produced jar targets Java 8). On this workstation:

```powershell
.\build.ps1
```

The script compiles `src/**/*.java`, copies the non-Java resources (config tables, the
`kaizo-v1.rnqs` preset), and writes `build/ddoz-zx.jar`.

## CLI

```
java -jar build/ddoz-zx.jar cli --print-preset kaizo-v1
java -jar build/ddoz-zx.jar cli -i <vanilla.gba> -o <out.gba> -S <settings> -z <seed> \
     --base-ips <faster-firered.ips> --guaranteed-catch --weights-preset daily-v1 \
     -l --print-sha1 --fingerprints
```

Flags:

- `-s <file>` / `-S <string>` — `.rnqs` file or settings string.
- `-z <seed>` — deterministic seed (passed to `Randomizer.randomize`).
- `--base-ips <file>` — apply an IPS/IPS32 patch to the ROM before randomization.
- `--guaranteed-catch` — minimum catch rate level 5 (the one intentional Kaizo deviation).
- `--weights-preset daily-v1` — lab/early-route species weighting; `none` disables.
- `--weight-power`, `--weight-cap`, `--type-bias TYPE=factor`, `--type-bias-scope lab|early|all`.
- `--fingerprints` — print `fingerprint:<stage> <sha>` for starters/wild/trainers/movesets/rom.
- `--double-check` — generate twice and compare the SHA-1.
- `--probe --species <id>` or `--type <type>` `--max <n>` `[--from <seed>]` — seed search for starters.
- `--print-preset kaizo-v1` — print the bundled official preset settings string.

## Preset

`kaizo-v1` is the official `FRLG Kaizo.rnqs` settings file, bundled as
`src/com/dabomstew/pkrandom/ddoz/kaizo-v1.rnqs`. It matches the tracker's
`ironmon_tracker/RandomizerSettings/FRLG Kaizo.rnqs`, except that the Daily Dose pipeline forces
guaranteed catch (`--guaranteed-catch`).

## Layout

- `src/com/dabomstew/pkrandom/ddoz/DdozCli.java` — CLI glue.
- `src/com/dabomstew/pkrandom/ddoz/SpeciesWeights.java` — weighting.
- `src/com/dabomstew/pkrandom/ddoz/IpsPatcher.java` — IPS/IPS32.
- `src/**` — upstream UPR-ZX 4.6.1 source (GPL-3), with small hooks in
  `AbstractRomHandler` for weighted starters/wilds and public `checkPokemonRestrictions`.

## License

GPL-3. See `LICENSE.txt` (upstream).
