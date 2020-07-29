package gitbucket.core.service

import gitbucket.core.model._
import org.scalatest.funspec.AnyFunSpec

class LabelsServiceSpec extends AnyFunSpec with ServiceSpecBase {
  describe("getLabels") {
    it("should be empty when not have any labels") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")

        generateNewUserWithDBRepository("user1", "repo2")
        dummyService.createLabel("user1", "repo2", "label1", "000000")

        generateNewUserWithDBRepository("user2", "repo1")
        dummyService.createLabel("user2", "repo1", "label1", "000000")

        assert(dummyService.getLabels("user1", "repo1").isEmpty)
      }
    }
    it("should return contained labels") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        val labelId1 = dummyService.createLabel("user1", "repo1", "label1", "000000")
        val labelId2 = dummyService.createLabel("user1", "repo1", "label2", "ffffff")

        generateNewUserWithDBRepository("user1", "repo2")
        dummyService.createLabel("user1", "repo2", "label1", "000000")

        generateNewUserWithDBRepository("user2", "repo1")
        dummyService.createLabel("user2", "repo1", "label1", "000000")

        def getLabels = dummyService.getLabels("user1", "repo1")

        assert(getLabels.length == 2)
        assert(
          getLabels == List(
            Label("user1", "repo1", labelId1, "label1", "000000"),
            Label("user1", "repo1", labelId2, "label2", "ffffff")
          )
        )
      }
    }
  }

  describe("getLabel") {
    it("should return None when the label not exist") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")

        assert(dummyService.getLabel("user1", "repo1", 1) == None)
        assert(dummyService.getLabel("user1", "repo1", "label1") == None)
      }
    }
    it("should return a label fetched by label id") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        val labelId1 = dummyService.createLabel("user1", "repo1", "label1", "000000")
        dummyService.createLabel("user1", "repo1", "label2", "ffffff")

        generateNewUserWithDBRepository("user1", "repo2")
        dummyService.createLabel("user1", "repo2", "label1", "000000")

        generateNewUserWithDBRepository("user2", "repo1")
        dummyService.createLabel("user2", "repo1", "label1", "000000")

        def getLabel = dummyService.getLabel("user1", "repo1", labelId1)
        assert(getLabel == Some(Label("user1", "repo1", labelId1, "label1", "000000")))
      }
    }
    it("should return a label fetched by label name") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        val labelId1 = dummyService.createLabel("user1", "repo1", "label1", "000000")
        dummyService.createLabel("user1", "repo1", "label2", "ffffff")

        generateNewUserWithDBRepository("user1", "repo2")
        dummyService.createLabel("user1", "repo2", "label1", "000000")

        generateNewUserWithDBRepository("user2", "repo1")
        dummyService.createLabel("user2", "repo1", "label1", "000000")

        def getLabel = dummyService.getLabel("user1", "repo1", "label1")
        getLabel == Some(Label("user1", "repo1", labelId1, "label1", "000000"))
      }
    }
  }
  describe("createLabel") {
    it("should return accurate label id") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        generateNewUserWithDBRepository("user1", "repo2")
        generateNewUserWithDBRepository("user2", "repo1")
        dummyService.createLabel("user1", "repo1", "label1", "000000")
        dummyService.createLabel("user1", "repo2", "label1", "000000")
        dummyService.createLabel("user2", "repo1", "label1", "000000")
        val labelId = dummyService.createLabel("user1", "repo1", "label2", "000000")
        assert(labelId == 4)
        def getLabel = dummyService.getLabel("user1", "repo1", labelId)
        assert(getLabel == Some(Label("user1", "repo1", labelId, "label2", "000000")))
      }
    }
  }
  describe("updateLabel") {
    it("should change target label") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        generateNewUserWithDBRepository("user1", "repo2")
        generateNewUserWithDBRepository("user2", "repo1")
        val labelId = dummyService.createLabel("user1", "repo1", "label1", "000000")
        dummyService.createLabel("user1", "repo2", "label1", "000000")
        dummyService.createLabel("user2", "repo1", "label1", "000000")
        dummyService.updateLabel("user1", "repo1", labelId, "updated-label", "ffffff")
        def getLabel = dummyService.getLabel("user1", "repo1", labelId)
        assert(getLabel == Some(Label("user1", "repo1", labelId, "updated-label", "ffffff")))
      }
    }
  }
  describe("deleteLabel") {
    it("should remove target label") {
      withTestDB { implicit session =>
        generateNewUserWithDBRepository("user1", "repo1")
        generateNewUserWithDBRepository("user1", "repo2")
        generateNewUserWithDBRepository("user2", "repo1")
        val labelId = dummyService.createLabel("user1", "repo1", "label1", "000000")
        dummyService.createLabel("user1", "repo2", "label1", "000000")
        dummyService.createLabel("user2", "repo1", "label1", "000000")
        dummyService.deleteLabel("user1", "repo1", labelId)
        assert(dummyService.getLabel("user1", "repo1", labelId) == None)
      }
    }
  }
}
