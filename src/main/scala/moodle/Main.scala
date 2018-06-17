package moodle

import java.io.File

import moodle.datatypes._

import scala.util.{Failure, Success, Try}

object Main extends App {

  sealed trait AuthMethod
  case class SessionKeyAuth(key: String) extends AuthMethod
  case class CredentialsAuth(username: String, password: String) extends AuthMethod

  // --

  val authMethod: AuthMethod = ??? // Choose your authentication method (session key or credentials)
  val outputDirectory: String = ??? // Choose the output directory (it is advised to use a dedicated directory due to the large amount of files that may be generated
  val maxRetries: Int = 3 // Should be enough

  // --

  val rootDirectory = new File(outputDirectory)
  assert(rootDirectory.isDirectory)

  print("Logging in... ")
  val client = authMethod match {
    case SessionKeyAuth(key) => new MoodleClient(key)
    case CredentialsAuth(username, password) => MoodleClient(username, password)
  }

  client.isConnected match {
    case Some(user) => println("Successfully logged as \"" + user + "\"")
    case None => throw new IllegalStateException("Not connected!")
  }

  val courses: Seq[Course] = client.getCourses

  println(courses.size + " courses found. Downloading all of them in separate sub-directories of " + rootDirectory.getAbsolutePath)
  println()

  courses.foreach(course => {
    val courseDirectory = new File(rootDirectory.getAbsolutePath + File.separator + course.name)
    courseDirectory.mkdir()

    client.getCourseResources(course).foreach {
      case fileResource: CourseResourceFile =>
        print(s"""[${course.name}] Downloading "${fileResource.title}"... """)
        def withRetry(n: Int): Unit = {
          if(n == 0) {
            println("Couldn't download file, skipping")
          } else {
            Try {
              client.downloadFile(fileResource, courseDirectory)
            } match {
              case Success(_) => println("Done.")
              case Failure(throwable) =>
                print(s"(attempt ${maxRetries - n + 1} failed) ")
                withRetry(n - 1)
            }
          }
        }
        withRetry(maxRetries)

      case _ =>
    }
  })

}
