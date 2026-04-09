package com.hcare.integration;

import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Static utility that maps US state codes (two-letter USPS abbreviations) to their primary
 * {@link ZoneId}.
 *
 * <p>States that span multiple timezones are mapped to their most populous / dominant zone:
 * <ul>
 *   <li>Indiana (IN) — {@code America/Indiana/Indianapolis} (majority of the state; observes ET)
 *   <li>Arizona (AZ) — {@code America/Phoenix} (state does not observe DST; Navajo Nation excepted)
 *   <li>Michigan (MI) — {@code America/Detroit} (nearly all residents are in Eastern Time)
 *   <li>Nebraska (NE), Kansas (KS) — {@code America/Chicago} (Central Time covers the majority)
 *   <li>Tennessee (TN) — {@code America/Chicago} (Nashville/Memphis are Central)
 *   <li>Kentucky (KY) — {@code America/New_York} (Louisville/Lexington dominate)
 *   <li>North/South Dakota (ND, SD) — {@code America/Chicago} (majority population is Central)
 * </ul>
 */
public final class TimezoneResolver {

  private static final Map<String, ZoneId> ZONES;

  static {
    Map<String, ZoneId> map = new HashMap<>(64);

    // Eastern Time
    map.put("CT", ZoneId.of("America/New_York"));
    map.put("DC", ZoneId.of("America/New_York"));
    map.put("DE", ZoneId.of("America/New_York"));
    map.put("FL", ZoneId.of("America/New_York"));
    map.put("GA", ZoneId.of("America/New_York"));
    map.put("IN", ZoneId.of("America/Indiana/Indianapolis"));
    map.put("KY", ZoneId.of("America/New_York"));
    map.put("MA", ZoneId.of("America/New_York"));
    map.put("MD", ZoneId.of("America/New_York"));
    map.put("ME", ZoneId.of("America/New_York"));
    map.put("MI", ZoneId.of("America/Detroit"));
    map.put("NC", ZoneId.of("America/New_York"));
    map.put("NH", ZoneId.of("America/New_York"));
    map.put("NJ", ZoneId.of("America/New_York"));
    map.put("NY", ZoneId.of("America/New_York"));
    map.put("OH", ZoneId.of("America/New_York"));
    map.put("PA", ZoneId.of("America/New_York"));
    map.put("RI", ZoneId.of("America/New_York"));
    map.put("SC", ZoneId.of("America/New_York"));
    map.put("VA", ZoneId.of("America/New_York"));
    map.put("VT", ZoneId.of("America/New_York"));
    map.put("WV", ZoneId.of("America/New_York"));

    // Central Time
    map.put("AL", ZoneId.of("America/Chicago"));
    map.put("AR", ZoneId.of("America/Chicago"));
    map.put("IA", ZoneId.of("America/Chicago"));
    map.put("IL", ZoneId.of("America/Chicago"));
    map.put("KS", ZoneId.of("America/Chicago"));
    map.put("LA", ZoneId.of("America/Chicago"));
    map.put("MN", ZoneId.of("America/Chicago"));
    map.put("MO", ZoneId.of("America/Chicago"));
    map.put("MS", ZoneId.of("America/Chicago"));
    map.put("ND", ZoneId.of("America/Chicago"));
    map.put("NE", ZoneId.of("America/Chicago"));
    map.put("OK", ZoneId.of("America/Chicago"));
    map.put("SD", ZoneId.of("America/Chicago"));
    map.put("TN", ZoneId.of("America/Chicago"));
    map.put("TX", ZoneId.of("America/Chicago"));
    map.put("WI", ZoneId.of("America/Chicago"));

    // Mountain Time
    map.put("AZ", ZoneId.of("America/Phoenix"));
    map.put("CO", ZoneId.of("America/Denver"));
    map.put("ID", ZoneId.of("America/Denver"));
    map.put("MT", ZoneId.of("America/Denver"));
    map.put("NM", ZoneId.of("America/Denver"));
    map.put("UT", ZoneId.of("America/Denver"));
    map.put("WY", ZoneId.of("America/Denver"));

    // Pacific Time
    map.put("CA", ZoneId.of("America/Los_Angeles"));
    map.put("NV", ZoneId.of("America/Los_Angeles"));
    map.put("OR", ZoneId.of("America/Los_Angeles"));
    map.put("WA", ZoneId.of("America/Los_Angeles"));

    // Alaska
    map.put("AK", ZoneId.of("America/Anchorage"));

    // Hawaii — no DST
    map.put("HI", ZoneId.of("Pacific/Honolulu"));

    ZONES = Collections.unmodifiableMap(map);
  }

  private TimezoneResolver() {
    // utility class — no instances
  }

  /**
   * Resolves the primary {@link ZoneId} for a US state.
   *
   * @param stateCode two-letter USPS state abbreviation (case-sensitive, uppercase)
   * @return the primary ZoneId for that state
   * @throws IllegalArgumentException if the state code is not recognized
   */
  public static ZoneId resolve(String stateCode) {
    ZoneId zone = ZONES.get(stateCode);
    if (zone == null) {
      throw new IllegalArgumentException(
          "Unknown US state code: '" + stateCode + "'. Expected a two-letter USPS abbreviation.");
    }
    return zone;
  }
}
