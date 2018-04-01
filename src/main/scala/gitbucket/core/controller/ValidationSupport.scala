package gitbucket.core.controller

import org.json4s.{JField, JObject, JString}
import org.scalatra._
import org.scalatra.json._
import org.scalatra.forms._
import org.scalatra.i18n.I18nSupport
import org.scalatra.servlet.ServletBase

/**
 * Extends scalatra-forms to support the client-side validation and Ajax requests as well.
 */
trait ValidationSupport extends FormSupport { self: ServletBase with JacksonJsonSupport with I18nSupport =>

  def get[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    get(path) {
      validate(form)(errors => BadRequest(), form => action(form))
    }
  }

  def post[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    post(path) {
      validate(form)(errors => BadRequest(), form => action(form))
    }
  }

  def put[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    put(path) {
      validate(form)(errors => BadRequest(), form => action(form))
    }
  }

  def delete[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    registerValidate(path, form)
    delete(path) {
      validate(form)(errors => BadRequest(), form => action(form))
    }
  }

  def ajaxGet[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    get(path) {
      validate(form)(errors => ajaxError(errors), form => action(form))
    }
  }

  def ajaxPost[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    post(path) {
      validate(form)(errors => ajaxError(errors), form => action(form))
    }
  }

  def ajaxDelete[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    delete(path) {
      validate(form)(errors => ajaxError(errors), form => action(form))
    }
  }

  def ajaxPut[T](path: String, form: ValueType[T])(action: T => Any): Route = {
    put(path) {
      validate(form)(errors => ajaxError(errors), form => action(form))
    }
  }

  private def registerValidate[T](path: String, form: ValueType[T]) = {
    post(path.replaceFirst("/$", "") + "/validate") {
      contentType = "application/json"
      toJson(form.validate("", multiParams, messages))
    }
  }

  /**
   * Responds errors for ajax requests.
   */
  private def ajaxError(errors: Seq[(String, String)]): JObject = {
    status = 400
    contentType = "application/json"
    toJson(errors)
  }

  /**
   * Converts errors to JSON.
   */
  private def toJson(errors: Seq[(String, String)]): JObject =
    JObject(errors.map {
      case (key, value) =>
        JField(key, JString(value))
    }.toList)

}
