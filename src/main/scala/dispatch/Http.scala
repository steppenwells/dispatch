package dispatch

import java.io.{InputStream,OutputStream,BufferedInputStream,BufferedOutputStream}

import org.apache.http._
import org.apache.http.client._
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods._
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.utils.URLEncodedUtils

import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicNameValuePair
import org.apache.http.protocol.{HTTP, HttpContext}
import org.apache.http.params.{HttpProtocolParams, BasicHttpParams}
import org.apache.http.util.EntityUtils
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}

case class StatusCode(code: Int, contents:String)
  extends Exception("Exceptional resoponse code: " + code + "\n" + contents)

class Http(
  val host: Option[HttpHost], 
  val headers: List[(String, String)],
  val creds: Option[(String, String)]
) {
  def this(host: HttpHost) = this(Some(host), Nil, None)
  def this(hostname: String, port: Int) = this(new HttpHost(hostname, port))
  def this(hostname: String) = this(new HttpHost(hostname))
  lazy val client = new ConfiguredHttpClient {
    for (h <- host; (name, password) <- creds) {
      getCredentialsProvider.setCredentials(
        new AuthScope(h.getHostName, h.getPort), 
        new UsernamePasswordCredentials(name, password)
      )
    }
  }

  /** Uses bound host server in HTTPClient execute. */
  def execute(req: HttpUriRequest):HttpResponse = {
    Http.log.info("%s %s", req.getMethod, req.getURI)
    host match {
      case None => client.execute(req)
      case Some(host) => client.execute(host, req)
    }
  }
  /** Sets authentication credentials for bound host. */
  def as (name: String, pass: String) = new Http(host, headers, Some((name, pass)))
  /** Add header */
  def << (k: String, v: String) = new Http(host, (k,v) :: headers, creds)
    
  /** eXecute wrapper */
  def x [T](req: HttpUriRequest) = new {
    /** handle response codes, response, and entity in block */
    def apply(block: (Int, HttpResponse, Option[HttpEntity]) => T) = {
      val res = execute(req)
      val ent = res.getEntity match {
        case null => None
        case ent => Some(ent)
      }
      try { block(res.getStatusLine.getStatusCode, res, ent) }
      finally { ent foreach (_.consumeContent) }
    }
    
    /** Handle reponse entity in block if reponse code returns true from chk. */
    def when(chk: Int => Boolean)(block: (HttpResponse, Option[HttpEntity]) => T) = this { (code, res, ent) => 
      if (chk(code)) block(res, ent)
      else throw StatusCode(code,
        ent.map(EntityUtils.toString(_, HTTP.UTF_8)).getOrElse("")
      )
    }
    
    /** Handle reponse entity in block when response code is 200 - 204 */
    def ok = (this when {code => (200 to 204) contains code}) _
  }
  
  /** Generally for the curried response function of Responder: >>, j$, <>, etc. */
  def apply[T](block: Http => T) = block(this)
}

/** Extension point for static request definitions. */
class /(path: String) extends Request("/" + path) {
  def this(req: Request) = this(req.req.getURI.toString.substring(1))
}

/** Factory for requests from the root. */
object / {
  def apply(path: String) = new Request("/" + path)
}

/** Wrapper to handle common requests, preconfigured as response wrapper for a 
  * get request but defs return other method responders. */
class Request(val req: HttpUriRequest) extends Responder {
  
  /** Start with GET by default. */
  def this(uri: String) = this(new HttpGet(uri))

  /** Append an element to this request's path */
  def / (path: String) = new Request(req.getURI + "/" + path)

  /** Put the given object.toString and return response wrapper. */
  def <<< (body: Any) = {
    val m = new HttpPut(req.getURI)
    m setEntity new StringEntity(body.toString, HTTP.UTF_8)
    HttpProtocolParams.setUseExpectContinue(m.getParams, false)
    new Request(m)
  }
  /** Post the given key value sequence and return response wrapper. */
  def << (values: Map[String, Any]) = {
    val m = new HttpPost(req.getURI)
    m setEntity new UrlEncodedFormEntity(Http.map2ee(values), HTTP.UTF_8)
    new Request(m)
  }
  
  /** Use <<? instead: the high precedence of ? is problematic. */
  @deprecated def ?< (values: Map[String, Any]) = <<? (values)
  /** Get with query parameters */
  def <<? (values: Map[String, Any]) = if(values.isEmpty) this else
    new Request(new HttpGet(req.getURI + Http ? (values)))

  /** HTTP Delete request. */
  def <--() = new Request(new HttpDelete(req.getURI))
}

trait Responder {
  val req: HttpUriRequest
  /** Execute and process response in block */
  def apply [T] (block: (Int, HttpResponse, Option[HttpEntity]) => T)(http: Http) = {
    http.headers foreach { case (k, v) => req.addHeader(k, v) }
    (http x req) (block)
  }
  /** Handle response and entity in block if OK. */
  def ok [T] (block: (HttpResponse, Option[HttpEntity]) => T)(http: Http) = {
    http.headers foreach { case (k, v) => req.addHeader(k, v) }
    (http x req) ok (block)
  }
  /** Handle response entity in block if OK. */
  def okee [T] (block: HttpEntity => T) = ok { 
    case (_, Some(ent)) => block(ent)
    case (res, _) => error("response has no entity: " + res)
  } _
  /** Handle InputStream in block if OK. */
  def >> [T] (block: InputStream => T) = okee (ent => block(ent.getContent))
  /** Return response in String if OK. (Don't blow your heap, kids.) */
  def as_str = okee { EntityUtils.toString(_, HTTP.UTF_8) }
  /** Write to the given OutputStream. */
  def >>> [OS <: OutputStream](out: OS)(http: Http) = { okee { _.writeTo(out) } (http); out }
  /** Process response as XML document in block */
  def <> [T] (block: xml.NodeSeq => T) = >> { stm => block(xml.XML.load(stm)) }
  
  /** Process response as JsValue in block */
  def ># [T](block: json.Js.JsF[T]) = >> { stm => block(json.Js(stm)) }
  /** Use ># instead: $ is forbidden. */
  @deprecated def $ [T](block: json.Js.JsF[T]) = >#(block)
  
  /** Ignore response body if OK. */
  def >| = ok ((r,e) => ()) _
}


class ConfiguredHttpClient extends DefaultHttpClient { 
  override def createHttpParams = {
    val params = new BasicHttpParams
    HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1)
    HttpProtocolParams.setContentCharset(params, HTTP.UTF_8)
    HttpProtocolParams.setUseExpectContinue(params, false)
    params
  }
}

/** May be used directly from any thread. */
object Http extends Http(None, Nil, None) {
  import org.apache.http.conn.scheme.{Scheme,SchemeRegistry,PlainSocketFactory}
  import org.apache.http.conn.ssl.SSLSocketFactory
  import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager
  
  // import to support e.g. Http("http://example.com/" >>> System.out)
  implicit def str2req(str: String) = new Request(str)

  override lazy val client = new ConfiguredHttpClient {
    override def createClientConnectionManager() = {
      val registry = new SchemeRegistry()
      registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80))
      registry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443))
      new ThreadSafeClientConnManager(getParams(), registry)
    }
  }
  val log = net.lag.logging.Logger.get
  /** Convert repeating name value tuples to list of pairs for httpclient */
  def map2ee(values: Map[String, Any]) = 
    new java.util.ArrayList[BasicNameValuePair](values.size) {
      values.foreach { case (k, v) => add(new BasicNameValuePair(k, v.toString)) }
    }
  /** Produce formatted query strings from a Map of parameters */
  def ?(values: Map[String, Any]) = if (values.isEmpty) "" else 
    "?" + URLEncodedUtils.format(map2ee(values), HTTP.UTF_8)
}