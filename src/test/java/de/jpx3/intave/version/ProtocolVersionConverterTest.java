package de.jpx3.intave.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProtocolVersionConverterTest {
  @Test
  void mapsMinecraft26_2BeforeRemoteRanges() {
    assertEquals("26.2.0", ProtocolVersionConverter.versionByProtocolVersion(776));
    assertEquals(776, ProtocolVersionConverter.protocolVersionBy("26.2"));
    assertEquals(776, ProtocolVersionConverter.protocolVersionBy("26.2.0"));
  }
}
