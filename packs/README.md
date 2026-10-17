# Packs originales de los jefes (cifrados)

`jefes-17.tar.enc` guarda los packs de MythicMobs/ModelEngine de los 17 jefes de TF Client, tal como se compraron, para
reconvertirlos o corregirlos (`tf-client/tools/bosses/build_bosses.py`). Están **cifrados** (AES-256, clave en
PBKDF2 de 300 000 vueltas) porque este repositorio es público y las licencias de LittleRoom prohíben redistribuirlos: la
contraseña la tiene el dueño (no está en el repo; pídesela).

Abrirlo (Linux/macOS, o Git Bash en Windows):

    openssl enc -d -aes-256-cbc -pbkdf2 -iter 300000 -in packs/jefes-17.tar.enc -out jefes-17.tar   # pide la contraseña
    tar -xf jefes-17.tar                                                                               # README.md y packs/*.zip

Dentro, `README.md` dice qué zip es de cada jefe:

| Jefe (id en TF Client) | Pack |
|---|---|
| abominacion | LITTLEROOM_PARASITIC_ABOMINATION_v1_0 |
| archivista | LITTLEROOM_The_Archivist |
| arconte_astral | LITTLEROOM_ASTRAL_ARCHON |
| astwood | Astwood |
| contemplador | LITTLEROOM_beholder_v1_2 |
| druida_boreal | LITTLEROOM_borealdruid_v1_0 |
| eldric | LITTLEROOM_ELDRIC_v1_0 |
| esqueleto_torre | LITTLEROOM_TowerSkeleton_v1_0 |
| gargola | LITTLEROOM_GARGOYLE_v1_0 |
| guardian_hueco | LITTLEROOM_hollowkeeper_v1_0 |
| hidra | LITTLEROOM_HYDRA_v1_0 |
| ignevar | littleroom_ignevar |
| kazk | LITTLEROOM_kazk_v1_0 |
| mega_guardian | LITTLEROOM_MEGAWARDEN_v1_0- |
| piglin_mecha | LITTLEROOM_PIGLINMECH_v1_0 |
| rey_calabaza | LITTLEROOM_pumpkinking_v1_0 |
| tormenta_wither | Littleroom_WitherStorm_1.0 |

Para añadir packs nuevos: descifra, añade los zip en `packs/` y su fila en el README de dentro, y vuelve a cifrar con
la misma contraseña:

    tar -cf - README.md packs | openssl enc -aes-256-cbc -pbkdf2 -iter 300000 -salt -out packs/jefes-17.tar.enc

## `void.tar.enc` (1.3.88): los originales de The Void

Con la misma contraseña y el mismo comando (`... -in packs/void.tar.enc -out void.tar`): dentro, `void/` con el pack
del caballero de luz (`NPCs_For_Mini_Games_vol.1.zip`, Wardenful Studio: el modelo es `skywars_npc.bbmodel`) y el mapa
de la isla (`EndCarageKitMap.schematic`). La isla se convierte con `python3 -I tf-client/tools/void/isla.py <schematic>`.
