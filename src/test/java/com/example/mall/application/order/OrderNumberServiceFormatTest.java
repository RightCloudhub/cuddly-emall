package com.example.mall.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OrderNumberServiceFormatTest {

    private static final Pattern ASKFLOW_REGEX = Pattern.compile("[A-Z]{2,4}\\d{6,}");

    @Test
    void formatMatchesMallOrderFormat() {
        assertThat(OrderNumberService.format(1L)).isEqualTo("MO000000000001");
        assertThat(OrderNumberService.format(123_456_789_012L)).isEqualTo("MO123456789012");
    }

    @Test
    void formattedNumberMatchesAskFlowSearchOrderRegex() {
        // AskFlow's tools.py regex: [A-Z]{2,4}\d{6,}
        assertThat(ASKFLOW_REGEX.matcher(OrderNumberService.format(1L)).find()).isTrue();
        assertThat(ASKFLOW_REGEX.matcher(OrderNumberService.format(42L)).find()).isTrue();
    }

    @Test
    void formattedNumberMatchesMallSpecificFormat() {
        Pattern mallFormat = Pattern.compile("^MO\\d{12}$");
        for (long v : new long[] {1L, 999L, 1_234_567_890L, 999_999_999_999L}) {
            assertThat(mallFormat.matcher(OrderNumberService.format(v)).matches())
                    .as("value " + v)
                    .isTrue();
        }
    }

    @Test
    void overflowingSequenceIsRejected() {
        assertThatThrownBy(() -> OrderNumberService.format(1_000_000_000_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeSequenceIsRejected() {
        assertThatThrownBy(() -> OrderNumberService.format(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
