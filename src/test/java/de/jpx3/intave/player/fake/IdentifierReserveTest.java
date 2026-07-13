package de.jpx3.intave.player.fake;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.executor.Synchronizer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

final class IdentifierReserveTest {
  public interface NextEntityIdAccess {
    int nextEntityId(World world);
  }

  @Test
  void exhaustedAsyncPoolReservesOnPrimaryThread() throws Exception {
    Thread primaryThread = Thread.currentThread();
    BlockingQueue<Runnable> synchronizedTasks = new LinkedBlockingQueue<>();
    List<Thread> accessThreads = new CopyOnWriteArrayList<>();
    AtomicInteger identifiers = new AtomicInteger();
    UnsafeValues unsafe = proxy(
      new Class[]{UnsafeValues.class, NextEntityIdAccess.class},
      (method, arguments) -> {
        if (method.getName().equals("nextEntityId")) {
          accessThreads.add(Thread.currentThread());
          return identifiers.incrementAndGet();
        }
        return defaultValue(method.getReturnType());
      }
    );
    BukkitScheduler scheduler = proxy(new Class[]{BukkitScheduler.class}, (method, arguments) -> defaultValue(method.getReturnType()));
    Server server = proxy(
      new Class[]{Server.class},
      (method, arguments) -> {
        switch (method.getName()) {
          case "getLogger": return Logger.getLogger("IdentifierReserveTest");
          case "getName": return "TestServer";
          case "getVersion": return "TestServer (MC: 26.2)";
          case "getBukkitVersion": return "26.2";
          case "getScheduler": return scheduler;
          case "getUnsafe": return unsafe;
          case "isPrimaryThread": return Thread.currentThread() == primaryThread;
          default: return defaultValue(method.getReturnType());
        }
      }
    );
    UUID worldId = UUID.randomUUID();
    World world = proxy(
      new Class[]{World.class},
      (method, arguments) -> method.getName().equals("getUID") ? worldId
        : method.getName().equals("getName") ? "test-world"
        : defaultValue(method.getReturnType())
    );

    Field bukkitServer = field(Bukkit.class, "server");
    Field currentVersion = field(MinecraftVersion.class, "currentVersion");
    Field currentOrAbove = field(MinecraftVersion.class, "atCurrentOrAbove");
    Object previousServer = bukkitServer.get(null);
    Object previousVersion = currentVersion.get(null);
    Object previousCurrentOrAbove = currentOrAbove.get(MinecraftVersions.VER26_2);

    bukkitServer.set(null, null);
    Bukkit.setServer(server);
    currentVersion.set(null, new MinecraftVersion("26.2"));
    currentOrAbove.set(MinecraftVersions.VER26_2, null);

    Field synchronizationExecutor = field(Synchronizer.class, "synchronizationExecutor");
    Object previousExecutor = synchronizationExecutor.get(null);
    synchronizationExecutor.set(null, (Executor) synchronizedTasks::add);

    AtomicReference<Integer> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread worker = new Thread(() -> {
      try {
        result.set(IdentifierReserve.acquireNew(world));
      } catch (Throwable throwable) {
        failure.set(throwable);
      }
    });
    AtomicReference<Throwable> stalledFailure = new AtomicReference<>();
    UUID stalledWorldId = UUID.randomUUID();
    World stalledWorld = proxy(
      new Class[]{World.class},
      (method, arguments) -> method.getName().equals("getUID") ? stalledWorldId
        : method.getName().equals("getName") ? "stalled-world"
        : defaultValue(method.getReturnType())
    );
    Thread stalledWorker = new Thread(() -> {
      try {
        IdentifierReserve.acquireNew(stalledWorld);
      } catch (Throwable throwable) {
        stalledFailure.set(throwable);
      }
    });

    try {
      worker.start();
      Runnable synchronizedTask = synchronizedTasks.poll(5, TimeUnit.SECONDS);
      assertNotNull(synchronizedTask, "async acquisition did not request synchronization");
      synchronizedTask.run();
      worker.join(5000);
      assertFalse(worker.isAlive(), "async acquisition did not complete");
      while ((synchronizedTask = synchronizedTasks.poll()) != null) {
        synchronizedTask.run();
      }
      assertNull(failure.get(), () -> "async acquisition failed: " + failure.get());
      assertNotNull(result.get());
      assertTrue(result.get() > 0);
      assertFalse(accessThreads.isEmpty());
      assertTrue(accessThreads.stream().allMatch(primaryThread::equals), "nextEntityId ran off the primary thread");

      synchronizationExecutor.set(null, (Executor) command -> {});
      stalledWorker.start();
      stalledWorker.join(6000);
      assertFalse(stalledWorker.isAlive(), "async acquisition waited indefinitely for synchronization");
      assertInstanceOf(IllegalStateException.class, stalledFailure.get());
    } finally {
      worker.interrupt();
      stalledWorker.interrupt();
      synchronizationExecutor.set(null, previousExecutor);
      currentVersion.set(null, previousVersion);
      currentOrAbove.set(MinecraftVersions.VER26_2, previousCurrentOrAbove);
      bukkitServer.set(null, previousServer);
      ((Map<?, ?>) field(IdentifierReserve.class, "availableIds").get(null)).clear();
    }
  }

  private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<?>[] interfaces, Invocation invocation) {
    return (T) Proxy.newProxyInstance(
      IdentifierReserveTest.class.getClassLoader(),
      interfaces,
      (proxy, method, arguments) -> invocation.invoke(method, arguments)
    );
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0F;
    if (type == double.class) return 0D;
    return null;
  }

  private interface Invocation {
    Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
  }
}
