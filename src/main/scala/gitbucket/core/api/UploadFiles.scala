package gitbucket.core.api


case class UploadFiles(branch: String, path: String, fileIds : Map[String,String], message: String)
{
  def isValid: Boolean = {

    fileIds.size > 0
  }


}
