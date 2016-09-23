package gitbucket.core.api


case class UploadFilesAsBytesArray(fileName:String, fileBytes: Array[Byte], message: String)
{
  def isValid: Boolean = {
    fileName.length>0
  }
}
