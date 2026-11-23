package gitbucket.core.util

import org.scalatest.funsuite.AnyFunSuite

class HttpClientUtilSpec extends AnyFunSuite {

  test("isPrivateAddress") {
    assert(HttpClientUtil.isPrivateAddress("localhost") == true)
    assert(HttpClientUtil.isPrivateAddress("192.168.10.2") == true)
    assert(HttpClientUtil.isPrivateAddress("169.254.169.254") == true)
    assert(HttpClientUtil.isPrivateAddress("www.google.com") == false)
  }

  test("isPrivateAddress covers the unspecified address and the shared address space") {
    assert(HttpClientUtil.isPrivateAddress("0.0.0.0") == true)
    assert(HttpClientUtil.isPrivateAddress("0") == true)
    assert(HttpClientUtil.isPrivateAddress("::") == true)
    assert(HttpClientUtil.isPrivateAddress("[::]") == true)
    assert(HttpClientUtil.isPrivateAddress("100.64.0.0") == true)
    assert(HttpClientUtil.isPrivateAddress("100.64.0.1") == true)
    assert(HttpClientUtil.isPrivateAddress("100.127.255.255") == true)
    assert(HttpClientUtil.isPrivateAddress("100.63.255.255") == false)
    assert(HttpClientUtil.isPrivateAddress("100.128.0.0") == false)
  }

}
