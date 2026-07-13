package de.jpx3.intave.player.fake;

import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class IdentifierReserveTest {
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
}
