package object model {
  import scala.slick.lifted.MappedTypeMapper

  // java.util.Date TypeMapper
  implicit val dateTypeMapper = MappedTypeMapper.base[java.util.Date, java.sql.Timestamp](
      d => new java.sql.Timestamp(d.getTime),
      t => new java.util.Date(t.getTime)
  )

  /**
   * Returns system date.
   */
  def currentDate = new java.util.Date()

}