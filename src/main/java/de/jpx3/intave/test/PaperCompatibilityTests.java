package de.jpx3.intave.test;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.player.fake.IdentifierReserve;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.version.ProtocolVersionConverter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

import java.util.HashSet;
import java.util.Set;

public final class PaperCompatibilityTests extends IntegrationTests {
  public PaperCompatibilityTests() {
    super("PC");
  }

  @Test(severity = Severity.ERROR)
  public void testMinecraft26_2ProtocolClassification() {
    if (!MinecraftVersion.current().equals(MinecraftVersions.VER26_2)) {
      return;
    }
    ProtocolMetadata metadata = new ProtocolMetadata(null, ProtocolMetadata.VER_26_2);
    assertEquals(ProtocolMetadata.VER_26_2, metadata.protocolVersion());
    assertEquals(ProtocolMetadata.VER_26_2, ProtocolVersionConverter.protocolVersionBy(MinecraftVersion.current()));
    assertEquals(MinecraftVersions.VER26_2, metadata.minecraftVersion());
    assertFalse(metadata.outdatedClient());
  }

  @Test(severity = Severity.ERROR)
  public void testReservedEntityIdsAreWorldScoped() {
    for (World world : Bukkit.getWorlds()) {
      Set<Integer> occupied = new HashSet<>();
      for (Entity entity : world.getEntities()) {
        occupied.add(entity.getEntityId());
      }
      Set<Integer> reserved = new HashSet<>();
      for (int i = 0; i < 8; i++) {
        int entityId = IdentifierReserve.acquireNew(world);
        assertTrue(entityId > 0);
        assertTrue(reserved.add(entityId));
        assertFalse(occupied.contains(entityId));
      }
      ArmorStand spawned = world.spawn(new Location(world, 0, 0, 0), ArmorStand.class);
      assertFalse(reserved.contains(spawned.getEntityId()));
      spawned.remove();
    }
  }
}
