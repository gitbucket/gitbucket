package gitbucket.core.api


case class UploadFilesAsBytesArray(branch: String, fileName:String, fileBytes: Array[Byte], message: String)
{
  def isValid: Boolean = {
    branch.length>0 &&
    fileName.length>0
  }
}
