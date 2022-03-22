### ${title}
>
> 机器IP  ：${app.localIp}
> 进程ID  : ${app.processId}
> 应用名称 ：${app.applicationName}
>
<#list multipleEvent as event>
> 标题    ：${event.title}
> 通知时间 ：${event.time}
> 通知频次 ：${event.frequency}
>
<#list event.argsMap as arg>
> ${arg.key} : ${arg.value}
</#list>
---------------------------------------------
</#list>