package view
import java.util.Date
import java.text.SimpleDateFormat

object helpers {
  
  def datetime(date: Date): String = {
    new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(date)
  }
  
  def date(date: Date): String = {
    new SimpleDateFormat("yyyy/MM/dd").format(date)
  }
  
  def cut(message: String, length: Int): String = {
    if(message.length > length){
      message.substring(0, length) + "..."
    } else {
      message
    }
  }
  
}