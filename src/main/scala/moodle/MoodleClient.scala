package moodle

import moodle.datatypes._
import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.net.HttpCookie

import net.ruippeixotog.scalascraper.browser.JsoupBrowser

import scalaj.http.{Http, HttpRequest, HttpResponse}

class MoodleClient(val moodleSession: String) {
  import MoodleClient._
  import net.ruippeixotog.scalascraper.dsl.DSL._
  import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
  import net.ruippeixotog.scalascraper.dsl.DSL.Parse._

  private val browser = JsoupBrowser()

  def request(path: String, absolute: Boolean = false): HttpRequest = Http(if(absolute) path else moodleUrl(path)).cookies(Seq(new HttpCookie(moodleSessionKey, moodleSession)))

  def get(path: String, absolute: Boolean = false): String = request(path, absolute).asString.body

  def homepage: String = get("/my/")

  def getCourses: Seq[Course] = {
    val document = browser.parseString(homepage)

    val regexId = "coc\\-course\\-([0-9]+)".r

    val divs = document >> elementList("div#coc-courselist > div")
    val courses = for(div <- divs) yield {
      val id = div >> attr("id") match { case regexId(idStr) => idStr.toInt }
      val a = div >> element("div > h3 > a")
      Course(id, a >> text, a >> attr("href"))
    }

    courses
  }

  def coursePage(course: Course): String = get(course.url, absolute = true)

  def getCourseResources(course: Course): Seq[CourseResource] = {
    val document = browser.parseString(coursePage(course))

    val map: Map[String, (String, String) => CourseResource] = Map(
      "modtype_label" -> ((title, _) => CourseResourceLabel(title)),
      "modtype_resource" -> (CourseResourceFile(_, _)),
      "modtype_url" -> (CourseResourceURL(_, _)),
      "modtype_assign" -> (CourseResourceAssignment(_, _)),
      "modtype_forum" -> (CourseResourceForum(_, _)),
      "modtype_feedback" -> (CourseResourceFeedback(_, _)),
      "modtype_choice" -> (CourseResourcePoll(_, _)),
      "modtype_page" -> (CourseResourcePage(_, _)),
      "modtype_choicegroup" -> (CourseResourceChoiceGroup(_, _)),
      "modtype_folder" -> (CourseResourceFolder(_, _)),
      "modtype_lti" -> (CourseResourceLTI(_, _))
    )

    val lis = document >> elementList("li.activity")
    val resources = for(li <- lis) yield {
      li >?> element("div > div > div > div > a") match {
        case Some(a) =>
          val resourceUrl = a >> attr("href")
          val containsSpan = (a >?> element("span > span")).isDefined
          val resourceName = a >> text("span")
          val resourceNameClean = if(containsSpan) resourceName.split(" ").reverse.tail.reverse.mkString(" ") else resourceName
          val classes = li >> attr("class")
          val resource = map.getOrElse(classes.trim.split("\\s+").last, CourseResourceUnknown(_, _))(resourceNameClean, resourceUrl)

          Some(resource)
        case None => None
      }
    }

    resources.flatten
  }

  def downloadFile(resource: CourseResourceFile, directory: File): Unit = {
    val redirect = request(resource.url, absolute = true).asString
    assert(redirect.isNotError)
    assert(redirect.location.isDefined)
    val bytes = request(redirect.location.get, absolute = true).asBytes
    assert(bytes.isNotError)

    val filenameRegex = ".*filename=\"([^\"]*)\".*".r
    val originalFilename = bytes.header("Content-Disposition").get match {
      case filenameRegex(name) => name
    }

    val (originalFileNameWithout, extension) = {
      val split = originalFilename.split("\\.")
      (split.take(split.size - 1).mkString("."), split(split.size - 1))
    }

    val out = new BufferedOutputStream(new FileOutputStream(directory.getAbsolutePath + File.separator + resource.title.replaceAll("[\\\\/:*?\"<>|]", "") + "." + extension))

    out.write(bytes.body)
    out.flush()

    out.close()
  }

  def isConnected: Option[String] = {
    val dom = browser.parseString(homepage)

    (dom >?> element("ul.pull-right > li > a > em").map(_.text)).filter(_ != "Log out")
  }

}

object MoodleClient {

  private val moodleEndpoint = "https://moodle.epfl.ch"
  private val tequilaEndpoint = "https://tequila.epfl.ch"

  private val (moodleSessionKey, tequilaSessionKey) = ("MoodleSession", "TequilaPHP")

  private def moodleUrl(path: String): String = s"$moodleEndpoint$path"
  private def tequilaUrl(path: String): String = s"$tequilaEndpoint$path"

  def apply(moodleSession: String): MoodleClient = new MoodleClient(moodleSession)

  def apply(username: String, password: String): MoodleClient = {

    // Step 1
    val response1: HttpResponse[String] = Http(moodleUrl("/auth/tequila/index.php")).asString
    assert(response1.isNotError, "HTTP error step 1")
    val tequilaPhp = response1.cookies.find(_.getName == tequilaSessionKey) match {
      case Some(cookie) => cookie.getValue
      case None => throw new IllegalStateException("No TequilaPHP cookie on step 1")
    }
    val moodleSession = response1.cookies.find(_.getName == moodleSessionKey) match {
      case Some(cookie) => cookie.getValue
      case None => throw new IllegalStateException("No MoodleSession cookie on step 1")
    }
    val location = response1.location match {
      case Some(url) => url
      case None => throw new IllegalStateException("No location provived on step 1")
    }

    // Step 2
    val response2: HttpResponse[String] = Http(location).asString
    assert(response2.isNotError, "HTTP error step 2")

    // Step 3
    val response3: HttpResponse[String] = Http(tequilaUrl("/cgi-bin/tequila/login")).postForm(
      Seq(("requestkey", tequilaPhp), ("username", username), ("password", password))
    ).asString
    assert(response3.isNotError, "HTTP error step 3")
    assert(response3.location.nonEmpty, "Invalid credentials")

    // Step 4
    val response4: HttpResponse[String] = Http(moodleUrl("/auth/tequila/index.php")).cookies(
      Seq(new HttpCookie(moodleSessionKey, moodleSession), new HttpCookie(tequilaSessionKey, tequilaPhp))
    ).asString
    assert(response4.isNotError, "HTTP error step 4")
    assert(response4.location.contains(moodleUrl("/my/")), "Error while logging in: " + response4.location.getOrElse(""))


    new MoodleClient(moodleSession)
  }
}