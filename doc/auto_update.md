Automatic Schema Updating
========
GitBucket uses H2 database to manage project and account data. GitBucket updates database schema automatically in the first run after the upgrading.

To release a new version of GitBucket, add the version definition to the [servlet.AutoUpdate](https://github.com/takezoe/gitbucket/blob/master/src/main/scala/servlet/AutoUpdateListener.scala) at first.

```scala
object AutoUpdate {
  ...
  /**
   * The history of versions. A head of this sequence is the current BitBucket version.
   */
  val versions = Seq(
      Version(1, 0)
  )
  ...
```

Next, add a SQL file which updates database schema into [/src/main/resources/update/](https://github.com/takezoe/gitbucket/tree/master/src/main/resources/update) as ```MAJOR_MINOR.sql```.

GitBucket stores the current version to ```GITBUCKET_HOME/version``` and checks it at start-up. If the stored version differs from the actual version, it executes differences of SQL files between the stored version and the actual version. And ```GITBUCKET_HOME/version``` is updated by the actual version.

We can also add any Scala code for upgrade GitBucket which modifies ｒesources other than database. Override ```Version.update``` like below:

```scala
val versions = Seq(
  new Version(1, 3){
    override def update(conn: Connection): Unit = {
      super.update(conn)
      // Add any code here!
    }
  },
  Version(1, 2),
  Version(1, 1),
  Version(1, 0)
)
```
