package cn.dev666.component.event.notice.channel;

import cn.dev666.component.event.notice.config.EventNoticeProperties;
import cn.dev666.component.event.notice.event.DefaultNoticeEvent;
import cn.dev666.component.event.notice.utils.DataUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.util.Date;
import java.util.List;

@Slf4j
@AllArgsConstructor
public class DefaultMailChannel implements AggregationChannel<DefaultNoticeEvent> {

    private final MailSender mailSender;

    private final String mailFrom;

    private final EventNoticeProperties.EmailProperties properties;

    @Override
    public boolean notice(DefaultNoticeEvent event) {
        return sendNotice(event.getTitle(), event.getContentWithApplcationInfo().toString());
    }

    @Override
    public boolean notice(List<DefaultNoticeEvent> list) {

        if (list.size() == 1){
            return notice(list.get(0));
        }

        int num = 0;
        StringBuilder sb = new StringBuilder();
        sb.append(DataUtils.getApplicationInfo());

        for (DefaultNoticeEvent event : list) {
            num++;
            sb.append(" \n\n ------------------------------------- \n\n ")
                .append(event.getTitle()).append(" \n ")
                .append(event.getContent());
        }
        String title = DataUtils.getProfiles() + "环境，" + num + "条报警信息聚合";
        return sendNotice(title, sb.toString());
    }


    protected boolean sendNotice(String title, String content) {
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
