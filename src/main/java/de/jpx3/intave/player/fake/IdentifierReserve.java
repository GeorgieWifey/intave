package de.jpx3.intave.player.fake;

import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static de.jpx3.intave.adapter.MinecraftVersions.VER26_2;

/**
 * Entity Ids can only be aquired sync, but we need our ids async
 * So we reserve us a bunch of ids so we can use them later
 */
public final class IdentifierReserve {
  private static final int REQUIRED_ID_POOL_SIZE = 25;

  private static final Map<UUID, Queue<Integer>> availableIds = new ConcurrentHashMap<>();

  public static void setup() {
    Bukkit.getWorlds().forEach(IdentifierReserve::refreshIfRequired);
  }

  public static int acquireNew(World world) {
    Queue<Integer> worldIds = availableIds.computeIfAbsent(world.getUID(), key -> new ConcurrentLinkedDeque<>());
    Integer identifier = worldIds.poll();
    if (identifier == null) {
      identifier = reserveEntityIdSynchronized(world);
    }
    refreshIfRequired(world, worldIds);
    return identifier;
  }

  private static void refreshIfRequired(World world) {
    refreshIfRequired(world, availableIds.computeIfAbsent(world.getUID(), key -> new ConcurrentLinkedDeque<>()));
  }

  private static void refreshIfRequired(World world, Queue<Integer> worldIds) {
    if (worldIds.size() < REQUIRED_ID_POOL_SIZE) {
      if (Bukkit.isPrimaryThread()) {
        refillEntityIds(world, worldIds);
      } else {
        Synchronizer.synchronize(() -> refillEntityIds(world, worldIds));
      }
    }
  }

  private static void refillEntityIds(World world, Queue<Integer> worldIds) {
    int missing = (REQUIRED_ID_POOL_SIZE - worldIds.size());
    if (missing > 0) {
      Arrays.stream(reserveEntityIds(world, missing)).forEach(worldIds::add);
    }
  }

  private static int[] reserveEntityIds(World world, int amount) {
    return IntStream.range(0, amount).map(i -> reserveEntityId(world)).toArray();
  }

  private static int reserveEntityId(World world) {
    int newId = VER26_2.atOrAbove() ? ModernAccess.nextEntityId(world) : LegacyAccess.nextEntityId();
    if (newId == 0) {
      throw new IllegalStateException("Unable to reserve entity ID");
    }
    return newId;
  }

  private static int reserveEntityIdSynchronized(World world) {
    if (Bukkit.isPrimaryThread()) {
      return reserveEntityId(world);
    }
    FutureTask<Integer> reservation = new FutureTask<>(() -> reserveEntityId(world));
    Synchronizer.synchronize(reservation);
    try {
      return reservation.get(5, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while reserving entity ID", exception);
    } catch (ExecutionException exception) {
      throw new IllegalStateException("Unable to reserve entity ID for " + world.getName(), exception.getCause());
    } catch (TimeoutException exception) {
      reservation.cancel(false);
      throw new IllegalStateException("Timed out while reserving entity ID for " + world.getName(), exception);
    }
  }

  private static final class LegacyAccess {
    private static final Field ENTITY_COUNT_FIELD = Lookup.serverField("Entity", "entityCount");

    private static int nextEntityId() {
      try {
        if (ENTITY_COUNT_FIELD.getType() == AtomicInteger.class) {
          return ((AtomicInteger) ENTITY_COUNT_FIELD.get(null)).incrementAndGet();
        }
        int newId = ENTITY_COUNT_FIELD.getInt(null);
        ENTITY_COUNT_FIELD.setInt(null, newId + 1);
        return newId;
      } catch (IllegalAccessException exception) {
        throw new IllegalStateException("Unable to reserve legacy entity ID", exception);
      }
    }
  }

  private static final class ModernAccess {
    private static final Object UNSAFE = Bukkit.getUnsafe();
    private static final Method NEXT_ENTITY_ID = findNextEntityIdMethod();

    private static Method findNextEntityIdMethod() {
      try {
        return UNSAFE.getClass().getMethod("nextEntityId", World.class);
      } catch (NoSuchMethodException exception) {
        throw new IllegalStateException("Unable to access nextEntityId(World)", exception);
      }
    }

    private static int nextEntityId(World world) {
      try {
        return (int) NEXT_ENTITY_ID.invoke(UNSAFE, world);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Unable to reserve entity ID for " + world.getName(), exception);
      }
    }
  }
}
