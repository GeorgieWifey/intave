package de.jpx3.intave.entity.size;

import de.jpx3.intave.reflect.access.ReflectiveHandleAccess;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Sheep;

import static de.jpx3.intave.adapter.MinecraftVersions.VER26_2;

public final class EntitySizeTests extends IntegrationTests {
  public EntitySizeTests() {
    super("ES");
  }

  @Test(
    severity = Severity.ERROR
  )
  public void testSheep() {
    World world = Bukkit.getWorlds().get(0);
    // spawn sheep
    Sheep sheep = world.spawn(new Location(world, 0,0,0), Sheep.class);
    Object handle = ReflectiveHandleAccess.handleOf(sheep);
    // get size
    Class<?> entityClass = handle.getClass();
    HitboxSize size = HitboxSizeAccess.dimensionsOfNMSEntityClass(entityClass);
    if (size == null || Math.abs(size.width() - 0.9) > 0.01 || Math.abs(size.height() - 1.3) > 0.01) {
      fail("Failed to fetch sheep size, is " + size);
    }
  }

  @Test(
    severity = Severity.ERROR
  )
  public void testMushroomCowLookupOrder() {
    if (!VER26_2.atOrAbove()) {
      return;
    }
    World world = Bukkit.getWorlds().get(0);
    MushroomCow mushroomCow = world.spawn(new Location(world, 0, 0, 0), MushroomCow.class);
    Class<?> entityClass = ReflectiveHandleAccess.handleOf(mushroomCow).getClass();
    HitboxSize fallback = HitboxSizeAccess.dimensionsOfNMSEntityClass(entityClass);
    if (fallback.width() != 0 || fallback.height() != 0) {
      fail("Mushroom cow class fallback unexpectedly resolved " + fallback);
    }
    HitboxSize size = HitboxSizeAccess.dimensionsOfNMSEntityClass(entityClass, mushroomCow.getType());
    if (Math.abs(size.width() - 0.9) > 0.01 || Math.abs(size.height() - 1.4) > 0.01) {
      fail("Failed to fetch mushroom cow size, is " + size);
    }
  }

  @Test(
    severity = Severity.ERROR
  )
  public void testIronGolemClassName() {
    if (!VER26_2.atOrAbove()) {
      return;
    }
    World world = Bukkit.getWorlds().get(0);
    IronGolem ironGolem = world.spawn(new Location(world, 0, 0, 0), IronGolem.class);
    Class<?> entityClass = ReflectiveHandleAccess.handleOf(ironGolem).getClass();
    HitboxSize size = HitboxSizeAccess.dimensionsOfNMSEntityClass(entityClass);
    if (Math.abs(size.width() - 1.4) > 0.01 || Math.abs(size.height() - 2.7) > 0.01) {
      fail("Failed to fetch iron golem size, is " + size);
    }
  }

  @Test(
    severity = Severity.ERROR
  )
  public void testUnknownEntityClass() {
    if (!VER26_2.atOrAbove()) {
      return;
    }
    HitboxSize size = HitboxSizeAccess.dimensionsOfNMSEntityClass(EntitySizeTests.class);
    if (size.width() != 0 || size.height() != 0) {
      fail("Unknown entity size is not zero: " + size);
    }
  }

  @Test(
    severity = Severity.ERROR
  )
  public void testSulfurCubeClassName() throws ClassNotFoundException {
    if (!VER26_2.atOrAbove()) {
      return;
    }
    Class<?> entityClass = Class.forName("net.minecraft.world.entity.monster.cubemob.SulfurCube");
    HitboxSize size = HitboxSizeAccess.dimensionsOfNMSEntityClass(entityClass);
    if (size.width() <= 0 || size.height() <= 0) {
      fail("Failed to fetch sulfur cube size, is " + size);
    }
  }
}
