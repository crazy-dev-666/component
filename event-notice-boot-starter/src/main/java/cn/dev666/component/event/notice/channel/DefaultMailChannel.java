package cn.dev666.component.event.notice.channel;

import cn.dev666.component.event.notice.config.EventNoticeProperties;
import cn.dev666.component.event.notice.event.DefaultNoticeEvent;
import freemarker.template.Template;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.util.Date;

@Slf4j
@AllArgsConstructor
public class DefaultMailChannel extends AbstractAggregationChannel<DefaultNoticeEvent> {

    private final MailSender mailSender;

    private final String mailFrom;

    private final EventNoticeProperties.EmailProperties properties;

    @Override
    public Template getSingleTemplate() throws Exception{
        return configuration.getTemplate("singleEventMail.ftl");
    }

    @Override
    public Template getMultipleTemplate() throws Exception {
        return configuration.getTemplate("multipleEventMail.ftl");
    }

    @Override
    public boolean sendNotice(String title, String content) {
        String[] to = properties == null ? null : properties.getTo();
        if (to == null || to.length == 0) {
            log.debug("{} 邮件发送失败，没有配置收件人", title);
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(title);
        message.setText(content);
        message.setFrom(mailFrom);
        message.setSentDate(new Date());

        String[] cc = properties.getCc();
        if (cc != null && cc.length > 0){
            message.setCc(cc);
        }
        mailSender.send(message);
        return true;
    }
}
