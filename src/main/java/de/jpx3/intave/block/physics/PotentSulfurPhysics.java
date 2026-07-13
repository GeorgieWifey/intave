package de.jpx3.intave.block.physics;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;

import static de.jpx3.intave.share.ClientMath.floor;
import static de.jpx3.intave.world.WorldHeight.LOWER_WORLD_LIMIT;
import static de.jpx3.intave.world.WorldHeight.UPPER_WORLD_LIMIT;

public final class PotentSulfurPhysics {
  private static final Material POTENT_SULFUR = Material.getMaterial("POTENT_SULFUR");
  private static final double LAUNCH = 0.20000000298023224D;
  private static final int MAX_PLUME_HEIGHT = 24;

  private PotentSulfurPhysics() {
  }

  public static void apply(
    User user,
    SimulationEnvironment environment,
    Motion motion,
    BoundingBox boundingBox
  ) {
    if (POTENT_SULFUR == null
      || !MinecraftVersions.VER26_2.atOrAbove()
      || user.protocolVersion() < ProtocolMetadata.VER_26_2
      || user.player().isFlying()
      || user.player().getGameMode() == GameMode.SPECTATOR
      || environment.isInVehicle()) {
      return;
    }

    World world = user.player().getWorld();
    int minSulfurY = Math.max(LOWER_WORLD_LIMIT, floor(boundingBox.minY) - MAX_PLUME_HEIGHT);
    int maxSulfurY = Math.min(UPPER_WORLD_LIMIT - 1, floor(boundingBox.maxY) - 1);
    for (int x = floor(boundingBox.minX); x <= floor(boundingBox.maxX); x++) {
      for (int z = floor(boundingBox.minZ); z <= floor(boundingBox.maxZ); z++) {
        for (int sulfurY = minSulfurY; sulfurY <= maxSulfurY; sulfurY++) {
          int plumeTop = plumeTop(user, world, x, sulfurY, z);
          if (plumeTop > sulfurY + 1
            && boundingBox.intersectsWith(x, sulfurY + 1, z, x + 1, plumeTop, z + 1)) {
            int waterDepth = waterDepth(user, world, x, sulfurY, z);
            if (motion.motionY < (double) 0.3F + 0.1D * waterDepth) {
              motion.motionY += LAUNCH;
            }
          }
        }
      }
    }
  }

  private static int plumeTop(User user, World world, int x, int sulfurY, int z) {
    if (VolatileBlockAccess.typeAccess(user, world, x, sulfurY, z) != POTENT_SULFUR) {
      return -1;
    }
    BlockVariant variant = VolatileBlockAccess.variantAccess(user, world, x, sulfurY, z);
    Object state = variant.propertyOf("potent_sulfur_state");
    if (!"ERUPTING".equals(state) && !"CONTINUOUS".equals(state)) {
      return -1;
    }

    int waterDepth = waterDepth(user, world, x, sulfurY, z);
    if (waterDepth == 0 || waterDepth > 4) {
      return -1;
    }
    int firstPassableY = sulfurY + waterDepth + 1;
    if (!passable(user, world, x, firstPassableY, z)) {
      return -1;
    }

    int plumeTop = Math.min(sulfurY + 1 + 6 * waterDepth, UPPER_WORLD_LIMIT);
    for (int y = firstPassableY; y < plumeTop; y++) {
      if (!passable(user, world, x, y, z)) {
        return y;
      }
    }
    return plumeTop;
  }

  private static int waterDepth(User user, World world, int x, int sulfurY, int z) {
    int depth = 0;
    while (depth < 5 && sourceWater(user, world, x, sulfurY + depth + 1, z)) {
      depth++;
    }
    return depth;
  }

  private static boolean sourceWater(User user, World world, int x, int y, int z) {
    Material type = VolatileBlockAccess.typeAccess(user, world, x, y, z);
    Fluid fluid = VolatileBlockAccess.fluidAccess(user, world, x, y, z);
    // Fluid source metadata is not retained; 26.2 non-WATER water states are source-only.
    return fluid.isOfWater()
      && (type != Material.WATER || fluid.level() == 0)
      && VolatileBlockAccess.collisionShapeAccess(user, x, y, z).isEmpty();
  }

  private static boolean passable(User user, World world, int x, int y, int z) {
    Material type = VolatileBlockAccess.typeAccess(user, world, x, y, z);
    return type == Material.AIR
      || type == Material.WATER
      || VolatileBlockAccess.collisionShapeAccess(user, x, y, z).isEmpty();
  }
}
