package dispatch.twitter

import json._

object Search extends Js {
  val to_user_id = 'to_user_id ? num
  val from_user_id = 'from_user_id ? num
  val source = 'source ? str
  val id = 'id ? num
  val text = 'text ? str
  val created_at = 'created_at ? str
  val iso_language_code = 'iso_language_code ? str
  val from_user = 'from_user ? str
}

class Search extends Js {
  import Http._
  lazy val http = new Http("search.twitter.com")

  def apply(params: Map[String, String]): List[JsObject] = 
    http { /("search.json") <<? params ># { 'results ! (list ! obj) } }

  def apply(count: Int)(q: String): List[JsObject] = 
    this(Map("q" -> q, "rpp" -> count.toString))

  def apply: String => List[JsObject] = apply(20)
}

trait Twitter extends Js {
  import Http._
  lazy val http = new Http("twitter.com")
}

object Status extends Js {
  val user = new Obj('user) {
    val followers_count = 'followers_count ? num
    val screen_name = 'screen_name ? str
  }
  val text = 'text ? str
}

trait UserFields {
  import Js._
  val followers_count = 'followers_count ? num
  val screen_name = 'screen_name ? str
}

object User extends UserFields

class Statuses extends Twitter {
  import Js._
  val svc = /("statuses")
  
  def public_timeline = 
    http(svc / "public_timeline.json" ># (list ! obj))
  def user_timeline(user: String) =
    http(svc / "user_timeline" / (user + ".json") ># (list ! obj))
}

class Users extends Twitter {
  val svc = /("users")

  def show(user: String) =
    http(svc / "show" / (user + ".json") ># obj)
}