package com.hcare.config;

import com.hcare.domain.*;
import com.hcare.evv.VerificationMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Profile("dev")
@Component
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final AgencyRepository agencyRepository;
    private final AgencyUserRepository agencyUserRepository;
    private final ClientRepository clientRepository;
    private final CaregiverRepository caregiverRepository;
    private final CaregiverCredentialRepository caregiverCredentialRepository;
    private final CaregiverScoringProfileRepository caregiverScoringProfileRepository;
    private final CaregiverAvailabilityRepository caregiverAvailabilityRepository;
    private final BackgroundCheckRepository backgroundCheckRepository;
    private final PayerRepository payerRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final AuthorizationRepository authorizationRepository;
    private final CarePlanRepository carePlanRepository;
    private final GoalRepository goalRepository;
    private final AdlTaskRepository adlTaskRepository;
    private final ShiftRepository shiftRepository;
    private final EvvRecordRepository evvRecordRepository;
    private final FeatureFlagsRepository featureFlagsRepository;
    private final FamilyPortalUserRepository familyPortalUserRepository;
    private final CaregiverClientAffinityRepository caregiverClientAffinityRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(
            AgencyRepository agencyRepository,
            AgencyUserRepository agencyUserRepository,
            ClientRepository clientRepository,
            CaregiverRepository caregiverRepository,
            CaregiverCredentialRepository caregiverCredentialRepository,
            CaregiverScoringProfileRepository caregiverScoringProfileRepository,
            CaregiverAvailabilityRepository caregiverAvailabilityRepository,
            BackgroundCheckRepository backgroundCheckRepository,
            PayerRepository payerRepository,
            ServiceTypeRepository serviceTypeRepository,
            AuthorizationRepository authorizationRepository,
            CarePlanRepository carePlanRepository,
            GoalRepository goalRepository,
            AdlTaskRepository adlTaskRepository,
            ShiftRepository shiftRepository,
            EvvRecordRepository evvRecordRepository,
            FeatureFlagsRepository featureFlagsRepository,
            FamilyPortalUserRepository familyPortalUserRepository,
            CaregiverClientAffinityRepository caregiverClientAffinityRepository,
            PasswordEncoder passwordEncoder) {
        this.agencyRepository = agencyRepository;
        this.agencyUserRepository = agencyUserRepository;
        this.clientRepository = clientRepository;
        this.caregiverRepository = caregiverRepository;
        this.caregiverCredentialRepository = caregiverCredentialRepository;
        this.caregiverScoringProfileRepository = caregiverScoringProfileRepository;
        this.caregiverAvailabilityRepository = caregiverAvailabilityRepository;
        this.backgroundCheckRepository = backgroundCheckRepository;
        this.payerRepository = payerRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.authorizationRepository = authorizationRepository;
        this.carePlanRepository = carePlanRepository;
        this.goalRepository = goalRepository;
        this.adlTaskRepository = adlTaskRepository;
        this.shiftRepository = shiftRepository;
        this.evvRecordRepository = evvRecordRepository;
        this.featureFlagsRepository = featureFlagsRepository;
        this.familyPortalUserRepository = familyPortalUserRepository;
        this.caregiverClientAffinityRepository = caregiverClientAffinityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (agencyRepository.count() > 0) {
            log.info("=== DevDataSeeder: data already present, skipping ===");
            return;
        }
        log.info("=== DevDataSeeder: seeding {} agencies ===", 3);
        seed();
        log.info("=== DevDataSeeder: complete ===");
    }

    @Transactional
    private void seed() {
        String encodedPassword = passwordEncoder.encode("Admin1234!");

        // -------------------------
        // Agency 1: Sunrise Home Care (TX)
        // -------------------------
        Agency agency1 = agencyRepository.save(new Agency("Sunrise Home Care", "TX"));
        UUID a1 = agency1.getId();
        String slug1 = "sunrise";

        FeatureFlags ff1 = new FeatureFlags(a1);
        ff1.setAiSchedulingEnabled(false);
        ff1.setFamilyPortalEnabled(true);
        featureFlagsRepository.save(ff1);

        seedAgencyUsers(a1, slug1, encodedPassword);

        List<Payer> payers1 = seedPayers(a1, "TX", List.of(
                new String[]{"Texas Medicaid", "MEDICAID"},
                new String[]{"Medicare Advantage TX", "MEDICARE"},
                new String[]{"Bright Futures Private Pay", "PRIVATE_PAY"}));

        List<ServiceType> serviceTypes1 = seedServiceTypes(a1);

        double[] baseTx = {30.3, -97.7};
        List<Client> clients1 = seedClients(a1, "TX", List.of(
                new String[]{"Dorothy", "Henderson", "1945-03-14", "en", "TX-MCD-SUN-001"},
                new String[]{"Harold",  "Mitchell",  "1942-11-08", "en", "TX-MCD-SUN-002"},
                new String[]{"Patricia","Gallagher", "1953-07-22", "en,es","TX-MCD-SUN-003"},
                new String[]{"Gerald",  "Kowalski",  "1948-04-30", "en", null},  // private pay
                new String[]{"Betty",   "Thornton",  "1938-12-01", "en", "TX-MCD-SUN-005"}),
                baseTx);

        List<Caregiver> caregivers1 = seedCaregivers(a1, baseTx, List.of(
                new String[]{"Ashley", "Rodriguez"},
                new String[]{"Tyler", "Brooks"},
                new String[]{"Jessica", "Nguyen"},
                new String[]{"Brandon", "Washington"},
                new String[]{"Kayla", "Martinez"}));

        seedAuthorizations(a1, slug1, clients1, payers1, serviceTypes1);
        seedCarePlans(a1, clients1);
        seedShifts(a1, clients1, caregivers1, serviceTypes1, payers1, slug1);

        log.info("  [{}] Seeded: {} clients, {} caregivers, {} payers",
                agency1.getName(), clients1.size(), caregivers1.size(), payers1.size());

        // -------------------------
        // Agency 2: Golden Years Care (FL)
        // -------------------------
        Agency agency2 = agencyRepository.save(new Agency("Golden Years Care", "FL"));
        UUID a2 = agency2.getId();
        String slug2 = "golden";

        FeatureFlags ff2 = new FeatureFlags(a2);
        ff2.setAiSchedulingEnabled(false);
        ff2.setFamilyPortalEnabled(true);
        featureFlagsRepository.save(ff2);

        seedAgencyUsers(a2, slug2, encodedPassword);

        List<Payer> payers2 = seedPayers(a2, "FL", List.of(
                new String[]{"Florida Medicaid", "MEDICAID"},
                new String[]{"Medicare Plan FL", "MEDICARE"},
                new String[]{"Prestige Private Pay", "PRIVATE_PAY"},
                new String[]{"Veterans Affairs FL", "VA"}));

        List<ServiceType> serviceTypes2 = seedServiceTypes(a2);

        double[] baseFl = {27.9, -82.4};
        List<Client> clients2 = seedClients(a2, "FL", List.of(
                new String[]{"Walter", "Freeman",  "1944-06-15", "en", "FL-MCD-GLD-001"},
                new String[]{"Barbara","Novak",    "1950-02-28", "en", "FL-MCD-GLD-002"},
                new String[]{"Dennis", "Crawford", "1956-09-18", "en", "FL-MCD-GLD-003"},
                new String[]{"Sandra", "Okafor",   "1947-01-05", "en", null},  // private pay
                new String[]{"Roger",  "Kimball",  "1941-08-22", "en", "FL-MCD-GLD-005"}),
                baseFl);

        List<Caregiver> caregivers2 = seedCaregivers(a2, baseFl, List.of(
                new String[]{"Megan", "Torres"},
                new String[]{"Jordan", "Taylor"},
                new String[]{"Samantha", "Patel"},
                new String[]{"Austin", "Williams"},
                new String[]{"Lauren", "Chen"}));

        seedAuthorizations(a2, slug2, clients2, payers2, serviceTypes2);
        seedCarePlans(a2, clients2);
        seedShifts(a2, clients2, caregivers2, serviceTypes2, payers2, slug2);

        log.info("  [{}] Seeded: {} clients, {} caregivers, {} payers",
                agency2.getName(), clients2.size(), caregivers2.size(), payers2.size());

        // -------------------------
        // Agency 3: Harmony Home Health (CA)
        // -------------------------
        Agency agency3 = agencyRepository.save(new Agency("Harmony Home Health", "CA"));
        UUID a3 = agency3.getId();
        String slug3 = "harmony";

        FeatureFlags ff3 = new FeatureFlags(a3);
        ff3.setAiSchedulingEnabled(true);
        ff3.setFamilyPortalEnabled(true);
        featureFlagsRepository.save(ff3);

        seedAgencyUsers(a3, slug3, encodedPassword);

        List<Payer> payers3 = seedPayers(a3, "CA", List.of(
                new String[]{"Medi-Cal", "MEDICAID"},
                new String[]{"SeniorCare LTC", "LTC_INSURANCE"},
                new String[]{"Pacific Private Pay", "PRIVATE_PAY"}));

        List<ServiceType> serviceTypes3 = seedServiceTypes(a3);

        double[] baseCa = {34.1, -118.2};
        List<Client> clients3 = seedClients(a3, "CA", List.of(
                new String[]{"Carol",   "Stevenson", "1952-05-10", "en", "CA-MCD-HAR-001"},
                new String[]{"Kenneth", "Yamamoto",  "1949-12-07", "en", "CA-MCD-HAR-002"},
                new String[]{"Donna",   "Burke",     "1957-08-25", "en", "CA-MCD-HAR-003"},
                new String[]{"Frank",   "Delgado",   "1945-03-19", "en", null},  // private pay
                new String[]{"Judith",  "Schreiber", "1960-11-14", "en", "CA-MCD-HAR-005"}),
                baseCa);

        List<Caregiver> caregivers3 = seedCaregivers(a3, baseCa, List.of(
                new String[]{"Alyssa", "Ramirez"},
                new String[]{"Darius", "Peterson"},
                new String[]{"Brittany", "Kim"},
                new String[]{"Carlos", "Rivera"},
                new String[]{"Kaitlyn", "O'Brien"}));

        seedAuthorizations(a3, slug3, clients3, payers3, serviceTypes3);
        seedCarePlans(a3, clients3);
        seedShifts(a3, clients3, caregivers3, serviceTypes3, payers3, slug3);
        seedAgency3ScoringData(a3, caregivers3, clients3);

        log.info("  [{}] Seeded: {} clients, {} caregivers, {} payers",
                agency3.getName(), clients3.size(), caregivers3.size(), payers3.size());
    }

    // -------------------------------------------------------------------------
    // Helper: Agency users
    // -------------------------------------------------------------------------
    private void seedAgencyUsers(UUID agencyId, String slug, String encodedPassword) {
        agencyUserRepository.save(new AgencyUser(
                agencyId, "admin@" + slug + ".dev", encodedPassword, UserRole.ADMIN));
        agencyUserRepository.save(new AgencyUser(
                agencyId, "scheduler@" + slug + ".dev", encodedPassword, UserRole.SCHEDULER));
    }

    // -------------------------------------------------------------------------
    // Helper: Payers
    // -------------------------------------------------------------------------
    private List<Payer> seedPayers(UUID agencyId, String state, List<String[]> specs) {
        return specs.stream().map(s -> {
            PayerType type = PayerType.valueOf(s[1]);
            return payerRepository.save(new Payer(agencyId, s[0], type, state));
        }).toList();
    }

    // -------------------------------------------------------------------------
    // Helper: Service types (same two for every agency)
    // -------------------------------------------------------------------------
    private List<ServiceType> seedServiceTypes(UUID agencyId) {
        ServiceType pcs = serviceTypeRepository.save(new ServiceType(
                agencyId, "Personal Care Services", "PCS", true, "[\"HHA\"]"));
        ServiceType snv = serviceTypeRepository.save(new ServiceType(
                agencyId, "Skilled Nursing Visit", "SNV", true, "[\"RN\",\"LPN\"]"));
        return List.of(pcs, snv);
    }

    // -------------------------------------------------------------------------
    // Helper: Clients
    // -------------------------------------------------------------------------
    private List<Client> seedClients(UUID agencyId, String state, List<String[]> specs, double[] base) {
        List<Client> saved = new java.util.ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            String[] s = specs.get(i);
            String firstName = s[0];
            String lastName = s[1];
            LocalDate dob = LocalDate.parse(s[2]);
            String langs = s[3].contains(",") ? "[\"en\",\"es\"]" : "[\"en\"]";

            String medicaidId = s.length > 4 ? s[4] : null;

            Client client = new Client(agencyId, firstName, lastName, dob);
            client.setServiceState(state);
            client.setPreferredLanguages(langs);
            client.setNoPetCaregiver(false);
            client.setStatus(ClientStatus.ACTIVE);
            if (medicaidId != null) client.setMedicaidId(medicaidId);
            double latOffset = i * 0.05;
            double lngOffset = i * 0.05;
            client.setLat(BigDecimal.valueOf(base[0] + latOffset).setScale(7, java.math.RoundingMode.HALF_UP));
            client.setLng(BigDecimal.valueOf(base[1] - lngOffset).setScale(7, java.math.RoundingMode.HALF_UP));

            Client persisted = clientRepository.save(client);
            saved.add(persisted);

            // Family portal user
            FamilyPortalUser fpu = new FamilyPortalUser(persisted.getId(), agencyId,
                    firstName.toLowerCase() + ".family@portal.dev");
            fpu.setName(firstName + "'s Family");
            familyPortalUserRepository.save(fpu);
        }
        return saved;
    }

    // -------------------------------------------------------------------------
    // Helper: Caregivers (+ scoring profile, credentials, background checks, availability)
    // -------------------------------------------------------------------------
    private static final List<String> SPANISH_SURNAMES = List.of(
            "Rodriguez", "Martinez", "Torres", "Ramirez", "Rivera", "Delgado");

    private List<Caregiver> seedCaregivers(UUID agencyId, double[] base, List<String[]> specs) {
        List<Caregiver> saved = new java.util.ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < specs.size(); i++) {
            String firstName = specs.get(i)[0];
            String lastName = specs.get(i)[1];
            String email = firstName.toLowerCase() + "." + lastName.toLowerCase().replace("'", "") + "@hcare.dev";

            boolean spanishSurname = SPANISH_SURNAMES.stream()
                    .anyMatch(s -> s.equalsIgnoreCase(lastName));
            String languages = spanishSurname ? "[\"en\",\"es\"]" : "[\"en\"]";

            Caregiver cg = new Caregiver(agencyId, firstName, lastName, email);
            cg.setStatus(CaregiverStatus.ACTIVE);
            cg.setHasPet(i == 2); // one per agency at index 2
            cg.setLanguages(languages);
            cg.setHireDate(today.minusYears(2));
            double latOffset = i * 0.05;
            double lngOffset = i * 0.05;
            cg.setHomeLat(BigDecimal.valueOf(base[0] + latOffset).setScale(7, java.math.RoundingMode.HALF_UP));
            cg.setHomeLng(BigDecimal.valueOf(base[1] - lngOffset).setScale(7, java.math.RoundingMode.HALF_UP));

            Caregiver persisted = caregiverRepository.save(cg);
            UUID cgId = persisted.getId();
            saved.add(persisted);

            // Scoring profile
            caregiverScoringProfileRepository.save(new CaregiverScoringProfile(cgId, agencyId));

            // Credentials
            seedCredentials(cgId, agencyId, i, today);

            // Background checks
            seedBackgroundChecks(cgId, agencyId, i, today);

            // Availability Mon-Fri 08:00-17:00
            for (DayOfWeek dow : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
                caregiverAvailabilityRepository.save(
                        new CaregiverAvailability(cgId, agencyId, dow,
                                LocalTime.of(8, 0), LocalTime.of(17, 0)));
            }
        }
        return saved;
    }

    private void seedCredentials(UUID cgId, UUID agencyId, int idx, LocalDate today) {
        // CPR — issued 1 year ago, expiry varies by index
        LocalDate cprIssue = today.minusYears(1);
        LocalDate cprExpiry = switch (idx) {
            case 0 -> today.plusDays(20);
            case 1 -> today.plusDays(45);
            default -> today.plusYears(2);
        };
        caregiverCredentialRepository.save(new CaregiverCredential(
                cgId, agencyId, CredentialType.CPR, cprIssue, cprExpiry));

        // HHA — issued 2 years ago, expires in 3 years
        caregiverCredentialRepository.save(new CaregiverCredential(
                cgId, agencyId, CredentialType.HHA,
                today.minusYears(2), today.plusYears(3)));

        // TB_TEST — issued 6 months ago, expires in 18 months
        caregiverCredentialRepository.save(new CaregiverCredential(
                cgId, agencyId, CredentialType.TB_TEST,
                today.minusMonths(6), today.plusMonths(18)));
    }

    private void seedBackgroundChecks(UUID cgId, UUID agencyId, int idx, LocalDate today) {
        // STATE_REGISTRY — checked 1 year ago, renewal 1 year from now
        BackgroundCheck stateCheck = new BackgroundCheck(
                cgId, agencyId, BackgroundCheckType.STATE_REGISTRY,
                BackgroundCheckResult.CLEAR, today.minusYears(1));
        stateCheck.setRenewalDueDate(today.plusYears(1));
        backgroundCheckRepository.save(stateCheck);

        // FBI — checked 2 years ago, renewal varies by index
        LocalDate fbiRenewal = (idx == 2) ? today.plusDays(25) : today.plusYears(2);
        BackgroundCheck fbiCheck = new BackgroundCheck(
                cgId, agencyId, BackgroundCheckType.FBI,
                BackgroundCheckResult.CLEAR, today.minusYears(2));
        fbiCheck.setRenewalDueDate(fbiRenewal);
        backgroundCheckRepository.save(fbiCheck);
    }

    // -------------------------------------------------------------------------
    // Helper: Authorizations (Medicaid clients at index 0, 1, 2)
    // -------------------------------------------------------------------------
    private void seedAuthorizations(UUID agencyId, String slug, List<Client> clients,
            List<Payer> payers, List<ServiceType> serviceTypes) {
        // Find the Medicaid payer (first in list for all agencies by design)
        Payer medicaidPayer = payers.stream()
                .filter(p -> p.getPayerType() == PayerType.MEDICAID)
                .findFirst()
                .orElseThrow();
        // PCS is the first service type
        ServiceType pcs = serviceTypes.get(0);

        LocalDate startOfYear = LocalDate.now().withDayOfYear(1);
        LocalDate endOfYear = LocalDate.now().withDayOfYear(LocalDate.now().lengthOfYear());

        BigDecimal[] usedUnitsByIndex = {
                new BigDecimal("170"),
                new BigDecimal("100"),
                new BigDecimal("60")
        };

        int limit = Math.min(clients.size(), 3);
        for (int i = 0; i < limit; i++) {
            Client client = clients.get(i);
            String authNumber = String.format("AUTH-%s-%03d", slug, i + 1);
            Authorization auth = new Authorization(
                    client.getId(), medicaidPayer.getId(), pcs.getId(), agencyId,
                    authNumber, new BigDecimal("200"), UnitType.HOURS,
                    startOfYear, endOfYear);
            auth.addUsedUnits(usedUnitsByIndex[i]);
            authorizationRepository.save(auth);
        }
    }

    // -------------------------------------------------------------------------
    // Helper: Care plans + goals + ADL tasks
    // -------------------------------------------------------------------------
    private void seedCarePlans(UUID agencyId, List<Client> clients) {
        for (Client client : clients) {
            CarePlan plan = new CarePlan(client.getId(), agencyId, 1);
            plan.activate();
            CarePlan saved = carePlanRepository.save(plan);
            UUID planId = saved.getId();

            goalRepository.save(new Goal(planId, agencyId,
                    "Improve mobility and balance to reduce fall risk"));
            goalRepository.save(new Goal(planId, agencyId,
                    "Maintain personal hygiene independence with minimal assistance"));

            AdlTask bathing = new AdlTask(planId, agencyId,
                    "Bathing assistance", AssistanceLevel.MODERATE_ASSIST);
            bathing.setSortOrder(1);
            adlTaskRepository.save(bathing);

            AdlTask medication = new AdlTask(planId, agencyId,
                    "Medication reminder", AssistanceLevel.SUPERVISION);
            medication.setSortOrder(2);
            adlTaskRepository.save(medication);
        }
    }

    // -------------------------------------------------------------------------
    // Helper: Shifts — covers all 5 clients with deliberate EVV status variety
    //
    // TODAY's visit list per agency (10 rows):
    //   Client[0] 08:00 COMPLETED  → GREEN  (all 6 federal elements, on-time GPS)
    //   Client[0] 13:00 IN_PROGRESS→ GREY   (no EVV record yet)
    //   Client[0] 18:00 OPEN       → GREY   (unassigned)
    //   Client[1] 09:00 COMPLETED  → YELLOW (clock-in 35 min late)
    //   Client[1] 14:00 ASSIGNED   → GREY
    //   Client[2] 10:00 COMPLETED  → RED    (clientMedicaidId intentionally missing)
    //   Client[2] 15:00 ASSIGNED   → GREY
    //   Client[3] 09:00 COMPLETED  → EXEMPT (co-resident caregiver flag)
    //   Client[4] 11:00 OPEN       → GREY   (unassigned)
    //   Client[1] yesterday MISSED → GREY   (no EVV record)
    // -------------------------------------------------------------------------
    private void seedShifts(UUID agencyId, List<Client> clients, List<Caregiver> caregivers,
            List<ServiceType> serviceTypes, List<Payer> payers, String slug) {
        if (clients.isEmpty() || caregivers.isEmpty()) return;

        ServiceType pcs = serviceTypes.get(0);
        LocalDate today = LocalDate.now();

        // Resolve authorization IDs for Medicaid clients (indices 0, 1, 2)
        UUID[] authIds = new UUID[3];
        for (int i = 0; i < 3 && i < clients.size(); i++) {
            UUID cid = clients.get(i).getId();
            authIds[i] = authorizationRepository.findAll().stream()
                    .filter(a -> a.getClientId().equals(cid) && a.getAgencyId().equals(agencyId))
                    .findFirst().map(Authorization::getId).orElse(null);
        }

        Client c0 = clients.get(0); Caregiver cg0 = caregivers.get(0);
        Client c1 = clients.get(1); Caregiver cg1 = caregivers.get(1);
        Client c2 = clients.get(2); Caregiver cg2 = caregivers.get(2);
        Client c3 = clients.get(3); Caregiver cg3 = caregivers.get(3);
        Client c4 = clients.get(4);

        // ---- Past 7 days: client[0] — all GREEN --------------------------------
        for (int d = 1; d <= 7; d++) {
            LocalDateTime s = today.minusDays(d).atTime(9, 0);
            Shift shift = savedCompleted(agencyId, c0, cg0.getId(), pcs.getId(), authIds[0], s, s.plusHours(4));
            saveEvv(shift, agencyId, VerificationMethod.GPS,
                    c0.getMedicaidId(), c0.getLat(), c0.getLng(), s, s.plusHours(4), false);
        }

        // ---- TODAY: client[0] — GREEN / GREY / GREY ----------------------------
        Shift t0a = savedCompleted(agencyId, c0, cg0.getId(), pcs.getId(), authIds[0],
                today.atTime(8, 0), today.atTime(12, 0));
        saveEvv(t0a, agencyId, VerificationMethod.GPS,
                c0.getMedicaidId(), c0.getLat(), c0.getLng(),
                today.atTime(8, 0), today.atTime(12, 0), false); // GREEN

        Shift t0b = new Shift(agencyId, null, c0.getId(), cg0.getId(), pcs.getId(), authIds[0],
                today.atTime(13, 0), today.atTime(17, 0));
        t0b.setStatus(ShiftStatus.IN_PROGRESS);
        shiftRepository.save(t0b); // GREY

        shiftRepository.save(new Shift(agencyId, null, c0.getId(), null, pcs.getId(), authIds[0],
                today.atTime(18, 0), today.atTime(22, 0))); // OPEN → GREY

        // ---- TODAY: client[1] — YELLOW / GREY ----------------------------------
        // Clock-in 35 minutes after scheduled start → exceeds 30-min anomaly threshold
        Shift t1a = savedCompleted(agencyId, c1, cg1.getId(), pcs.getId(), authIds[1],
                today.atTime(9, 0), today.atTime(13, 0));
        saveEvv(t1a, agencyId, VerificationMethod.GPS,
                c1.getMedicaidId(), c1.getLat(), c1.getLng(),
                today.atTime(9, 35), today.atTime(13, 0), false); // YELLOW: late clock-in

        shiftRepository.save(new Shift(agencyId, null, c1.getId(), cg1.getId(), pcs.getId(), authIds[1],
                today.atTime(14, 0), today.atTime(18, 0))); // ASSIGNED → GREY

        // ---- TODAY: client[2] — RED / GREY -------------------------------------
        // EVV record present but clientMedicaidId null → missing federal element 2
        Shift t2a = savedCompleted(agencyId, c2, cg2.getId(), pcs.getId(), authIds[2],
                today.atTime(10, 0), today.atTime(14, 0));
        saveEvv(t2a, agencyId, VerificationMethod.GPS,
                null, c2.getLat(), c2.getLng(),
                today.atTime(10, 0), today.atTime(14, 0), false); // RED: missing medicaidId

        shiftRepository.save(new Shift(agencyId, null, c2.getId(), cg2.getId(), pcs.getId(), authIds[2],
                today.atTime(15, 0), today.atTime(19, 0))); // ASSIGNED → GREY

        // ---- TODAY: client[3] — EXEMPT -----------------------------------------
        // co-resident flag suppresses EVV requirement regardless of payer
        Shift t3a = savedCompleted(agencyId, c3, cg3.getId(), pcs.getId(), null,
                today.atTime(9, 0), today.atTime(13, 0));
        saveEvv(t3a, agencyId, VerificationMethod.GPS,
                null, c3.getLat(), c3.getLng(),
                today.atTime(9, 0), today.atTime(13, 0), true); // EXEMPT: coResident=true

        // ---- TODAY: client[4] — OPEN → GREY ------------------------------------
        shiftRepository.save(new Shift(agencyId, null, c4.getId(), null, pcs.getId(), null,
                today.atTime(11, 0), today.atTime(15, 0)));

        // ---- MISSED: client[1] yesterday afternoon — GREY ----------------------
        Shift missed = new Shift(agencyId, null, c1.getId(), cg1.getId(), pcs.getId(), authIds[1],
                today.minusDays(1).atTime(14, 0), today.minusDays(1).atTime(18, 0));
        missed.setStatus(ShiftStatus.MISSED);
        shiftRepository.save(missed);

        // ---- UPCOMING: client[0] next 3 days — ASSIGNED → GREY ----------------
        for (int d = 1; d <= 3; d++) {
            LocalDateTime s = today.plusDays(d).atTime(9, 0);
            shiftRepository.save(new Shift(agencyId, null, c0.getId(), cg0.getId(),
                    pcs.getId(), authIds[0], s, s.plusHours(4)));
        }
    }

    /** Creates and persists a COMPLETED shift. */
    private Shift savedCompleted(UUID agencyId, Client client, UUID caregiverId,
            UUID serviceTypeId, UUID authId, LocalDateTime start, LocalDateTime end) {
        Shift shift = new Shift(agencyId, null, client.getId(), caregiverId,
                serviceTypeId, authId, start, end);
        shift.setStatus(ShiftStatus.COMPLETED);
        return shiftRepository.save(shift);
    }

    /** Creates and persists an EVV record. */
    private void saveEvv(Shift shift, UUID agencyId, VerificationMethod method,
            String clientMedicaidId, BigDecimal lat, BigDecimal lng,
            LocalDateTime timeIn, LocalDateTime timeOut, boolean coResident) {
        EvvRecord evv = new EvvRecord(shift.getId(), agencyId, method);
        evv.setClientMedicaidId(clientMedicaidId);
        evv.setLocationLat(lat);
        evv.setLocationLon(lng);
        evv.setTimeIn(timeIn);
        evv.setTimeOut(timeOut);
        evv.setCoResident(coResident);
        evv.setStateFields("{}");
        evvRecordRepository.save(evv);
    }

    // -------------------------------------------------------------------------
    // Agency 3 only: seed scoring profiles and caregiver-client affinities so
    // the AI scheduling engine produces a meaningful, non-trivial ranking.
    //
    // Caregivers: Alyssa[0], Darius[1], Brittany[2], Carlos[3], Kaitlyn[4]
    // Clients:    Carol[0],  Kenneth[1], Donna[2],   Frank[3],  Judith[4]
    //
    // Expected ranking for Carol's next shift (highest → lowest):
    //   Brittany (high continuity + best reliability) > Alyssa (closest + most
    //   history, but near overtime) > Darius (available, moderate) >
    //   Kaitlyn (high cancel rate) > Carlos (far + near overtime)
    // -------------------------------------------------------------------------
    private void seedAgency3ScoringData(UUID agencyId,
                                        List<Caregiver> caregivers,
                                        List<Client> clients) {
        // Each entry: [historicalCompleted, currentWeekShifts, hoursPerCurrentShift, cancelled]
        // "historical" shifts are in prior weeks so they add 0 current-week hours.
        int[][] profileSetups = {
            {31, 9, 4, 2},   // Alyssa:  40 completed (31 historical + 9 this week×4h=36h), 2 cancelled
            {25, 0, 0, 8},   // Darius:  25 completed, 0 this week, 8 cancelled
            {55, 5, 4, 1},   // Brittany: 60 completed (55 historical + 5×4h=20h), 1 cancelled
            { 5, 9, 4, 0},   // Carlos:  15 completed (5 historical + 9×4h=36h + 1×2h=38h), 0 cancelled
            {10, 0, 0, 5},   // Kaitlyn: 10 completed, 0 this week, 5 cancelled
        };
        // Carlos gets one extra 2h shift this week to reach 38h total
        boolean[] carlosExtraShift = {false, false, false, true, false};

        for (int i = 0; i < caregivers.size(); i++) {
            CaregiverScoringProfile profile = caregiverScoringProfileRepository
                    .findByCaregiverId(caregivers.get(i).getId())
                    .orElseThrow();

            int[] s = profileSetups[i];
            int historicalCompleted = s[0];
            int currentWeekShifts  = s[1];
            int hoursPerShift      = s[2];
            int cancelled          = s[3];

            // Historical completed shifts — contribute to total count but not current-week hours
            // (simulates the weekly reset having run since these shifts)
            for (int j = 0; j < historicalCompleted; j++) {
                profile.updateAfterShiftCompletion(BigDecimal.ZERO);
            }
            // Current-week shifts — accumulate into currentWeekHours
            BigDecimal weekHours = hoursPerShift == 0 ? BigDecimal.ZERO
                    : new BigDecimal(hoursPerShift);
            for (int j = 0; j < currentWeekShifts; j++) {
                profile.updateAfterShiftCompletion(weekHours);
            }
            // Carlos's extra 2h shift to reach 38h this week
            if (carlosExtraShift[i]) {
                profile.updateAfterShiftCompletion(new BigDecimal("2"));
            }
            // Cancellations
            for (int j = 0; j < cancelled; j++) {
                profile.updateAfterShiftCancellation();
            }

            caregiverScoringProfileRepository.save(profile);
        }

        // Caregiver-client affinity (visit history)
        // visitCount saturates continuity score at 10; a count of 0 means no prior history.
        int[][] affinityMatrix = {
            //             Carol[0] Kenneth[1] Donna[2] Frank[3] Judith[4]
            /* Alyssa  */ {10,      5,         0,       0,       0},
            /* Darius  */ { 0,      3,         7,       0,       0},
            /* Brittany*/ { 8,      0,         0,       0,       6},
            /* Carlos  */ { 2,      0,         0,       9,       0},
            /* Kaitlyn */ { 0,      0,         2,       0,       0},
        };

        for (int cgIdx = 0; cgIdx < caregivers.size(); cgIdx++) {
            CaregiverScoringProfile profile = caregiverScoringProfileRepository
                    .findByCaregiverId(caregivers.get(cgIdx).getId())
                    .orElseThrow();

            for (int clIdx = 0; clIdx < clients.size(); clIdx++) {
                int visits = affinityMatrix[cgIdx][clIdx];
                if (visits == 0) continue;

                CaregiverClientAffinity affinity = new CaregiverClientAffinity(
                        profile.getId(), clients.get(clIdx).getId(), agencyId);
                for (int v = 0; v < visits; v++) {
                    affinity.incrementVisitCount();
                }
                caregiverClientAffinityRepository.save(affinity);
            }
        }

        log.info("  [Harmony] Scoring profiles and affinities seeded for {} caregivers",
                caregivers.size());
    }
}
