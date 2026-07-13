package de.jpx3.intave.player.fake;

import de.jpx3.intave.cleanup.GarbageCollector;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class IdentifierReserveTest {
  @Test
  @SuppressWarnings("unchecked")
  void worldPoolIsClearedOnWorldUnload() throws Exception {
    UUID worldId = UUID.randomUUID();
    World world = (World) Proxy.newProxyInstance(
      World.class.getClassLoader(),
      new Class<?>[]{World.class},
      (proxy, method, arguments) -> {
        switch (method.getName()) {
          case "getUID":
            return worldId;
          case "getName":
            return "test-world";
          case "hashCode":
            return System.identityHashCode(proxy);
          case "equals":
            return proxy == arguments[0];
          case "toString":
            return "test-world";
          default:
            throw new UnsupportedOperationException(method.toString());
        }
      }
    );

    Field availableIdsField = IdentifierReserve.class.getDeclaredField("availableIds");
    availableIdsField.setAccessible(true);
    Map<Object, Queue<Integer>> availableIds =
      (Map<Object, Queue<Integer>>) availableIdsField.get(null);
    Queue<Integer> uuidIds = filledQueue(100);
    Queue<Integer> worldIds = filledQueue(200);
    availableIds.clear();
    availableIds.put(worldId, uuidIds);
    availableIds.put(world, worldIds);
    try {
      IdentifierReserve.acquireNew(world);
      availableIds.remove(uuidIds.size() == 25 ? world : worldId);

      GarbageCollector.clear(world);

      assertTrue(availableIds.isEmpty());
    } finally {
      availableIds.clear();
    }
  }

  @Test
  void primaryReservationRunsInline() {
    AtomicBoolean synchronizedCall = new AtomicBoolean();

    int identifier = IdentifierReserve.reserveEntityIdSynchronized(
      true, () -> 17, command -> synchronizedCall.set(true), 10, TimeUnit.MILLISECONDS, "test-world"
    );

    assertEquals(17, identifier);
    assertFalse(synchronizedCall.get());
  }

  @Test
  void asyncReservationRunsThroughSynchronizer() throws Exception {
    Thread primaryThread = Thread.currentThread();
    BlockingQueue<Runnable> synchronizedTasks = new LinkedBlockingQueue<>();
    AtomicReference<Thread> reservationThread = new AtomicReference<>();
    CompletableFuture<Integer> result = CompletableFuture.supplyAsync(() ->
      IdentifierReserve.reserveEntityIdSynchronized(
        false,
        () -> {
          reservationThread.set(Thread.currentThread());
          return 23;
        },
        synchronizedTasks::add,
        1,
        TimeUnit.SECONDS,
        "test-world"
      )
    );

    Runnable synchronizedTask = synchronizedTasks.poll(1, TimeUnit.SECONDS);
    assertNotNull(synchronizedTask);
    synchronizedTask.run();

    assertEquals(23, result.get(1, TimeUnit.SECONDS));
    assertSame(primaryThread, reservationThread.get());
  }

  @Test
  void asyncReservationTimesOut() {
    IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
      IdentifierReserve.reserveEntityIdSynchronized(
        false, () -> 31, command -> {}, 10, TimeUnit.MILLISECONDS, "timeout-world"
      )
    );

    assertTrue(exception.getMessage().contains("timeout-world"));
  }

  private static Queue<Integer> filledQueue(int firstIdentifier) {
    Queue<Integer> identifiers = new ConcurrentLinkedDeque<>();
    for (int identifier = firstIdentifier; identifier < firstIdentifier + 26; identifier++) {
      identifiers.add(identifier);
    }
    return identifiers;
  }
}
