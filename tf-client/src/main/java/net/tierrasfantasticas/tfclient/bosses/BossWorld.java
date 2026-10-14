package net.tierrasfantasticas.tfclient.bosses;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;

/** Lo que el motor de skills pide al mundo para los jefes: esbirros, objetos que caen y agarres. */
final class BossWorld implements SkillMobs.World {
    @Override
    public SkillMobs.Host spawnMinion(SkillMobs.Host owner, SkillDefs.MobDef def, ServerLevel level, Vec3 at, float yaw) {
        if (!(owner instanceof BossEntity boss)) return null;
        // Como mucho 24 esbirros vivos por jefe (las fosas del Esqueleto sacan uno cada vez)
        int alive = level.getEntitiesOfClass(BossEntity.class, boss.getBoundingBox().inflate(96), e -> e.boss() == boss && e != boss && e.isAlive()).size();
        if (alive >= 24) return null;
        BossEntity m = TFBosses.ESBIRRO.get().create(level);
        if (m == null) return null;
        m.moveTo(at.x, at.y, at.z, yaw, 0F);
        m.configure(boss.bossId(), def.name, boss.difficulty(), 1, boss);
        if (boss.rift() != null) m.setRift(boss.rift(), Vec3.atBottomCenterOf(boss.homePos()));
        level.addFreshEntity(m);
        return m;
    }

    @Override
    public void drop(SkillDefs.ClassDef cls, String item, ServerLevel level, Vec3 at) {
        RegistryObject<net.minecraft.world.item.Item> o = TFBosses.DROPS.get(cls.id + ":" + item.toLowerCase(Locale.ROOT));
        if (o == null) return;
        ItemEntity e = new ItemEntity(level, at.x, at.y + 0.5, at.z, new ItemStack(o.get()));
        e.setDefaultPickUpDelay();
        level.addFreshEntity(e);
    }

    @Override
    public void seat(SkillMobs.Host host, String seat, LivingEntity rider) {
        if (rider.isPassenger() || !(host.mob().level() instanceof ServerLevel level)) return;
        SeatEntity s = TFBosses.ASIENTO.get().create(level);
        if (s == null) return;
        s.boss = host.mob().getUUID();
        s.seat = seat;
        Vec3 p = SkillMobs.bonePos(host, seat);
        if (p == null) p = rider.position();
        s.moveTo(p.x, p.y - 0.6, p.z, 0F, 0F);
        level.addFreshEntity(s);
        rider.startRiding(s, true);
    }

    @Override
    public void unseat(SkillMobs.Host host, String seat) {
        for (SeatEntity s : seats(host, seat)) s.release();
    }

    @Override
    public List<LivingEntity> seated(SkillMobs.Host host, String seat) {
        List<LivingEntity> out = new ArrayList<>();
        for (SeatEntity s : seats(host, seat)) {
            for (Entity e : s.getPassengers()) if (e instanceof LivingEntity le) out.add(le);
        }
        return out;
    }

    private static List<SeatEntity> seats(SkillMobs.Host host, String seat) {
        Entity mob = host.mob();
        if (mob.level().isClientSide) return List.of();
        return mob.level().getEntitiesOfClass(SeatEntity.class, mob.getBoundingBox().inflate(32),
                s -> mob.getUUID().equals(s.boss) && !s.isRemoved() && (seat == null || seat.equalsIgnoreCase(s.seat)));
    }
}
