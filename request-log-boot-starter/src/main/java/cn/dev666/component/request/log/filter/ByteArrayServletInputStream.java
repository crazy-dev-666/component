package cn.dev666.component.request.log.filter;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;

public class ByteArrayServletInputStream extends ServletInputStream {

    private ByteArrayInputStream byteArrayInputStream;

    ByteArrayServletInputStream(ByteArrayInputStream byteArrayInputStream) {
        this.byteArrayInputStream = byteArrayInputStream;
    }

    @Override
    public int read() {
        return byteArrayInputStream.read();
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void setReadListener(ReadListener readListener) {

    }
}
