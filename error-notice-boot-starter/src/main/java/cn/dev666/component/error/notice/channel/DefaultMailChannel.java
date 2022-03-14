package cn.dev666.component.error.notice.channel;

import cn.dev666.component.error.notice.config.ErrorNoticeProperties;
import cn.dev666.component.error.notice.content.ContentResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.util.Date;

@Slf4j
@AllArgsConstructor
public class DefaultMailChannel implements Channel {

    private final MailSender mailSender;

    private final String mailFrom;

    private final ErrorNoticeProperties.EmailProperties properties;

    @Override
    public boolean notice(ContentResult cr) {
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
}
