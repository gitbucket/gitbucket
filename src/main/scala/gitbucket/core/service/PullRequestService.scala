package gitbucket.core.service

import gitbucket.core.model.{Issue, PullRequest, CommitStatus, CommitState, CommitComment}
import gitbucket.core.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import difflib.{Delta, DiffUtils}
import gitbucket.core.util.ControlUtil._
import gitbucket.core.util.Directory._
import gitbucket.core.util.Implicits._
import gitbucket.core.util.JGitUtil
import gitbucket.core.util.JGitUtil.{CommitInfo, DiffInfo}
import gitbucket.core.view
import gitbucket.core.view.helpers
import org.eclipse.jgit.api.Git
import scala.collection.JavaConverters._


trait PullRequestService { self: IssuesService with CommitsService =>
  import PullRequestService._

  def getPullRequest(owner: String, repository: String, issueId: Int)
                    (implicit s: Session): Option[(Issue, PullRequest)] =
    getIssue(owner, repository, issueId.toString).flatMap{ issue =>
      PullRequests.filter(_.byPrimaryKey(owner, repository, issueId)).firstOption.map{
        pullreq => (issue, pullreq)
      }
    }

  def updateCommitId(owner: String, repository: String, issueId: Int, commitIdTo: String, commitIdFrom: String)
                    (implicit s: Session): Unit =
    PullRequests.filter(_.byPrimaryKey(owner, repository, issueId))
                .map(pr => pr.commitIdTo -> pr.commitIdFrom)
                .update((commitIdTo, commitIdFrom))

  def getPullRequestCountGroupByUser(closed: Boolean, owner: Option[String], repository: Option[String])
                                    (implicit s: Session): List[PullRequestCount] =
    PullRequests
      .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
      .filter { case (t1, t2) =>
        (t2.closed         === closed.bind) &&
        (t1.userName       === owner.get.bind, owner.isDefined) &&
        (t1.repositoryName === repository.get.bind, repository.isDefined)
      }
      .groupBy { case (t1, t2) => t2.openedUserName }
      .map { case (userName, t) => userName -> t.length }
      .sortBy(_._2 desc)
      .list
      .map { x => PullRequestCount(x._1, x._2) }

//  def getAllPullRequestCountGroupByUser(closed: Boolean, userName: String)(implicit s: Session): List[PullRequestCount] =
//    PullRequests
//      .innerJoin(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
//      .innerJoin(Repositories).on { case ((t1, t2), t3) => t2.byRepository(t3.userName, t3.repositoryName) }
//      .filter { case ((t1, t2), t3) =>
//        (t2.closed === closed.bind) &&
//          (
//            (t3.isPrivate === false.bind) ||
//            (t3.userName  === userName.bind) ||
//            (Collaborators.filter { t4 => t4.byRepository(t3.userName, t3.repositoryName) && (t4.collaboratorName === userName.bind)} exists)
//          )
//      }
//      .groupBy { case ((t1, t2), t3) => t2.openedUserName }
//      .map { case (userName, t) => userName -> t.length }
//      .sortBy(_._2 desc)
//      .list
//      .map { x => PullRequestCount(x._1, x._2) }

  def createPullRequest(originUserName: String, originRepositoryName: String, issueId: Int,
        originBranch: String, requestUserName: String, requestRepositoryName: String, requestBranch: String,
        commitIdFrom: String, commitIdTo: String)(implicit s: Session): Unit =
    PullRequests insert PullRequest(
      originUserName,
      originRepositoryName,
      issueId,
      originBranch,
      requestUserName,
      requestRepositoryName,
      requestBranch,
      commitIdFrom,
      commitIdTo)

  def getPullRequestsByRequest(userName: String, repositoryName: String, branch: String, closed: Boolean)
                              (implicit s: Session): List[PullRequest] =
    PullRequests
      .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
      .filter { case (t1, t2) =>
        (t1.requestUserName       === userName.bind) &&
        (t1.requestRepositoryName === repositoryName.bind) &&
        (t1.requestBranch         === branch.bind) &&
        (t2.closed                === closed.bind)
      }
      .map { case (t1, t2) => t1 }
      .list

  /**
   * for repository viewer.
   * 1. find pull request from from `branch` to othre branch on same repository
   *   1. return if exists pull request to `defaultBranch`
   *   2. return if exists pull request to othre branch
   * 2. return None
   */
  def getPullRequestFromBranch(userName: String, repositoryName: String, branch: String, defaultBranch: String)
                              (implicit s: Session): Option[(PullRequest, Issue)] =
    PullRequests
      .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
      .filter { case (t1, t2) =>
        (t1.requestUserName       === userName.bind) &&
        (t1.requestRepositoryName === repositoryName.bind) &&
        (t1.requestBranch         === branch.bind) &&
        (t1.userName              === userName.bind) &&
        (t1.repositoryName        === repositoryName.bind) &&
        (t2.closed                === false.bind)
      }
      .sortBy{ case (t1, t2) => t1.branch =!= defaultBranch.bind }
      .firstOption

  /**
   * Fetch pull request contents into refs/pull/${issueId}/head and update pull request table.
   */
  def updatePullRequests(owner: String, repository: String, branch: String)(implicit s: Session): Unit =
    getPullRequestsByRequest(owner, repository, branch, false).foreach { pullreq =>
      if(Repositories.filter(_.byRepository(pullreq.userName, pullreq.repositoryName)).exists.run){
        // Update the git repository
        val (commitIdTo, commitIdFrom) = JGitUtil.updatePullRequest(
          pullreq.userName, pullreq.repositoryName, pullreq.branch, pullreq.issueId,
          pullreq.requestUserName, pullreq.requestRepositoryName, pullreq.requestBranch)

        // Collect comment positions
        val positions = getCommitComments(pullreq.userName, pullreq.repositoryName, pullreq.commitIdTo, true)
          .collect {
            case CommitComment(_, _, _, commentId, _, _, Some(file), None, Some(newLine), _, _, _) => (file, commentId, Right(newLine))
            case CommitComment(_, _, _, commentId, _, _, Some(file), Some(oldLine), None, _, _, _) => (file, commentId, Left(oldLine))
          }
          .groupBy { case (file, _, _) => file }
          .map { case (file, comments) => file ->
            comments.map { case (_, commentId, lineNumber) => (commentId, lineNumber) }
          }

        // Update comments position
        updatePullRequestCommentPositions(positions, pullreq.requestUserName, pullreq.requestRepositoryName, pullreq.commitIdTo, commitIdTo)

        // Update commit id in the PULL_REQUEST table
        updateCommitId(pullreq.userName, pullreq.repositoryName, pullreq.issueId, commitIdTo, commitIdFrom)
      }
    }

  def getPullRequestByRequestCommit(userName: String, repositoryName: String, toBranch:String, fromBranch: String, commitId: String)
                              (implicit s: Session): Option[(PullRequest, Issue)] = {
    if(toBranch == fromBranch){
      None
    } else {
      PullRequests
        .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
        .filter { case (t1, t2) =>
          (t1.userName              === userName.bind) &&
          (t1.repositoryName        === repositoryName.bind) &&
          (t1.branch                === toBranch.bind) &&
          (t1.requestUserName       === userName.bind) &&
          (t1.requestRepositoryName === repositoryName.bind) &&
          (t1.requestBranch         === fromBranch.bind) &&
          (t1.commitIdTo            === commitId.bind)
        }
        .firstOption
    }
  }

  private def updatePullRequestCommentPositions(positions: Map[String, Seq[(Int, Either[Int, Int])]], userName: String, repositoryName: String,
                                                oldCommitId: String, newCommitId: String)(implicit s: Session): Unit = {

    val (_, diffs) = getRequestCompareInfo(userName, repositoryName, oldCommitId, userName, repositoryName, newCommitId)

    val patchs = positions.map { case (file, _) =>
      diffs.find(x => x.oldPath == file).map { diff =>
        (diff.oldContent, diff.newContent) match {
          case (Some(oldContent), Some(newContent)) => {
            val oldLines = oldContent.replace("\r\n", "\n").split("\n")
            val newLines = newContent.replace("\r\n", "\n").split("\n")
            file -> Option(DiffUtils.diff(oldLines.toList.asJava, newLines.toList.asJava))
          }
          case _ =>
            file -> None
        }
      }.getOrElse {
        file -> None
      }
    }

    positions.foreach { case (file, comments) =>
      patchs(file) match {
        case Some(patch) => file -> comments.foreach { case (commentId, lineNumber) => lineNumber match {
          case Left(oldLine)  => updateCommitCommentPosition(commentId, newCommitId, Some(oldLine), None)
          case Right(newLine) =>
            var counter = newLine
            patch.getDeltas.asScala.filter(_.getOriginal.getPosition < newLine).foreach { delta =>
              delta.getType match {
                case Delta.TYPE.CHANGE =>
                  if(delta.getOriginal.getPosition <= newLine - 1 && newLine <= delta.getOriginal.getPosition + delta.getRevised.getLines.size){
                    counter = -1
                  } else {
                    counter = counter + (delta.getRevised.getLines.size - delta.getOriginal.getLines.size)
                  }
                case Delta.TYPE.INSERT => counter = counter + delta.getRevised.getLines.size
                case Delta.TYPE.DELETE => counter = counter - delta.getOriginal.getLines.size
              }
            }
            if(counter >= 0){
              updateCommitCommentPosition(commentId, newCommitId, None, Some(counter))
            }
        }}
        case _ => comments.foreach { case (commentId, lineNumber) => lineNumber match {
          case Right(oldLine) => updateCommitCommentPosition(commentId, newCommitId, Some(oldLine), None)
          case Left(newLine)  => updateCommitCommentPosition(commentId, newCommitId, None, Some(newLine))
        }}
      }
    }
  }

  def getRequestCompareInfo(userName: String, repositoryName: String, branch: String,
                            requestUserName: String, requestRepositoryName: String, requestCommitId: String): (Seq[Seq[CommitInfo]], Seq[DiffInfo]) =
    using(
      Git.open(getRepositoryDir(userName, repositoryName)),
      Git.open(getRepositoryDir(requestUserName, requestRepositoryName))
    ){ (oldGit, newGit) =>
      val oldId = oldGit.getRepository.resolve(branch)
      val newId = newGit.getRepository.resolve(requestCommitId)

      val commits = newGit.log.addRange(oldId, newId).call.iterator.asScala.map { revCommit =>
        new CommitInfo(revCommit)
      }.toList.splitWith { (commit1, commit2) =>
        helpers.date(commit1.commitTime) == view.helpers.date(commit2.commitTime)
      }

      val diffs = JGitUtil.getDiffs(newGit, oldId.getName, newId.getName, true)

      (commits, diffs)
    }

}

object PullRequestService {

  val PullRequestLimit = 25

  case class PullRequestCount(userName: String, count: Int)

  case class MergeStatus(
    hasConflict: Boolean,
    commitStatues:List[CommitStatus],
    branchProtection: ProtectedBranchService.ProtectedBranchInfo,
    branchIsOutOfDate: Boolean,
    hasUpdatePermission: Boolean,
    needStatusCheck: Boolean,
    hasMergePermission: Boolean,
    commitIdTo: String){

    val statuses: List[CommitStatus] =
      commitStatues ++ (branchProtection.contexts.toSet -- commitStatues.map(_.context).toSet).map(CommitStatus.pending(branchProtection.owner, branchProtection.repository, _))
    val hasRequiredStatusProblem = needStatusCheck && branchProtection.contexts.exists(context => statuses.find(_.context == context).map(_.state) != Some(CommitState.SUCCESS))
    val hasProblem = hasRequiredStatusProblem || hasConflict || (!statuses.isEmpty && CommitState.combine(statuses.map(_.state).toSet) != CommitState.SUCCESS)
    val canUpdate = branchIsOutOfDate && !hasConflict
    val canMerge = hasMergePermission && !hasConflict && !hasRequiredStatusProblem
    lazy val commitStateSummary:(CommitState, String) = {
      val stateMap = statuses.groupBy(_.state)
      val state = CommitState.combine(stateMap.keySet)
      val summary = stateMap.map{ case (keyState, states) => states.size+" "+keyState.name }.mkString(", ")
      state -> summary
    }
    lazy val statusesAndRequired:List[(CommitStatus, Boolean)] = statuses.map{ s => s -> branchProtection.contexts.exists(_==s.context) }
    lazy val isAllSuccess = commitStateSummary._1==CommitState.SUCCESS
  }
}
