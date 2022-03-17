package cn.dev666.component.event.notice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DealEventResult {
    private boolean remindFlag;
    private String frequency;
}
