package gitbucket.core.controller

import gitbucket.core.TestingGitBucketServer
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Need to run `sbt package` before running this test.
 */
class FileUploadControllerSpec extends AnyFunSuite {

  private val noRedirects = RequestConfig.custom().setRedirectsEnabled(false).build()

  private def withClient[T](f: CloseableHttpClient => T): T =
    Using.resource(HttpClients.custom().build())(f)

  private def signin(client: CloseableHttpClient, port: Int): Unit = {
    val post = new HttpPost(s"http://localhost:$port/signin")
    post.setConfig(noRedirects)
    post.setEntity(
      new UrlEncodedFormEntity(
        Seq(new BasicNameValuePair("userName", "root"), new BasicNameValuePair("password", "root")).asJava
      )
    )
    Using.resource(client.execute(post)) { res =>
      EntityUtils.consume(res.getEntity)
      assert(res.getStatusLine.getStatusCode == 302, "signin failed")
    }
  }

  /** Uploads a file of the given size and returns the status code. */
  private def upload(client: CloseableHttpClient, url: String, fileName: String, size: Int): Int = {
    val boundary = "----upload"
    val head = s"--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
      "Content-Type: application/octet-stream\r\n\r\n"
    val tail = s"\r\n--$boundary--\r\n"
    val body = head.getBytes(UTF_8) ++ Array.fill[Byte](size)('a') ++ tail.getBytes(UTF_8)
    val post = new HttpPost(url)
    post.setHeader("Content-Type", s"multipart/form-data; boundary=$boundary")
    post.setEntity(new ByteArrayEntity(body))
    Using.resource(client.execute(post)) { res =>
      EntityUtils.consume(res.getEntity)
      res.getStatusLine.getStatusCode
    }
  }

  private def uploadedFiles(server: TestingGitBucketServer): Seq[File] = {
    def list(dir: File): Seq[File] =
      Option(dir.listFiles).toSeq.flatten.flatMap(f => if (f.isDirectory) list(f) else Seq(f))
    list(new File(server.getDirectory(), "tmp/_upload"))
  }

  test("anonymous users can't upload files") {
    Using.resource(new TestingGitBucketServer(19993)) { server =>
      withClient { client =>
        assert(upload(client, s"http://localhost:${server.port}/upload/tmp", "a.txt", 10) == 400)
        assert(upload(client, s"http://localhost:${server.port}/upload/image", "a.png", 10) == 400)
        assert(uploadedFiles(server).isEmpty)
      }
    }
  }

  test("anonymous users can upload an avatar image if account registration is allowed") {
    Using.resource(new TestingGitBucketServer(19993)) { server =>
      server.updateSettings("allow_account_registration" -> "true")
      withClient { client =>
        assert(upload(client, s"http://localhost:${server.port}/upload/image", "a.png", 10) == 200)
        assert(upload(client, s"http://localhost:${server.port}/upload/tmp", "a.txt", 10) == 400)
        assert(uploadedFiles(server).size == 1)
      }
    }
  }

  test("files larger than upload.maxFileSize are refused") {
    Using.resource(new TestingGitBucketServer(19993)) { server =>
      server.updateSettings("upload.maxFileSize" -> "1000")
      withClient { client =>
        signin(client, server.port)
        val url = s"http://localhost:${server.port}/upload/tmp"
        assert(upload(client, url, "a.txt", 1000) == 200)
        assert(upload(client, url, "a.txt", 1001) == 413)
        assert(uploadedFiles(server).size == 1)
      }
    }
  }

  test("release files are limited by upload.largeMaxFileSize") {
    Using.resource(new TestingGitBucketServer(19993)) { server =>
      server.client("root", "root").createRepository("repo1").autoInit(true).create()
      server.updateSettings("upload.maxFileSize" -> "1000", "upload.largeMaxFileSize" -> "100000")
      withClient { client =>
        signin(client, server.port)
        val url = s"http://localhost:${server.port}/upload/release/root/repo1/v1.0"
        assert(upload(client, url, "a.zip", 50000) == 200)
        assert(upload(client, url, "a.zip", 100001) == 413)
      }
    }
  }
}
