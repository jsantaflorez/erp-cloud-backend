package com.erp.erp_cloud.service;

/**
 * NEW (2026-11-17): reglas compartidas para convertir un código de cuenta
 * de SIEWIN al código del ERP. Las usan el importador de PUC
 * (SiewinPucImportService, desde los niveles numéricos) y el de saldos de
 * apertura (SiewinBalanceImportService, desde el código impreso con
 * guiones), para que ambos lleguen SIEMPRE al mismo código.
 *
 * El ERP arma cada nivel con 2 dígitos (regla +2 por nivel de
 * ChartOfAccountService). SIEWIN guarda del nivel 4 en adelante con 4
 * dígitos ("11-05-05-0001"), y en la práctica nunca pasa de 99 salvo en
 * las cuentas "Varios", que SIEWIN numera 9999. Decisión del usuario
 * (2026-11-17): 9999 se convierte en 99, la convención usual de "varios".
 * Si esa cuenta 99 ya existe con otra cuenta, cada importador lo reporta
 * como choque, nunca la sobreescribe ni elige otro número. Cualquier otro
 * valor mayor que 99 sigue sin poder representarse (null).
 */
public final class SiewinCodes {

    /** Valor que SIEWIN usa para la cuenta "Varios" de un nivel. */
    public static final int CATCH_ALL_LEVEL = 9999;
    /** Tramo que recibe la cuenta "Varios" en el ERP. */
    public static final String CATCH_ALL_SLOT = "99";

    private SiewinCodes() {
    }

    /** Tramo de 2 dígitos para un nivel de SIEWIN, o null si no es representable. */
    public static String levelSlot(int value) {
        if (value == CATCH_ALL_LEVEL) {
            return CATCH_ALL_SLOT;
        }
        if (value >= 0 && value <= 99) {
            return String.format("%02d", value);
        }
        return null;
    }

    /**
     * Código del ERP a partir del código impreso por SIEWIN con guiones
     * ("11", "11-05", "11-05-05-01", "11-05-05-0001", "22-05-05-9999").
     * Cada tramo se lee como número (así "01" y "0001" dan lo mismo).
     * Devuelve null si algún tramo no es representable.
     */
    public static String erpCodeFromDashed(String dashed) {
        if (dashed == null || dashed.isBlank()) {
            return null;
        }
        String[] parts = dashed.trim().split("-");
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                return null;
            }
            int value = Integer.parseInt(part);
            if (i == 0) {
                // El primer tramo es el grupo de 2 dígitos tal cual ("11").
                code.append(part.length() == 1 ? part : String.format("%02d", value));
                continue;
            }
            String slot = levelSlot(value);
            if (slot == null) {
                return null;
            }
            code.append(slot);
        }
        return code.toString();
    }

    /** true si el código impreso usa el valor "Varios" (9999) en algún tramo. */
    public static boolean usesCatchAll(String dashed) {
        if (dashed == null) {
            return false;
        }
        for (String part : dashed.split("-")) {
            String p = part.trim();
            if (!p.isEmpty() && p.chars().allMatch(Character::isDigit) && Integer.parseInt(p) == CATCH_ALL_LEVEL) {
                return true;
            }
        }
        return false;
    }
}
