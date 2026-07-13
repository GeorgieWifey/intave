package de.jpx3.intave.version;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;

import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_26_2;

public final class ProtocolVersionConverter {
  public static String versionByProtocolVersion(int version) {
    return version == VER_26_2 ? "26.2.0" : ranges().byProtocolVersion(version);
  }

  public static int protocolVersionBy(MinecraftVersion version) {
    return MinecraftVersions.VER26_2.equals(version) ? VER_26_2 : ranges().nearestProtocolVersion(version.getVersion());
  }

  public static int protocolVersionBy(String version) {
    return protocolVersionBy(new MinecraftVersion(version));
  }

  private static ProtocolVersionRanges ranges() {
    return RangesHolder.RANGES;
  }

  private static final class RangesHolder {
    private static final Resource RESOURCE = Resources.localServiceCacheResource("protocolversions", "protocolversions", TimeUnit.DAYS.toMillis(14));
    private static final ProtocolVersionRanges RANGES = RESOURCE.collectLines(ProtocolVersionRangesCompiler.resourceCollector());
  }
}
