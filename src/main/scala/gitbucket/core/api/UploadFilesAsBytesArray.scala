package gitbucket.core.api


case class UploadFilesAsBytesArray(branch: String, fileName:String, fileBytes: Array[Byte], message: String)
{
  
}
