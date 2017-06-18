Notification Email
========

GitBucket can send email notification to users if this feature is enabled by an administrator.

The timing of the notification are as follows:

##### at the issue registration (new issue, new pull request)
When a record is saved into the ```ISSUE``` table, GitBucket does the notification.

##### at the comment registration
Among the records in the ```ISSUE_COMMENT``` table, them to be counted as a comment (i.e. the record ```ACTION``` column value is "comment" or "close_comment" or "reopen_comment") are saved, GitBucket does the notification.

##### at the status update (close, reopen, merge)
When the ```CLOSED``` column value is updated, GitBucket does the notification.

Notified users are as follows:

* individual repository's owner
* group members of group repository
* collaborators
* participants

However, the person performing the operation is excluded from the notification.
