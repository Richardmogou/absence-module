package com.banque.absences.exception;

public class EmployeTypeIntrouvableException extends RuntimeException {

    private final String gradeDeclencheur;

    public EmployeTypeIntrouvableException(String gradeDeclencheur) {
        super("Aucun employe representatif du grade " + gradeDeclencheur
                + " n'a ete trouve dans la plateforme d'identite");
        this.gradeDeclencheur = gradeDeclencheur;
    }

    public String getGradeDeclencheur() {
        return gradeDeclencheur;
    }
}
