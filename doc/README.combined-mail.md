Combined Mail
=============

As of version 3.4, gitbucket sends out individual mails to each recipient.
This way, one recipient doesn't know about the other recipients.

For me, I'd like to see a list of all recipients. This way, I can verify
whether a certain person got a notification of not.

So I've created a patch which collects all recipients within the "to"
list and sends the mail once to all of them.

To verify this, you can:

* Start a dummy email server: `python -m smtpd -n -c DebuggingServer localhost:2525`
* Launch gitbucket
* Configure it to use the dummy email server
* Perform some actions within gitbucket that trigger emails

For me, the dummy email server produced this output:

```
---------- MESSAGE FOLLOWS ----------
Date: Sat, 27 Jun 2015 08:12:14 +0200 (CEST)
From: root <notifications@gitbucket.com>
To: root@localhost, a@test.com, b@test.com
Message-ID: <646588341.7.1435385534754.JavaMail.uli@ulinuc>
Subject: [r] Test-Ticket (#1)
MIME-Version: 1.0
Content-Type: multipart/mixed; 
	boundary="----=_Part_6_1497724562.1435385534749"
X-Peer: 127.0.0.1

------=_Part_6_1497724562.1435385534749
Content-Type: text/html; charset=UTF-8
Content-Transfer-Encoding: 7bit


root just wrote:<br/>
<p>Another comment</p><br/>
--<br/>
<a href="http://localhost:8080/root/r/issues/1#comment-3">View it on GitBucket</a>
    
------=_Part_6_1497724562.1435385534749--
------------ END MESSAGE ------------
```
