package com.erp.erp_cloud.dto;

import com.erp.erp_cloud.enums.ChartTemplateType;

/**
 * Optional request body for POST /api/v1/chart-of-accounts/import-template.
 *
 * templateType is OPTIONAL: when the target company already has
 * Company.chartTemplate configured, this can be omitted entirely and that
 * value is used. It only needs to be sent the first time a company adopts
 * a template (it is then persisted onto Company.chartTemplate for future
 * imports/suggestions) -- see ChartAccountTemplateImportService for the
 * exact resolution rule and the CHART_TEMPLATE_MISMATCH guard.
 */
public record ImportChartTemplateRequest(ChartTemplateType templateType) {
}
