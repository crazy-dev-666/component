package cn.dev666.component.error.notice.channel;

import cn.dev666.component.error.notice.config.ErrorNoticeProperties;
import cn.dev666.component.error.notice.content.ContentResult;
import cn.dev666.component.error.notice.content.DefaultContentResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.util.Date;
import java.util.List;

@Slf4j
@AllArgsConstructor
public class DefaultMailChannel implements AggregationChannel<DefaultContentResult> {

    private final MailSender mailSender;

    private final String mailFrom;

    private final ErrorNoticeProperties.EmailProperties properties;

    @Override
    public boolean notice(DefaultContentResult cr) {
        String[] to = properties == null ? null : properties.getTo();
        if (to == null || to.length == 0) {
            log.debug("{} 邮件发送失败，没有配置收件人", cr.getTitle());
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(cr.getTitle());
        message.setText(cr.getContent());
        message.setFrom(mailFrom);
        message.setSentDate(new Date());

        String[] cc = properties.getCc();
        if (cc != null && cc.length > 0){
            message.setCc(cc);
        }
        mailSender.send(message);
        return true;
    }

    @Override
    public DefaultContentResult resultAggregation(String profiles, List<ContentResult> list) {

        int num = 0;
        StringBuilder sb = new StringBuilder();

        for (ContentResult contentResult : list) {
            num++;
            sb.append(contentResult.simpleFormat())
                    .append(" \n\n ------------------------------------- \n\n ");
        }
        String title = profiles + "环境，" + num + "条报警信息聚合";
        return new DefaultContentResult(title, sb.toString());
    }
}
