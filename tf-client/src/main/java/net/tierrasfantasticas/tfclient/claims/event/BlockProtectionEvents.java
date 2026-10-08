package net.tierrasfantasticas.tfclient.claims.event;

import net.tierrasfantasticas.tfclient.claims.item.ProtectionBlock;

import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimConfig;
import net.tierrasfantasticas.tfclient.claims.data.ClaimFlags;
import net.tierrasfantasticas.tfclient.claims.data.ClaimGroup;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.claims.gui.ClaimMenuHandler;
import net.tierrasfantasticas.tfclient.claims.util.DecorationProtection;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class BlockProtectionEvents {
    private static int fireSweepCounter = 0;

    private static boolean isBypassing(Player player) {
        return player.hasPermissions(2) && ClaimManager.getInstance().isBypassing(player.getUUID());
    }

    private static boolean denyForVisitor(Claim claim, Player player, boolean flag) {
        if (claim.canModify(player)) {
            return false;
        }
        return BlockProtectionEvents.isBypassing(player) ? false : flag;
    }

    private static void deny(Player player, String s) {
        if (player instanceof ServerPlayer) {
            ServerPlayer serverplayer = (ServerPlayer)player;
            if (!s.isEmpty()) {
                serverplayer.displayClientMessage((Component)Component.literal((String)s).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent breakevent) {
        LevelAccessor levelAccessor = breakevent.getLevel();
        if (levelAccessor instanceof Level) {
            Player player;
            Level level = (Level)levelAccessor;
            if (!level.isClientSide && (player = breakevent.getPlayer()) != null && !BlockProtectionEvents.isBypassing(player)) {
                ClaimTier claimtier;
                BlockPos blockpos = breakevent.getPos();
                BlockState blockstate = breakevent.getState();
                Claim claim = ClaimManager.getInstance().getClaimByCenter(level, blockpos);
                if (claim != null && (claimtier = claim.getTier()) != null && ClaimBlocks.isClaimConcreteForTier(blockstate.getBlock(), claimtier)) {
                    if (!claim.isOwner(player) && !player.hasPermissions(2)) {
                        BlockProtectionEvents.deny(player, "[!] Solo el due\u00f1o puede romper esta protecci\u00f3n.");
                        breakevent.setCanceled(true);
                    } else {
                        ClaimManager.getInstance().removeClaim(level, blockpos);
                        if (!player.getAbilities().instabuild) {
                            ItemStack itemstack = ClaimBlocks.createTierItem(claimtier, 1);
                            if (!player.getInventory().add(itemstack)) {
                                player.drop(itemstack, false);
                            }
                        }
                        level.playSound(null, blockpos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0f, 1.0f);
                        if (player instanceof ServerPlayer) {
                            ((ServerPlayer)player).displayClientMessage((Component)Component.literal((String)"\u2714 Zona eliminada. Protecci\u00f3n devuelta a tu inventario.").withStyle(ChatFormatting.GREEN), false);
                        }
                        level.setBlockAndUpdate(blockpos, Blocks.AIR.defaultBlockState());
                        breakevent.setCanceled(false);
                    }
                    return;
                }
                Claim claim1 = ClaimManager.getInstance().getClaimAt(level, blockpos);
                if (claim1 != null && !claim1.canModify(player)) {
                    if (!blockstate.is(BlockTags.LOGS) || !claim1.getFlags().publicMode && !claim1.getFlags().blockTreeChopping) {
                        if (!BlockProtectionEvents.isMatureCrop(blockstate) || !claim1.getFlags().publicMode && !claim1.getFlags().blockCropHarvest) {
                            if (BlockProtectionEvents.denyForVisitor(claim1, player, claim1.getFlags().blockBreaking || claim1.getFlags().publicMode)) {
                                BlockProtectionEvents.deny(player, "[!] No puedes romper bloques aqu\u00ed.");
                                breakevent.setCanceled(true);
                            }
                        } else {
                            BlockProtectionEvents.deny(player, "[!] No puedes cosechar cultivos aqu\u00ed.");
                            breakevent.setCanceled(true);
                        }
                    } else {
                        BlockProtectionEvents.deny(player, "[!] No puedes talar \u00e1rboles en esta zona.");
                        breakevent.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent entityplaceevent) {
        LevelAccessor levelAccessor = entityplaceevent.getLevel();
        if (levelAccessor instanceof Level) {
            Claim claim;
            Player player;
            Entity entity;
            Level level = (Level)levelAccessor;
            if (!level.isClientSide && (entity = entityplaceevent.getEntity()) instanceof Player && !BlockProtectionEvents.isBypassing(player = (Player)entity) && (claim = ClaimManager.getInstance().getClaimAt(level, entityplaceevent.getPos())) != null && BlockProtectionEvents.denyForVisitor(claim, player, claim.getFlags().blockBuilding || claim.getFlags().publicMode)) {
                BlockProtectionEvents.deny(player, "[!] No puedes construir aqu\u00ed.");
                entityplaceevent.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock rightclickblock) {
        Level level = rightclickblock.getLevel();
        if (!level.isClientSide) {
            ClaimTier claimtier;
            Player player = rightclickblock.getEntity();
            BlockPos blockpos = rightclickblock.getPos();
            ItemStack itemstack = rightclickblock.getItemStack();
            Claim claim = ClaimManager.getInstance().getClaimByCenter(level, blockpos);
            if (claim != null) {
                ClaimTier claimtier1 = claim.getTier();
                BlockState blockstate = level.getBlockState(blockpos);
                if (claimtier1 != null && ClaimBlocks.isClaimConcreteForTier(blockstate.getBlock(), claimtier1) && !player.isShiftKeyDown()) {
                    if (rightclickblock.getHand() == InteractionHand.MAIN_HAND) {
                        if (!claim.isOwner(player) && !player.hasPermissions(2)) {
                            BlockProtectionEvents.deny(player, "[x] Solo el due\u00f1o puede administrar esta zona.");
                        } else if (player instanceof ServerPlayer) {
                            ClaimMenuHandler.open((ServerPlayer)player, claim, 0);
                        }
                    }
                    rightclickblock.setCanceled(true);
                    rightclickblock.setCancellationResult(InteractionResult.SUCCESS);
                    return;
                }
            }
            if ((claimtier = ClaimBlocks.readTier(itemstack)) != null && !BlockProtectionEvents.isBypassing(player)) {
                InteractionResult interactionresult1 = this.tryPlaceClaim(player, level, rightclickblock.getHand(), rightclickblock.getFace(), blockpos, itemstack, claimtier);
                rightclickblock.setCanceled(true);
                rightclickblock.setCancellationResult(interactionresult1);
            } else {
                InteractionResult interactionresult = this.regularChecks(player, level, blockpos, rightclickblock.getFace(), itemstack);
                if (interactionresult != InteractionResult.PASS) {
                    rightclickblock.setCanceled(true);
                    rightclickblock.setCancellationResult(interactionresult);
                }
            }
        }
    }

    private InteractionResult tryPlaceClaim(Player player, Level level, InteractionHand interactionhand, Direction direction, BlockPos blockpos, ItemStack itemstack, ClaimTier claimtier) {
        int i;
        BlockState blockstate = level.getBlockState(blockpos);
        BlockPos blockpos1 = blockstate.canBeReplaced() ? blockpos : blockpos.relative(direction);
        BlockState blockstate1 = level.getBlockState(blockpos1);
        if (!blockstate1.isAir() && !blockstate1.canBeReplaced()) {
            return InteractionResult.PASS;
        }
        ClaimManager claimmanager = ClaimManager.getInstance();
        Claim claim = claimmanager.getClaimAt(level, blockpos1);
        if (claim != null && !claim.canModify(player) && !player.hasPermissions(2)) {
            BlockProtectionEvents.deny(player, "[x] No puedes construir en esta zona.");
            return InteractionResult.SUCCESS;
        }
        List<Claim> list = claimmanager.overlappingClaims(level, blockpos1, claimtier.radius, claimtier.height);
        UUID uuid = null;
        if (!list.isEmpty()) {
            UUID uuid1 = null;
            boolean flag = true;
            for (Claim claim1 : list) {
                if (claim1.getGroupId() == null) {
                    flag = false;
                    break;
                }
                if (uuid1 == null) {
                    uuid1 = claim1.getGroupId();
                    continue;
                }
                if (uuid1.equals(claim1.getGroupId())) continue;
                flag = false;
                break;
            }
            if (!flag || uuid1 == null || !claimmanager.isRegistered(uuid1, player.getUUID())) {
                BlockProtectionEvents.deny(player, "[x] Esta zona se solapar\u00eda con otra existente.");
                return InteractionResult.SUCCESS;
            }
            uuid = uuid1;
        }
        if ((i = ClaimManager.getMaxClaimsPerPlayer()) > 0 && !player.hasPermissions(2) && claimmanager.getClaimsOf(player.getUUID()).size() >= i) {
            BlockProtectionEvents.deny(player, "[x] Has alcanzado el l\u00edmite de zonas (" + i + ").");
            return InteractionResult.SUCCESS;
        }
        Block block = ClaimBlocks.blockForTier(claimtier);
        level.setBlockAndUpdate(blockpos1, block instanceof ProtectionBlock ? ((ProtectionBlock)block).facing(player.getDirection()) : block.defaultBlockState());
        level.playSound(null, blockpos1, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
        Claim claim2 = claimmanager.createClaim(level, blockpos1, player, claimtier);
        if (uuid != null && claim2 != null) {
            claimmanager.joinClaimToGroup(claim2, uuid);
        }
        if (!player.getAbilities().instabuild) {
            itemstack.shrink(1);
        }
        player.swing(interactionhand);
        if (player instanceof ServerPlayer) {
            if (uuid != null) {
                ClaimGroup claimgroup = claimmanager.getGroup(uuid);
                String s = claimgroup != null ? claimgroup.getName() : "grupo";
                ((ServerPlayer)player).displayClientMessage((Component)Component.literal((String)("\u2714 Piedra unida a la zona \"" + s + "\".")).withStyle(ChatFormatting.GREEN), false);
            } else {
                ((ServerPlayer)player).displayClientMessage((Component)Component.literal((String)("\u2714 Zona creada: " + claimtier.label() + " bloques | hasta el cielo y " + claimtier.height + " bajo la piedra")).withStyle(ChatFormatting.GREEN), false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult regularChecks(Player player, Level level, BlockPos blockpos, Direction direction, ItemStack itemstack) {
        Claim claim1;
        boolean flag;
        if (BlockProtectionEvents.isBypassing(player)) {
            return InteractionResult.PASS;
        }
        ClaimManager claimmanager = ClaimManager.getInstance();
        Claim claim = claimmanager.getClaimAt(level, blockpos);
        boolean bl = flag = claim != null && !claim.canModify(player);
        if (itemstack.getItem() instanceof BucketItem && (claim1 = claimmanager.getClaimAt(level, blockpos.relative(direction))) != null && !claim1.canModify(player) && claim1.getFlags().blockFluids) {
            BlockProtectionEvents.deny(player, "[!] No puedes colocar fluidos aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        if (!flag) {
            return InteractionResult.PASS;
        }
        ClaimFlags claimflags = claim.getFlags();
        if (claimflags.blockAllInteractions) {
            BlockProtectionEvents.deny(player, "[!] No tienes ning\u00fan permiso de interacci\u00f3n en esta zona.");
            return InteractionResult.FAIL;
        }
        BlockState blockstate = level.getBlockState(blockpos);
        Block block = blockstate.getBlock();
        if (claimflags.blockChestAccess && BlockProtectionEvents.isContainer(level, blockpos)) {
            BlockProtectionEvents.deny(player, "[!] No puedes abrir contenedores aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        if (claimflags.blockAnvilUse && block instanceof AnvilBlock) {
            BlockProtectionEvents.deny(player, "[!] No puedes usar yunques aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        if (claimflags.blockSignEditing && block instanceof SignBlock) {
            BlockProtectionEvents.deny(player, "[!] No puedes editar letreros aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        if (claimflags.blockDoorsAccess && BlockProtectionEvents.isDoorLike(blockstate)) {
            BlockProtectionEvents.deny(player, "[!] No puedes usar puertas, botones ni placas aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        if (claimflags.blockEntityInteract && BlockProtectionEvents.isInteractiveBlock(blockstate)) {
            BlockProtectionEvents.deny(player, "[!] No puedes interactuar aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem rightclickitem) {
        Claim claim;
        Player player;
        Level level = rightclickitem.getLevel();
        if (!level.isClientSide && !BlockProtectionEvents.isBypassing(player = rightclickitem.getEntity()) && ClaimBlocks.readTierId(rightclickitem.getItemStack()) == null && (claim = ClaimManager.getInstance().getClaimAt(level, player.blockPosition())) != null && !claim.canModify(player) && claim.getFlags().blockItemUse) {
            BlockProtectionEvents.deny(player, "[!] No puedes usar items en esta zona.");
            rightclickitem.setCanceled(true);
            rightclickitem.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onTrample(BlockEvent.FarmlandTrampleEvent farmlandtrampleevent) {
        LevelAccessor levelAccessor = farmlandtrampleevent.getLevel();
        if (levelAccessor instanceof Level) {
            Claim claim;
            Level level = (Level)levelAccessor;
            if (!level.isClientSide && (claim = ClaimManager.getInstance().getClaimAt(level, farmlandtrampleevent.getPos())) != null && (claim.getFlags().blockTrampling || claim.getFlags().publicMode)) {
                farmlandtrampleevent.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate detonate) {
        Level level = detonate.getLevel();
        if (!level.isClientSide) {
            detonate.getAffectedBlocks().removeIf(blockpos -> {
                Claim claim = ClaimManager.getInstance().getClaimAt(level, (BlockPos)blockpos);
                return claim != null && (claim.getFlags().blockExplosions || claim.getFlags().publicMode);
            });
            detonate.getAffectedEntities().removeIf(entity -> {
                if (!ClaimConfig.get().protectDecorationFromExplosions) {
                    return false;
                }
                if (!DecorationProtection.isDecoration(entity)) {
                    return false;
                }
                Claim claim = DecorationProtection.claimFor(level, entity);
                return claim != null && (claim.getFlags().blockExplosions || claim.getFlags().publicMode);
            });
        }
    }

    @SubscribeEvent
    public void onPiston(PistonEvent.Pre pre) {
        LevelAccessor levelAccessor = pre.getLevel();
        if (levelAccessor instanceof Level) {
            Level level = (Level)levelAccessor;
            if (!level.isClientSide) {
                BlockPos blockpos = pre.getPos();
                Direction direction = pre.getDirection();
                Claim claim = ClaimManager.getInstance().getClaimAt(level, blockpos);
                PistonStructureResolver pistonstructureresolver = pre.getStructureHelper();
                if (pistonstructureresolver != null && pistonstructureresolver.resolve()) {
                    for (BlockPos blockpos2 : pistonstructureresolver.getToPush()) {
                        if (!BlockProtectionEvents.crossClaimBlocked(level, claim, blockpos2, blockpos2.relative(direction))) continue;
                        pre.setCanceled(true);
                        return;
                    }
                    for (BlockPos blockpos3 : pistonstructureresolver.getToDestroy()) {
                        if (!BlockProtectionEvents.crossClaimBlocked(level, claim, blockpos3, blockpos3)) continue;
                        pre.setCanceled(true);
                        return;
                    }
                } else {
                    BlockPos blockpos1 = blockpos.relative(direction);
                    if (BlockProtectionEvents.crossClaimBlocked(level, claim, blockpos1, blockpos1.relative(direction))) {
                        pre.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onEnderPearl(EntityTeleportEvent.EnderPearl enderpearl) {
        ServerPlayer serverplayer = enderpearl.getPlayer();
        if (serverplayer != null) {
            Level level = serverplayer.level();
            BlockPos blockpos = BlockPos.containing((double)enderpearl.getTargetX(), (double)enderpearl.getTargetY(), (double)enderpearl.getTargetZ());
            Claim claim = ClaimManager.getInstance().getClaimAt(level, blockpos);
            if (claim != null && !claim.canModify((Player)serverplayer) && !BlockProtectionEvents.isBypassing((Player)serverplayer) && (claim.getFlags().blockEnderPearl || claim.getFlags().publicMode)) {
                enderpearl.setCanceled(true);
                BlockProtectionEvents.deny((Player)serverplayer, "[!] No puedes teletransportarte a esta zona.");
            }
        }
    }

    private static boolean crossClaimBlocked(Level level, Claim claim, BlockPos blockpos, BlockPos blockpos1) {
        Claim claim1;
        Claim claim2 = ClaimManager.getInstance().getClaimAt(level, blockpos);
        return BlockProtectionEvents.sameClaim(claim2, claim1 = ClaimManager.getInstance().getClaimAt(level, blockpos1)) && BlockProtectionEvents.sameClaim(claim, claim2) ? false : BlockProtectionEvents.protectsBuilding(claim2) || BlockProtectionEvents.protectsBuilding(claim1) || BlockProtectionEvents.protectsBuilding(claim);
    }

    private static boolean sameClaim(Claim claim, Claim claim1) {
        if (claim == null && claim1 == null) {
            return true;
        }
        return claim != null && claim1 != null ? claim.getClaimId().equals(claim1.getClaimId()) : false;
    }

    private static boolean protectsBuilding(Claim claim) {
        return claim == null ? false : claim.getFlags().publicMode || claim.getFlags().blockBuilding;
    }

    public static boolean isContainer(Level level, BlockPos blockpos) {
        BlockState blockstate = level.getBlockState(blockpos);
        Block block = blockstate.getBlock();
        if (!(block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock || block instanceof DispenserBlock || block instanceof HopperBlock)) {
            BlockEntity blockentity = level.getBlockEntity(blockpos);
            return blockentity instanceof Container;
        }
        return true;
    }

    private static boolean isMatureCrop(BlockState blockstate) {
        boolean bl;
        Block block = blockstate.getBlock();
        if (block instanceof CropBlock) {
            CropBlock cropblock = (CropBlock)block;
            bl = cropblock.isMaxAge(blockstate);
        } else {
            bl = false;
        }
        return bl;
    }

    private static boolean isDoorLike(BlockState blockstate) {
        if (blockstate.is(BlockTags.DOORS)) {
            return true;
        }
        if (blockstate.is(BlockTags.TRAPDOORS)) {
            return true;
        }
        if (blockstate.is(BlockTags.FENCE_GATES)) {
            return true;
        }
        return blockstate.is(BlockTags.BUTTONS) ? true : blockstate.getBlock() == Blocks.LEVER;
    }

    private static boolean isInteractiveBlock(BlockState blockstate) {
        Block block = blockstate.getBlock();
        return block == Blocks.CRAFTING_TABLE || block == Blocks.ENCHANTING_TABLE || block == Blocks.GRINDSTONE || block == Blocks.BREWING_STAND;
    }

    public static void tickFireSweep(MinecraftServer minecraftserver) {
        if (++fireSweepCounter % ClaimConfig.get().fireSweepIntervalTicks == 0) {
            for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
                for (Claim claim : ClaimManager.getInstance().getClaimsInWorld(serverlevel.dimension().location().toString())) {
                    if (!claim.getFlags().blockFire && !claim.getFlags().publicMode) continue;
                    for (ServerPlayer serverplayer : serverlevel.players()) {
                        if (!claim.contains(serverplayer.blockPosition())) continue;
                        BlockProtectionEvents.extinguishAround(serverlevel, serverplayer.blockPosition(), claim);
                    }
                }
            }
        }
    }

    private static void extinguishAround(ServerLevel serverlevel, BlockPos blockpos, Claim claim) {
        int i = ClaimConfig.get().fireSweepRadius;
        BlockPos.MutableBlockPos mutableblockpos = new BlockPos.MutableBlockPos();
        for (int j = -i; j <= i; ++j) {
            for (int k = -i; k <= i; ++k) {
                for (int l = -i; l <= i; ++l) {
                    Block block;
                    mutableblockpos.set(blockpos.getX() + j, blockpos.getY() + k, blockpos.getZ() + l);
                    if (!claim.contains((BlockPos)mutableblockpos) || (block = serverlevel.getBlockState((BlockPos)mutableblockpos).getBlock()) != Blocks.FIRE && block != Blocks.SOUL_FIRE) continue;
                    serverlevel.setBlock(mutableblockpos.immutable(), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}

