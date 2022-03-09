package cn.dev666.component.error.email.notice.mail;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;

@Slf4j
@AllArgsConstructor
public class MailService {

    private static final String EXCEPTION_MAIL_TEMPLATE;

    static {
        EXCEPTION_MAIL_TEMPLATE =
                "\n" +
                "机器IP  ：%s \n" +
                "应用名称 ：%s \n" +
                "请求URL ：%s \n" + "\n" +
                "异常时间 ：%s \n" +
                "异常频次 ：%s \n" + "\n" +
                "异常详情 ：%s";
    }

    private final MailSender mailSender;

    private final String profiles;

    private final String mailFrom;

    private final String applicationName;


    public void exceptionSend(Exception ex, String url, long time, String frequency, String[] cc, String... to) {
        long start = System.currentTimeMillis();
        try {
            log.info("start send exception（{}） mail to {} ", ex.getClass().getSimpleName(), Arrays.toString(to));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(profiles + " 环境异常报警：" + ex.getClass().getSimpleName());
            String context = String.format(EXCEPTION_MAIL_TEMPLATE, getLocalIp(), applicationName, url,
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time)), frequency, getStackTrace(ex));
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
            log.info("end send exception mail, cost time {} ms ", System.currentTimeMillis() - start);
        }
    }

    /**
     * 获取堆栈信息
     */
    private String getStackTrace(Throwable throwable){
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
            return sw.toString();
        }
    }

    /**
     * 获取当前机器的IP
     */
    public static String getLocalIp() {
        try {
            InetAddress candidateAddress = null;
            // 遍历所有的网络接口
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) {
                NetworkInterface anInterface = interfaces.nextElement();
                // 在所有的接口下再遍历IP
                for (Enumeration<InetAddress> addresses = anInterface.getInetAddresses(); addresses.hasMoreElements();) {
                    InetAddress address = addresses.nextElement();
                    // 排除loopback类型地址
                    if (!address.isLoopbackAddress()) {
                        if (address.isSiteLocalAddress()) {
                            // 如果是site-local地址，就是它了
                            return address.getHostAddress();
                        } else if (candidateAddress == null) {
                            // site-local类型的地址未被发现，先记录候选地址
                            candidateAddress = address;
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                return candidateAddress.getHostAddress();
            }
            // 如果没有发现 non-loopback地址.只能用最次选的方案
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
                return "";
            }
            return jdkSuppliedAddress.getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }
}
