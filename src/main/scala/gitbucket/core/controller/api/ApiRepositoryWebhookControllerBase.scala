package gitbucket.core.controller.api
import gitbucket.core.api._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.{WebHook, WebHookContentType}
import gitbucket.core.service.{RepositoryService, WebHookService}
import gitbucket.core.util._
import gitbucket.core.util.Implicits._
import org.scalatra.NoContent

trait ApiRepositoryWebhookControllerBase extends ControllerBase {
  self: RepositoryService with WebHookService with ReferrerAuthenticator with WritableUsersAuthenticator =>

  /*
   * i. List repository webhooks
   * https://docs.github.com/en/rest/reference/repos#list-repository-webhooks
   */
  get("/api/v3/repos/:owner/:repository/hooks")(referrersOnly { repository =>
    val apiWebhooks = for {
      (hook, events) <- getWebHooks(repository.owner, repository.name)
    } yield {
      ApiWebhook("Repository", hook, events)
    }
    JsonFormat(apiWebhooks)
  })

  /*
   * ii. Create a repository webhook
   * https://docs.github.com/en/rest/reference/repos#create-a-repository-webhook
   */
  post("/api/v3/repos/:owner/:repository/hooks")(writableUsersOnly { repository =>
    (for {
      data <- extractFromJsonBody[CreateARepositoryWebhook] if data.isValid
      ctype = if (data.config.content_type == "form") WebHookContentType.FORM else WebHookContentType.JSON
      events = data.events.map(p => WebHook.Event.valueOf(p)).toSet
    } yield {
      addWebHook(
        repository.owner,
        repository.name,
        data.config.url,
        events,
        ctype,
        data.config.secret
      )
      getWebHook(repository.owner, repository.name, data.config.url) match {
        case Some(createdHook) => JsonFormat(ApiWebhook("Repository", createdHook._1, createdHook._2))
        case _                 =>
      }
    }) getOrElse NotFound()
  })

  /*
   * iii. Get a repository webhook
   * https://docs.github.com/en/rest/reference/repos#get-a-repository-webhook
   */
  get("/api/v3/repos/:owner/:repository/hooks/:id")(referrersOnly { repository =>
    val hookId = params("id").toInt
    getWebHookById(hookId) match {
      case Some(hook) => JsonFormat(ApiWebhook("Repository", hook._1, hook._2))
      case _          => NotFound()
    }
  })

  /*
   * iv. Update a repository webhook
   * https://docs.github.com/en/rest/reference/repos#update-a-repository-webhook
   */
  patch("/api/v3/repos/:owner/:repository/hooks/:id")(writableUsersOnly { repository =>
    val hookId = params("id").toInt
    (for {
      data <- extractFromJsonBody[UpdateARepositoryWebhook] if data.isValid
      ctype = data.config.content_type match {
        case "json" => WebHookContentType.JSON
        case _      => WebHookContentType.FORM
      }
    } yield {
      val events = (data.events ++ data.add_events)
        .filterNot(p => data.remove_events.contains(p))
        .map(p => WebHook.Event.valueOf(p))
        .toSet
      updateWebHookByApi(
        hookId,
        repository.owner,
        repository.name,
        data.config.url,
        events,
        ctype,
        data.config.secret
      )
      getWebHookById(hookId) match {
        case Some(updatedHook) => JsonFormat(ApiWebhook("Repository", updatedHook._1, updatedHook._2))
        case _                 =>
      }
    }) getOrElse NotFound()
  })

  /*
   * v. Delete a repository webhook
   * https://docs.github.com/en/rest/reference/repos#delete-a-repository-webhook
   */
  delete("/api/v3/repos/:owner/:repository/hooks/:id")(writableUsersOnly { repository =>
    val hookId = params("id").toInt
    getWebHookById(hookId) match {
      case Some(_) =>
        deleteWebHookById(params("id").toInt)
        NoContent()
      case _ => NotFound()
    }
  })

  /*
   * vi. Ping a repository webhook
   * https://docs.github.com/en/rest/reference/repos#ping-a-repository-webhook
   */

  /*
 * vi. Test the push repository webhook
 * https://docs.github.com/en/rest/reference/repos#test-the-push-repository-webhook
 */

}
