Activity Timeline
========
GitBucket records several types of user activity to ```ACTIVITY``` table. Activity types are shown below:

type              | message                                              | additional information
------------------|------------------------------------------------------|------------------------
create_repository |$user created $owner/$repo                            |-
open_issue        |$user opened issue $owner/$repo#$issueId              |-
close_issue       |$user closed issue $owner/$repo#$issueId              |-
close_issue       |$user closed pull request $owner/$repo#$issueId       |-
reopen_issue      |$user reopened issue $owner/$repo#$issueId            |-
comment_issue     |$user commented on issue $owner/$repo#$issueId        |-
comment_issue     |$user commented on pull request $owner/$repo#$issueId |-
create_wiki       |$user created the $owner/$repo wiki                   |$page
edit_wiki         |$user edited the $owner/$repo wiki                    |$page<br>$page:$commitId(since 1.5)
push              |$user pushed to $owner/$repo#$branch to $owner/$repo  |$commitId:$shortMessage\n*
create_tag        |$user created tag $tag at $owner/$repo                |-
create_branch     |$user created branch $branch at $owner/$repo          |-
delete_branch     |$user deleted branch $branch at $owner/$repo          |-
fork              |$user forked $owner/$repo to $owner/$repo             |-
open_pullreq      |$user opened pull request $owner/$repo#issueId        |-
merge_pullreq     |$user merge pull request $owner/$repo#issueId         |-
