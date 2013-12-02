package org.sqs4j;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;

public class HttpRequestHandler extends SimpleChannelUpstreamHandler {
  //@wjw_comment ע��:��Ϊʹ����HttpServerPipelineFactory��ÿһ��ChannelPipeline����һ���µ�HttpRequestHandlerʵ��(Ҳ����˵���ǵ�ʵ����),
  //û�й����ͻ,���Է���ʹ��ʵ������!!!
  private HttpRequest _request;
  private boolean _readingChunks;
  private final StringBuilder _buf = new StringBuilder(128); //Buffer that stores the response content

  private final Sqs4jApp _app;
  private Charset _charsetObj;

  public HttpRequestHandler(Sqs4jApp app) {
    _app = app;
    _charsetObj = _app._conf.charsetDefaultCharset;
  }

  private boolean checkUser(HttpResponse response) throws IOException {
    String username = "";
    String password = "";
    String userPass = _request.getHeader("Authorization");

    if (null != userPass) {
      userPass = userPass.substring(6, userPass.length());

      userPass = _app.getBASE64DecodeOfStr(userPass, _charsetObj.name());
      final int pos = userPass.indexOf(':');
      if (pos > 0) {
        username = userPass.substring(0, pos);
        password = userPass.substring(pos + 1, userPass.length());
      }
    } else {
      response.setHeader("WWW-Authenticate", "Basic realm=\"Sqs4J\"");
      response.setStatus(HttpResponseStatus.UNAUTHORIZED);
      _buf.append("HTTPSQS_ERROR:��Ҫ�û���/����!");
      return false;
    }

    if (_app._conf.adminUser.equals(username) && _app._conf.adminPass.equals(password)) {
      return true;
    } else {
      response.setHeader("WWW-Authenticate", "Basic realm=\"Sqs4J\"");
      response.setStatus(HttpResponseStatus.UNAUTHORIZED);
      _buf.append("HTTPSQS_ERROR:�û�����/���� ����!");
      return false;
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    //e.getCause().printStackTrace();
    e.getChannel().close();
  }

  /**
   * ����ģ��
   * 
   * @param ctx
   * @param e
   * @throws Exception
   */
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    if (!_readingChunks) {
      _request = (HttpRequest) e.getMessage();
      _buf.setLength(0);

      if (_request.isChunked()) {
        _readingChunks = true;
      } else {
        writeResponse(e);
      }
    } else {
      final HttpChunk chunk = (HttpChunk) e.getMessage();

      if (chunk.isLast()) {
        _readingChunks = false;
        writeResponse(e);
      }
    }

  }

  private void writeResponse(MessageEvent e) throws IOException {
    //����URL����
    QueryStringDecoder queryStringDecoder = new QueryStringDecoder(_request.getUri(), _app._conf.charsetDefaultCharset);
    Map<String, List<String>> requestParameters = queryStringDecoder.getParameters();

    String charset = (null != requestParameters.get("charset")) ? requestParameters.get("charset").get(0) : null; //�ȴ�query�����charset
    if (null == charset) {
      if (null != _request.getHeader("Content-Type")) {
        charset = _app.getCharsetFromContentType(_request.getHeader("Content-Type"));
        if (null == charset) {
          charset = _app._conf.defaultCharset;
        } else if (!charset.equalsIgnoreCase(_app._conf.defaultCharset)) { //˵����ѯ������ָ�����ַ���,������ȱʡ�ַ�����һ��
          _charsetObj = Charset.forName(charset);
          queryStringDecoder = new QueryStringDecoder(_request.getUri(), _charsetObj);
          requestParameters = queryStringDecoder.getParameters();
        }
      } else {
        charset = _app._conf.defaultCharset;
      }
    } else if (!charset.equalsIgnoreCase(_app._conf.defaultCharset)) { //˵����ѯ������ָ�����ַ���,������ȱʡ�ַ�����һ��
      _charsetObj = Charset.forName(charset);
      queryStringDecoder = new QueryStringDecoder(_request.getUri(), _charsetObj);
      requestParameters = queryStringDecoder.getParameters();
    }

    //����GET������
    final String httpsqs_input_auth = (null != requestParameters.get("auth")) ? requestParameters.get("auth").get(0) : null; /* get,put,view����֤���� */
    final String httpsqs_input_name = (null != requestParameters.get("name")) ? requestParameters.get("name").get(0) : null; /* �������� */
    final String httpsqs_input_opt = (null != requestParameters.get("opt")) ? requestParameters.get("opt").get(0) : null; //�������
    final String httpsqs_input_data = (null != requestParameters.get("data")) ? requestParameters.get("data").get(0) : null; //��������
    final String httpsqs_input_pos_tmp = (null != requestParameters.get("pos")) ? requestParameters.get("pos").get(0) : null; //����λ�õ�
    final String httpsqs_input_num_tmp = (null != requestParameters.get("num")) ? requestParameters.get("num").get(0) : null; //�����ܳ���
    long httpsqs_input_pos = 0;
    long httpsqs_input_num = 0;
    if (null != httpsqs_input_pos_tmp) {
      httpsqs_input_pos = Long.parseLong(httpsqs_input_pos_tmp);
    }
    if (null != httpsqs_input_num_tmp) {
      httpsqs_input_num = Long.parseLong(httpsqs_input_num_tmp);
    }

    //���ظ��û���Headerͷ��Ϣ
    final HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    response.setHeader("Content-Type", "text/plain;charset=" + charset);
    response.setHeader("Connection", "keep-alive");
    response.setHeader("Cache-Control", "no-cache");

    Sqs4jApp._lock.lock();
    try {
      /* �����Ƿ�����ж� */
      if (null != httpsqs_input_name && null != httpsqs_input_opt && httpsqs_input_name.length() <= 256) {
        /* ����� */
        if (httpsqs_input_opt.equals("put")) {
          if (null != _app._conf.auth && !_app._conf.auth.equals(httpsqs_input_auth)) {
            _buf.append("HTTPSQS_AUTH_FAILED");
          } else {
            /* ���Ƚ���POST������Ϣ */
            if (_request.getMethod().getName().equalsIgnoreCase("POST")) {
              final long now_putpos = _app.httpsqs_now_putpos(httpsqs_input_name);
              if (now_putpos > 0) {
                final String key = httpsqs_input_name + ":" + now_putpos;
                final String value = URLDecoder.decode(_request.getContent().toString(_charsetObj), charset);
                _app._db.put(key.getBytes(Sqs4jApp.DB_CHARSET), value.getBytes(Sqs4jApp.DB_CHARSET));
                response.setHeader("Pos", now_putpos);
                _buf.append("HTTPSQS_PUT_OK");
              } else {
                _buf.append("HTTPSQS_PUT_END");
              }
            } else if (null != httpsqs_input_data) { //���POST���������ݣ���ȡURL��data������ֵ
              final long now_putpos = _app.httpsqs_now_putpos(httpsqs_input_name);
              if (now_putpos > 0) {
                final String key = httpsqs_input_name + ":" + now_putpos;
                final String value = httpsqs_input_data;
                _app._db.put(key.getBytes(Sqs4jApp.DB_CHARSET), value.getBytes(Sqs4jApp.DB_CHARSET));
                response.setHeader("Pos", now_putpos);
                _buf.append("HTTPSQS_PUT_OK");
              } else {
                _buf.append("HTTPSQS_PUT_END");
              }
            } else {
              _buf.append("HTTPSQS_PUT_ERROR");
            }
          }
        } else if (httpsqs_input_opt.equals("get")) { //������
          if (null != _app._conf.auth && !_app._conf.auth.equals(httpsqs_input_auth)) {
            _buf.append("HTTPSQS_AUTH_FAILED");
          } else {
            final long now_getpos = _app.httpsqs_now_getpos(httpsqs_input_name);
            if (0 == now_getpos) {
              _buf.append("HTTPSQS_GET_END");
            } else {
              final String key = httpsqs_input_name + ":" + now_getpos;
              final byte[] value = _app._db.get(key.getBytes(Sqs4jApp.DB_CHARSET));
              if (null != value) {
                response.setHeader("Pos", now_getpos);
                _buf.append(new String(value, Sqs4jApp.DB_CHARSET));
              } else {  //@wjw_note: ��������״���Ŀ����Լ�С,�Ǿ���������"putpos"��,����ͻȻ����,û���ü�д��ǰ"putpos"ָʾ��λ�õ�����!
                //_buf.append("HTTPSQS_GET_END");
                response.setHeader("Pos", now_getpos);
                _buf.append("");
              }
            }
          }
          /* �鿴������������ */
        } else if (httpsqs_input_opt.equals("view") && httpsqs_input_pos >= 1
            && httpsqs_input_pos <= Sqs4jApp.DEFAULT_MAXQUEUE) {
          if (null != _app._conf.auth && !_app._conf.auth.equals(httpsqs_input_auth)) {
            _buf.append("HTTPSQS_AUTH_FAILED");
          } else {
            final String httpsqs_output_value = _app.httpsqs_view(httpsqs_input_name, httpsqs_input_pos);
            if (httpsqs_output_value == null) {
              _buf.append("HTTPSQS_ERROR_NOFOUND");
            } else {
              _buf.append(httpsqs_output_value);
            }
          }
          /* �鿴����״̬����ͨ�����ʽ�� */
        } else if (httpsqs_input_opt.equals("status")) {
          String put_times = "1st lap";
          final String get_times = "1st lap";

          final long maxqueue = _app.httpsqs_read_maxqueue(httpsqs_input_name); /* ���������� */
          final long putpos = _app.httpsqs_read_putpos(httpsqs_input_name); /* �����д��λ�� */
          final long getpos = _app.httpsqs_read_getpos(httpsqs_input_name); /* �����ж�ȡλ�� */
          long ungetnum = 0;
          if (putpos >= getpos) {
            ungetnum = Math.abs(putpos - getpos); //��δ����������
          } else if (putpos < getpos) {
            ungetnum = Math.abs(maxqueue - getpos + putpos); /* ��δ���������� */
            put_times = "2nd lap";
          }
          _buf.append(String.format("HTTP Simple Queue Service (Sqs4J)v%s\n", Sqs4jApp.VERSION));
          _buf.append("------------------------------\n");
          _buf.append(String.format("Queue Name: %s\n", httpsqs_input_name));
          _buf.append(String.format("Maximum number of queues: %d\n", maxqueue));
          _buf.append(String.format("Put position of queue (%s): %d\n", put_times, putpos));
          _buf.append(String.format("Get position of queue (%s): %d\n", get_times, getpos));
          _buf.append(String.format("Number of unread queue: %d\n", ungetnum));
          _buf.append("ScheduleSync running: " + !_app._scheduleSync.isShutdown());
          /* �鿴����״̬��JSON��ʽ������ͷ��˳����� */
        } else if (httpsqs_input_opt.equals("status_json")) {
          String put_times = "1st lap";
          final String get_times = "1st lap";

          final long maxqueue = _app.httpsqs_read_maxqueue(httpsqs_input_name); /* ���������� */
          final long putpos = _app.httpsqs_read_putpos(httpsqs_input_name); /* �����д��λ�� */
          final long getpos = _app.httpsqs_read_getpos(httpsqs_input_name); /* �����ж�ȡλ�� */
          long ungetnum = 0;
          if (putpos >= getpos) {
            ungetnum = Math.abs(putpos - getpos); //��δ����������
          } else if (putpos < getpos) {
            ungetnum = Math.abs(maxqueue - getpos + putpos); /* ��δ���������� */
            put_times = "2nd lap";
          }

          _buf.append(String.format("{\"name\": \"%s\",\"maxqueue\": %d,\"putpos\": %d,\"putlap\": \"%s\",\"getpos\": %d,\"getlap\": \"%s\",\"unread\": %d, \"sync\": \"%s\"}\n", httpsqs_input_name, maxqueue, putpos, put_times, getpos, get_times, ungetnum, !_app._scheduleSync.isShutdown()));
          /* ���ö��� */
        } else if (httpsqs_input_opt.equals("reset")) {
          if (checkUser(response)) {
            final boolean reset = _app.httpsqs_reset(httpsqs_input_name);
            if (reset) {
              _buf.append(String.format("%s", "HTTPSQS_RESET_OK"));
            } else {
              _buf.append(String.format("%s", "HTTPSQS_RESET_ERROR"));
            }
          }
          /* �������Ķ�����������СֵΪ10�������ֵΪ10���� */
        } else if (httpsqs_input_opt.equals("maxqueue") && httpsqs_input_num >= 10
            && httpsqs_input_num <= Sqs4jApp.DEFAULT_MAXQUEUE) {
          if (checkUser(response)) {
            if (_app.httpsqs_maxqueue(httpsqs_input_name, httpsqs_input_num) != 0) {
              _buf.append(String.format("%s", "HTTPSQS_MAXQUEUE_OK")); //���óɹ�
            } else {
              _buf.append(String.format("%s", "HTTPSQS_MAXQUEUE_CANCEL")); //����ȡ��
            }
          }
          /* ���ö�ʱ�����ڴ����ݵ����̵ļ��ʱ�䣬��СֵΪ1�룬���ֵΪ10���� */
        } else if (httpsqs_input_opt.equals("synctime") && httpsqs_input_num >= 1
            && httpsqs_input_num <= Sqs4jApp.DEFAULT_MAXQUEUE) {
          if (checkUser(response)) {
            if (_app.httpsqs_synctime((int) httpsqs_input_num) >= 1) {
              _buf.append(String.format("%s", "HTTPSQS_SYNCTIME_OK"));
            } else {
              _buf.append(String.format("%s", "HTTPSQS_SYNCTIME_CANCEL"));
            }
          }
          /* �ֶ�ˢ���ڴ����ݵ����� */
        } else if (httpsqs_input_opt.equals("flush")) {
          if (checkUser(response)) {
            _app.flush();
            _buf.append(String.format("%s", "HTTPSQS_FLUSH_OK"));
          }
        } else { /* ������� */
          _buf.append(String.format("%s", "HTTPSQS_ERROR:δ֪������!"));
        }
      } else {
        _buf.append(String.format("%s", "HTTPSQS_ERROR:����������!"));
      }
    } catch (Throwable ex) {
      ex.printStackTrace();
    } finally {
      Sqs4jApp._lock.unlock();
    }

    response.setContent(ChannelBuffers.copiedBuffer(_buf.toString(), _charsetObj));
    response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, response.getContent().readableBytes());
    // Write the response.
    ChannelFuture future = e.getChannel().write(response);

    // Close the non-keep-alive connection after the write operation is done.
    boolean keepAlive = HttpHeaders.isKeepAlive(_request);
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE);
    }

  }

}
