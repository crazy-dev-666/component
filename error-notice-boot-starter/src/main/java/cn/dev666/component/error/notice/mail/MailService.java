package cn.dev666.component.error.notice.mail;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.util.Arrays;
import java.util.Date;

@Slf4j
@AllArgsConstructor
public class MailService {

    private final MailSender mailSender;

    private final String mailFrom;

    public void send(String[] to, String[] cc, String title, String context) {
        long start = System.currentTimeMillis();
        try {
            log.info("start send error notice mail to {} ", Arrays.toString(to));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(title);
            message.setText(context);
            message.setFrom(mailFrom);
            message.setSentDate(new Date());
            if (cc != null && cc.length > 0){
                message.setCc(cc);
            }
            mailSender.send(message);
        } catch (Exception e) {
            log.error("send exception mail error", e);
        } finally {
            log.info("end send error notice mail, cost time {} ms ", System.currentTimeMillis() - start);
        }
    }

}
