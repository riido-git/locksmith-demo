package in.riido.locksmith.demo.tracker;

import in.riido.locksmith.autoconfigure.LocksmithProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

/**
 * Counts, in Redis, how many callers are inside each guarded section at the same moment. All
 * instances share the Redis, so the counts cover every instance. The stress tests read them through
 * {@code /tracker} to prove that a lock never had two holders and a semaphore never had more
 * holders than permits.
 */
@Component
@RequiredArgsConstructor
public class ConcurrencyTracker {

  private static final String PREFIX = "tracker:";
  private static final String NAMES = "tracker:names";

  // One more caller in a role (holder, reader or writer); keeps the highest count seen. A reader
  // that finds a
  // writer inside, or a writer that finds a reader inside, counts an overlap.
  private static final String ENTER =
      """
			local inside = redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
			redis.call('HINCRBY', KEYS[1], ARGV[1] .. 'Entries', 1)
			if inside > tonumber(redis.call('HGET', KEYS[1], ARGV[1] .. 'Max') or '0') then
				redis.call('HSET', KEYS[1], ARGV[1] .. 'Max', inside)
			end
			if ARGV[2] ~= '' and tonumber(redis.call('HGET', KEYS[1], ARGV[2]) or '0') > 0 then
				redis.call('HINCRBY', KEYS[1], 'overlaps', 1)
			end
			if ARGV[3] ~= '' then
				redis.call('HSET', KEYS[1], 'limit', ARGV[3])
			end
			redis.call('SADD', KEYS[2], KEYS[1])
			return inside
			""";

  private static final String LEAVE = "return redis.call('HINCRBY', KEYS[1], ARGV[1], -1)";

  private final RedissonClient redisson;
  private final LocksmithProperties locksmith;

  /**
   * Enters a section that allows {@code limit} callers at once: 1 for a lock, the permit count for
   * a semaphore.
   */
  public Inside enter(String name, int limit) {
    return enter(name, "holder", "", String.valueOf(limit));
  }

  /** Enters as a reader of a read/write lock. */
  public Inside enterReader(String name) {
    return enter(name, "reader", "writer", "");
  }

  /** Enters as the writer of a read/write lock. */
  public Inside enterWriter(String name) {
    return enter(name, "writer", "reader", "");
  }

  /** Enters, works for {@code workMs}, and leaves. */
  public void work(String name, int limit, long workMs) {
    try (Inside ignored = enter(name, limit)) {
      Work.sleep(workMs);
    }
  }

  /** Every section seen since the last reset, with its counters. */
  public Map<String, Map<String, Long>> snapshot() {
    Map<String, Map<String, Long>> sections = new TreeMap<>();
    for (String key : redisson.<String>getSet(NAMES, StringCodec.INSTANCE).readAll()) {
      Map<String, Long> counters = new TreeMap<>();
      redisson
          .<String, String>getMap(key, StringCodec.INSTANCE)
          .readAllMap()
          .forEach((field, value) -> counters.put(field, Long.parseLong(value)));
      sections.put(key.substring(PREFIX.length()), counters);
    }
    return sections;
  }

  public void reset() {
    List<String> keys =
        new ArrayList<>(redisson.<String>getSet(NAMES, StringCodec.INSTANCE).readAll());
    keys.add(NAMES);
    redisson.getKeys().delete(keys.toArray(String[]::new));
  }

  /** The Redis keys Locksmith has right now, found by its key prefix. */
  public List<String> locksmithKeys() {
    return redisson
        .getKeys()
        .getKeysStream(KeysScanOptions.defaults().pattern("*" + locksmith.keyPrefix() + "*"))
        .filter(key -> !key.startsWith(PREFIX))
        .sorted()
        .toList();
  }

  private Inside enter(String name, String role, String conflictingRole, String limit) {
    String key = PREFIX + name;
    script()
        .eval(
            RScript.Mode.READ_WRITE,
            ENTER,
            RScript.ReturnType.LONG,
            List.of(key, NAMES),
            role,
            conflictingRole,
            limit);
    return () ->
        script().eval(RScript.Mode.READ_WRITE, LEAVE, RScript.ReturnType.LONG, List.of(key), role);
  }

  private RScript script() {
    return redisson.getScript(StringCodec.INSTANCE);
  }

  /** A caller inside a section; closing it leaves the section. */
  public interface Inside extends AutoCloseable {

    @Override
    void close();
  }
}
