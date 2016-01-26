package gitbucket.core.service

import gitbucket.core.model._

import org.specs2.mutable.Specification

class LabelsServiceSpec extends Specification with ServiceSpecBase {
  "getLabels" should {
    "be empty when not have any labels" in { withTestDB { implicit session =>
      generateNewUserWithDBRepository("user1", "repo1")

      generateNewUserWithDBRepository("user1", "repo2")
      dummyService.createLabel("user1", "repo2", "label1", "000000")

      generateNewUserWithDBRepository("user2", "repo1")
      dummyService.createLabel("user2", "repo1", "label1", "000000")

      dummyService.getLabels("user1", "repo1") must haveSize(0)
    }}
    "return contained labels" in { withTestDB { implicit session =>
      generateNewUserWithDBRepository("user1", "repo1")
      val labelId1 = dummyService.createLabel("user1", "repo1", "label1", "000000")
      val labelId2 = dummyService.createLabel("user1", "repo1", "label2", "ffffff")

      generateNewUserWithDBRepository("user1", "repo2")
      dummyService.createLabel("user1", "repo2", "label1", "000000")

      generateNewUserWithDBRepository("user2", "repo1")
      dummyService.createLabel("user2", "repo1", "label1", "000000")

      def getLabels = dummyService.getLabels("user1", "repo1")

      getLabels must haveSize(2)
      getLabels must_== List(
        Label("user1", "repo1", labelId1, "label1", "000000"),
        Label("user1", "repo1", labelId2, "label2", "ffffff"))
    }}
  }
  "getLabel" should {
    "return None when the label not exist" in { withTestDB { implicit session =>
      generateNewUserWithDBRepository("user1", "repo1")

      dummyService.getLabel("user1", "repo1", 1) must beNone
      dummyService.getLabel("user1", "repo1", "label1") must beNone
    }}
    "return a label fetched by label id" in { withTestDB { implicit session =>
      generateNewUserWithDBRepository("user1", "repo1")
      val labelId1 = dummyService.createLabel("user1", "repo1", "label1", "000000")
      dummyService.createLabel("user1", "repo1", "label2", "ffffff")

      generateNewUserWithDBRepository("user1", "repo2")
      dummyService.createLabel("user1", "repo2", "label1", "000000")

      generateNewUserWithDBRepository("user2", "repo1")
      dummyService.createLabel("user2", "repo1", "label1", "000000")

      def getLabel = dummyService.getLabel("user1", "repo1", labelId1)
      getLabel must_== Some(Label("user1", "repo1", labelId1, "label1", "000000"))
    }}
    "return a label fetched by label name" in { withTestDB { implicit session =>
      generateNewUserWithDBRepository("user1", "repo1")
      val labelId1 = dummyService.createLabel("user1", "repo1", "label1", "000000")
      dummyService.createLabel("user1", "repo1", "label2", "ffffff")

      generateNewUserWithDBRepository("user1", "repo2")
      dummyService.createLabel("user1", "repo2", "label1", "000000")

      generateNewUserWithDBRepository("user2", "repo1")
      dummyService.createLabel("user2", "repo1", "label1", "000000")

      def getLabel = dummyService.getLabel("user1", "repo1", "label1")
      getLabel must_== Some(Label("user1", "repo1", labelId1, "label1", "000000"))
    }}
  }
  "createLabel" should {
    "return accurate label id" in { withTestDB { implicit session =>
      generateNewUserWithDBRepository("user1", "repo1")
      generateNewUserWithDBRepository("user1", "repo2")
      generateNewUserWithDBRepository("user2", "repo1")
      dummyService.createLabel("user1", "repo1", "label1", "000000")
      dummyService.createLabel("user1", "repo2", "label1", "000000")
      dummyService.createLabel("user2", "repo1", "label1", "000000")
      val labelId = dummyService.createLabel("user1", "repo1", "label2", "000000")
      labelId must_== 4
      def getLabel = dummyService.getLabel("user1", "repo1", labelId)
      getLabel must_== Some(Label("user1", "repo1", labelId, "label2", "000000"))
    }}
  }
  "updateLabel" should {
    "change target label" in { withTestDB { implicit session =>
      generateNewUserWithDBRepository("user1", "repo1")
      generateNewUserWithDBRepository("user1", "repo2")
      generateNewUserWithDBRepository("user2", "repo1")
      val labelId = dummyService.createLabel("user1", "repo1", "label1", "000000")
      dummyService.createLabel("user1", "repo2", "label1", "000000")
      dummyService.createLabel("user2", "repo1", "label1", "000000")
      dummyService.updateLabel("user1", "repo1", labelId, "updated-label", "ffffff")
      def getLabel = dummyService.getLabel("user1", "repo1", labelId)
      getLabel must_== Some(Label("user1", "repo1", labelId, "updated-label", "ffffff"))
    }}
  }
  "deleteLabel" should {
    "remove target label" in { withTestDB { implicit session =>
      generateNewUserWithDBRepository("user1", "repo1")
      generateNewUserWithDBRepository("user1", "repo2")
      generateNewUserWithDBRepository("user2", "repo1")
      val labelId = dummyService.createLabel("user1", "repo1", "label1", "000000")
      dummyService.createLabel("user1", "repo2", "label1", "000000")
      dummyService.createLabel("user2", "repo1", "label1", "000000")
      dummyService.deleteLabel("user1", "repo1", labelId)
      dummyService.getLabel("user1", "repo1", labelId) must beNone
    }}
  }
}
