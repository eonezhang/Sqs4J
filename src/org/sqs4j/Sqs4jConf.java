package org.sqs4j;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Sqs4J配置文件 User: wstone Date: 2010-7-30 Time: 13:08:46
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "Sqs4j")
public class Sqs4jConf {
  @XmlElement(required = true)
  public String bindAddress = "*"; //监听地址,*代表所有

  @XmlElement(required = true)
  public int bindPort = 1218; //监听端口

  @XmlElement(required = true)
  public int backlog = 200; //侦听 backlog 长度

  @XmlElement(required = true)
  public int soTimeout = 60; //HTTP请求的超时时间(秒)

  @XmlElement(required = true)
  public String defaultCharset = "UTF-8"; //缺省字符集
  @XmlTransient
  public Charset charsetDefaultCharset = Charset.forName(defaultCharset); //HTTP字符集

  @XmlElement(required = false)
  public String dbPath = ""; //数据库目录,缺省在:System.getProperty("user.dir", ".") + "/db"

  @XmlElement(required = true)
  public int syncinterval = 1; //同步更新内容到磁盘的间隔时间

  @XmlElement(required = true)
  public String adminUser = "admin"; //管理员用户名

  @XmlElement(required = true)
  public String adminPass = "123456"; //管理员口令

  @XmlElement(required = true)
  public int jmxPort = 1219; //JMX监听端口

  @XmlElement(required = false)
  public String auth = ""; //Sqs4j的get,put,view的验证密码,为空时不验证

  public static Sqs4jConf load(String path) throws Exception {
    InputStream in = null;
    try {
      in = new FileInputStream(path);
      Sqs4jConf conf = unmarshal(Sqs4jConf.class, in);
      return conf;
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException ex) {
        }
      }
    }
  }

  public void store(String path) throws Exception {
    OutputStream out = null;
    try {
      if (dbPath.equals(System.getProperty("user.dir", ".") + "/db")) {
        dbPath = "";
      }
      out = new FileOutputStream(path);
      if (this.auth == null) {
        try {
          this.auth = "";
          marshaller(this, out, true);
        } finally {
          this.auth = null;
        }
      } else {
        marshaller(this, out, true);
      }
    } finally {
      if (out != null) {
        try {
          out.close();
        } catch (IOException ex) {
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T unmarshal(Class<T> docClass, InputStream inputStream) throws JAXBException {
    JAXBContext jc = JAXBContext.newInstance(docClass);
    Unmarshaller u = jc.createUnmarshaller();
    T doc = (T) u.unmarshal(inputStream);
    return doc;
  }

  public static void marshaller(Object docObj, OutputStream pathname, boolean perttyFormat) throws JAXBException,
      IOException {
    JAXBContext context = JAXBContext.newInstance(docObj.getClass());
    Marshaller m = context.createMarshaller();
    if (perttyFormat) {
      m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    }
    m.marshal(docObj, pathname);
  }

  @Override
  public String toString() {
    return "Sqs4jConf{" + "bindAddress='" + bindAddress + '\'' + ", bindPort=" + bindPort + ", backlog=" + backlog
        + ", soTimeout=" + soTimeout + ", defaultCharset='" + defaultCharset + '\'' + ", dbPath='" + dbPath + '\''
        + ", syncinterval=" + syncinterval + ", adminUser='" + adminUser + '\'' + ", adminPass='" + "******" + '\''
        + ", jmxPort='" + jmxPort + '\'' + ", auth='" + "******" + '\'' + '}';
  }

  public static void main(String[] args) {
    Sqs4jConf Sqs4jConfA = new Sqs4jConf();
    try {
      Sqs4jConfA.dbPath = "c:\\abc";
      Sqs4jConfA.store("z:/1.xml");
      Sqs4jConfA = Sqs4jConf.load("z:/1.xml");
      System.out.println(Sqs4jConfA);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
