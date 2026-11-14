CSRF Protection
========
GitBucket rejects POST, PUT, PATCH and DELETE requests that carry cookies but not the session's CSRF token. They get
`403` and a warning in the log. This applies to core, plugins and `/upload/*`. A request with cookies but without a
valid session (expired, or lost by a server restart) is rejected too; reloading the page gives a new token.

Not checked:

- requests with an `Authorization` header (API tokens, Basic auth, git and Git LFS clients)
- requests without cookies (e.g. webhook receivers, scripts). Sign-in is the exception: it always needs the token from
  the sign-in page.

GET requests are not checked, so they must not change anything.

The token changes at sign-in. Scripts that keep the token must read it again after signing in.

## Forms

Put the token field first in every `<form method="post">`:

```html
<form method="post" action="@context.path/...">
  @gitbucket.core.view.helpers.csrfField
  ...
</form>
```

On pages that use the core layout (`gitbucket.core.html.main`), a script adds the field to forms that lack it when
they are submitted. Render it yourself anyway.

For a link that changes something, use `<a data-method="post" href="...">`; the core script sends it as a form with
the token.

## JavaScript

On pages that use the core layout, `$.ajax` / `$.post` and Dropzone send the token in the `X-CSRF-TOKEN` header
automatically. If you pass your own Dropzone `headers`, include it.

For `fetch`, `XMLHttpRequest` and other libraries, send one of these headers:

- `X-CSRF-TOKEN` with the content of `<meta name="csrf-token">` (core layout)
- `X-XSRF-TOKEN` with the value of the `XSRF-TOKEN` cookie (every page; axios does this by default)

```javascript
fetch(url, {
  method: 'POST',
  headers: { 'X-CSRF-TOKEN': document.querySelector('meta[name="csrf-token"]').content },
  body: params
});
```

Send the token in a header for multipart requests.

## Pages without the core layout

Render the token yourself with `@context.csrfToken` (in a meta tag or a hidden field), or read the `XSRF-TOKEN`
cookie in JavaScript.

## Plugins for older GitBucket versions

`helpers.csrfField` and `context.csrfToken` don't exist in older versions, so templates that use them don't compile
there. A plugin that supports both sends the header from JavaScript, taking the token from the `XSRF-TOKEN` cookie or
the meta tag if present; older versions ignore it.

## Endpoints for other clients

Endpoints called by other programs (package deploys, CI, scripts) must authenticate with the `Authorization` header,
not with a session cookie.

## Implementation (for core developers)

- The check runs in `PreProcessControllerBase`, the first filter on all paths. It uses
  `gitbucket.core.scalatra.CsrfTokenSupport`, a backport of a proposed change to Scalatra's
  `org.scalatra.CsrfTokenSupport` (scalatra/scalatra#2085), not Scalatra's trait itself.
- Differences from Scalatra 3.2.1 (details in the backport's Scaladoc):
  - security: an unsafe request with a `Cookie` header needs the session's token, also when there is no session or the
    session has no token; constant-time comparison; a helper to replace the token (used at sign-in);
  - sessions: the token is created when it is first read and the check never creates an HTTP session; Scalatra's
    trait creates one for every request.
- GitBucket's own rules (`Authorization` exemption, sign-in without cookies, `X-XSRF-TOKEN`, the cookie, the log line)
  are in `PreProcessControllerBase`. Templates read the token with `context.csrfToken`.
- Known issue: GitBucket itself still creates a session for every request (`Context.sidebarCollapse`,
  `ControllerBase.LoginAccount`, `ApiAuthenticationFilter`, Scalatra's `FlashMapSupport`), so the session part of the
  backport has no effect yet. New code should read the session with `request.getSession(false)` (Scalatra:
  `sessionOption`) unless it has to store something.
- When Scalatra has the change, `ScalatraCsrfTokenSupportCanarySpec` fails; then switch back to Scalatra's trait as
  described in the backport's Scaladoc.
