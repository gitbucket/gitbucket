package gitbucket.core.controller

class AnonymousAccessController extends AnonymousAccessControllerBase

trait AnonymousAccessControllerBase extends ControllerBase {
  get(!context.settings.allowAnonymousAccess, context.loginAccount.isEmpty) {
    if(!context.currentPath.startsWith("/assets") && !context.currentPath.startsWith("/signin") &&
      !context.currentPath.startsWith("/register") && !context.currentPath.startsWith("/plugin-assets")) {
      Unauthorized()
    } else {
      pass()
    }
  }
}
