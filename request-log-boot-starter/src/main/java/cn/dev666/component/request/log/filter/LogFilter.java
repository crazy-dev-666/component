package cn.dev666.component.request.log.filter;

import cn.dev666.component.request.log.enums.LogRequestLevel;
import cn.dev666.component.request.log.enums.LogResponseLevel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.zip.GZIPInputStream;

@Slf4j
public class LogFilter extends OncePerRequestFilter implements Ordered {

	private final int requestOmitLength;

	private final int order;

	private final int responseOmitLength;

    private LogRequestLevel requestLevel;

	private LogResponseLevel responseLevel;

    private Set<String> headers;

	private long slowRequestThresholdMillis;

	public LogFilter(int order, LogRequestLevel requestLevel, LogResponseLevel responseLevel,
                     DataSize requestOmitLength, DataSize responseOmitLength,
                     Set<String> headers, Duration slowRequestThreshold) {
		this.order = order;
		this.requestLevel = requestLevel;
		this.responseLevel = responseLevel;
		this.requestOmitLength = (int)requestOmitLength.toBytes();
		this.responseOmitLength = (int)responseOmitLength.toBytes();
		if (headers != null && headers.size() > 0) {
			this.headers = new HashSet<>(headers.size());
			for (String header : headers) {
				this.headers.add(header.toLowerCase());
			}
		}else {
			this.headers = Collections.emptySet();
		}
		this.slowRequestThresholdMillis = slowRequestThreshold.toMillis();
	}

	@Override
	public int getOrder() {
		return order;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
	    long requestTime = System.currentTimeMillis();

		if (!LogRequestLevel.NOTHING.equals(this.requestLevel)) {
			StringBuilder builder = new StringBuilder();
			request = dealRequestInfo(builder, request);
			log.info("{}", builder.toString());
		}

		if (LogResponseLevel.NOTHING.equals(this.responseLevel)){
			filterChain.doFilter(request, response);
			return;
		}

		boolean logBody = !LogResponseLevel.ERROR_NOBODY.equals(this.responseLevel) &&
							!LogResponseLevel.SLOW_ERROR_NOBODY.equals(this.responseLevel);
		ByteArrayOutputStream os = null;
		if (logBody){
			final ByteArrayOutputStream finalByteArrayOutputStream = new ByteArrayOutputStream();
			response = new HttpServletResponseWrapper(response) {
				@Override
				public ServletOutputStream getOutputStream() throws IOException {
					return new TeeServletOutputStream(super.getOutputStream(), finalByteArrayOutputStream);
				}
			};
			os = finalByteArrayOutputStream;
		}

		filterChain.doFilter(request, response);
		long costTime = System.currentTimeMillis() - requestTime;
		HttpStatus httpStatus = HttpStatus.valueOf(response.getStatus());
		String responseInfo = dealResponseInfo(httpStatus, logBody, request, response, costTime, os);

		if (httpStatus.isError()) {
            log.error("{}", responseInfo);
        }else {
			if (costTime > slowRequestThresholdMillis && (
					LogResponseLevel.ALL.equals(this.responseLevel) ||
					LogResponseLevel.SLOW_ERROR.equals(this.responseLevel) ||
							LogResponseLevel.SLOW_ERROR_NOBODY.equals(this.responseLevel))){

				log.warn("{}", responseInfo);

			}else if (LogResponseLevel.ALL.equals(this.responseLevel)){

				log.info("{}", responseInfo);

			}
        }
	}

	private String dealResponseInfo(HttpStatus httpStatus, boolean logBody, HttpServletRequest request, HttpServletResponse response, long costTime, ByteArrayOutputStream os) {
		StringBuilder builder = new StringBuilder();
		builder.append(request.getMethod()).append(" ").append(request.getRequestURI()).append(", ")
				.append(httpStatus.value()).append(" ").append(httpStatus.getReasonPhrase()).append(", ")
				.append(costTime).append(" ms");
		if (logBody) {
			String responseBody = "";
			// json xml 输出响应体
			String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE);
			if (contentType != null && (contentType.startsWith(MediaType.APPLICATION_JSON_VALUE) || contentType.startsWith(MediaType.APPLICATION_XML_VALUE))) {
				responseBody = os.toString();
			}

			if (StringUtils.hasText(responseBody) && responseBody.length() > this.responseOmitLength){
				responseBody = responseBody.substring(0, this.responseOmitLength) + "...(共" + responseBody.length() + "字节)";
			}

			// 其他类型默认按二进制流处理
			if (!StringUtils.hasLength(responseBody) && os.size() > 0) {
				responseBody = "Binary data" + "(" + os.size() + " byte)";
			}

			if (StringUtils.hasLength(responseBody)) {
				builder.append("\n\n").append(responseBody).append("\n");
			}
		}

		return builder.toString();
	}

	private HttpServletRequest dealRequestInfo(StringBuilder builder, HttpServletRequest request) throws UnsupportedEncodingException {
		builder.append(request.getMethod()).append(' ').append(request.getRequestURI());

		if (RequestMethod.GET.name().equalsIgnoreCase(request.getMethod())
				&& StringUtils.hasText(request.getQueryString())){
			builder.append("?").append(request.getQueryString());
		}

		if (LogRequestLevel.URL.equals(this.requestLevel)){
			return request;
		}

		boolean hasHeader = false;

		if (!LogRequestLevel.URL_BODY.equals(this.requestLevel)) {
			Enumeration<String> headerNames = request.getHeaderNames();
			boolean allFlag = !LogRequestLevel.URL_BODY_SOME_HEADER.equals(this.requestLevel);

			StringBuilder headerBuilder = new StringBuilder();

			while (headerNames.hasMoreElements()) {
				String headerName = headerNames.nextElement();
				if (allFlag || logHeader(headerName)) {
					Enumeration<String> headers = request.getHeaders(headerName);
					while (headers.hasMoreElements()) {
						String value = headers.nextElement();
						headerBuilder.append(headerName).append(": ").append(value).append('\n');
					}
				}
			}

			if (headerBuilder.length() > 0){
				hasHeader = true;
				builder.append("\n\n").append(headerBuilder);
			}
		}

		String requestContentType = request.getHeader(HttpHeaders.CONTENT_TYPE);

		String requestBody = "";

		if (requestContentType != null){
			// 普通表单提交
			if (requestContentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)){
				requestBody = request.getParameterMap().toString();
			// 文件表单提交
			}else if (requestContentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)){
				requestBody = getFormParam(request);
			// xml/json/text
			}else if (requestContentType.startsWith(MediaType.APPLICATION_JSON_VALUE)
					|| requestContentType.startsWith(MediaType.APPLICATION_XML_VALUE)
					|| requestContentType.startsWith("text")){
				requestBody = getRequestBody(request);
				String encoding = request.getCharacterEncoding();
				byte[] bytes;
				if (encoding != null){
					bytes = requestBody.getBytes(encoding);
				}else {
					bytes = requestBody.getBytes(StandardCharsets.UTF_8);
				}
				final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
				request = new HttpServletRequestWrapper(request) {
					@Override
					public ServletInputStream getInputStream() {
						return new ByteArrayServletInputStream(byteArrayInputStream);
					}
				};
			}else {
				log.debug("unsupported content-type {}.", requestContentType);
			}
		}

		if (StringUtils.hasText(requestBody) && requestBody.length() > this.requestOmitLength){
			requestBody = requestBody.substring(0, this.requestOmitLength) + "...(共" + requestBody.length() + "字节)";
		}

		int contentLength = request.getContentLength();
		// 其他类型默认按二进制流处理
		if (!StringUtils.hasLength(requestBody) && contentLength > 0) {
			requestBody = "Binary data" + "(" + contentLength + " byte)";
		}

		boolean hasBody = false;
		if (StringUtils.hasLength(requestBody)) {
			hasBody = true;
			if (!hasHeader){
				builder.append("\n");
			}
			builder.append("\n").append(requestBody);
		}

		if (hasHeader || hasBody) {
			builder.append("\n");
		}
		return request;
	}

	private boolean logHeader(String headerName) {
		if (headers.isEmpty()){
			return false;
		}
		return headers.contains(headerName.toLowerCase());
	}


	private String getRequestBody(HttpServletRequest request) {
		try {
			String gzipHeader = request.getHeader(HttpHeaders.CONTENT_ENCODING);
			if (gzipHeader != null && "gzip".equalsIgnoreCase(gzipHeader.trim())){
				GZIPInputStream gzipBody = new GZIPInputStream(request.getInputStream());
				return IOUtils.toString(gzipBody, StandardCharsets.UTF_8);
			}

			int contentLength = request.getContentLength();
			if(contentLength <= 0){
				return "";
			}

			return IOUtils.toString(request.getReader());
		} catch (IOException e) {
			log.error("获取请求体失败，原因：{}", e.getMessage());
			return "";
		}
	}

	private String getFormParam(HttpServletRequest request) {
		MultipartResolver resolver = new StandardServletMultipartResolver();
		MultipartHttpServletRequest mRequest = resolver.resolveMultipart(request);

		Map<String,Object> param = new HashMap<>();
		Map<String,String[]> parameterMap = mRequest.getParameterMap();
		if (!parameterMap.isEmpty()){
			param.putAll(parameterMap);
		}
		Map<String, MultipartFile> fileMap = mRequest.getFileMap();
		if(!fileMap.isEmpty()){
			for (Map.Entry<String, MultipartFile> fileEntry : fileMap.entrySet()) {
				MultipartFile file = fileEntry.getValue();
				param.put(fileEntry.getKey(), file.getOriginalFilename()+ "(" + file.getSize()+" byte)");
			}
		}
		return param.toString();
	}
}
