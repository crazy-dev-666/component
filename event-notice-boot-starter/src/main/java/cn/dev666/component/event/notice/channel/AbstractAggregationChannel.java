package cn.dev666.component.event.notice.channel;

import cn.dev666.component.event.notice.event.NoticeEvent;
import cn.dev666.component.event.notice.utils.DataUtils;
import freemarker.template.Template;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractAggregationChannel<E extends NoticeEvent> extends AbstractChannel<E> implements AggregationChannel<E> {

    @Override
    public boolean notice(List<E> list) throws Exception {

        if (list.size() == 1){
           return notice(list.get(0));
        }

        Template template = getMultipleTemplate();
        Map<String, Object> map = new HashMap<>();
        String title = DataUtils.getProfiles() + "环境，" + list.size() + "条报警信息聚合";
        map.put("title",title);
        map.put("app", DataUtils.getInfo());
        map.put("multipleEvent", list);
        StringWriter sw = new StringWriter();
        template.process(map, sw);
        return sendNotice(title, sw.toString());
    }

    public abstract Template getMultipleTemplate() throws Exception;
}
