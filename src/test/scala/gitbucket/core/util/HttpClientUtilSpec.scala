package gitbucket.core.util

import org.scalatest.FunSuite

class HttpClientUtilSpec extends FunSuite {

  test("isPrivateAddress") {
    assert(HttpClientUtil.isPrivateAddress("localhost") == true)
    assert(HttpClientUtil.isPrivateAddress("192.168.10.2") == true)
    assert(HttpClientUtil.isPrivateAddress("169.254.169.254") == true)
    assert(HttpClientUtil.isPrivateAddress("www.google.com") == false)
  }

}
