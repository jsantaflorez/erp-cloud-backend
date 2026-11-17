package com.erp.erp_cloud.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SiewinCodesTest {

    @Test
    @DisplayName("levelSlot: 0-99 as two digits, 9999 as 99, anything else not representable")
    void levelSlot() {
        assertThat(SiewinCodes.levelSlot(5)).isEqualTo("05");
        assertThat(SiewinCodes.levelSlot(99)).isEqualTo("99");
        assertThat(SiewinCodes.levelSlot(9999)).isEqualTo("99");
        assertThat(SiewinCodes.levelSlot(100)).isNull();
        assertThat(SiewinCodes.levelSlot(1234)).isNull();
    }

    @Test
    @DisplayName("erpCodeFromDashed: SIEWIN printed codes to ERP codes")
    void erpCodeFromDashed() {
        assertThat(SiewinCodes.erpCodeFromDashed("11")).isEqualTo("11");
        assertThat(SiewinCodes.erpCodeFromDashed("11-05")).isEqualTo("1105");
        assertThat(SiewinCodes.erpCodeFromDashed("11-05-05-01")).isEqualTo("11050501");
        assertThat(SiewinCodes.erpCodeFromDashed("11-05-05-0001")).isEqualTo("11050501");
        assertThat(SiewinCodes.erpCodeFromDashed("22-05-05-9999")).isEqualTo("22050599");
        assertThat(SiewinCodes.erpCodeFromDashed("13-28-05-0001-0002")).isEqualTo("1328050102");
        assertThat(SiewinCodes.erpCodeFromDashed("22-05-05-1234")).isNull();
        assertThat(SiewinCodes.erpCodeFromDashed("79762603")).isEqualTo("79762603");
        assertThat(SiewinCodes.usesCatchAll("22-05-05-9999")).isTrue();
        assertThat(SiewinCodes.usesCatchAll("22-05-05-0014")).isFalse();
    }
}
