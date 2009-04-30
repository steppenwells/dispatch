import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers

class HttpSpec extends Spec with ShouldMatchers {
  import dispatch._
  import Http._

  val jane = "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.\n"
  val tus = new Http("technically.us")
  
  describe("Singleton Http test get") {
    get_specs(Http, "http://technically.us/test.text")
  }
  describe("Bound host get") {
    get_specs(tus, "/test.text")
  }
  def get_specs(http: Http, test: Request) = {
    it("should equal expected string") {
      http(test.as_str) should equal (jane)
    }
    it("should stream to expected sting") {
      http(test >> { stm => io.Source.fromInputStream(stm).mkString }) should equal (jane)
    }
    it("should write to expected sting bytes") {
      http(test >>> new java.io.ByteArrayOutputStream).toByteArray should equal (jane.getBytes)
    }
  }
}