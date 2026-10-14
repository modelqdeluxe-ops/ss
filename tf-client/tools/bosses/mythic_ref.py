"""Catálogo de referencia de MythicMobs 5 y ModelEngine 4 (mecánicas, condiciones, objetivos y disparadores, con sus
alias) para `audit.py --cobertura`: qué tiene el motor de TF Client, qué acepta sin hacer nada y qué le falta.

Es una lista hecha a mano de lo que se ve en los packs de jefes; no es la lista oficial completa. Si un pack trae algo
que no esté aquí, el auditor normal ya lo marca como «sin implementar»: añádelo aquí también.

Cada entrada: «nombre alias1 alias2 ...». Con «*» delante: lo usan mucho los packs de jefes (importancia alta).
"""

MECHANICS = """
*aura buff debuff
*auraremove removeaura removebuff
*barcreate createbar
barremove removebar
*barset setbar
*basedamage
blackscreen
blockdestabilize
*blockmask effect:blockmask
blockphysics
blockunmask
*blockwave effect:blockwave
bloodyscreen effect:bloodyscreen
bossborder effect:bossborder
breakblock
*cancelevent
*chain
chainmissile
*clearthreat
*command cmd consolecommand
consume
*damage d
decapitate
*delay
*directionalvelocity
*disengage
disguise
*dismount
*displaytransformation
*dropitem
ender effect:ender
enderbeam effect:enderbeam
*endprojectile terminateprojectile
*equip
*explosion explode
extinguish
*fakeexplosion effect:explosion
fill
firework effect:firework
flames effect:flames
fly
*forcepull
*freeze
geyser effect:geyser
giveitem
*glow
*goto
*gcd globalcooldown setgcd
*heal
*healpercent
*hide
hit
hologram
*ignite
itemspray effect:itemspray
*jump
*leap
*lightning effect:lightning
fakelightning
*look rotatetowards
*lunge
*message msg
*metaskill skill s cast
*missile
*modifydamage
modifyglobalscore
*modifyprojectile
modifyscore
modifytargetscore
*mount
mountme
mounttarget
oxygen
*particle particles effect:particle e:p
particlebox effect:particlebox
particleequation
*particleline effect:particleline e:pl
particlelinehelix
particlelinering
*particleorbital effect:particleorbital e:po
*particlering effect:particlering e:pr
*particlesphere effect:particlesphere e:ps
particletornado effect:particletornado
atom effect:atom
pickupitem
playanimation
*potion
*potionclear
*prison
*projectile p
*propel
*pull
randommessage
*randomskill
*rally
*recoil
remount
*remove
removehelditem
removeowner
resetai
runaigoalselector
runaitargetselector
*sendactionmessage actionmessage
*sendtitle title
sendtoast
*setai setnoai
setblockopen
*setblock setblocktype
setcollidable
*setfaction
setgamemode
setgliding
*setgravity
*sethealth
setlevel
*setmaxhealth
setmobcolor
*setname
*setnodamageticks
setowner
setparent
setpathfindingmalus
setpose
*setrotation
*setskillcooldown
*setstance
*settarget
*settextdisplay
*shield absorbshield
shieldbreak
*shieldpercent
*shoot
*shootfireball
shootpotion
shootshulkerbullet
*showentity show
*signal
skybox effect:skybox
smoke effect:smoke
smokeswirl effect:smokeswirl
*sound effect:sound e:s
*spin
spring effect:spring
stopsound effect:stopsound
*stun
*sudoskill sudo
*summon
summonareaeffectcloud
summonfallingblock
*suicide
swap
switch
takeitem
*teleport tp
teleportin
*teleportto tpt
*threat threatchange threatmod
*throw
time
*totem
*variableadd varadd
variablemath varmath
*variableset setvariable setvar
*variablesubtract varsubtract
*variableunset unsetvariable
*velocity
weather
swing
feed
enchant
*model
cullconfig
*lockmodel
*bodyrotation bodyclamp
pairmodel
petrify
*state animation
*modifystate modifyanimation modstate
*defaultstate
lockmodelhead lockhead
*remapmodel
renderinit
statetoggle togglestate
submodel
swapentity
*scale meg_scale setmodelscale
rootmotion
animatemodel
changeparent
*changepart
cycleparts
enchant
*tint
glowbone
*brightness
*partvisibility partvis
modelplayerskin modelskin
billboard
*mountmodel
*dismountmodel
*dismountall
meg_jump
move
nofall
syncyaw
bindhitbox
hitboxconfig
*segment seg
ik inversekinematics
iktarget
ikchain
jiggleimpulse
headtarget
headconfig
modeldisguise
modelundisguise
linkitembone
setitemmodel
leash
leashself
setmodeltag
setmodeltagvisible
vfx
vfxpos
vfxposreset
vfxrot
vfxrotreset
vfxscale
vfxscalereset
vfxchangemodel
vfxsetvis
vfxenchant
vfxtint
vfxbrightness
"""

CONDITIONS = """
*altitude height
biome
*blocking isblocking
blocktype
burning isburning onfire
*chance
children
crouching sneaking issneaking
*damagecause damagetype
dawn
day
dimension
*distance
distancefromspawn
dusk
*entitytype type
*entitiesinradius eir
faction
fallspeed
*fieldofview fov infieldofview
gliding
globalscore scoreglobal
*onground grounded
haspassenger
*haspotioneffect haspotion
*hasaura hasbuff
hasaurastacks aurastacks
hasowner
*hastag tag
*hastarget
*health hp
*healthpercent hpp hppercent
heightabove
heightbelow
*holding
inblock
*incombat
inside
inwater isinwater
isclimbing
isfrozen
isliving living
ismonster monster
isplayer player
itemrecharging
lastdamagecause
level
lightlevel
*lineofsight los
lunarphase moonphase
mobsinchunk
*mobsinradius mir
mobsinworld
motiony
mounted
moving ismoving
*mythicmobtype mmt mobtype
night
*offgcd gcd
outside
owner isowner
playerkills
*playersinradius pir
playersonline
*playerwithin
*playernotwithin
raining
score
sprinting issprinting
*stance
sunny
targets
*targetwithin
*targetnotwithin
targetinlineofsight
thundering
time
*variableequals varequals vareq
*variableinrange varinrange
*variableisset varisset
*velocity directionalvelocity
wearing iswearing wielding iswielding
world
*ydiff ydifference
haspermission
hasai
*boundingboxesoverlap bbo
*modelhaspassengers hasmodelpassengers
"""

TARGETERS = """
*self caster boss mob
*target t
*trigger
*owner
*parent summoner
*children child summons
*nearestplayer np
*playersinradius pir
*playersinring
playersinworld world
playersonserver server everyone
*playersnearorigin pno
*mobsinradius mir
*mobsnearorigin mno
*entitiesinradius eir livingentitiesinradius
*entitiesinring
*entitiesincone eic
*entitiesinline eil
*entitiesnearorigin eno
itemsinradius iir
*threattable tt
*randomthreattarget rtt
randomthreattargetlocation rttl
*selflocation casterlocation sl
*selfeyelocation eyelocation sel
*forward f
*targetlocation tl targetloc
*triggerlocation
*spawnlocation casterspawnlocation
*origin o source
*location l
*ring
cone
sphere
rectangle
*randomlocationsnearcaster rlnc
*randomlocationsnearorigin rlno
*randomlocationsneartargets rlnt
blocksinradius bir
blocksnearorigin bno
*projectileforward pf
*variablelocation vl varlocation
ownerlocation
*flooroftarget fot
*modelpart mp
*modelpassengers
targetblock tb
"""

TRIGGERS = """
*oncombat
*onspawn
*ontimer
*ondamaged
*ondeath
*onsignal
*onentercombat
*ondropcombat
*onchangetarget
*onplayerkill
onkill
oninteract
onattack
onload
onspawnorload
onready
ondespawn
onshoot onbowshoot onshootbow
onbowhit
onskilldamage onskillhit onskill_damage
onprojectilehit
onprojectileland
onhear onvibration
ondismounted
onexplode
onprime
oncreepercharge
onteleport
ontame
onbreed
ontrade
onbucket
onchangeworld
onenterbounds
onexitbounds
onplayerenter
onplayerexit
oncinematicstart
oncinematicend
"""


# Etiquetas de hueso de ModelEngine 4 (wiki «Bone Behaviors»): las lee el conversor (tools/vfx_bb.py, bone_tag)
BONE_TAGS = """
*h head
*hi head_inherit
h[] head_params
*p seat
*ob hitbox_obb
b hitbox_aabb
g ghost
tag nametag
l leash
ih item_head
ir item_right
il item_left
*seg
segf
*tl
tlf
jig
jig[]
ik[]
phead
prarm
plarm
pbody
prleg
plleg
limb[]
"""


def parse(text):
    """[(nombre, [alias...], importante)]"""
    out = []
    for line in text.strip().splitlines():
        line = line.strip()
        if not line:
            continue
        hot = line.startswith('*')
        names = line.lstrip('*').split()
        out.append((names[0], names[1:], hot))
    return out


ALL = {
    'mecánicas': parse(MECHANICS),
    'condiciones': parse(CONDITIONS),
    'objetivos': parse(TARGETERS),
    'disparadores': parse(TRIGGERS),
}
TAGS = parse(BONE_TAGS)
