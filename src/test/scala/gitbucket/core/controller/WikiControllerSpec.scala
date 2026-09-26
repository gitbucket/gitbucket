package gitbucket.core.controller

import gitbucket.core.TestingGitBucketServer
import gitbucket.core.util.StringUtil
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.impl.client.{BasicCookieStore, CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.scalatest.funsuite.AnyFunSuite

import java.util.{Arrays => JArrays}
import scala.util.Using

/**
 * Need to run `sbt package` before running this test.
 */
class WikiControllerSpec extends AnyFunSuite {

  private val pageNames = Seq("title+", "100%", "x%41y", "a b", "日本語")

  test("wiki pages whose names contain URL-special characters can be viewed, edited and deleted") {
    withWiki { (server, httpClient) =>
      val base = s"http://localhost:${server.port}/root/wiki_test/wiki"

      pageNames.foreach { pageName =>
        val encoded = StringUtil.urlEncode(pageName)
        val (viewStatus, viewBody) = get(httpClient, s"$base/$encoded")
        assert(viewStatus == 200 && viewBody.contains(content(pageName)), s"view $pageName")

        val (historyStatus, _) = get(httpClient, s"$base/$encoded/_history")
        assert(historyStatus == 200, s"history $pageName")

        val (editStatus, editBody) = get(httpClient, s"$base/$encoded/_edit")
        assert(editStatus == 200 && editBody.contains(content(pageName)), s"edit $pageName")
      }

      val (deleteStatus, _) = get(httpClient, s"$base/${StringUtil.urlEncode("title+")}/_delete")
      assert(deleteStatus == 302)
      val (_, pageList) = get(httpClient, s"$base/_pages")
      assert(!pageList.contains(s"/wiki/${StringUtil.urlEncode("title+")}\""), "title+ should be deleted")
      assert(
        pageList.contains(s"/wiki/${StringUtil.urlEncode("a b")}\""),
        "deleting title+ must not delete other pages"
      )
      val (otherStatus, otherBody) = get(httpClient, s"$base/${StringUtil.urlEncode("a b")}")
      assert(otherStatus == 200 && otherBody.contains(content("a b")), "deleting title+ must not affect other pages")
    }
  }

  test("a raw + in a wiki URL still means a space, as in links created before GitBucket 3.8") {
    withWiki { (server, httpClient) =>
      val (status, body) = get(httpClient, s"http://localhost:${server.port}/root/wiki_test/wiki/a+b")
      assert(status == 200 && body.contains(content("a b")))
    }
  }

  test("news feed links to wiki pages are URL-encoded") {
    withWiki { (server, httpClient) =>
      val (_, dashboard) = get(httpClient, s"http://localhost:${server.port}/")
      pageNames.foreach { pageName =>
        val link = s"/root/wiki_test/wiki/${StringUtil.urlEncode(pageName)}\""
        assert(dashboard.contains(link), s"news feed link for $pageName")
      }
    }
  }

  private def content(pageName: String): String = s"content of [$pageName]"

  private def withWiki(f: (TestingGitBucketServer, CloseableHttpClient) => Unit): Unit = {
    Using.resource(new TestingGitBucketServer(19995)) { server =>
      server.client("root", "root").createRepository("wiki_test").autoInit(true).create()

      Using.resource(HttpClients.custom().setDefaultCookieStore(new BasicCookieStore()).build()) { httpClient =>
        post(httpClient, s"http://localhost:${server.port}/signin", "userName" -> "root", "password" -> "root")

        pageNames.foreach { pageName =>
          val status = post(
            httpClient,
            s"http://localhost:${server.port}/root/wiki_test/wiki/_new",
            "pageName" -> pageName,
            "content" -> content(pageName),
            "message" -> s"Create $pageName",
            "currentPageName" -> "",
            "id" -> ""
          )
          assert(status == 302, s"create $pageName")
        }
        f(server, httpClient)
      }
    }
  }

  private def get(httpClient: CloseableHttpClient, url: String): (Int, String) = {
    val request = new HttpGet(url)
    request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
    Using.resource(httpClient.execute(request)) { response =>
      (response.getStatusLine.getStatusCode, EntityUtils.toString(response.getEntity, "UTF-8"))
    }
  }

  private def post(httpClient: CloseableHttpClient, url: String, params: (String, String)*): Int = {
    val request = new HttpPost(url)
    request.setEntity(
      new UrlEncodedFormEntity(JArrays.asList(params.map { case (k, v) => new BasicNameValuePair(k, v) }*), "UTF-8")
    )
    Using.resource(httpClient.execute(request)) { response =>
      EntityUtils.consume(response.getEntity)
      response.getStatusLine.getStatusCode
    }
  }
}
