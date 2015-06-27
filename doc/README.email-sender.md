Email Sender
============

As of gitbucket 3.4, the sender of the notification emails is build like this:

* {fromName} {fromAddress} ... in case you configured both of them
* {loginName} notifications@gitbucket.com ... in case you configured none of them
* {fromAddress} ... in case you configured only the from address

For me, I'd like to see this:

* {loginName} {fromAddress} ... when from name isn't configured
