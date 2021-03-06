/* 
** Copyright [2012-2013] [Megam Systems]
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
** http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
package controllers.stack

import scalaz._
import scalaz.NonEmptyList
import Scalaz._
import play.api.http.Status._
import java.io.{ StringWriter, PrintWriter }
/*
 * @author ram
 *
 */
package object stack {

  type RawResult = (Int, Option[Map[String, String]])

  object RawResult {
    def apply(Id: Int, results: Map[String, String]) = (Id, results.some)
  }

  type ResultInError = Option[(Int, String)]

  object ResultInError {
    def apply[C](m: (Int, String)): ResultInError = m.some
  }
  
  val tailMsg =
    """
        |********************************************************************************* 
        |** You can search/ask for help in our forums.
  		|** https://groups.google.com/forum/?fromgroups=#!forum/megamlive. 
  		|** If it still persists => Please create a ticket at our support link (http://www.megam.co/support).
  		|** Take a quick peek at our docs to see if you missed something.  
        |** (https://api.megam.co, http://docs.megam.co for more help.)
        |********************************************************************************* """.stripMargin

  val tailWithStacktrace =
    """
        |********************************************************************************* 
        |** Refer the stacktrace for more information. You can search/ask for help in our forums.
  		|** https://groups.google.com/forum/?fromgroups=#!forum/megamlive. 
  		|** If it still persists => Please create a ticket at our support link (http://www.megam.co/support).
  		|** Take a quick peek at our docs to see if you missed something.  
        |** (https://api.megam.co, http://docs.megam.co for more help.)
        |********************************************************************************* """.stripMargin

  implicit class RichThrowable(thrownExp: Throwable) {
    def fold[T](malformedBodyError: MalformedBodyError => T,
      malformedHeaderError: MalformedHeaderError => T,
      serviceUnavailableError: ServiceUnavailableError => T,
      resourceNotFound: ResourceItemNotFound => T,
      anyError: Throwable => T): T = thrownExp match {
      case m @ MalformedBodyError(_, _, _)      => malformedBodyError(m)
      case h @ MalformedHeaderError(_, _, _)    => malformedHeaderError(h)
      case c @ ServiceUnavailableError(_, _, _) => serviceUnavailableError(c)
      case r @ ResourceItemNotFound(_, _, _)    => resourceNotFound(r)
      case t @ _                                => anyError(t)
    }
  }

  case class MalformedBodyError(input: String, msg: String, httpCode: Int = BAD_REQUEST)
    extends Error

  case class MalformedHeaderError(input: String, msg: String, httpCode: Int = NOT_ACCEPTABLE)
    extends Error

  case class ServiceUnavailableError(input: String, msg: String, httpCode: Int = SERVICE_UNAVAILABLE)
    extends Error

  case class ResourceItemNotFound(input: String, msg: String, httpCode: Int = NOT_FOUND)
    extends Error

  case class HttpReturningError(errNel: NonEmptyList[Throwable]) extends Exception({
    errNel.map { err: Throwable =>
      err.fold(
        m => """%d: Body received from the API call contains invalid input. 'body:' %n'%s' 
            |
            |The error received when parsing the JSON is 
            |<=====>\n
            |%s
            |<=====>
            |Verify the body content as required for this resource. 
            |%s""".format(m.httpCode, m.input, m.msg, tailWithStacktrace).stripMargin,
        h => """%d: Header received from the API call contains invalid input. 'header:' %n'%s' 
            |
            |The error received is 
            |<=====>\n
            |%s
            |<=====>
            |Verify the header content as required for this resource. 
            |%s""".format(h.httpCode, h.input, h.msg, tailWithStacktrace).stripMargin,

        c => """%d: Service seems to be unavailable. The layer responsible for fullfilling the request Body received from the API call contains invalid input. 'body:'  '%s' 
            |came back with errors. 
            |The error received is 
            |<=====>\n
            |%s
            |<=====>
            |%s""".format(c.httpCode, c.input, c.msg, tailWithStacktrace).stripMargin,
        r => """%d: The resource requested wasn't found '?:'  '%s' 
            |
            |The error received is 
            |<=====>\n
            |%s
            |<=====>
            |%s""".format(r.httpCode, r.input, r.msg, tailWithStacktrace).stripMargin,

        t => """%d: Ooops ! I know its crazy. We flunked. 
            |                   
            |To help you debug, please read the message and the stacktrace below. 
            |=======================> Message <.!.> <=============================
            |                                 ( ^ )
            |                                 ~~~~
            %s
            |
            |=======================> Stack trace <===============================
            |%s
            |=======================> Stack trace <===============================
   |%s.""".format(INTERNAL_SERVER_ERROR, t.getLocalizedMessage,
          { val u = new StringWriter; t.printStackTrace(new PrintWriter(u)); u.toString },
          tailWithStacktrace).stripMargin)
    }.list.mkString("\n")
  }) {

    def code: Option[Int] = {
      (errNel.map { err: Throwable =>
        err.fold(m => m.httpCode.some, h => h.httpCode.some, c => c.httpCode.some, r => r.httpCode.some, t => INTERNAL_SERVER_ERROR.some)
      }.list.head)
    }
  }

}
