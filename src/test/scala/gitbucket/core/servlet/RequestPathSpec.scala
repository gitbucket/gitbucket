package gitbucket.core.servlet

import gitbucket.core.TestingGitBucketServer
import org.scalatest.funsuite.AnyFunSuite

import java.io.{BufferedReader, InputStreamReader}
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Using

/**
 * Need to run `sbt package` before running this test.
 */
class RequestPathSpec extends AnyFunSuite {

  /** Sends the path as is (HTTP clients would remove the dot segments) and returns the status code. */
  private def rawGet(port: Int, path: String, credentials: Option[(String, String)] = None): Int = {
    Using.resource(new Socket("localhost", port)) { socket =>
      val authorization = credentials
        .map { case (login, password) =>
          "Authorization: Basic " + Base64.getEncoder.encodeToString(s"$login:$password".getBytes("UTF-8")) + "\r\n"
        }
        .getOrElse("")
      val request = s"GET $path HTTP/1.1\r\nHost: localhost:$port\r\nUser-Agent: git/2.43.0\r\n" +
        authorization + "Connection: close\r\n\r\n"
      socket.getOutputStream.write(request.getBytes(StandardCharsets.US_ASCII))
      socket.getOutputStream.flush()
      val statusLine =
        new BufferedReader(new InputStreamReader(socket.getInputStream, StandardCharsets.US_ASCII)).readLine()
      statusLine.split(" ")(1).toInt
    }
  }

  test("paths with dot segments are rejected") {
    Using.resource(new TestingGitBucketServer(19993)) { server =>
      server.client("root", "root").createRepository("public_repo").autoInit(true).create()
      server.createUser("alice", "alice", "alice@example.com", "root", "root")
      server.client("alice", "alice").createRepository("private_repo").private_(true).autoInit(true).create()
      server.createUser("bob", "bob", "bob@example.com", "root", "root")
      server.client("bob", "bob").createRepository("own_repo").autoInit(true).create()

      val port = server.port
      val upload = "info/refs?service=git-upload-pack"
      val receive = "info/refs?service=git-receive-pack"

      // Normal requests
      assert(rawGet(port, s"/git/root/public_repo.git/$upload") == 200)
      assert(rawGet(port, s"/git/alice/private_repo.git/$upload") == 401)
      assert(rawGet(port, s"/git/alice/private_repo.git/$receive", Some(("bob", "bob"))) == 401)

      // Dot segments, also percent-encoded or behind an encoded slash
      val anonymous = Seq(
        s"/git/root/public_repo.git/../../alice/private_repo.git/$upload",
        s"/git/root/public_repo.git/%2e%2e/%2E%2E/alice/private_repo.git/$upload",
        s"/git/root/public_repo.git/.%2e/..%2Falice/private_repo.git/$upload",
        s"/git/root/./public_repo.git/$upload",
        "/root/public_repo/../../alice/private_repo",
        "/root%2F../alice/private_repo"
      ).map(path => path -> rawGet(port, path))
      val signedIn = Seq(s"/git/bob/own_repo.git/../../alice/private_repo.git/$receive")
        .map(path => path -> rawGet(port, path, Some(("bob", "bob"))))
      val accepted = (anonymous ++ signedIn).filter(_._2 != 400)
      assert(accepted.isEmpty, accepted.map { case (path, status) => s"$status $path" }.mkString("\n", "\n", ""))
    }
  }
}
