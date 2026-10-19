package club.rainbowkitty.artifacts.orb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Keeps a flying holder between the lines the fence leaves unwalled: nudges them down under the
// ceiling off any ledge, and up to WaterLift's floor over water. The client has no step-down, and
// steps up only on the ground (Entity.collide), so the server nudges instead: through an explosion
// packet's knockback, which the client adds to its own velocity, the blast silent and out of
// sight. A motion packet would overwrite the client's velocity with the server's stale copy, and a
// relative teleport would stall the server's view of the player until the client acknowledged it.
// See the plan's S10 to S12.
final class AltitudeNudge {
    // The fastest nudge, in blocks a tick, which a nudge every tick settles at. Sneaking in flight
    // settles at 0.375.
    private static final double NUDGE_SPEED = 0.75;

    // How far inside a line a nudge aims the box, and how far past that aim the predicted box may
    // sit before another nudge goes out. Their difference keeps a finished nudge inside the line.
    private static final double MARGIN = 0.15;
    private static final double DEADBAND = 0.1;

    // How far over the water floor sneaking is cancelled, so a holder resting on it doesn't sink
    // through and bob back up.
    private static final double FLOOR_BAND = 1.0;

    // How far past the lookahead the ray under the holder's centre looks; inside
    // AltitudeFence.FORWARD_BUFFER, so every column on it is one the fence's grid can smooth.
    private static final double PATH_MARGIN = 1.0;

    // Spacing of the points sampled along that ray.
    private static final double SAMPLE_STEP = 0.25;

    // Flight keeps this share of vertical velocity each tick (Player.travel), and holding jump or
    // sneak adds this multiple of the flying speed each tick (LocalPlayer.aiStep).
    static final double FLIGHT_DAMPING = 0.6;
    private static final double VERTICAL_INPUT_FACTOR = 3.0;

    // A nudge with less than this share of its effect still to come is forgotten.
    private static final double SPENT = 0.01;

    // How far below the holder the blast sits: past the client's 32-block particle cull
    // (ClientLevel.doAddParticle), for a particle type that does not override it.
    private static final double BLAST_DEPTH = 64.0;

    // A sound the client skips outright, with no subtitle (SoundEngine.play).
    private static final Holder<SoundEvent> SILENT =
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);

    // UUID -> the nudges still playing out on a holder's client, while they last or the holder is
    // over the ceiling or under the floor.
    private static final Map<UUID, State> STATES = new HashMap<>();

    private AltitudeNudge() {
    }

    // Whether a holder is being nudged or is outside the lines, so the fence must rescan them every
    // tick even while they hang still.
    static boolean isActive(UUID uuid) {
        return STATES.containsKey(uuid);
    }

    // Nudges the holder once more if what is already on its way to their client falls short of
    // bringing the box inside the lines on their way. While a nudge lasts, every tick also cancels
    // the jump or sneak working against it, which the reckoning leaves out, as does resting on the
    // water floor. The grid is the fence's, which covers the ray under the holder's centre.
    static void update(ServerPlayer player, HeightGrid grid, AABB box, Vec3 ahead) {
        UUID uuid = player.getUUID();
        int now = player.level().getServer().getTickCount();
        double roundTrip = player.connection.latency() / AltitudeFence.MILLIS_PER_TICK;
        State state = STATES.get(uuid);
        double coming = state == null ? 0.0 : state.coming(now, roundTrip);
        Lines lines = lines(player, grid, box, ahead, roundTrip);

        double push = 0.0;
        boolean outside = lines.ceiling() < box.maxY || lines.floor() > box.minY;
        if (outside || state != null && !state.pushes.isEmpty()) {
            if (state == null) {
                state = new State();
                STATES.put(uuid, state);
            }
            double over = box.maxY + coming - (lines.ceiling() - MARGIN);
            double under = lines.floor() + MARGIN - (box.minY + coming);
            if (over > DEADBAND) {
                push = -(1.0 - FLIGHT_DAMPING) * Math.min(over, NUDGE_SPEED);
            } else if (under > DEADBAND) {
                push = (1.0 - FLIGHT_DAMPING) * Math.min(under, NUDGE_SPEED);
            }
            if (push != 0.0) {
                state.pushes.add(new Push(now, push));
            }
        } else {
            STATES.remove(uuid);
        }

        boolean down = push < 0.0 || box.maxY > lines.ceiling() || coming < 0.0;
        boolean up = push > 0.0 || box.minY < lines.floorHere() + FLOOR_BAND || coming > 0.0;
        Input input = player.getLastClientInput();
        double climb = VERTICAL_INPUT_FACTOR * player.getAbilities().getFlyingSpeed();
        if (down && input.jump() && !input.shift()) {
            push -= climb;
        } else if (up && input.shift() && !input.jump()) {
            push += climb;
        }
        if (push != 0.0) {
            send(player, push);
        }
    }

    // Drops a holder's nudges.
    static void forget(UUID uuid) {
        STATES.remove(uuid);
    }

    // The lines on the ray under the holder's centre, from their own column along their heading to
    // the lookahead. Each column over whose line the box top stands caps it at that line, plus as
    // far as NUDGE_SPEED descends before the centre reaches it on the client; a column past the
    // walk caps it at HeightGrid.deepestLine, to be refined as it comes in reach, and a void
    // column, walled off by the fence, caps nothing. Each column over water floors the feet the
    // same way at WaterLift's floor, never within a box of that column's own line, which a low
    // flight ceiling can bring under the floor. floorHere is the floor over the holder's own
    // column alone.
    private static Lines lines(ServerPlayer player, HeightGrid grid, AABB box, Vec3 ahead,
            double roundTrip) {
        int head = Mth.ceil(box.maxY - HeightGrid.BOX_EPSILON) - 1;
        int feet = Mth.floor(box.minY + HeightGrid.BOX_EPSILON);
        int atLeast = Mth.ceil(box.maxY - HeightGrid.BOX_EPSILON);
        Vec3 movement = player.getKnownMovement();
        double speed = movement.horizontalDistance();
        double length = 0.0;
        double alongX = 0.0;
        double alongZ = 0.0;
        if (speed >= AltitudeFence.STILL_EPSILON) {
            length = Math.hypot(ahead.x, ahead.z) + PATH_MARGIN;
            alongX = movement.x / speed;
            alongZ = movement.z / speed;
        }
        double centreX = (box.minX + box.maxX) / 2.0;
        double centreZ = (box.minZ + box.maxZ) / 2.0;
        double height = box.maxY - box.minY;

        double ceiling = Double.POSITIVE_INFINITY;
        double floor = Double.NEGATIVE_INFINITY;
        double floorHere = Double.NEGATIVE_INFINITY;
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        int samples = Mth.ceil(length / SAMPLE_STEP);
        for (int i = 0; i <= samples; i++) {
            double moved = Math.min(i * SAMPLE_STEP, length);
            int x = Mth.floor(centreX + alongX * moved);
            int z = Mth.floor(centreZ + alongZ * moved);
            if (x == lastX && z == lastZ) {
                continue;
            }
            lastX = x;
            lastZ = z;
            if (!grid.covers(x, head, z) || !grid.covers(x, feet, z)) {
                break;
            }
            double lead = moved == 0.0 ? 0.0 : Math.max(0.0, moved / speed - roundTrip);

            int line = grid.ceilingLevel(x, head, z, atLeast);
            if (line == HeightGrid.NO_LINE && !grid.isVoid(x, z)) {
                line = grid.deepestLine();
            }
            if (line != HeightGrid.NO_LINE && line < box.maxY - HeightGrid.BOX_EPSILON) {
                ceiling = Math.min(ceiling, line + NUDGE_SPEED * lead);
            }

            int waterFloor = WaterLift.floorLevel(player.level(), grid, x, feet, z);
            if (waterFloor != WaterLift.NO_FLOOR) {
                double water = waterFloor;
                if (line != HeightGrid.NO_LINE) {
                    water = Math.min(water, line - height - MARGIN);
                }
                floor = Math.max(floor, water - NUDGE_SPEED * lead);
                if (moved == 0.0) {
                    floorHere = water;
                }
            }
        }
        return new Lines(ceiling, floor, floorHere);
    }

    // Sends the nudge as a blast's knockback, the only part of the packet the client acts on here.
    private static void send(ServerPlayer player, double push) {
        Vec3 blast = player.position().subtract(0.0, BLAST_DEPTH, 0.0);
        // 26.3 added a play-sound flag to the packet.
        player.connection.send(new ClientboundExplodePacket(blast, 0.0F, 0,
                Optional.of(new Vec3(0.0, push, 0.0)), ParticleTypes.SMOKE, SILENT,
                //? if >=26.3 {
                /*WeightedList.of(), false));
                *///?} else {
                WeightedList.of()));
                //?}
    }

    // The highest the box top may be, the lowest its bottom may be, and the floor over the
    // holder's own column; infinite where nothing bounds them.
    private record Lines(double ceiling, double floor, double floorHere) {
    }

    // One holder's nudges still playing out on their client.
    private static final class State {
        private final List<Push> pushes = new ArrayList<>();

        // How much further, in blocks, the nudges sent will move the box on the client beyond
        // where the server now sees it. A nudge reaches the client half a round trip after it is
        // sent, and the client's position reaches the server half a round trip later, so the
        // server has seen the ticks from a full round trip after sending. Each tick moves the box
        // by the nudge's velocity and keeps FLIGHT_DAMPING of it, so a nudge `v` moves it
        // v / (1 - FLIGHT_DAMPING) in all, of which FLIGHT_DAMPING^seen is still to come.
        double coming(int now, double roundTrip) {
            double total = 0.0;
            for (Iterator<Push> it = this.pushes.iterator(); it.hasNext(); ) {
                Push push = it.next();
                double seen = Math.max(0.0, now - push.tick() - roundTrip);
                double left = Math.pow(FLIGHT_DAMPING, seen);
                if (left < SPENT) {
                    it.remove();
                    continue;
                }
                total += push.velocity() * left / (1.0 - FLIGHT_DAMPING);
            }
            return total;
        }
    }

    // A nudge sent: the tick it went out and the vertical velocity it adds.
    private record Push(int tick, double velocity) {
    }
}
