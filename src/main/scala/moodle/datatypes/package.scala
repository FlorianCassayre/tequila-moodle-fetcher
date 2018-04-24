package moodle

package object datatypes {

  case class Course(id: Int, name: String, url: String)


  sealed abstract class CourseResource(val title: String)
  sealed abstract class CourseResourceWithURL(override val title: String, val url: String) extends CourseResource(title)


  case class CourseResourceLabel(override val title: String) extends CourseResource(title)

  // TODO file type
  case class CourseResourceFile(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

  case class CourseResourceURL(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

  case class CourseResourceAssignment(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

  case class CourseResourceForum(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

  case class CourseResourceUnknown(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

  case class CourseResourceFeedback(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

  case class CourseResourcePoll(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

  case class CourseResourcePage(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

  case class CourseResourceChoiceGroup(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

  case class CourseResourceFolder(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

  case class CourseResourceLTI(override val title: String, override val url: String) extends CourseResourceWithURL(title, url)

}
