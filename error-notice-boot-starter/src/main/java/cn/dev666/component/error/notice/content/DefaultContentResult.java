package cn.dev666.component.error.notice.content;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DefaultContentResult implements ContentResult {

    private String title;

    private String content;
}
