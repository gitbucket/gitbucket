package gitbucket.core.controller.api
import gitbucket.core.api.{ApiRepositoryWebhook, CreateAWebhook, JsonFormat}
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.{WebHook, WebHookContentType}
import gitbucket.core.service.WebHookService
import gitbucket.core.util.OwnerAuthenticator
import gitbucket.core.util.Implicits._
import org.scalatra.{NoContent, NotFound}

trait ApiRepositoryWebhookcontrollerBase extends ControllerBase {
  self: WebHookService with OwnerAuthenticator =>

  /**
   * i. List hooks
   * https://developer.github.com/v3/repos/hooks/#list-hooks
   */
  get("/api/v3/repos/:owner/:repository/hooks")(ownerOnly { repository =>
    JsonFormat(getWebHooks(repository.owner, repository.name).map {
      case (wh, events) =>
        ApiRepositoryWebhook(wh, events)
    })
  })

  /*
   * ii. Get single hook
   * https://developer.github.com/v3/repos/hooks/#get-single-hook
   * not implemented
   */
  get("/api/v3/repos/:owner/:repository/hooks/:id")(ownerOnly { repository =>
    params
      .get("id")
      .map { id =>
        getWebHookById(repository.owner, repository.name, id.toInt)
          .map {
            case (hook, events) =>
              JsonFormat(ApiRepositoryWebhook(hook, events))
          }
          .getOrElse(NotFound)
      }
      .getOrElse(NotFound)
  })

  /**
   * iii. Create a hook
   * https://developer.github.com/v3/repos/hooks/#create-a-hook
   */
  post("/api/v3/repos/:owner/:repository/hooks")(ownerOnly { repository =>
    (for {
      data <- extractFromJsonBody[CreateAWebhook]
      loginAccount <- context.loginAccount
    } yield {
      val url = data.config.url
      val events = data.events.map(WebHook.Event.valueOf(_)).toSet
      val ctype = WebHookContentType.valueOf(data.config.content_type)
      val webHook = addWebHook(repository.owner, repository.name, url, events, ctype, data.config.secret)
      JsonFormat(ApiRepositoryWebhook(webHook, events))
    }).getOrElse(NotFound())
  })

  /*
   * iv. Edit a hook
   * https://developer.github.com/v3/repos/hooks/#edit-a-hook
   * not implemented
   */

  /*
   * v. Test a push hook
   * https://developer.github.com/v3/repos/hooks/#test-a-push-hook
   * not implemented
   */

  /*
   * vi. Ping a hook
   * https://developer.github.com/v3/repos/hooks/#ping-a-hook
   * not implemented
   */

  /*
   * vii. Delete a hook
   * https://developer.github.com/v3/repos/hooks/#delete-a-hook
   * not implemented
   */
  delete("/api/v3/repos/:owner/:repository/hooks/:id")(ownerOnly { repository =>
    deleteWebHook(repository.owner, repository.name, params("id").toInt)
    NoContent()
  })

  /*
   * viii. Receiving Webhooks
   * https://developer.github.com/v3/repos/hooks/#receiving-webhooks
   * not implemented
   */

  /*
 * ix. PubSubHubbub
 * https://developer.github.com/v3/repos/hooks/#pubsubhubbub
 * not implemented
 */

}
