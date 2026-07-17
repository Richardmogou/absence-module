package com.banque.absences.exception;

import com.banque.absences.service.DureeInsuffisanteMissionLongueException;
import com.banque.absences.service.EtapeInvalideException;
import com.banque.absences.service.EtapeVerrouilleeException;
import com.banque.absences.service.JustificatifRequisException;
import com.banque.absences.service.ModificationImpossibleException;
import com.banque.absences.service.MotifInconnuException;
import com.banque.absences.service.MotifRequisException;
import com.banque.absences.service.OverrideCibleInterditeException;
import com.banque.absences.service.ProlongationNonAutoriseeException;
import com.banque.absences.service.ReseauNonRenseigneException;
import com.banque.absences.service.SuppressionImpossibleException;
import com.banque.absences.service.TransitionIllegaleException;
import com.banque.absences.service.ValidateurNonAutoriseException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccesDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("ACCES_REFUSE", e.getMessage()));
    }

    @ExceptionHandler(CircuitNonDetermineException.class)
    public ResponseEntity<ApiError> handleCircuitNonDetermine(CircuitNonDetermineException exception) {
        ApiError error = new ApiError(
                "CIRCUIT_NON_DETERMINE",
                "Le grade porte par le jeton ne correspond a aucune regle d'affectation");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(DoublonDetecteException.class)
    public ResponseEntity<ApiError> handleDoublonDetecte(DoublonDetecteException exception) {
        ApiError error = new ApiError("DOUBLON_DETECTE", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * US-ADM-004 — Doublon validateur détecté à la création du circuit.
     * 409 DOUBLON_VALIDATEUR_DETECTE ≠ 422 EMPLOYE_TYPE_INTROUVABLE :
     * ici le contrôle s'est exécuté et a identifié les deux étapes en conflit.
     */
    @ExceptionHandler(DoublonValidateurDetecteException.class)
    public ResponseEntity<Map<String, Object>> handleDoublonValidateurDetecte(
            DoublonValidateurDetecteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code",                      "DOUBLON_VALIDATEUR_DETECTE",
                "circuitId",                 e.getCircuitId().toString(),
                "etapeHierarchiqueId",       e.getEtapeHierarchiqueId().toString(),
                "etapeRoleFixeRedondanteId", e.getEtapeRoleFixeRedondanteId().toString(),
                "choixPossibles",            new String[]{"SUPPRIMER", "CONSERVER"}));
    }

    /**
     * US-ADM-006 — Aucun employé représentatif du grade déclenché trouvé.
     * 422 EMPLOYE_TYPE_INTROUVABLE ≠ 409 DOUBLON_VALIDATEUR_DETECTE :
     * ici le contrôle anti-doublon n'a pas pu s'exécuter faute de données.
     */
    @ExceptionHandler(EmployeTypeIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> handleEmployeTypeIntrouvable(
            EmployeTypeIntrouvableException e) {
        return ResponseEntity.status(422).body(Map.of(
                "code", "EMPLOYE_TYPE_INTROUVABLE",
                "message", e.getMessage(),
                "gradeDeclencheur", e.getGradeDeclencheur()));
    }

    @ExceptionHandler(ValidateurNonAutoriseException.class)
    public ResponseEntity<ApiError> handleValidateurNonAutorise(ValidateurNonAutoriseException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("VALIDATEUR_NON_AUTORISE", e.getMessage()));
    }

    @ExceptionHandler(DureeInsuffisanteMissionLongueException.class)
    public ResponseEntity<ApiError> handleDureeInsuffisante(DureeInsuffisanteMissionLongueException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("DUREE_INSUFFISANTE_MISSION_LONGUE", e.getMessage()));
    }

    @ExceptionHandler(com.banque.absences.service.DureeInsuffisanteCongeAnnuelException.class)
    public ResponseEntity<ApiError> handleDureeInsuffisanteCongeAnnuel(com.banque.absences.service.DureeInsuffisanteCongeAnnuelException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("DUREE_INSUFFISANTE_CONGE_ANNUEL", e.getMessage()));
    }

    @ExceptionHandler(MotifRequisException.class)
    public ResponseEntity<ApiError> handleMotifRequis(MotifRequisException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("MOTIF_REQUIS", e.getMessage()));
    }

    @ExceptionHandler(MotifInconnuException.class)
    public ResponseEntity<ApiError> handleMotifInconnu(MotifInconnuException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("MOTIF_INCONNU", e.getMessage()));
    }

    /**
     * CDCT EX-9 — cible de forçage refusée. 422 et non 409 TRANSITION_ILLEGALE : la cible est
     * interdite sur cet endpoint quel que soit l'état de la demande, ce n'est pas un conflit
     * d'état.
     */
    @ExceptionHandler(OverrideCibleInterditeException.class)
    public ResponseEntity<ApiError> handleOverrideCibleInterdite(OverrideCibleInterditeException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("OVERRIDE_CIBLE_INTERDITE", e.getMessage()));
    }

    @ExceptionHandler(ReseauNonRenseigneException.class)
    public ResponseEntity<ApiError> handleReseauNonRenseigne(ReseauNonRenseigneException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("RESEAU_NON_RENSEIGNE", e.getMessage()));
    }

    @ExceptionHandler(JustificatifRequisException.class)
    public ResponseEntity<ApiError> handleJustificatifRequis(JustificatifRequisException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("JUSTIFICATIF_REQUIS", e.getMessage()));
    }

    @ExceptionHandler(ProlongationNonAutoriseeException.class)
    public ResponseEntity<ApiError> handleProlongationNonAutorisee(ProlongationNonAutoriseeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("PROLONGATION_NON_AUTORISEE", e.getMessage()));
    }

    @ExceptionHandler(TransitionIllegaleException.class)
    public ResponseEntity<ApiError> handleTransitionIllegale(TransitionIllegaleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("TRANSITION_ILLEGALE", e.getMessage()));
    }

    @ExceptionHandler(ModificationImpossibleException.class)
    public ResponseEntity<ApiError> handleModificationImpossible(ModificationImpossibleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("MODIFICATION_IMPOSSIBLE", e.getMessage()));
    }

    @ExceptionHandler(SuppressionImpossibleException.class)
    public ResponseEntity<ApiError> handleSuppressionImpossible(SuppressionImpossibleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("SUPPRESSION_IMPOSSIBLE", e.getMessage()));
    }

    @ExceptionHandler(EtapeInvalideException.class)
    public ResponseEntity<ApiError> handleEtapeInvalide(EtapeInvalideException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("ETAPE_INVALIDE", e.getMessage()));
    }

    @ExceptionHandler(EtapeVerrouilleeException.class)
    public ResponseEntity<ApiError> handleEtapeVerrouillee(EtapeVerrouilleeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("ETAPE_VERROUILLEE", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("REQUETE_INVALIDE", e.getMessage()));
    }

    /**
     * Échec de validation du payload (@Valid @RequestBody) : sans ce handler, Spring
     * renvoie sa réponse 400 par défaut, sans champ {@code code}, ce qui empêche le
     * front d'afficher un message précis. On agrège ici les messages des champs invalides.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(" ; "));
        if (message.isBlank()) {
            message = "Le formulaire contient des champs invalides.";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERREUR", message));
    }

    /**
     * Entité introuvable (ex : demande référencée lors de la soumission ou de l'ajout
     * d'un justificatif). Sans ce handler, l'exception remonte en 500 sans {@code code}.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("DEMANDE_INTROUVABLE", e.getMessage()));
    }

    public record ApiError(String code, String message) {
    }
}
