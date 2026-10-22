package net.tierrasfantasticas.tfclient.crates.block;

import net.tierrasfantasticas.tfclient.crates.util.CrateParticles;
import net.tierrasfantasticas.tfclient.crates.util.CrateSfx;
import net.tierrasfantasticas.tfclient.crates.animation.AnimationRegistry;
import net.tierrasfantasticas.tfclient.crates.animation.CrateAnimation;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.config.ParticleLayer;
import net.tierrasfantasticas.tfclient.crates.config.Rarity;
import net.tierrasfantasticas.tfclient.crates.crate.CrateRegistry;
import net.tierrasfantasticas.tfclient.crates.registry.ModRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

public class CrateBlockEntity extends BlockEntity {
   private CrateConfig config = new CrateConfig();
   private int seenRegistryGeneration = -1;
   public static final float P_ANTICIPATION_END = 0.1F;
   public static final float P_OPEN_END = 0.22F;
   public static final float P_REVEAL_END = 0.9F;
   public static final int REEL_STEPS = 180;
   private static final int OPEN_TICKS = 16;
   private static final int HOLD_TICKS = 70;
   private static final int CLOSE_TICKS = 26;
   private static final float SPIRAL_FRAC = 0.39F;
   private static final int SPIRAL_MIN_TICKS = 130;
   private static final int PEAK_HOLD_TICKS = 18;
   private static final int SPIRAL_BONUS_MYTHIC = 24;
   private static final int BUILDUP_TICKS = 196;
   public boolean animating = false;
   public int animTick = 0;
   public int animTotal = 150;
   private int tSpiralEnd = 0;
   private int tOpenEnd = 0;
   private int tSpinStop = 0;
   private int tHoldEnd = 0;
   private int tRiseEnd = 0;
   private boolean peakPlayed = false;
   private boolean instant = false;
   private CrateAnimation animation = AnimationRegistry.get(AnimationRegistry.defaultId());
   private int animColor = 16777215;
   private ItemStack rewardIcon = ItemStack.EMPTY;
   private final List<ItemStack> candidates = new ArrayList<>();
   private int winnerIndex = 0;
   private Rarity effectRarity = Rarity.COMMON;
   private int[] candidateRarities = new int[0];
   private int soundStage = 0;
   private int winTick = -1;
   private int noteIndex = 0;
   private int lastReelIndex = -1;
   private int lastRiseTick = -100;
   public float ambientTime = 0.0F;
   private final Set<UUID> openedBy = new HashSet<>();
   public boolean sceneLidMode = false;
   private boolean muteAudio = false;
   // (1.3.99) la crate del mundo, a los tiempos de la cinemática del Santuario de las Hadas (CrateCinematicScreen): la
   // llave llega y gira, tiembla, se abre del todo con un pilar de luz y el premio sube encima; se cierra al final
   public static final int SCENE_TOTAL = 360;
   public static final int SCENE_KEY = 20;
   public static final int SCENE_KEY_IN = 66;
   public static final int SCENE_TURN = 70;
   public static final int SCENE_TREMBLE = 84;
   public static final int SCENE_OPEN = 102;
   public static final int SCENE_REVEAL = 258;
   public static final int SCENE_CLOSE_START = 326;
   private ItemStack sceneWinner = ItemStack.EMPTY;

   public boolean hasOpenedBy(UUID id) {
      return id != null && this.openedBy.contains(id);
   }

   public void markOpenedBy(UUID id) {
      if (id != null && this.openedBy.add(id)) {
         this.setChanged();
      }
   }

   public CrateBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)ModRegistry.CRATE_BE.get(), pos, state);
   }

   public CrateConfig getConfig() {
      return this.config;
   }

   public void setConfig(CrateConfig config) {
      this.config = config == null ? new CrateConfig() : config;
      this.animColor = this.config.rarity.rgb();
      this.setChanged();
      if (this.level != null && !this.level.isClientSide) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   public Rarity getRarity() {
      return this.config.rarity;
   }

   public CrateAnimation getAnimation() {
      return this.animation;
   }

   public int getAnimColor() {
      return this.animColor;
   }

   public ItemStack getRewardIcon() {
      return this.rewardIcon;
   }

   public List<ItemStack> getCandidates() {
      return this.candidates;
   }

   public int getWinnerIndex() {
      return this.winnerIndex;
   }

   public Rarity getEffectRarity() {
      return this.effectRarity;
   }

   public int[] getCandidateRarities() {
      return this.candidateRarities;
   }

   public static int[] decodeRarities(CompoundTag wrap) {
      return wrap != null && wrap.contains("rar") ? wrap.getIntArray("rar") : new int[0];
   }

   public void startAnimation(String animationId, int rarityColor, int winnerIndex, int winnerRarity, int[] candRarities, List<ItemStack> cands) {
      this.muteAudio = false;
      this.animation = AnimationRegistry.get(animationId);
      int base = Math.max(this.animation.style() == CrateAnimation.Style.INSTANT ? 26 : 6, this.animation.durationTicks());
      boolean bl = this.instant = this.animation.style() == CrateAnimation.Style.INSTANT;
      if (this.instant) {
         this.tSpiralEnd = 0;
         this.tOpenEnd = 0;
         this.tSpinStop = 0;
         this.tHoldEnd = 0;
         this.tRiseEnd = 0;
         this.animTotal = base;
      } else {
         this.tSpinStop = Math.round((float)base * 0.9F);
         Rarity cr = this.config.rarity;
         this.tSpiralEnd = 196;
         this.tOpenEnd = this.tSpiralEnd + 16;
         if (this.tOpenEnd >= this.tSpinStop - 4) {
            this.tOpenEnd = Math.max(this.tSpiralEnd + 2, this.tSpinStop - 6);
         }

         this.tRiseEnd = Math.max(4, this.tSpiralEnd - peakHoldTicks(cr));
         this.tHoldEnd = this.tSpinStop + 70;
         this.animTotal = this.tHoldEnd + 26;
      }

      this.animColor = rarityColor;
      Rarity[] rv = Rarity.values();
      this.effectRarity = rv[Math.max(0, Math.min(rv.length - 1, winnerRarity))];
      this.candidateRarities = candRarities == null ? new int[0] : candRarities;
      this.candidates.clear();
      if (cands != null) {
         for (ItemStack s : cands) {
            if (s != null && !s.isEmpty()) {
               this.candidates.add(s);
            }
         }
      }

      this.winnerIndex = this.candidates.isEmpty() ? 0 : Math.max(0, Math.min(this.candidates.size() - 1, winnerIndex));
      this.rewardIcon = this.candidates.isEmpty() ? ItemStack.EMPTY : this.candidates.get(this.winnerIndex);
      this.animTick = 0;
      this.soundStage = 0;
      this.winTick = -1;
      this.noteIndex = 0;
      this.lastReelIndex = -1;
      this.lastRiseTick = -100;
      this.peakPlayed = false;
      this.animating = true;
      if (this.level != null) {
         this.playUnlock(this.config.rarity);
      }
   }

   public void startSceneLid(int rarityColor, int winnerRarity, boolean muteAudio, ItemStack winner) {
      this.startSceneLid(rarityColor, winnerRarity, muteAudio);
      this.sceneWinner = winner == null ? ItemStack.EMPTY : winner.copy();
   }

   /** El premio que sube encima de la crate (para los que miran desde fuera). */
   public ItemStack getSceneWinner() {
      return this.sceneWinner;
   }

   public void startSceneLid(int rarityColor, int winnerRarity, boolean muteAudio) {
      this.instant = false;
      this.sceneLidMode = true;
      this.animating = true;
      this.muteAudio = muteAudio;
      this.sceneWinner = ItemStack.EMPTY;
      this.animation = AnimationRegistry.get(AnimationRegistry.defaultId());
      this.animTick = 0;
      this.animTotal = SCENE_TOTAL;
      this.tSpiralEnd = SCENE_OPEN;
      this.tOpenEnd = SCENE_OPEN + 10;
      this.tSpinStop = SCENE_CLOSE_START;
      this.tHoldEnd = SCENE_CLOSE_START;
      this.tRiseEnd = 0;
      this.animColor = rarityColor;
      Rarity[] rv = Rarity.values();
      this.effectRarity = rv[Math.max(0, Math.min(rv.length - 1, winnerRarity))];
      this.candidates.clear();
      this.candidateRarities = new int[0];
      this.winnerIndex = 0;
      this.rewardIcon = ItemStack.EMPTY;
      this.soundStage = 0;
      this.winTick = -1;
      this.noteIndex = 0;
      this.lastReelIndex = -1;
      this.lastRiseTick = -100;
      this.peakPlayed = false;
      if (this.level != null) {
         this.playUnlock(this.config.rarity);
      }
   }

   public void startSceneLid(int rarityColor, int winnerRarity) {
      this.startSceneLid(rarityColor, winnerRarity, false);
   }

   public float progress() {
      return this.animating ? Math.min(1.0F, (float)this.animTick / (float)Math.max(1, this.animTotal)) : 0.0F;
   }

   public int getSpiralEndTick() {
      return this.tSpiralEnd;
   }

   public int getOpenEndTick() {
      return this.tOpenEnd;
   }

   public int getSpinStopTick() {
      return this.tSpinStop;
   }

   public int getHoldEndTick() {
      return this.tHoldEnd;
   }

   public boolean isInstant() {
      return this.instant;
   }

   public ParticleLayer.Phase currentPhase() {
      if (!this.animating) {
         return ParticleLayer.Phase.IDLE;
      } else if (this.instant) {
         return ParticleLayer.Phase.REVEAL;
      } else {
         int t = this.animTick;
         if (t < this.tSpiralEnd) {
            return ParticleLayer.Phase.ANTICIPATION;
         } else if (t < this.tOpenEnd) {
            return ParticleLayer.Phase.OPEN;
         } else {
            return t < this.tSpinStop ? ParticleLayer.Phase.REVEAL : ParticleLayer.Phase.FINALE;
         }
      }
   }

   public float lidOpen(float partial) {
      if (!this.animating) {
         return 0.0F;
      } else if (this.sceneLidMode) {
         float ts = (float)this.animTick + partial;
         if (ts < SCENE_TREMBLE) {
            return 0.0F;
         } else if (ts < SCENE_OPEN) {
            // golpecitos de la tapa mientras tiembla
            float k = (ts - SCENE_TREMBLE) / (SCENE_OPEN - SCENE_TREMBLE);
            return Math.max(0.0F, (float)Math.sin(ts * 1.9F)) * Math.max(0.0F, (float)Math.sin(ts * 0.7F)) * (0.02F + 0.07F * k);
         } else if (ts < SCENE_OPEN + 8) {
            return easeOutBack((ts - SCENE_OPEN) / 8.0F);
         } else if (ts < SCENE_CLOSE_START) {
            return 1.0F;
         } else {
            return ts < SCENE_TOTAL ? 1.0F - easeInOut(Math.min(1.0F, (ts - SCENE_CLOSE_START) / (SCENE_TOTAL - SCENE_CLOSE_START))) : 0.0F;
         }
      } else if (this.instant) {
         return 1.0F;
      } else {
         float t = (float)this.animTick + partial;
         if (t <= (float)this.tSpiralEnd) {
            return 0.0F;
         } else if (t < (float)this.tOpenEnd) {
            return easeOutBack((t - (float)this.tSpiralEnd) / Math.max(1.0F, (float)(this.tOpenEnd - this.tSpiralEnd)));
         } else if (t < (float)this.tHoldEnd) {
            return 1.0F;
         } else {
            return t < (float)this.animTotal
               ? 1.0F - easeInOut(Math.min(1.0F, (t - (float)this.tHoldEnd) / Math.max(1.0F, (float)(this.animTotal - this.tHoldEnd))))
               : 0.0F;
         }
      }
   }

   public float shake(float partial) {
      if (this.animating && this.sceneLidMode) {
         float t = (float)this.animTick + partial;
         if (t < SCENE_TREMBLE || t >= SCENE_OPEN) return 0.0F;
         return (float)Math.sin(t * 2.4F) * 0.045F * (t - SCENE_TREMBLE) / (SCENE_OPEN - SCENE_TREMBLE);
      }
      if (this.animating && !this.instant) {
         float t = (float)this.animTick + partial;
         if (t >= (float)this.tSpiralEnd) {
            return 0.0F;
         } else {
            float intensity = ((float)this.tSpiralEnd - t) / Math.max(1.0F, (float)this.tSpiralEnd);
            return (float)Math.sin((double)(t * 2.4F)) * 0.06F * intensity;
         }
      } else {
         return 0.0F;
      }
   }

   public float revealProgress(float partial) {
      if (this.instant) {
         return 1.0F;
      } else {
         float t = (float)this.animTick + partial;
         if (t <= (float)this.tOpenEnd) {
            return 0.0F;
         } else {
            return t >= (float)this.tSpinStop ? 1.0F : (t - (float)this.tOpenEnd) / Math.max(1.0F, (float)(this.tSpinStop - this.tOpenEnd));
         }
      }
   }

   public float finaleProgress(float partial) {
      if (this.instant) {
         return 1.0F;
      } else {
         float t = (float)this.animTick + partial;
         return t <= (float)this.tSpinStop ? 0.0F : Math.min(1.0F, (t - (float)this.tSpinStop) / 14.0F);
      }
   }

   public float closeProgress(float partial) {
      if (this.animating && !this.instant) {
         float t = (float)this.animTick + partial;
         if (t <= (float)this.tHoldEnd) {
            return 0.0F;
         } else {
            return t >= (float)this.animTotal ? 1.0F : (t - (float)this.tHoldEnd) / Math.max(1.0F, (float)(this.animTotal - this.tHoldEnd));
         }
      } else {
         return 0.0F;
      }
   }

   public static void serverTick(Level level, BlockPos pos, BlockState state, CrateBlockEntity be) {
      int generation = CrateRegistry.generation();
      if (be.seenRegistryGeneration != generation) {
         be.seenRegistryGeneration = generation;
         if (level instanceof ServerLevel serverLevel) {
            String id = be.config == null ? null : be.config.id;
            if (id != null && !id.isBlank()) {
               CrateConfig fresh = CrateRegistry.get(serverLevel).get(id);
               if (fresh != null) {
                  if (!fresh.save().equals(be.config.save())) {
                     be.setConfig(fresh.copy());
                  }
               }
            }
         }
      }
   }

   public static void clientTick(Level level, BlockPos pos, BlockState state, CrateBlockEntity be) {
      be.ambientTime++;
      if (be.animating) {
         be.animTick++;
         if (be.sceneLidMode) {
            be.emitLayers(level, pos, be.currentPhase());
            be.emitAccent(level, pos);
            be.advanceSceneSounds();
         } else {
            be.emitLayers(level, pos, be.currentPhase());
            be.emitAccent(level, pos);
            be.emitBuildupSpiral(level, pos);
            be.emitSpiralBurst(level, pos);
            be.advanceSounds();
         }

         if (be.animTick >= be.animTotal) {
            be.animating = false;
            be.sceneLidMode = false;
            be.sceneWinner = ItemStack.EMPTY;
            be.animTick = 0;
            be.rewardIcon = ItemStack.EMPTY;
            be.candidates.clear();
            be.animColor = be.config.rarity.rgb();
            be.effectRarity = be.config.rarity;
         }
      } else if (be.config.particles) {
         be.emitLayers(level, pos, ParticleLayer.Phase.IDLE);
      }
   }

   private void advanceSceneSounds() {
      int t = this.animTick;
      if (t == SCENE_OPEN) {
         this.playOpenAccent(this.effectRarity);
      } else if (t == SCENE_REVEAL) {
         this.playWin(this.effectRarity);
      } else if (t == SCENE_REVEAL + 6) {
         this.playWinTail(this.effectRarity);
      } else if (t == SCENE_CLOSE_START) {
         this.playClose(this.config.rarity);
      }
   }

   /**
    * (1.3.109, el dueño: «dale rework a los efectos VFX de la crate, me parecen feos») una partícula que se suelta: las de
    * Minecraft (las que el dueño elija en el editor) o las propias de TF (CrateParticles: luces suaves y destellos que
    * suman luz), que sustituyen al polvo, las letras de encantamiento, las varas del End, los fuegos y las explosiones.
    */
   private interface Fx {
      void at(Level level, double x, double y, double z, double vx, double vy, double vz);
   }

   private static Fx vanilla(ParticleOptions o) {
      return (l, x, y, z, vx, vy, vz) -> l.addParticle(o, x, y, z, vx, vy, vz);
   }

   private static Fx tf(int kind, int rgb, float size) {
      return (l, x, y, z, vx, vy, vz) -> CrateParticles.add(l, kind, x, y, z, vx, vy, vz, rgb, size);
   }

   private Fx light(int rgb, float size) {
      return tf(CrateParticles.GLOW, rgb, size);
   }

   private Fx sparkle(int rgb, float size) {
      return tf(CrateParticles.SPARK, rgb, size);
   }

   private void emitLayers(Level level, BlockPos pos, ParticleLayer.Phase phase) {
      for (ParticleLayer layer : this.config.particleLayers) {
         Fx opt;
         if (layer.phase == phase
            && (phase != ParticleLayer.Phase.IDLE || level.getGameTime() % (long)Math.max(1, layer.interval) == 0L)
            && (opt = this.resolve(layer)) != null) {
            this.emitShape(level, pos, layer, opt);
         }
      }
   }

   private Fx resolve(ParticleLayer layer) {
      String id = layer.particleId == null ? "" : layer.particleId.trim();
      String path = id.contains(":") ? id.substring(id.indexOf(58) + 1) : id;
      int color = layer.useRarityColor ? this.animColor : parseHex(layer.colorHex, this.animColor);
      switch (path) {
         case "crate_glow":
            return this.light(color, 1.0F);
         case "crate_spark":
            return this.sparkle(color, 1.0F);
         case "crate_flash":
            return tf(CrateParticles.FLASH, color, 1.6F);
         default:
            ParticleOptions o = this.resolveVanilla(layer, id, path, color);
            return o == null ? null : vanilla(o);
      }
   }

   private ParticleOptions resolveVanilla(ParticleLayer layer, String id, String path, int color) {
      if (path.startsWith("fs_")) {
         return this.resolveFsPreset(path, color);
      } else {
         switch (path) {
            case "dust":
               return this.dust(color, 1.4F);
            case "dust_color_transition":
               return new DustColorTransitionOptions(rgbVec(color), new Vector3f(1.0F, 1.0F, 1.0F), 1.4F);
            case "block":
               return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());
            case "block_marker":
               return new BlockParticleOption(ParticleTypes.BLOCK_MARKER, Blocks.AMETHYST_BLOCK.defaultBlockState());
            case "falling_dust":
               return new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.AMETHYST_BLOCK.defaultBlockState());
            case "item":
               return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.NETHER_STAR));
            case "sculk_charge":
               return new SculkChargeParticleOptions(0.0F);
            case "shriek":
               return new ShriekParticleOption(0);
            default:
               ResourceLocation rl = ResourceLocation.tryParse(id);
               if (rl == null) {
                  return null;
               } else {
                  ParticleType type = (ParticleType)ForgeRegistries.PARTICLE_TYPES.getValue(rl);
                  return type instanceof SimpleParticleType ? (SimpleParticleType)type : null;
               }
         }
      }
   }

   private static Vector3f rgbVec(int color) {
      return new Vector3f((float)(color >> 16 & 0xFF) / 255.0F, (float)(color >> 8 & 0xFF) / 255.0F, (float)(color & 0xFF) / 255.0F);
   }

   private ParticleOptions resolveFsPreset(String path, int layerColor) {
      switch (path) {
         case "fs_dust_red":
            return this.dust(16724016, 1.2F);
         case "fs_dust_orange":
            return this.dust(16747032, 1.2F);
         case "fs_dust_gold":
            return this.dust(16760868, 1.2F);
         case "fs_dust_yellow":
            return this.dust(16773192, 1.2F);
         case "fs_dust_lime":
            return this.dust(6750003, 1.2F);
         case "fs_dust_green":
            return this.dust(2271263, 1.2F);
         case "fs_dust_aqua":
            return this.dust(3407840, 1.2F);
         case "fs_dust_blue":
            return this.dust(3368703, 1.2F);
         case "fs_dust_purple":
            return this.dust(10105855, 1.2F);
         case "fs_dust_magenta":
            return this.dust(16727264, 1.2F);
         case "fs_dust_pink":
            return this.dust(16751304, 1.2F);
         case "fs_dust_white":
            return this.dust(16777215, 1.2F);
         case "fs_dust_tiny":
            return this.dust(layerColor, 0.6F);
         case "fs_dust_huge":
            return this.dust(layerColor, 2.6F);
         case "fs_fade_fire":
            return new DustColorTransitionOptions(rgbVec(16723992), rgbVec(16769104), 1.4F);
         case "fs_fade_ice":
            return new DustColorTransitionOptions(rgbVec(3399935), rgbVec(16777215), 1.4F);
         case "fs_fade_void":
            return new DustColorTransitionOptions(rgbVec(10105855), rgbVec(1443106), 1.4F);
         case "fs_fade_toxic":
            return new DustColorTransitionOptions(rgbVec(2271263), rgbVec(11992892), 1.4F);
         case "fs_fade_royal":
            return new DustColorTransitionOptions(rgbVec(16760868), rgbVec(16777215), 1.4F);
         case "fs_shard_gold":
            return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GOLD_BLOCK.defaultBlockState());
         case "fs_shard_diamond":
            return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIAMOND_BLOCK.defaultBlockState());
         case "fs_shard_amethyst":
            return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());
         case "fs_shard_emerald":
            return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.EMERALD_BLOCK.defaultBlockState());
         case "fs_burst_star":
            return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.NETHER_STAR));
         case "fs_burst_gem":
            return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.DIAMOND));
         case "fs_soul_swirl":
            return new SculkChargeParticleOptions(0.0F);
         default:
            return null;
      }
   }

   private DustParticleOptions dust(int color, float scale) {
      return new DustParticleOptions(
         new Vector3f((float)(color >> 16 & 0xFF) / 255.0F, (float)(color >> 8 & 0xFF) / 255.0F, (float)(color & 0xFF) / 255.0F), scale
      );
   }

   private double particleDensity() {
      return (this.config.rarity == Rarity.COMMON ? 0.55 : 1.0) * this.sceneShare();
   }

   /**
    * (1.3.104, el dueño: «más suaves en la escena») Quien abre la crate ve sus partículas dentro de la escena
    * (CrateCinematicScreen.worldParticles), donde se miran de cerca y con unas 1 000 a la vez tapaban la crate: en su
    * juego (muteAudio: solo el que abre), mientras dura la escena, sale SCENE_SHARE de cada capa. Los demás jugadores
    * las siguen viendo enteras en el mundo (cada cliente suelta las suyas).
    */
   private double sceneShare() {
      return this.animating && this.sceneLidMode && this.muteAudio ? SCENE_SHARE : 1.0;
   }

   private static final double SCENE_SHARE = 0.20;  // (1.3.105, el dueño: «un poco menos»; era 0,35)

   private void emitShape(Level level, BlockPos pos, ParticleLayer layer, Fx opt) {
      double crateScale = (double)this.config.rarity.sizeScale();
      double scale = 1.0 + (crateScale - 1.0) * 0.22;
      double cx = (double)pos.getX() + 0.5;
      double cy = (double)pos.getY() + Math.max(0.0, layer.yOffset) * scale;
      double cz = (double)pos.getZ() + 0.5;
      RandomSource rng = level.random;
      int n = Math.max(1, (int)Math.round((double)Math.max(1, layer.count) * this.particleDensity()));
      double r = layer.radius * scale;
      double sp = layer.speed;
      double spread = layer.spread * scale;
      double t = (double)this.ambientTime * 0.1;

      for (int i = 0; i < n; i++) {
         switch (layer.shape) {
            case HALO: {
               double angle = t + (double)i * ((Math.PI * 2) / (double)n);
               opt.at(
                  level,
                  cx + Math.cos(angle) * r,
                  cy + 0.05 * scale * Math.sin(t * 1.7 + (double)i),
                  cz + Math.sin(angle) * r,
                  -Math.sin(angle) * sp,
                  sp * 0.4,
                  Math.cos(angle) * sp
               );
               break;
            }
            case RING: {
               double angle = (double)i * ((Math.PI * 2) / (double)n);
               opt.at(level, cx + Math.cos(angle) * r, cy, cz + Math.sin(angle) * r, Math.cos(angle) * sp, 0.0, Math.sin(angle) * sp);
               break;
            }
            case BURST:
               double ax = (rng.nextDouble() - 0.5) * 2.0;
               double az = (rng.nextDouble() - 0.5) * 2.0;
               double ay = 0.4 + rng.nextDouble() * 0.6;
               double mag = Math.max(0.001, Math.sqrt(ax * ax + ay * ay + az * az));
               opt.at(level, cx, cy, cz, ax / mag * (sp + spread), ay / mag * (sp + spread), az / mag * (sp + spread));
               break;
            case COLUMN:
               opt.at(
                  level, cx + (rng.nextDouble() - 0.5) * spread, cy + rng.nextDouble() * (0.4 * scale + r), cz + (rng.nextDouble() - 0.5) * spread, 0.0, sp, 0.0
               );
               break;
            case SPIRAL:
               double tt = (double)this.ambientTime * 0.18;
               double frac = (double)((i + (int)(this.ambientTime % 3.0F)) % n) / (double)n;
               double ang = tt + frac * (Math.PI * 4);
               double rr = r * (1.05 - frac * 0.4);
               opt.at(level, cx + Math.cos(ang) * rr, cy + frac * 1.5 * scale, cz + Math.sin(ang) * rr, -Math.sin(ang) * sp, sp + 0.02, Math.cos(ang) * sp);
               break;
            case FOUNTAIN: {
               double angle = rng.nextDouble() * Math.PI * 2.0;
               opt.at(level, cx, cy, cz, Math.cos(angle) * spread, sp + rng.nextDouble() * 0.15, Math.sin(angle) * spread);
               break;
            }
            case VORTEX: {
               double angle = t * 4.0 + (double)i * ((Math.PI * 2) / (double)n);
               double rr2 = r * (0.6 + 0.4 * Math.sin(t * 2.0 + (double)i));
               opt.at(
                  level,
                  cx + Math.cos(angle) * rr2,
                  cy + rng.nextDouble() * 0.5 * scale,
                  cz + Math.sin(angle) * rr2,
                  -Math.cos(angle) * sp * 2.0,
                  sp,
                  -Math.sin(angle) * sp * 2.0
               );
               break;
            }
            case RAIN:
               opt.at(
                  level,
                  cx + (rng.nextDouble() - 0.5) * (spread + r * 2.0),
                  cy + rng.nextDouble() * 0.5 * scale,
                  cz + (rng.nextDouble() - 0.5) * (spread + r * 2.0),
                  0.0,
                  -sp,
                  0.0
               );
               break;
            case POINT:
               opt.at(level, cx, cy, cz, (rng.nextDouble() - 0.5) * sp, rng.nextDouble() * sp, (rng.nextDouble() - 0.5) * sp);
         }
      }
   }

   private void emitAccent(Level level, BlockPos pos) {
      if (!this.instant) {
         int t = this.animTick;
         double cx = (double)pos.getX() + 0.5;
         double cz = (double)pos.getZ() + 0.5;
         double cyTop = (double)pos.getY() + 1.5;
         RandomSource rng = level.random;
         double share = this.sceneShare();
         if (t >= this.tOpenEnd && t < this.tSpinStop && t % 3 == 0) {
            Fx amb = this.themeFx(this.animation.theme());
            double ang = rng.nextDouble() * (Math.PI * 2);
            double rad = 0.5 + rng.nextDouble() * 0.2;
            if (share >= 1.0 || rng.nextDouble() < share) amb.at(
               level,
               cx + Math.cos(ang) * rad,
               (double)pos.getY() + 0.2 + rng.nextDouble() * 0.5,
               cz + Math.sin(ang) * rad,
               0.0,
               0.02 + rng.nextDouble() * 0.03,
               0.0
            );
         }

         if (t >= this.tSpinStop && t < this.tHoldEnd && t % 2 == 0) {
            Fx fin = this.sparkle(this.effectRarity.rgb(), 1.2F);
            int burst = t < this.tSpinStop + 12 ? 6 : 2;

            for (int i = 0; i < burst; i++) {
               double a2 = rng.nextDouble() * (Math.PI * 2);
               double s = 0.2 + rng.nextDouble() * 0.5;
               if (share >= 1.0 || rng.nextDouble() < share) fin.at(level, cx, cyTop, cz, Math.cos(a2) * s, 0.15 + rng.nextDouble() * 0.3, Math.sin(a2) * s);
            }

            for (int var19 = 0; var19 < 3; var19++) {
               double a3 = rng.nextDouble() * (Math.PI * 2);
               double s2 = 0.15 + rng.nextDouble() * 0.35;
               if (share >= 1.0 || rng.nextDouble() < share) this.light(this.animColor, 1.5F).at(level, cx, cyTop, cz, Math.cos(a3) * s2, 0.1 + rng.nextDouble() * 0.2, Math.sin(a3) * s2);
            }
         }

         if (t >= this.tHoldEnd && t < this.animTotal && t % 2 == 0) {
            double a4 = rng.nextDouble() * (Math.PI * 2);
            double rad2 = 0.2 + rng.nextDouble() * 0.25;
            if (share >= 1.0 || rng.nextDouble() < share) this.light(this.config.rarity.rgb(), 1.2F).at(level, cx + Math.cos(a4) * rad2, cyTop - 0.2, cz + Math.sin(a4) * rad2, 0.0, -0.06, 0.0);
         }
      }
   }

   private void emitBuildupSpiral(Level level, BlockPos pos) {
      if (!this.instant && this.tSpiralEnd > 1) {
         int t = this.animTick;
         if (this.animTick > 0 && t < this.tSpiralEnd) {
            Rarity r = this.config.rarity;
            boolean common = r == Rarity.COMMON;
            if (!common || ((int)this.ambientTime & 1) != 1) {
               double pscale = 1.0 + ((double)r.sizeScale() - 1.0) * 0.22;
               float p = Math.min(1.0F, (float)t / (float)Math.max(1, this.tSpiralEnd));
               double cx = (double)pos.getX() + 0.5;
               double cz = (double)pos.getZ() + 0.5;
               double baseY = (double)pos.getY() + 0.1;
               Fx dust = this.light(r.rgb(), 1.2F);
               Fx spark = this.sparkle(r.rgb(), 1.0F);
               int arms = common ? 1 + Math.round(p * 1.0F) : 2 + Math.round(p * 4.0F);
               double turns = 2.0 + (double)p * 1.5;
               double height = (1.25 + (double)p * 0.45) * pscale;
               double baseR = 0.55 * pscale;
               double spin = (double)this.ambientTime * 0.3;
               int steps = common ? 2 : 3;

               for (int a = 0; a < arms; a++) {
                  double armOff = (double)a * ((Math.PI * 2) / (double)arms);

                  for (int s = 0; s < steps; s++) {
                     double frac = ((double)s + (double)(this.ambientTime % 4.0F) * 0.25) / (double)steps;
                     double ang = spin + armOff + frac * turns * (Math.PI * 2);
                     double rr = baseR * (1.05 - frac * 0.45);
                     double px = cx + Math.cos(ang) * rr;
                     double pz = cz + Math.sin(ang) * rr;
                     double py = baseY + frac * height;
                     double vTan = 0.04 + (double)p * 0.05;
                     dust.at(level, px, py, pz, -Math.sin(ang) * vTan, 0.02 + (double)p * 0.03, Math.cos(ang) * vTan);
                     boolean addSpark = common ? s == steps - 1 : s == steps - 1 || p > 0.6F;
                     if (addSpark) {
                        spark.at(level, px, py, pz, -Math.sin(ang) * vTan * 0.6, 0.03, Math.cos(ang) * vTan * 0.6);
                     }
                  }
               }
            }
         }
      }
   }

   private void emitSpiralBurst(Level level, BlockPos pos) {
      if (!this.instant && this.tSpiralEnd > 1 && this.animTick == this.tSpiralEnd - 1) {
         Rarity r = this.config.rarity;
         int tier = r.ordinal();
         double scale = 1.0 + ((double)r.sizeScale() - 1.0) * 0.22;
         RandomSource rng = level.random;
         double cx = (double)pos.getX() + 0.5;
         double cz = (double)pos.getZ() + 0.5;
         double y = (double)pos.getY() + 0.6 * scale;
         double rad = 0.5 * scale;
         Fx dust = this.light(r.rgb(), 1.4F);
         Fx spark = this.sparkle(r.rgb(), 1.3F);
         float power = 1.0F + (float)tier * 0.35F;
         tf(CrateParticles.FLASH, r.rgb(), 2.4F + tier * 0.4F).at(level, cx, y, cz, 0.0, 0.0, 0.0);
         int puffs = 2 + tier;

         for (int i = 0; i < puffs; i++) {
            this.light(r.rgb(), 2.6F).at(
               level,
               cx + (rng.nextDouble() - 0.5) * rad,
               y + (rng.nextDouble() - 0.5) * 0.3 * scale,
               cz + (rng.nextDouble() - 0.5) * rad,
               0.0,
               0.0,
               0.0
            );
         }

         puffs = 20 + tier * 6;

         for (int j = 0; j < puffs; j++) {
            double ang = (double)j * ((Math.PI * 2) / (double)puffs);
            double px = cx + Math.cos(ang) * rad;
            double pz = cz + Math.sin(ang) * rad;
            double v = 0.18 * (double)power;
            dust.at(level, px, y, pz, Math.cos(ang) * v, 0.06, Math.sin(ang) * v);
            spark.at(level, px, y, pz, Math.cos(ang) * v * 0.7, 0.08, Math.sin(ang) * v * 0.7);
         }

         puffs = 24 + tier * 10;

         for (int k = 0; k < puffs; k++) {
            double ax = rng.nextDouble() - 0.5;
            double ay = rng.nextDouble() * 0.9 + 0.1;
            double az = rng.nextDouble() - 0.5;
            double mag = Math.max(0.001, Math.sqrt(ax * ax + ay * ay + az * az));
            double sp = (0.25 + rng.nextDouble() * 0.35) * (double)power;
            Fx p = k % 3 == 0 ? this.sparkle(0xFFFFFF, 1.1F) : (k % 3 == 1 ? spark : dust);
            p.at(level, cx, y, cz, ax / mag * sp, ay / mag * sp, az / mag * sp);
         }

         puffs = 8 + tier * 4;

         for (int l = 0; l < puffs; l++) {
            double a = rng.nextDouble() * (Math.PI * 2);
            double rr = rng.nextDouble() * rad * 0.6;
            spark.at(
               level,
               cx + Math.cos(a) * rr,
               y,
               cz + Math.sin(a) * rr,
               (rng.nextDouble() - 0.5) * 0.06,
               (0.25 + rng.nextDouble() * 0.4) * (double)power,
               (rng.nextDouble() - 0.5) * 0.06
            );
         }
      }
   }

   private Fx themeFx(CrateAnimation.Theme t) {
      return switch (t) {
         case INFERNAL -> this.sparkle(0xFF8A3A, 1.0F);
         case CELESTIAL -> this.sparkle(0xCFE4FF, 1.0F);
         case NEON -> this.light(0x4CFFE0, 1.0F);
         case MAGIC -> this.sparkle(0xC77DFF, 1.0F);
         case ANCIENT -> this.light(0xFFD27A, 1.0F);
         case NATURE -> this.light(0x8CFF7A, 1.0F);
         case CASINO -> this.sparkle(0xFFE24A, 1.0F);
         default -> this.light(this.animColor, 1.0F);
      };
   }

   private static int parseHex(String hex, int fallback) {
      if (hex == null) {
         return fallback;
      } else {
         try {
            return (int)Long.parseLong(hex.replace("#", "").trim(), 16);
         } catch (NumberFormatException var3) {
            return fallback;
         }
      }
   }

   private void advanceSounds() {
      if (this.instant) {
         if (this.soundStage < 60) {
            this.playWin(this.effectRarity);
            this.soundStage = 60;
         }
      } else {
         int t = this.animTick;
         Rarity cr = this.config.rarity;
         if (this.soundStage == 0 && t >= 2) {
            this.playSpiralCharge(cr);
            this.soundStage = 1;
         }

         float p;
         if (this.soundStage == 1
            && t > 2
            && t < this.tRiseEnd
            && t - this.lastRiseTick >= Math.max(2, Math.round(10.0F - (p = Math.min(1.0F, (float)(t - 2) / (float)Math.max(1, this.tRiseEnd - 2))) * 8.0F))) {
            this.lastRiseTick = t;
            this.playSpiralRise(cr, p);
         }

         if (this.soundStage == 1 && !this.peakPlayed && t >= this.tRiseEnd) {
            this.peakPlayed = true;
            this.playSpiralPeak(cr);
         }

         if (this.soundStage == 1 && t >= this.tSpiralEnd) {
            this.playOpenAccent(cr);
            this.soundStage = 2;
         }

         if (this.soundStage >= 2 && t >= this.tOpenEnd && t < this.tSpinStop && !this.candidates.isEmpty()) {
            float rp = this.revealProgress(0.0F);
            int n = this.candidates.size();
            int winner = Math.max(0, Math.min(n - 1, this.winnerIndex));
            float maxTravel = reelTravel(n, winner);
            int idx = (int)Math.floor((double)(easeOutReel(Math.min(1.0F, rp)) * maxTravel));
            if (idx != this.lastReelIndex) {
               this.lastReelIndex = idx;
               float pitch = 0.85F + rp * 0.45F;
               CrateSfx.tick(this.sfx, 0.5F, pitch);
            }
         }

         if (t >= this.tSpinStop && this.soundStage >= 2 && this.soundStage < 60) {
            this.playWin(this.effectRarity);
            this.soundStage = 60;
            this.winTick = t;
            this.noteIndex = 0;
         } else if (this.soundStage == 60 && this.noteIndex == 0 && t - this.winTick >= 4) {
            this.playWinTail(this.effectRarity);
            this.noteIndex = 1;
         }

         if (this.soundStage >= 60 && this.soundStage < 70 && t >= this.tHoldEnd) {
            this.playClose(cr);
            this.soundStage = 70;
         }
      }
   }

   // (1.3.109, el dueño: «ese sonido horrendo de las crates, odio ese sonido»: el estampido del Warden, truenos, el Wither
   // y los lamentos del valle de las almas) los sonidos de la crate del mundo son los de CrateSfx, como los de la apertura
   // clásica: una sola paleta, mágica y limpia
   private final CrateSfx.Sink sfx = this::play;

   private void playUnlock(Rarity r) {
      CrateSfx.unlock(this.sfx, r);
   }

   private void playSpiralCharge(Rarity r) {
      CrateSfx.spiralCharge(this.sfx, r);
   }

   private void playSpiralRise(Rarity r, float p) {
      CrateSfx.spiralRise(this.sfx, r, p);
   }

   private static int peakHoldTicks(Rarity r) {
      return 44;
   }

   private static int spiralBonusTicks(Rarity r) {
      return 24;
   }

   private void playSpiralPeak(Rarity r) {
      CrateSfx.spiralPeak(this.sfx, r);
   }

   private void playOpenAccent(Rarity r) {
      CrateSfx.openAccent(this.sfx, r);
   }

   private void playWin(Rarity r) {
      CrateSfx.win(this.sfx, r);
   }

   private void playWinTail(Rarity r) {
      CrateSfx.winTail(this.sfx, r);
   }

   private void playClose(Rarity r) {
      CrateSfx.close(this.sfx, r);
   }

   private void play(SoundEvent sound, float vol, float pitch) {
      if (this.level != null && sound != null && !this.muteAudio) {
         this.level
            .playLocalSound(
               (double)this.worldPosition.getX() + 0.5,
               (double)this.worldPosition.getY() + 0.5,
               (double)this.worldPosition.getZ() + 0.5,
               sound,
               SoundSource.BLOCKS,
               vol,
               pitch,
               false
            );
      }
   }

   private void play(Holder<SoundEvent> sound, float vol, float pitch) {
      if (sound != null) {
         this.play((SoundEvent)sound.value(), vol, pitch);
      }
   }

   private static float easeOutBack(float t) {
      float c1 = 1.70158F;
      float c2 = 2.70158F;
      float x = t - 1.0F;
      return 1.0F + 2.70158F * x * x * x + 1.70158F * x * x;
   }

   private static float easeInOut(float t) {
      return t < 0.5F ? 2.0F * t * t : 1.0F - (float)Math.pow((double)(-2.0F * t + 2.0F), 2.0) / 2.0F;
   }

   public static float easeOutReel(float t) {
      float x = 1.0F - t;
      return 1.0F - x * x * x * x * x;
   }

   public static float reelTravel(int n, int winner) {
      return n <= 0 ? 180.0F : (float)(180 + Math.floorMod(winner - 180, n));
   }

   protected void saveAdditional(CompoundTag tag) {
      super.saveAdditional(tag);
      tag.put("config", this.config.save());
      ListTag opened = new ListTag();

      for (UUID id : this.openedBy) {
         opened.add(StringTag.valueOf(id.toString()));
      }

      tag.put("openedBy", opened);
   }

   public void load(CompoundTag tag) {
      super.load(tag);
      if (tag.contains("config")) {
         this.config = CrateConfig.load(tag.getCompound("config"));
         this.animColor = this.config.rarity.rgb();
      }

      this.openedBy.clear();
      if (tag.contains("openedBy")) {
         ListTag opened = tag.getList("openedBy", 8);

         for (int i = 0; i < opened.size(); i++) {
            try {
               this.openedBy.add(UUID.fromString(opened.getString(i)));
            } catch (IllegalArgumentException var5) {
            }
         }
      }
   }

   public CompoundTag getUpdateTag() {
      CompoundTag tag = super.getUpdateTag();
      tag.put("config", this.config.save());
      return tag;
   }

   public void handleUpdateTag(CompoundTag tag) {
      super.handleUpdateTag(tag);
      if (tag.contains("config")) {
         this.config = CrateConfig.load(tag.getCompound("config"));
         this.animColor = this.config.rarity.rgb();
      }
   }

   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
      CompoundTag tag = pkt.getTag();
      if (tag != null && tag.contains("config")) {
         this.config = CrateConfig.load(tag.getCompound("config"));
         this.animColor = this.config.rarity.rgb();
      }
   }

   public static List<ItemStack> decodeItems(CompoundTag wrap) {
      ArrayList<ItemStack> out = new ArrayList<>();
      if (wrap == null) {
         return out;
      } else {
         ListTag list = wrap.getList("items", 10);

         for (int i = 0; i < list.size(); i++) {
            out.add(ItemStack.of(list.getCompound(i)));
         }

         return out;
      }
   }
}
