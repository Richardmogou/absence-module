package com.banque.absences.service;

import com.banque.absences.domain.*;
import com.banque.absences.repository.RegleAffectationRepository;
import com.banque.absences.security.KeycloakClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * RG-11 — Tests unitaires de {@link CircuitDeterminationService}.
 *
 * Stratégie :
 *  - {@link ClaimReaderService} mocké pour retourner le grade voulu
 *  - {@link RegleAffectationRepository} mocké pour retourner le circuit correspondant
 *  - {@link SecurityContextHolder} alimenté directement (comme dans ClaimReaderServiceTest)
 *  - Aucun contexte Spring, aucune BDD
 */
class CircuitDeterminationServiceTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private ClaimReaderService            claimReaderService;
    private RegleAffectationRepository    regleRepo;
    private CircuitDeterminationService   service;

    // ── Circuits de référence ─────────────────────────────────────────────────

    private ModeleCircuit circuitAgent;
    private ModeleCircuit circuitManager;
    private ModeleCircuit circuitReseau;

    @BeforeEach
    void setUp() {
        claimReaderService = mock(ClaimReaderService.class);
        regleRepo          = mock(RegleAffectationRepository.class);
        service            = new CircuitDeterminationService(claimReaderService, regleRepo);

        circuitAgent   = creerCircuit("Circuit Agent");
        circuitManager = creerCircuit("Circuit Manager — Congé Annuel");
        circuitReseau  = creerCircuit("Circuit Réseau — Congé Annuel");

        // Mapping grade → circuit (RG-11)
        stub("AGENT",          Optional.of(regles(circuitAgent)));
        stub("MANAGER",        Optional.of(regles(circuitManager)));
        stub("CHEF_PROCESSUS", Optional.of(regles(circuitReseau)));
        stub("DA",             Optional.of(regles(circuitReseau)));
        stub("STAGIAIRE",      Optional.empty());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Source de données paramétrée ──────────────────────────────────────────

    static Stream<Arguments> gradeCircuitMapping() {
        return Stream.of(
                Arguments.of("AGENT",          "Circuit Agent",                  true),
                Arguments.of("MANAGER",        "Circuit Manager — Congé Annuel", true),
                Arguments.of("CHEF_PROCESSUS", "Circuit Réseau — Congé Annuel",  true),
                Arguments.of("DA",             "Circuit Réseau — Congé Annuel",  true),
                Arguments.of("STAGIAIRE",      null,                              false)
        );
    }

    // ── Tests paramétrés ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "Grade {0} → circuit ''{1}'' présent={2}")
    @MethodSource("gradeCircuitMapping")
    @DisplayName("RG-11 — determinerCircuitApplicable retourne le bon circuit selon le grade")
    void determinerCircuit(String grade, String nomCircuitAttendu, boolean present) {
        injecterGrade(grade);
        when(claimReaderService.lireClaimGrade()).thenReturn(grade);

        DemandeAbsence demande = new DemandeAbsence();

        Optional<ModeleCircuit> result = service.determinerCircuitApplicable(demande);

        if (present) {
            assertThat(result).isPresent();
            assertThat(result.get().getNom()).isEqualTo(nomCircuitAttendu);
        } else {
            assertThat(result).isEmpty();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ModeleCircuit creerCircuit(String nom) {
        ModeleCircuit c = new ModeleCircuit();
        c.setNom(nom);
        c.setTypeAbsenceCible(TypeAbsence.CONGE_ANNUEL);
        return c;
    }

    private static RegleAffectation regles(ModeleCircuit circuit) {
        EtapeModeleCircuit etape = new EtapeModeleCircuit();
        etape.setModeleCircuit(circuit);
        etape.setOrdre(1);
        etape.setLibelle("Étape test");

        RegleAffectation regle = new RegleAffectation();
        regle.setEtapeModeleCircuit(etape);
        regle.setMecanisme(MecanismeResolution.HIERARCHIQUE);
        regle.setPriorite(1);
        return regle;
    }

    private void stub(String grade, Optional<RegleAffectation> resultat) {
        when(regleRepo.findFirstByGradeDeclencheurOrderByPrioriteAsc(grade)).thenReturn(resultat);
    }

    private static void injecterGrade(String grade) {
        Jwt jwt = Jwt.withTokenValue("token-test")
                .header("alg", "RS256")
                .subject("employe-test")
                .issuer("https://keycloak.banque.com/realms/afb")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim(KeycloakClaims.REALM_ACCESS,
                        Map.of(KeycloakClaims.ROLES, List.of("EMPLOYE")))
                .claim(KeycloakClaims.CLAIM_GRADE, grade)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
