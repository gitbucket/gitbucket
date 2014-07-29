package util

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.GZIPOutputStream

import org.apache.commons.io.FileUtils
import org.apache.tools.tar.{TarEntry, TarOutputStream}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.MutableObjectId
import org.eclipse.jgit.treewalk.TreeWalk
import util.ControlUtil._
import util.Directory._

/**
 * Archiver for Git Repository
 */
trait RepositoryArchiver {

  /**
   * Archive git repository
   * @param owner repository owner
   * @param repositoryName repository name
   * @param revision revision
   * @param file output archived file instance
   * @return archived file instance(same as file)
   */
  def archive(owner: String, repositoryName: String, revision: String, file: File): File

}

/**
 * Zip archiver
 */
object ZipArchiver extends RepositoryArchiver {

  def archive(owner: String, repositoryName: String, revision: String, file: File): File = {
    using(Git.open(getRepositoryDir(owner, repositoryName))){ git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(revision))
      using(new TreeWalk(git.getRepository)){ walk =>
        val reader   = walk.getObjectReader
        val objectId = new MutableObjectId

        using(new ZipOutputStream(new java.io.FileOutputStream(file))){ out =>
          walk.addTree(revCommit.getTree)
          walk.setRecursive(true)

          while(walk.next){
            val name = walk.getPathString
            val mode = walk.getFileMode(0)
            if(mode == FileMode.REGULAR_FILE || mode == FileMode.EXECUTABLE_FILE){
              walk.getObjectId(objectId, 0)
              val entry = new ZipEntry(name)
              val loader = reader.open(objectId)
              entry.setSize(loader.getSize)
              out.putNextEntry(entry)
              loader.copyTo(out)
              out.closeEntry()
            }
          }
        }
      }
    }
    file
  }

}

object TarGzArchiver extends RepositoryArchiver {

  def archive(owner: String, repositoryName: String, revision: String, file: File): File = {
    using(Git.open(getRepositoryDir(owner, repositoryName))){ git =>
      val revCommit = JGitUtil.getRevCommitFromId(git, git.getRepository.resolve(revision))
      using(new TreeWalk(git.getRepository)){ walk =>
        val reader   = walk.getObjectReader
        val objectId = new MutableObjectId

        using(new TarOutputStream(new GZIPOutputStream((new java.io.FileOutputStream(file))))) { out =>
          walk.addTree(revCommit.getTree)
          walk.setRecursive(true)

          while(walk.next){
            val name = walk.getPathString
            val mode = walk.getFileMode(0)
            if(mode == FileMode.REGULAR_FILE || mode == FileMode.EXECUTABLE_FILE){
              walk.getObjectId(objectId, 0)
              val entry = new TarEntry(name)
              val loader = reader.open(objectId)
              entry.setSize(loader.getSize)
              out.putNextEntry(entry)
              loader.copyTo(out)
              out.closeEntry()
            }
          }
        }
      }
    }
    file
  }

}
