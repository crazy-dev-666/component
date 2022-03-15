package cn.dev666.component.error.notice.content;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DefaultContentResult implements ContentResult {

    private String title;

    private String content;

    @Override
    public String simpleFormat() {
        return "标题：" + title + " \n\n "
                + content;
    }
}
