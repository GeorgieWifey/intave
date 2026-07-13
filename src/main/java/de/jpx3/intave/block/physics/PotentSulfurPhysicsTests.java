package de.jpx3.intave.block.physics;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.BlockCaches;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.test.After;
import de.jpx3.intave.test.Before;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.MockEmptyInventory;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static de.jpx3.intave.world.WorldHeight.UPPER_WORLD_LIMIT;

public final class PotentSulfurPhysicsTests extends IntegrationTests {
  private static final double LAUNCH = 0.20000000298023224D;

  private final Map<Block, BlockState> originalBlocks = new LinkedHashMap<>();
  private World world;
  private Player player;
  private User user;
  private BlockCache blockCache;
  private int baseX;
  private int baseY;
  private int baseZ;
  private UUID playerId;
  private int protocolVersion;
  private boolean flying;
  private GameMode gameMode;

  public PotentSulfurPhysicsTests() {
    super("PSP");
  }

  @Before
  public void setup() {
    if (!MinecraftVersions.VER26_2.atOrAbove()) {
      return;
    }
    world = Bukkit.getWorlds().get(0);
    Location spawn = world.getSpawnLocation();
    baseX = spawn.getBlockX() + 8;
    baseY = spawn.getBlockY() + 8;
    baseZ = spawn.getBlockZ() + 8;
    protocolVersion = ProtocolMetadata.VER_26_2;
    gameMode = GameMode.SURVIVAL;
    playerId = UUID.randomUUID();

    player = FakePlayerFactory.createPlayer(playerId, (method, arguments) -> {
      switch (method) {
        case "getWorld":
          return world;
        case "getLocation":
          return new Location(world, baseX + 0.5, baseY + 1, baseZ + 0.5);
        case "getInventory":
          return new MockEmptyInventory();
        case "getActivePotionEffects":
          return Collections.emptyList();
        case "isFlying":
          return flying;
        case "getGameMode":
          return gameMode;
      }
      return null;
    });
    assertEquals(playerId, player.getUniqueId());
    blockCache = BlockCaches.passthroughCacheWithNativeDrill(player);
    user = UserFactory.createTestUserFor(player, (testUser, key) -> {
      if ("protocolVersion".equals(key)) {
        return protocolVersion;
      }
      if ("blockCache".equals(key)) {
        return blockCache;
      }
      return null;
    });
    UserRepository.manuallyRegisterUser(player, user);
    clearTestArea();
  }

  @Test(testCode = "ACTIVE", severity = Severity.ERROR)
  public void activeStatesAndDepthsLaunch() {
    if (!supported()) {
      return;
    }
    setGeyser(baseX, "erupting", 1);
    assertEquals(LAUNCH, apply(0.0, boxAt(baseX, baseY + 1.1)));

    clearTestArea();
    setGeyser(baseX, "continuous", 4);
    assertEquals(LAUNCH, apply(0.0, boxAt(baseX, baseY + 20.0)));

    clearTestArea();
    setGeyser(baseX, "erupting", 0);
    setData(baseX, baseY + 1, baseZ, "minecraft:bubble_column[drag=false]");
    assertEquals(LAUNCH, apply(0.0, boxAt(baseX, baseY + 1.1)));
  }

  @Test(testCode = "INVALID", severity = Severity.ERROR)
  public void inactiveAndInvalidColumnsDoNotLaunch() {
    if (!supported()) {
      return;
    }
    setGeyser(baseX, "dormant", 1);
    assertEquals(0.0, apply(0.0, boxAt(baseX, baseY + 1.1)));

    clearTestArea();
    setGeyser(baseX, "erupting", 0);
    assertEquals(0.0, apply(0.0, boxAt(baseX, baseY + 1.1)));

    clearTestArea();
    setGeyser(baseX, "erupting", 0);
    setData(baseX, baseY + 1, baseZ, "minecraft:water[level=1]");
    assertEquals(0.0, apply(0.0, boxAt(baseX, baseY + 1.1)));

    clearTestArea();
    setGeyser(baseX, "erupting", 5);
    assertEquals(0.0, apply(0.0, boxAt(baseX, baseY + 1.1)));

    clearTestArea();
    setGeyser(baseX, "erupting", 1);
    setType(baseX, baseY + 2, baseZ, Material.STONE);
    assertEquals(0.0, apply(0.0, boxAt(baseX, baseY + 1.1)));
  }

  @Test(testCode = "RANGE", severity = Severity.ERROR)
  public void plumeStopsAtCapAndObstruction() {
    if (!supported()) {
      return;
    }
    setGeyser(baseX, "erupting", 1);
    assertEquals(LAUNCH, apply(0.0, boxAt(baseX, baseY + 6.8)));
    assertEquals(0.0, apply(0.0, boxAt(baseX, baseY + 7.01)));

    setType(baseX, baseY + 4, baseZ, Material.STONE);
    assertEquals(LAUNCH, apply(0.0, boxAt(baseX, baseY + 3.8)));
    assertEquals(0.0, apply(0.0, boxAt(baseX, baseY + 4.01)));
  }

  @Test(testCode = "CEILING", severity = Severity.ERROR)
  public void plumeExtendsAboveBuildCeiling() {
    if (!supported()) {
      return;
    }
    int sulfurY = UPPER_WORLD_LIMIT - 2;
    setData(baseX, sulfurY, baseZ, "minecraft:potent_sulfur[potent_sulfur_state=erupting]");
    setType(baseX, sulfurY + 1, baseZ, Material.WATER);
    assertEquals(LAUNCH, apply(0.0, boxAt(baseX, sulfurY + 6.8)));
  }

  @Test(testCode = "EXCLUSIONS", severity = Severity.ERROR)
  public void excludedPlayersDoNotLaunch() {
    if (!supported()) {
      return;
    }
    setGeyser(baseX, "erupting", 1);
    BoundingBox box = boxAt(baseX, baseY + 1.1);

    flying = true;
    assertEquals(0.0, apply(0.0, box));
    flying = false;

    gameMode = GameMode.SPECTATOR;
    assertEquals(0.0, apply(0.0, box));
    gameMode = GameMode.SURVIVAL;

    user.meta().movement().setVehicle(Entity.destroyedEntity());
    assertEquals(0.0, apply(0.0, box));
    user.meta().movement().dismountRidingEntity("test", false);

    protocolVersion = ProtocolMetadata.VER_26_2 - 1;
    user.meta().protocol().setProtocolVersion(protocolVersion);
    assertEquals(0.0, apply(0.0, box));
  }

  @Test(testCode = "OVERLAP", severity = Severity.ERROR)
  public void overlappingGeysersApplyIndependently() {
    if (!supported()) {
      return;
    }
    setGeyser(baseX, "continuous", 1);
    setGeyser(baseX + 1, "continuous", 1);
    BoundingBox bothColumns = BoundingBox.fromBounds(
      baseX + 0.75, baseY + 1.1, baseZ + 0.2,
      baseX + 1.25, baseY + 2.9, baseZ + 0.8
    );
    assertEquals(LAUNCH * 2.0, apply(0.0, bothColumns));
  }

  @Test(testCode = "THRESHOLD", severity = Severity.ERROR)
  public void launchRequiresMotionBelowDepthThreshold() {
    if (!supported()) {
      return;
    }
    setGeyser(baseX, "erupting", 1);
    double threshold = (double) 0.3F + 0.1D;
    assertEquals(threshold, apply(threshold, boxAt(baseX, baseY + 1.1)));
    assertEquals(Math.nextDown(threshold) + LAUNCH, apply(Math.nextDown(threshold), boxAt(baseX, baseY + 1.1)));
  }

  @Test(testCode = "SHAPES", severity = Severity.ERROR)
  public void sulfurCollisionShapesResolve() {
    if (!supported()) {
      return;
    }
    assertShape("POTENT_SULFUR", true);
    assertShape("SULFUR_SPIKE", false);
    assertSlabShape();
    assertWallShape();
  }

  private boolean supported() {
    return world != null && Material.getMaterial("POTENT_SULFUR") != null;
  }

  private void setGeyser(int x, String state, int waterDepth) {
    setData(x, baseY, baseZ, "minecraft:potent_sulfur[potent_sulfur_state=" + state + "]");
    for (int offset = 1; offset <= waterDepth; offset++) {
      setType(x, baseY + offset, baseZ, Material.WATER);
    }
  }

  private double apply(double motionY, BoundingBox boundingBox) {
    Motion motion = new Motion(0.0, motionY, 0.0);
    PotentSulfurPhysics.apply(user, user.meta().movement(), motion, boundingBox);
    return motion.motionY;
  }

  private BoundingBox boxAt(int x, double minY) {
    return BoundingBox.fromBounds(
      x + 0.2, minY, baseZ + 0.2,
      x + 0.8, minY + 1.8, baseZ + 0.8
    );
  }

  private void assertShape(String materialName, boolean cubic) {
    BlockShape shape = shapeOf(materialName);
    assertEquals(cubic, shape.isCubic());
    setType(baseX + 3, baseY, baseZ, Material.AIR);
  }

  private void assertSlabShape() {
    int x = baseX + 3;
    BlockShape shape = shapeOf("SULFUR_SLAB");
    BoundingBox outline = shape.outline();
    assertEquals((double) baseY, outline.minY);
    assertEquals(baseY + 0.5, outline.maxY);
    assertTrue(shape.intersectsWith(BoundingBox.fromBounds(
      x + 0.1, baseY + 0.1, baseZ + 0.1,
      x + 0.9, baseY + 0.4, baseZ + 0.9
    )));
    assertFalse(shape.intersectsWith(BoundingBox.fromBounds(
      x + 0.1, baseY + 0.6, baseZ + 0.1,
      x + 0.9, baseY + 0.9, baseZ + 0.9
    )));
    setType(x, baseY, baseZ, Material.AIR);
  }

  private void assertWallShape() {
    int x = baseX + 3;
    BlockShape shape = shapeOf("SULFUR_WALL");
    BoundingBox outline = shape.outline();
    assertEquals(x + 0.25, outline.minX);
    assertEquals(x + 0.75, outline.maxX);
    assertEquals(baseZ + 0.25, outline.minZ);
    assertEquals(baseZ + 0.75, outline.maxZ);
    assertEquals(baseY + 1.5, outline.maxY);
    assertTrue(shape.intersectsWith(BoundingBox.fromBounds(
      x + 0.4, baseY + 0.1, baseZ + 0.4,
      x + 0.6, baseY + 1.4, baseZ + 0.6
    )));
    assertFalse(shape.intersectsWith(BoundingBox.fromBounds(
      x + 0.01, baseY + 0.1, baseZ + 0.01,
      x + 0.2, baseY + 1.4, baseZ + 0.2
    )));
    setType(x, baseY, baseZ, Material.AIR);
  }

  private BlockShape shapeOf(String materialName) {
    Material material = Material.getMaterial(materialName);
    assertNotNull(material);
    int x = baseX + 3;
    setType(x, baseY, baseZ, material);
    BlockShape shape = blockCache.collisionShapeAt(x, baseY, baseZ);
    assertFalse(shape.isEmpty());
    return shape;
  }

  private void clearTestArea() {
    for (int x = baseX; x <= baseX + 1; x++) {
      for (int y = baseY; y <= baseY + 25; y++) {
        setType(x, y, baseZ, Material.AIR);
      }
    }
    setType(baseX + 3, baseY, baseZ, Material.AIR);
    setType(baseX + 2, baseY, baseZ, Material.AIR);
    setType(baseX + 4, baseY, baseZ, Material.AIR);
    setType(baseX + 3, baseY, baseZ - 1, Material.AIR);
    setType(baseX + 3, baseY, baseZ + 1, Material.AIR);
  }

  private void setType(int x, int y, int z, Material material) {
    Block block = world.getBlockAt(x, y, z);
    remember(block);
    block.setType(material, false);
  }

  private void setData(int x, int y, int z, String data) {
    Block block = world.getBlockAt(x, y, z);
    remember(block);
    try {
      Object blockData = Bukkit.class
        .getMethod("createBlockData", String.class)
        .invoke(null, data);
      Method setter = null;
      for (Method method : Block.class.getMethods()) {
        Class<?>[] parameters = method.getParameterTypes();
        if (method.getName().equals("setBlockData")
          && parameters.length == 2
          && parameters[0].isInstance(blockData)
          && parameters[1] == boolean.class) {
          setter = method;
          break;
        }
      }
      if (setter == null) {
        throw new NoSuchMethodException("Block.setBlockData");
      }
      setter.invoke(block, blockData, false);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void remember(Block block) {
    originalBlocks.putIfAbsent(block, block.getState());
  }

  @After
  public void teardown() {
    for (BlockState original : originalBlocks.values()) {
      original.update(true, false);
    }
    originalBlocks.clear();
    if (player != null) {
      UserRepository.unregisterUser(player);
      assertFalse(UserRepository.hasUser(player));
    }
  }
}
