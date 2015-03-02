package gitbucket.core.view

/**
 * Provides control information for pagination.
 * This class is used by paginator.scala.html.
 *
 * @param page the current page number
 * @param count the total record count
 * @param limit the limit record count per one page
 * @param width the width (number of cells) of the paginator
 */
case class Pagination(page: Int, count: Int, limit: Int, width: Int){

  /**
   * max page number
   */
  val max = (count - 1) / limit + 1

  /**
   * whether to omit the left side
   */
  val omitLeft  = width / 2 < page

  /**
   * whether to omit the right side
   */
  val omitRight = max - width / 2 > page

  /**
   * Returns true if given page number is visible.
   */
  def visibleFor(i: Int): Boolean = {
    if(i == 1 || i == max){
      true
    } else {
      val leftRange  = page - width / 2 + (if(omitLeft) 2 else 0)
      val rightRange = page + width / 2 - (if(omitRight) 2 else 0)

      val fixedRange = if(leftRange < 1){
        (1, rightRange + (leftRange * -1) + 1)
      } else if(rightRange > max){
        (leftRange - (rightRange - max), max)
      } else {
        (leftRange, rightRange)
      }

      (i >= fixedRange._1 && i <= fixedRange._2)
    }
  }
}
