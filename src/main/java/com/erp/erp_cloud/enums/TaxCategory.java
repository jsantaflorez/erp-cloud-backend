package com.erp.erp_cloud.enums;

/**
 * The three structural, report-driving buckets a Tax can belong to.
 *
 * REPLACES the earlier {@code TaxType} enum (2026-10-14, superseded the
 * same day, never committed) that tried to enumerate every SPECIFIC tax
 * name (IVA_GENERADO, RETEFUENTE, RETEICA, ...). That approach meant a
 * Colombian tax reform inventing a new concept (impuestos saludables,
 * al carbono, a plasticos, a bebidas azucaradas, etc.) would require a
 * backend code change + redeploy just to classify it -- exactly what
 * the user explicitly asked to avoid (2026-10-15): "aseguremos que el
 * catalogo de impuestos no quede atado a un grupo cerrado de tipos en
 * codigo duro".
 *
 * The fix: split "what report/behavior does this feed" (this enum --
 * genuinely stable, a tax reform doesn't invent a FOURTH accounting
 * mechanism) from "what is this tax actually called" (Tax.type, now
 * free text again -- see its own javadoc). Specifically:
 *
 * - RETENCION: ReteFuente, ReteICA, ReteIVA, or any future withholding
 *   concept. The Informe de Retenciones / certificate generator
 *   (planned Fase 4) filters on this category, then sub-groups by the
 *   free-text Tax.type (e.g. "RETEFUENTE" vs "RETEICA") since those
 *   are legally distinct concepts with no other structural difference.
 * - IVA: both IVA Generado (output, on sales) and IVA Descontable
 *   (input/deductible, on purchases) -- the Informe de IVA (planned
 *   Fase 3) tells them apart using Tax.sign ("C" = Generado, a
 *   liability; "D" = Descontable, a contra-liability), not a fourth
 *   category value, since every Tax already requires a sign to know
 *   which side of a journal line to post to.
 * - OTRO: anything that isn't a retention or VAT and doesn't (yet)
 *   feed a dedicated report -- impoconsumo, impuestos saludables/verdes
 *   (plasticos, carbono, bebidas azucaradas), or any future concept.
 *   Still posts normally via Tax.account/rate/sign/minimumBase exactly
 *   like every other Tax; it simply isn't grouped into Fase 3/4 today.
 *
 * JournalEntryService.handleTax() -- the code that actually posts a
 * tax-linked journal entry line -- never reads this field (or
 * Tax.type): it only needs account/rate/sign/requiresBase/minimumBase.
 * category/type exist purely for the fiscal reports to classify
 * already-posted data, so a brand-new Tax of any category never needs
 * a code change to be posted correctly.
 */
public enum TaxCategory {
    RETENCION,
    IVA,
    OTRO
}
