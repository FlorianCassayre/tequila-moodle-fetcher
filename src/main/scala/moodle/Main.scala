package moodle

import moodle.datatypes._

import scala.util.{Failure, Success, Try}

object Main extends App {

  print("Logging in... ")
  val client = new MoodleClient("")
  //val client = MoodleClient("username", "password")
  println("OK")

  val courses: Seq[Course] = client.getCourses

  println(courses.size + " courses found.")
  println()

  courses.foreach(course => {
    client.getCourseResources(course).foreach {
      case fileResource: CourseResourceFile =>
        print(s"""[${course.name}] Downloading "${fileResource.title}"... """)
        def withRetry(n: Int): Unit = {
          if(n == 0) {
            println("Couldn't download file, skipping")
          } else {
            Try {
              client.downloadFile(fileResource, "T:/moodle/")
            } match {
              case Success(_) => println("OK")
              case Failure(throwable) =>
                print("FAIL ")
                withRetry(n - 1)
            }
          }
        }
        withRetry(3)

      case _ =>
    }
  })

}
