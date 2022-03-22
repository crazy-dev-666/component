package cn.dev666.component.event.notice.channel;

import cn.dev666.component.event.notice.event.NoticeEvent;
import cn.dev666.component.event.notice.utils.DataUtils;
import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractChannel<E extends NoticeEvent> implements Channel<E> {

    protected static Configuration configuration;

    static {
        configuration = new Configuration(Configuration.getVersion());
        configuration.setDefaultEncoding("UTF-8");
        configuration.setClassLoaderForTemplateLoading(AbstractChannel.class.getClassLoader(), "/template/");
    }

    @Override
    public boolean notice(E event) throws Exception {
        Template template = getSingleTemplate();
        Map<String, Object> map = new HashMap<>();
        String title = DataUtils.getProfiles() + "环境，" + event.getTitle();
        map.put("title",title);
        map.put("app", DataUtils.getInfo());
        map.put("event", event);
        StringWriter sw = new StringWriter();
        template.process(map, sw);
        return sendNotice(title, sw.toString());
    }

    public abstract Template getSingleTemplate() throws Exception;

    public abstract boolean sendNotice(String title, String content) throws Exception;
}
